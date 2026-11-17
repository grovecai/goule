# 斩心魔 v1.0.0

首个正式版。汇总此前预发布阶段的独立计时、禁用提醒、首次引导和主题管理能力；应用功能沿用 v0.3.17。

## 主要功能

- 为每个目标 App 单独设定可刷与休息时长，独立累计前台用量，固定周期到点刷新；列表与详情显示剩余可刷时间。
- 额度用满后展示禁用页与解禁时刻，支持打开最近任务和返回桌面；修改戒律保留本轮用量、额度和原禁用截止。
- 首次三步引导覆盖无障碍、后台运行和厂商风险拦截白名单；服务关闭或连接中断时提供恢复提示。
- 六套内置主题：戒律长老、锅要糊了、摸鱼警报、挂科预警、拯救网瘾少年、亲妈催婚。支持固定或随机显示、自定义多个图片与台词主题、预览和管理。
- 修复已确认的 ColorOS 音量面板与特定系统容器事件干扰，并保留本机诊断日志。窗口内容读取保持关闭，戒律和用量保存在本机。

## 下载与升级

[国内 APK（Gitee）](https://gitee.com/grovecai/goule/releases/download/v1.0.0/goule-v1.0.0.apk) · [GitHub APK](https://github.com/grovecai/goule/releases/download/v1.0.0/goule-v1.0.0.apk) · [SHA-256](https://github.com/grovecai/goule/releases/download/v1.0.0/goule-v1.0.0.apk.sha256)

Android 11 及以上，版本 1.0.0/versionCode 21。沿用原发布签名，可覆盖升级并保留数据。此版本为正式发行版。

## 验证与已知边界

发布流水线执行 `./scripts/check`、Release 构建、版本元数据、原证书、包内源码提交和 ZIP 对齐检查，并回下载核对 GitHub/Gitee 的 APK 与 SHA-256 附件。

此前功能构建已通过 Android 15/API 35 模拟器十一组回归；OPPO PLJ110（Android 16、ColorOS V16.1.0）的原签名覆盖安装、数据保留、启动与服务绑定已有记录。本次正式版本包不另做真机安装，不能将已有功能构建结果等同于本版本全量真机回归。

系统撤销无障碍授权、停止应用或卸载后，限制可能失效；ColorOS 重启撤权仍需手动恢复。来电挂断后的禁用页自动恢复仍有已知问题，低电量系统容器修复的再次自然触发、新主题真实用满与自然到期、完整长周期及更多机型等继续待验。

详见[验证手册](https://github.com/grovecai/goule/blob/v1.0.0/docs/verification.md)与[逐项真机记录](https://github.com/grovecai/goule/blob/v1.0.0/docs/oppo-regression-2026-11-11.md)。
