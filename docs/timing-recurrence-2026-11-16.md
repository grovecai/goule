# 2026-11-16 红果漏计复发：ColorOS 系统容器更新清空前台

## 结论

这次已取得完整证据：10:17:26.895 的 `com.android.systemui / android.widget.FrameLayout / fullScreen=false / changes=0` 事件，让斩心魔将红果前台清空并取消 tick。该事件与 ColorOS 在电量达到 20% 后创建 `SeedlingCard` 浮层严格对齐；窗口管理器此时明确保持红果输入焦点。后续红果继续运行到 10:27:27，未再发目标窗口恢复事件，服务心跳一直正常，但用量停在 13510 ms。

可确认的业务根因为：将这类 SystemUI 根容器更新误当成目标 App 离开，并完全依赖下一次目标窗口事件恢复。系统材料支持本次更新来自 SeedlingCard；不能仅靠 FrameLayout 类名解码所有厂商面板，也不能据此反推昨天早晨必然是同一个浮层。

## 环境与采集

2026-11-16 10:29（北京时间），OPPO PLJ110，Android 16/API 36、ColorOS V16.1.0，manufacturer=OPPO。手机仍安装 2026-11-15 的 `732fb56` 诊断功能构建（0.3.14/code 17），进程 pid 30608 / session 30608-1794700908247 与昨天相同，未发生本轮重启或重新安装。

使用已授权 USB ADB 只读采集持久日志、无障碍状态、退出历史、安装信息、窗口、进程、电源、logcat、UsageStats。未操作手机界面、目标、权限或戒律。资料位于本机 `~/.cache/goule-android/timing-recurrence-20261116/`，完整系统日志仅本机留存，不提交其他 App 资料。

## 关键时间线（北京时间）

| 时间 | 真实记录 | 含义 |
| --- | --- | --- |
| 10:17:08.900 | 红果目标事件，usedMs=0，进入计时 | 识别与戒律有效 |
| 10:17:12.383 | 拒绝一条 awake=2066 / elapsed=2065 ms 的迟到样本 | 短暂主线程延迟会损失约 2 秒，不是后续约 10 分钟停表的主因 |
| 10:17:14.763 | SystemUI FrameLayout，fullScreen=false，changes=16 | 此次确实展开通知栏；系统 notification_panel_revealed 和焦点切至 NotificationShade 印证，暂停正确 |
| 10:17:17.125 / .181 | FrameLayout changes=32，随后红果目标事件 | 通知栏收起后由真实目标事件恢复，usedMs=3797 |
| 10:17:19.812 | OPPO 音量面板精确类名，fullScreen=true | 已有修复保留目标，仍采样 |
| 10:17:24.100 | OplusVolumeDialogImpl 的 Surface 销毁 | 音量面板已撤下，不能仅因接近时间就将最后停止事件归为它 |
| 10:17:26.565 | battery_level=20 | 低电量系统状态变化 |
| 10:17:26.874 | SystemUI 新建窗口 SeedlingCard | 厂商系统浮层出现 |
| 10:17:26.888 | WindowManager 当前/找到的焦点仍为红果 ShortSeriesActivity | 没有真正切到另一个 App |
| 10:17:26.895–.898 | FrameLayout changes=0；reconcile 红果→null，saved=true，interactive=true；usedMs=13510，tickDue=null | 明确的错误停止点 |
| 10:17:37 起每约 30 秒 | 服务连接且未锁屏；foreground=null；samples=6788、usedMs=13510 不变 | 服务活着，但没有采样；不是单纯 UI 延迟或持续拒绝样本 |
| 10:27:27 | 系统 ACTIVITY_PAUSED，红果才离开前台 | 停表后仍有约 10 分钟前台；活动前台不等同每秒触摸/观看证明 |

采集时当前周期 08:47:34.060–10:47:34.060，未跨越上述使用区间；剩余 286490 ms，向上取整为 4 分 47 秒。日志 failures=0 / pendingDropped=0；无存储失败或中途退出证据，不补算已经漏记的历史时段。

## 修复范围

只对 manufacturer=OPPO、包名 com.android.systemui、类名 android.widget.FrameLayout、fullScreen=false、contentChangeTypes=0 的已观察组合保留当前目标，并照常核对锁屏、授权、采样与到期。前台已为空时仍保持为空，不从系统浮层猜测恢复。

同机通知栏展开/收起使用相同类名，但带 PANE_APPEARED=16 / PANE_DISAPPEARED=32，必须继续走暂停路径；全屏、其他变化标记、其他类名/包名/厂商不扩大保留。已有音量精确类名与普通 Dialog 的处理不变。没有读取窗口标题、节点或内容，也没有新增权限或改变规则算法。

这个例外按可用元数据识别容器更新，不声称从事件中直接取得 SeedlingCard 标题。如果 ColorOS 的其他面板只发出完全相同的无变化标记事件，仍需进一步实测；现有真实通知栏样本与该组合可区分。其他 ROM 不继承此次推断。

## 回归与实际边界

新增纯 Kotlin 验证精确组合及厂商/类名/包名/全屏/变化标记反例；在隔离包的生产 FreezeService 重放通知栏出现/关闭、目标恢复、音量面板与 SeedlingCard 更新，确保没有新目标事件时仍持续累计。验证通知栏保持暂停、空前台不恢复、设置/中断退出、另一 App 用量独立、禁用截止不变。原最近任务、屏幕/锁屏、音量、UI 与规则检查继续执行。

实际检查、修复前失败与修复后结果见 [验证手册](verification.md#coloros-系统容器导致漏计2026-11-16)。模拟器注入 OPPO 策略与真实记录重放不等同于最终 ColorOS 修复包的实机运行验证；正式手机安装及真实通知栏/低电量浮层回归另记。
