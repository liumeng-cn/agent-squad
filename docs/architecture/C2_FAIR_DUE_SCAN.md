# C2 多用户到期命令公平扫描

> 状态：源码已写入 / 数据库回归和压力验证 NOT_RUN。范围仅为待领取命令发现，不改变 AgentScope 原生运行权威。

## 一、业务目标

当用户 A 拥有大量到期命令，而用户 B 只有少量到期命令时，原扫描按照所有命令的 `due_at,id` 排序并固定 `LIMIT 20`，可能导致 B 一直进不了候选窗口。本次目标是按用户轮次选取候选：优先取每个用户的第 1 条，再取每个用户的第 2 条，以减少单用户积压造成的扫描饥饿。

该规则只调整**候选命令发现**：是否真正获得命令领取权，仍由 `CommandClaimService` 与 `DeliveryMapper.claim` 的原事务、状态和租约规则确定。

## 二、排序与过滤规则

1. 命令类型限定为原来的 `SUBMIT / MESSAGE_SUBMIT / MASTER_SIGNAL / TASK_DIRECTIVE / STOP / RESUME / RESPOND`。
2. 仅纳入已经到期的 `PENDING`，或 `delivery_lease_until` 已经过期的 `DELIVERING`。
3. 同用户内部按**可再次领取时间**升序排序：前者使用 `due_at`，后者使用 `delivery_lease_until`，相同时间按自增 `id`。
4. 使用 `ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY ...)` 生成 `user_position`，全局先按 `user_position`、再按领取时间、ID 排序，最终仍然只取 20 条。
5. 保留 `MESSAGE_SUBMIT` 对同 Binding、同代际更早回复未完成时的 `NOT EXISTS` 校验。
6. 排除 `sq_user` 中明确为 `DISABLED` 的账号，但不在扫描阶段把所有没有 `sq_user` 行的本地开发主体直接拒绝；真正的账号活动状态检查仍在领取/业务安全点执行。

## 三、现有链路与事务边界

```mermaid
flowchart TD
    A[定时扫描到期持久命令] --> B[按用户轮次形成最多20个候选]
    B --> C[CommandClaimService.claim]
    C --> D[校验用户状态与Scope归属]
    D --> E{同Binding历史回复是否阻断}
    E -->|是| F[放弃本次领取，等待后续扫描]
    E -->|否| G[使用原token和lease的CAS领取]
    G --> H[原生AgentScope投递与持久收据]
```

- SQL 映射仅执行候选查询，不能在扫描阶段写执行状态。
- 同一命令即使被多个进程同时扫描，仍须由原领取事务的 `UPDATE ... WHERE status/lease` 保证唯一有效令牌。
- 已禁用用户的命令不会在扫描查询中被主动改成终态；重新启用后是否继续领取取决于原状态、租约、Scope 和业务授权。

## 四、数据库与容量影响

本次使用 MySQL 8 窗口函数对**全部符合条件的到期命令**排名，可能产生排序与临时表开销。原有 `idx_command_due(status,due_at)` 不代表该查询一定能高效使用索引。没有真实 `EXPLAIN ANALYZE` 及规模化压测证据前，不增加未经确认的索引，也不提高原扫描上限 20。

如果积压量增大，后续 C2 需设计可持久化的用户轮询游标/领取配额，与多实例调度和运行 Slot 一并验证，不能把此窗口查询当作最终公平调度器。

## 五、本地回归用例（均 NOT_RUN）

| 场景 | 准备数据 | 预期 |
| --- | --- | --- |
| 双用户积压 | A 有 100 条到期命令，B 有 1 条 | B 的命令能进入首个 20 条扫描结果 |
| 多用户公平 | 3 个用户分别有 30/2/1 条 | 先覆盖不同用户首条，再进入下一轮 |
| 同用户稳定顺序 | 同一用户多个相同到期时间 | 按 `id` 升序 |
| 租约已过期 | `DELIVERING` 且租约早于当前时间 | 按租约时间参与排名 |
| 未过期租约 | `DELIVERING` 且租约等于或晚于当前时间 | 不进入扫描 |
| 非到期消息 | `PENDING` 但 `due_at` 在未来 | 不进入扫描 |
| 旧消息阻断 | 同 Binding 更早回复处于阻断状态 | 后续 `MESSAGE_SUBMIT` 不被扫描 |
| 已禁用账号 | `sq_user.status='DISABLED'` | 不占用扫描窗口 |
| 兼容本地账号 | `sq_user` 中没有该本地 userId | 不因账号表缺行而直接过滤 |
| 双进程并发 | 同时查询相同候选并尝试 claim | 最多一个原有效令牌获得领取权 |
| 高积压性能 | 典型 1万/10万待处理规模 | 以真实执行计划、延迟和数据库负载判定能否放量 |

## 六、后续必须实现

- C2 正式每用户/全局执行容量 Slot 和公平调度，不使用短期投递租约冒充运行 Slot。
- 跨进程 Master/Worker 恢复与接管、并行取消及幂等副作用验证。
- 在可用的隔离 MySQL 8 环境执行 `EXPLAIN ANALYZE` 和上述真实回归；未执行前不具备上线准入资格。
