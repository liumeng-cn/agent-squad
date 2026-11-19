# 实际运行验证与复核入口

所有实验仅写临时目录及本研究目录，不访问项目数据库，不启动或修改业务应用。源码来自用户提供仓库的准确 Tag 导出，而非工作目录 v2.0.4。

## 官方测试

【运行验证】直接编译未修改的官方测试源码，使用项目既有测试 classpath、缓存的目标 SDK 与 JDBC Jar、H2及JUnit Platform Launcher运行。**215项通过，0失败、0跳过**。这不是官方全仓Maven构建。完整类名及摘要见 [official-test-results.json](official-test-results.json)。

包含 Session 提交幂等、孤儿提交、丢失已提交对象、旧writer fencing、writer seal、Inbox崩溃恢复、执行身份、检查点恢复、State版本契约、JsonFile状态、JDBC/H2 KV与State、Workspace日志发现、路径安全、文件移动删除、RemoteFilesystem CAS和隔离测试。故障日志中的 injected 异常是官方断言故障场景，不是测试失败。

JDBC证据限于H2方言，不可称为MySQL/PostgreSQL服务实测。Redis源码已深入读取，但本机无redis-server与目标扩展Jar，本轮未安装或启动Redis服务，故没有Redis服务实测。源码问题已回答，运行部署能力保持未验证。

## 三个独立JVM与强杀

【运行验证】A取得3秒writer lease并提交seq=1；B在租约有效时竞争被拒绝；对A发送SIGKILL，退出码-9；等待3.2秒后由C打开同目录，读回seq=1、取得epoch=2、拒绝旧epoch=1 writer提交，继续写seq=2。输出见 [process-probe-results.json](process-probe-results.json)。

这是同一Mac的POSIX本地文件共享，不是跨机器、共享盘或Redis/JDBC故障转移。它验证进程消失后已确认提交可读、租约接管与fencing，不证明外部工具副作用exactly-once，也未模拟断电或磁盘损坏。

复核代码只在临时目录执行：

```java
import io.agentscope.core.session.*;
import io.agentscope.harness.agent.session.LocalSessionStorage;
import java.nio.file.*;
import java.time.*;
import java.util.*;
/** 独立进程日志验证 */
public class ProcessJournalProbe {
    public static void main(String[] args) throws Exception {
        var log = new JournalSessionLog(new LocalSessionStorage(Path.of(args[1])), "probe");
        if (args[0].equals("hold")) {
            var writer = log.acquire("writer-a", Duration.ofSeconds(3));
            log.commit(writer, "batch-a", 0, List.of(new SessionEvent(1,"e1",1,1,"run/start","r","t",true,"{}")));
            System.out.println("READY seq=" + log.head().seq() + " epoch=" + writer.epoch());
            System.out.flush();
            Thread.sleep(60000);
        } else if (args[0].equals("compete")) {
            try { log.acquire("writer-b",Duration.ofSeconds(3)); throw new AssertionError("active lease stolen"); }
            catch (SessionLogException expected) { System.out.println("COMPETITOR_REJECTED"); }
        } else {
            if (log.readAfter(0,10).size()!=1) throw new AssertionError("committed event lost");
            var writer = log.acquire("writer-b",Duration.ofSeconds(3));
            if(writer.epoch()<=1) throw new AssertionError("epoch not advanced");
            try { log.commit(new SessionLog.Writer("writer-a",1),"stale",1,List.of(new SessionEvent(1,"bad",2,1,"run/end","r","t",true,"{}")));throw new AssertionError("stale writer accepted"); }
            catch(SessionLogException expected) { System.out.println("STALE_WRITER_FENCED"); }
            log.commit(writer,"batch-b",1,List.of(new SessionEvent(1,"e2",2,1,"run/end","r","t",true,"{}")));
            if(log.readAfter(0,10).size()!=2)throw new AssertionError("history not continuous");
            System.out.println("RECOVERED seq="+log.head().seq()+" epoch="+writer.epoch());
            log.release(writer);
        }
    }
}

```

## Snapshot真实格式

【运行验证】创建含合成memory/research.txt的tar归档，通过LocalSandboxSnapshot.persist写入；默认codec序列化descriptor后按SandboxSnapshot接口反序列化；restore读取字节与原tar完全一致。实际descriptor与大小见 [snapshot-probe-results.json](snapshot-probe-results.json)。这验证本地归档协议，不代表Docker或远程sandbox启停生命周期已验收。

```java
import io.agentscope.harness.agent.sandbox.snapshot.*;
import io.agentscope.core.util.JsonUtils;
import java.nio.file.*;
/** 本地快照验证 */
public class SnapshotProbe {
    public static void main(String[] args) throws Exception {
        var snapshot = new LocalSandboxSnapshot(args[0], "research-snapshot");
        try(var in = Files.newInputStream(Path.of(args[1]))) { snapshot.persist(in); }
        var json = JsonUtils.getJsonCodec().toJson(snapshot);
        var reopened = JsonUtils.getJsonCodec().fromJson(json, SandboxSnapshot.class);
        try(var in = reopened.restore()) {
            if(!java.util.Arrays.equals(in.readAllBytes(),Files.readAllBytes(Path.of(args[1])))) {
                throw new AssertionError("archive changed");
            }
        }
        System.out.println(json);
        System.out.println("SNAPSHOT_ROUNDTRIP_OK");
    }
}

```

## 既有真实样例

[runtime-samples.json](runtime-samples.json) 包含本轮真实AgentState JSON及本地Journal版本前缀解码结果。[历史V2实验](../agentscope-store-20261116/README.md) 仅作历史证据，不能代替当前HTTP和配置复跑。模型接口短问已响应，但不计作存储恢复测试；不保存或展示模型内部推理。

## 官方测试复跑方法

1. 从本地官方仓库以 `git archive v2.1.0-BETA1` 导出到独立临时目录。
2. 使用目标SDK Jar和测试依赖编译official-test-results.json列出的官方测试文件；JDBC测试同时编译H2TestSupport，Harness隔离测试同时编译testing夹具。
3. JUnit Launcher按类选择执行。以下为本次runner：

```java
import org.junit.platform.launcher.core.*;import org.junit.platform.launcher.listeners.*;import org.junit.platform.engine.discovery.DiscoverySelectors; public class ResearchTestRunner {public static void main(String[] args){var b=LauncherDiscoveryRequestBuilder.request();for(String c:args)b.selectors(DiscoverySelectors.selectClass(c));var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();launcher.registerTestExecutionListeners(listener);launcher.execute(b.build());var out=new java.io.PrintWriter(System.out,true);listener.getSummary().printTo(out);listener.getSummary().printFailuresTo(out);if(listener.getSummary().getTotalFailureCount()>0)System.exit(1);}}
```

测试没有修改官方源码；只选取本研究相关测试，不声称执行了所有外部后端测试。

## 目标版本兼容边界实验

【运行验证】默认codec忽略AgentState未来普通属性，但未知ContentBlock多态type被拒绝；SessionEvent构造拒绝schemaVersion=2。实际输出见 [compatibility-probe-results.json](compatibility-probe-results.json)。这只检验目标版本自己的解析边界，不代表两个SDK版本能安全并发运行。

## Inbox真实持久化样例

【运行验证】合成submit命令接受两次仅持久化一条，重新构造Journal与SessionInbox后仍可重建完整Command；实际消息JSON、Inbox commit/head及二进制前缀解析见 [inbox-probe-results.json](inbox-probe-results.json)。只验证接受幂等与重建，不宣称业务工具执行幂等。
