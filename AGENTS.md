# Goule 工程导航

- 修改产品行为、权限交互、计时或冷冻逻辑前，先读 [Android 首版设计](docs/android-design.md)。
- 修改页面、导航、权限提示或交互文案前，读 [首版交互设计](docs/interaction-design.md)。
- 接续工作前，先读 [PROGRESS.md](PROGRESS.md)，确认当前状态、待验证事项和下一步，再读 [实施计划](docs/implementation-plan.md) 核对范围与验收条件。
- 提交前运行 `./scripts/check`。涉及前台识别、锁屏、遮罩或权限时，按 [验证手册](docs/verification.md) 记录真机结果。

## 开发约定

- 规则状态转换使用纯 Kotlin，与 Android 服务、存储和界面分开。保持各 App 的计时和冷冻独立。
- 首轮原型只使用无障碍服务确认前台切换，保持窗口内容读取关闭。只对用户配置的目标 App 执行限制，保留系统导航、设置和紧急入口。
- 跨组件的工作先在实施计划写明范围和验收条件。阶段状态变化时更新 [PROGRESS.md](PROGRESS.md)，并在实施计划或验证手册记录实际检查和结果。真机结果应包含设备与系统版本。
- 新增可自动复现的回归检查时，将它接入 `./scripts/check`。
- 每轮代码修改完成并通过检查后，提交改动、合并到 `main` 分支并推送到远程。真机待验证项如实记录在进度和验证手册中。
- GitHub `origin` 为唯一日常推送入口，本机只需向 GitHub 推送 main。GitHub Actions 每日北京时间 08:17 检查应用/构建代码变化并自动原签名发布；阶段发布使用 `Publish Android release` 手动入口，默认递增补丁版本及 versionCode、继续预发布。发布流水线直接等待 Gitee 验证，再更新下载入口与发布记录；普通 main 更新及外部手工创建的 GitHub Release 仍由同步工作流处理。`gitee`（`https://gitee.com/grovecai/goule.git`）保留用于查看与验收，无需本机双推，不强制覆盖远端历史。按 [发布流程](docs/publishing.md) 执行，缺少签名 Secrets、权限或同步失败时明确记录，不将标签推送视为发行版完成。
