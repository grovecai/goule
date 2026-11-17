# 2026-11-18 ColorOS 自动清理后无障碍中断

## 结论

北京时间 06:35:45，ColorOS 通过 `FastClear[4013]` 路径杀死斩心魔进程。随后无障碍组件仍在启用名单，但 Bound/Binding 为空、Crashed 包含 FreezeService；重新打开主界面后仍未恢复绑定。用户明确反馈当时没有一键清理或划掉任务，只是正常使用，因此本次应归为系统自动清理导致的服务异常断连，不能沿用 11 月 13 日“手动一键清理”的触发结论。

已确认系统清理路径和未恢复的结果；尚未确定 `4013` 对应的厂商内部策略、豁免条件及自动清理的具体触发。约 13 秒前相机进入前台，同一秒多个进程被结束，并伴随内存状态变动，这是后续复测线索，不能直接认定为相机、内存不足或温控导致。本次退出被系统记为 OTHER KILLS BY SYSTEM，没有本应用 Java/native crash 或 ANR 的证据；Crashed services 表示框架的异常断连状态，不应直接翻译成应用崩溃。

## 环境与采集边界

- OPPO PLJ110，Android 16/API 36，ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`。
- 06:56:56 起只读采集，当前手机仍是 11 月 17 日 10:54 安装的亲妈催婚功能构建，0.3.16/code 19；仓库已发布的 v1.0.0/code 21 尚未安装到这台手机。本次不将两者混为一谈。
- boot_count=9，开机约 6 天 8 小时；此次不是重启后的 `sideload_app` 撤权。
- 使用现有采集脚本及只读 dumpsys/settings get/AppOps/getprop，未启动或强停应用、安装、重启、切换权限、修改戒律或运行 uiautomator。采集前主界面已被打开，本次采集没有触发该打开动作。
- 原始系统与应用日志仅留本机 `/Users/caiyuanrui/.cache/goule-android/service-stop-20261118/`，目录 700、文件 600；不提交其他应用的身份、整份日志或设备私有资料。

## 时间线与证据

| 北京时间 | 证据 | 说明 |
| --- | --- | --- |
| 06:35:32 左右 | 系统相机窗口进入，应用日志仍接收窗口事件 | 清理前约 13 秒的使用场景；不能据时间接近单独认定触发原因 |
| 06:35:32.562 | 持久日志心跳 `connected=true`，前台为空，无 tick，events=3460 | 服务当时仍连接；不在目标 App 内时没有 tick 是正常暂停计时 |
| 06:35:32.975 | 持久日志记录 SystemUI 非全屏 FrameLayout、changes=0，并执行 `passive ColorOS system container; retain target` | 现有容器修复已生效；前台原本为空，未猜测恢复目标；不能用旧漏计根因解释本次进程死亡 |
| 06:35:45.256 | events `am_kill` 明确记录本包 pid=28181、`o-kill(4013)`、`FastClear[4013]` | 可确认 ColorOS 清理路径；同一秒另有多个进程被清理，未见本包 `with oneclick` 或 `with swipe up` |
| 06:35:45.447 | `am_proc_died` 记录 pid=28181 死亡 | 与杀进程事件相互印证 |
| 06:35:45.474 | ApplicationExitInfo：reason=13 / OTHER KILLS BY SYSTEM，subreason=6013 / UNKNOWN，description=`o-kill(4013)`，trace=null | 厂商清理，不应自行解码细分数值；不把它记录为 Java/native crash、ANR 或标准 LOW_MEMORY |
| 06:54:54.728 | 新进程 pid=24844；持久日志记录上次 reason=13，Activity 创建时 `serviceConnected=false` | 主界面能启动，不能代表无障碍恢复 |
| 06:56:56 起 | Enabled 仍含 FreezeService，Bound/Binding 空，Crashed 含 FreezeService；全局 accessibility_enabled=0 | 组件登记保留、实际服务异常断连；不同于组件授权被删除 |

持久日志的上一进程结束前没有 `service unbound` 或 `service destroyed` 收尾记录，符合突然被杀时回调不一定执行的现场，不以缺少收尾推断应用主动关闭。系统 events 保留了杀进程现场，但 system/main 缓冲分别只保留约 06:44:46、06:55:48 之后的记录，缺少 06:35 的详细厂商策略日志。

当前 RUN_ANY_IN_BACKGROUND=allow，只能说明这一项 AppOps 未限制；不能证明厂商“完全允许后台行为”、最近任务锁定或风险行为白名单仍有效。系统 recents 中的 LOCK_TASK_AUTH_PINNABLE 也不是 ColorOS 最近任务卡片的小锁，本次没有读取可靠的小锁状态，不能声称用户没锁任务。

## 产品可以增强的地方（建议，尚未实现）

后续用户已要求实施前两项，现已完成原因展示和后台图片内存优化，实际范围及检查见 [后续验证](verification.md#中断原因展示与后台图片内存优化2026-11-18)。以下保留排查时的建议，提醒/自动清理真机对照等仍待验证，不代表已实现全部建议。

1. **优先展示最近中断时间及系统退出原因。** 当前已有中断/关闭区分与五秒连接等待，但原因只有 ADB 能看。启动时把上一进程退出时间、标准 reason、服务最后正常观测时间提供给恢复页，明确“系统结束进程”与“权限已关闭”的证据差别。当前日志只保存 reason/status/importance，没有保存系统 description；可补本包 description 的限长本机记录。description 的格式不稳定，应作为原始说明展示，不能只凭 reason=13 或字符串数字认定手动清理、FastClear、低内存或风险拦截。
2. **恢复路径更直接。** 复用现有首页暂停提示和系统入口，说明当前开关可能还开着，需要先关闭再开启；恢复成功继续以实际连接回调为准。已有入口和文案不能重复计为新能力。可评估直达本服务设置的公开入口并保留现有回退，不自动操作安全开关。
3. **测量并减少后台图片内存。** 源码中背景图每个 Drawable 实例完整解码，个人图片缓存上限 16 MiB，尚无内存回收回调。可验证适配屏幕的采样、共享只读位图和后台/内存压力时释放闲置缓存。采集时重开后的进程 PSS 约 109 MiB、图形约 39 MiB，属于另一个进程及前台状态，不能拿它证明退出时占用或泄漏；退出历史 RSS 108 MiB 也不是死亡瞬间精确测量。应先做开页面→回后台→相机往返的内存曲线及遮罩恢复回归。
4. **中断提醒需要单独验证可达性。** 可评估用户可选的状态通知和系统允许时的后台健康检查，服务未绑定时提供恢复入口。整个进程被杀时，依附同进程 Handler 的心跳也停止，无法即时自救；不能将普通定时任务、开机广播或常驻通知包装成保证自动重连。前台服务还需符合公开类型和启动限制，不能仅为保活直接加入。
5. **补自动清理兼容矩阵。** 在独立包持续采集系统策略日志，对比相机往返、后台图片内存、任务锁定、完全允许后台行为、风险行为白名单及待机。一次只改变一个条件，以服务实际绑定、未被列入 Crashed、规则与截止保留为验收。原有锁任务后一键清理通过，不能外推到本次 FastClear。

建议顺序为原因可见与恢复入口 → 内存测量和有证据的优化 → 自动清理实机对照，再决定是否值得增加权限及后台组件。本轮仅排查并记录建议，不修改产品行为、权限或计时。

## 当前恢复与验证结果

采集结束时服务仍未绑定，未替用户开关权限，不能记为已恢复。当前可由用户到系统无障碍设置，先关闭再开启「斩心魔」，按实际出现的安全提醒核对并保持开启，返回确认应用“已设置”。原有已提交规则/用量/截止由既有实现恢复；本轮未进行恢复前后数据逐项核对，不能宣称这次已经验收。服务停止期间无法观察到的用量不补算。

本轮只增加诊断、进度和验证文档。仓库检查结果记录在 [验证手册](verification.md#coloros-fastclear-导致无障碍中断2026-11-18)，不将自动检查当作手机恢复或 FastClear 修复验收。

## 公开机制依据

- [ApplicationExitInfo](https://developer.android.com/reference/android/app/ApplicationExitInfo)：reason=13 的含义；description 仅为系统提供的人类可读说明，跨设备/版本格式不稳定；RSS 为最近采样，非退出瞬间精确值。
- [AccessibilityService 生命周期](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService)：服务生命周期与绑定由系统管理，授权开启需要用户明确操作，普通 Activity 启动不是恢复绑定接口。
- [前台服务说明](https://developer.android.com/develop/background-work/services/fgs)：仅适用于用户可感知的持续任务，有通知、类型与后台启动限制；不据此承诺绕过 ColorOS 清理。
