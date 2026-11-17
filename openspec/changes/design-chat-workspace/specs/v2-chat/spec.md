# Spec Delta

## ADDED Requirements

### Requirement: 聊天页面布局
系统 SHALL 提供可折叠会话侧栏、用户信息入口、消息阅读区与输入区；当前后端JSON示例必须在页面明确标识。
#### Scenario: 新建演示会话
- **WHEN** 用户点击开启新对话
- **THEN** 展示欢迎语和输入框，发送接口回显请求结果，不生成真实回复

### Requirement: 统一接口基础能力
系统 SHALL 通过公共入口处理异常和获取当前用户，接口注释提供简短中文作用标题。
#### Scenario: 请求身份失效
- **WHEN** 请求没有有效用户身份
- **THEN** 通过公共异常映射返回一致结果，业务Controller不手写错误响应
