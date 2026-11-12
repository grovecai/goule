# 2026-11-13 OPPO 戒律暂停诊断

## 结论与边界

本次暂停的直接证据为：04:00:28 系统杀死应用进程，之后无障碍服务持续未绑定、被列为 Crashed services；04:17 用户重新打开主界面并未恢复服务。用户补充 04:00 左右刚执行过「一键清理或划掉任务」。结合时间及退出原因，最有证据支持的触发为清理操作导致系统结束进程，继而服务异常断开且未恢复绑定。采集时服务组件仍在启用名单，不能归因为此前 F02 的重启后侧载应用授权名单被清空。

本次可以确认系统杀进程和服务未恢复两个环节，清理触发来自用户操作回忆与时间吻合的推断。厂商退出说明只有 `o-kill(2)`，其数值含义没有得到进一步证据；04:00 的详细系统日志已轮转，不能区分一键清理和单独划掉任务，也不能确定厂商内部执行该清理的策略及后台设置的影响。不能仅凭 Crashed services 判为业务代码异常：进程退出记录的最新原因是 OTHER KILLS BY SYSTEM，而非 Java/native crash 或 ANR。

## 采集环境与范围

2026-11-13 04:21 起（北京时间），OPPO PLJ110，Android 16、ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`。只读采集设置、系统服务、退出历史、日志和安装包摘要；未重新授权、重启、安装、启动或强停应用，未运行 uiautomator，未修改戒律或手机设置。

当前安装的是 72a7263 修复功能构建，0.3.5/code 8，更新时间 2026-11-12 21:11:45。base.apk SHA-256 为 `abca8c46608a92164619c3d75a6dc31f3fdb7fdcb05293e100baedc366fcbeac`，与上轮安装记录一致，并非 GitHub v0.3.6 版本包。

原始采集保存在本机 `/Users/caiyuanrui/.cache/goule-android/pause-diagnosis-20261113/`，不将包含其他应用信息的整份系统日志提交到仓库。

本轮仅修改诊断和进度文档。`./scripts/check` 通过，包含仓库检查、既有 JVM 测试、Lint 和 Debug 构建；文档补充用户反馈后另做相对链接及空白检查通过。这些自动检查不代表完成手机恢复或清理场景回归。

## 实机证据

| 检查 | 实际结果 | 含义 |
| --- | --- | --- |
| 最新进程退出 | 2026-11-13 04:00:28.751，pid 25415，reason 13 / OTHER KILLS BY SYSTEM，subreason 2002 / UNKNOWN，description `o-kill(2)`，trace null | 系统记录为系统杀进程，不能将厂商细分原因自行解码为某一清理功能 |
| 用户补充的触发操作 | 04:00 左右刚执行过「一键清理或划掉任务」 | 与系统杀进程时间吻合；尚未区分两种清理操作，未做对照复现 |
| 当前应用进程 | pid 19506，04:17:36 由 MainActivity 启动 | 主界面已重新运行，但并不等于无障碍服务恢复 |
| enabled_accessibility_services | 仍含 `com.goule.prototype/com.goule.prototype.FreezeService` | 服务组件登记仍在 |
| accessibility_enabled | 0 | 全局无障碍运行标志为关闭；不等价于该组件已从授权名单删除 |
| Bound / Binding services | 均为空 | 没有实际绑定，也未见正在绑定 |
| Crashed services | 含 FreezeService | 框架记录此服务断连异常状态 |
| 04:18:51.646 系统日志 | mEnabledServices 和 mCrashedServices 同时包含 FreezeService | 与 dumpsys 状态相互印证 |
| 当前运行服务 | `dumpsys activity services com.goule.prototype` 为 nothing | 当前没有恢复中的应用服务 |
| 开机状态 | boot_count 9，uptime 108774 秒，约 30 小时 13 分 | 04:00 并非本轮刚开机；不能套用旧 F02 根因 |
| RUN_ANY_IN_BACKGROUND | allow | 当前这一项 AppOps 未限制；不能据此推断厂商「完全允许后台行为」状态 |

04:00 的详细触发现场不在现存日志范围内：采集时 events 最早约 04:02，system 最早约 04:18，main 最早约 04:21。保留下来的 crash buffer 没有此次应用异常堆栈。退出历史可保留更早事件，因此仍可确认 04:00:28 的系统杀进程记录。

## 为何开着主界面仍不能执行戒律

Android 16 AOSP 的无障碍框架在服务 Binder 死亡时标记 crashed；常规服务更新会跳过 crashed 集合里的组件。没有正在绑定或已绑定的服务时，全局 ACCESSIBILITY_ENABLED 也会变为 0。用户关闭对应服务后，框架才会清理其临时异常状态，供再次开启时重试。

以上是 AOSP 机制与本机采集状态一致的解释，并非对 ColorOS 全部内部实现的反编译结论，也不能据此保证所有 ROM 都不会自动恢复。依据：[AccessibilityServiceConnection](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/accessibility/java/com/android/server/accessibility/AccessibilityServiceConnection.java)、[AccessibilityManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/accessibility/java/com/android/server/accessibility/AccessibilityManagerService.java)、[AccessibilityUserState](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/accessibility/java/com/android/server/accessibility/AccessibilityUserState.java)。退出原因的含义参见 [ApplicationExitInfo](https://developer.android.com/reference/android/app/ApplicationExitInfo)。

应用的 [serviceState](../app/src/main/java/com/goule/prototype/MainActivity.kt) 将全局 ACCESSIBILITY_ENABLED=1 与组件在名单里同时作为 enabled 条件。当前全局标志为 0，曾激活的应用便进入 PAUSED，显示「无障碍服务已关闭」。所以暂停执行本身与实际断连相符，但文案把“组件仍授权、服务异常断开”和“用户关闭/系统撤销登记”合并了，无法准确说明本次原因。这是状态提示问题，不能解释为它导致系统杀进程。

## 恢复与后续定位

后续已按用户要求完成状态与指引修复及实机对照。一键清理的本轮日志明确记录 `o-kill(2) ... with oneclick`，正式包复现异常断开并通过手动重开恢复；任务锁定后再清理保持绑定。见 [后续验证](recovery-verification-2026-11-13.md)。以下保留初次诊断时尚未实施的范围说明，不能将其误读为当前工作仍未完成。

当前恢复应由用户在系统无障碍设置中关闭再开启「斩心魔」，随后核对 Bound services 和应用状态；本轮只诊断，尚未执行或验证这次恢复。不能把重新打开主界面当作恢复成功。

后续若修改产品，应分别处理：明确区分已授权但服务断连的提示；在应用中记录可读取的退出原因和服务生命周期；用专用测试包分别复现单独划掉与一键清理，持续采集触发日志，并核对厂商后台允许设置和任务锁定对两种操作的作用。不能用此前完全允许后台行为后的测试通过记录保证本次清理不受影响；单纯保活、开机接收器或修改计时引擎均没有本次证据支持其能解决问题。
