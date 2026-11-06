# 普通聊天浏览器隔离夹具

此夹具使用专用MySQL容器、真实SDK与本地流式模型，所有扫描设为一小时。前端使用实际工作台代码；测试代理只为browser-probe合成身份，**不验证网关登录**，不得用于正常开发或部署。不会读取业务数据库密码，也不调用外部模型。

1. 仓库根运行 `SQUAD_BROWSER_PROBE=true python3 tools/verification/run_mysql_probe.py --server-tests BrowserChatProbeTest`。
2. 等待 `/tmp/squad-browser-probe.json` 出现，运行 `web/workbench/node_modules/.bin/vite --config tools/verification/chat-browser/vite.config.mjs`。
3. 打开 `http://127.0.0.1:5199`，提交一条测试消息，应依次看到排队、正在回复、第一段正文。
4. 关闭聊天页面后运行 `touch /tmp/squad-browser-release` 让本地模型继续完成，再重开原会话，核对终稿与无重复回答。
5. 完成观察后运行 `touch /tmp/squad-browser-done`；测试会检查仅一次模型调用并清理专用容器。停止Vite。等待窗口有上限，超时会明确失败。

2026-11-05：已在真实浏览器观察到排队、回复中和分段正文；后续截图/断开操作连续两次自动权限审核超时，未绕过检查，人工夹具超时失败。因此不能将浏览器全流程标为通过。HTTP自动集成测试独立验证断开、重新订阅和终稿恢复。

同日后续重新验收通过：浏览器提交受控消息，看到“已收到，等待开始”及“页面断开后，”首段正文；关闭标签后释放模型，确认后台SUCCEEDED，再新开原会话并刷新，只有一条“页面断开后，回答仍然完成。”终稿。BrowserChatProbeTest本轮1项通过，仅一次模型调用，专用容器和5199代理均已停止。截图/tmp/squad-browser-generating-final.png及/tmp/squad-browser-restored-final.png。此记录补齐此前未完成的关闭/重开验收，不代表真实网关登录或多机部署已验收。
