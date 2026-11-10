# Goule Android 首版设计

Goule 是面向个人的 Android 自律工具。以 Enough 的 iOS 交互为基线，保留「每个 App 独立累计 → 用满后冷冻 → 到期开始下一轮」。以下为设计提案，拦截能力、计时精度和设备兼容性待原型验证。

## 1. 产品行为

- **逐 App 设置**。每条规则包含每轮可用时长和冷冻时长，默认「20 分钟 / 30 分钟」。首版建议先沿用 iOS 的可用 1–180 分钟和冷冻 15–180 分钟，便于跨端一致。Android 的候选实现按截止时间判断，15 分钟是产品选择，短冷冻可用调试配置验证。iOS 的 19 条调度上限无需直接搬过来，Android 首版容量以性能实测为准。
- **累计可交互前台时间**。同一 App 分段使用会相加。切出、锁屏和冷冻期间停止累积。首版支持当前 Android 用户的普通全屏 App，后台播放、分屏、画中画和工作资料另行验证。
- **独立冷冻**。用满后 Goule 尝试盖住目标 App 的交互区域，显示预计恢复时间。其他 App 照常使用。冷冻按现实时间流逝，反复打开和锁屏不会延长。到期后从零开始下一轮。沿用 iOS 的 24 小时使用窗口，未用完的额度在窗口结束后刷新，冷冻仍按自己的截止时间结束。
- **编辑与移除**。编辑后开始新一轮计时，已有冷冻保留本次截止时间。移除冷冻中的规则时先确认，完成后解除该规则的限制。24 小时规则保护继续作为提案。
- **设备控制权**。个人版保留系统卸载与关闭服务的入口。设置页可以提供规则保护说明，防卸载不承诺跨品牌支持。受管设备的包挂起或防卸载属于独立产品形态。 [Android 设备策略接口](https://developer.android.com/reference/android/app/admin/DevicePolicyManager.html)

## 2. 交互

### 首次使用与权限

欢迎页用「小红书累计用 20 分钟，冷冻 30 分钟」说明玩法。点击「开始设置」后进入单独的权限说明：

> **开启 Goule 的无障碍服务**
> Goule 会识别当前前台 App 和切换时间，用于累计你设置了规则的 App，并在用满后显示冷冻页。规则和用量保存在本机。你可以随时在系统设置中关闭服务。
> **「同意并前往设置」**　「稍后」

用户明确同意后，使用系统无障碍设置入口，让用户手动开启 Goule 服务。返回后验证系统开关和服务连接，确认连接后才显示「限制已启用」并引导添加 App。取消或拒绝后，仍可浏览帮助和配置草稿，首页显示「限制未启用」。如果开关已开但未连接，显示「正在连接」或「检查服务」。关闭服务后显示「限制已暂停」，保留规则，服务恢复后继续执行仍有效的冷冻。通过侧载测试时，部分设备可能提示受限设置，按设备实际提示和安装来源说明。 [无障碍服务](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService) · [Android 受限设置](https://support.google.com/android/answer/12623953)

### 规则、设置与冷冻页

| 界面 | 行为 |
| --- | --- |
| 规则首页 | 显示 App 名称、时长、计时或冷冻状态。冷冻中的条目优先。服务异常时置顶提示，单条规则关联的 App 已卸载时提示重新选择。 |
| 添加与编辑 | Goule 提供本机可启动 App 列表，选一个再设时长。重复选择进入已有规则。保存失败保留表单。 |
| 冷冻页 | 「本轮额度已用完」「预计 14:50 恢复使用」。主按钮用「返回桌面」，点按后请求 Android 回到桌面。返回桌面后再次进入目标 App，冷冻仍生效。 |
| 设置 | 无障碍服务状态、重新打开系统设置、计时说明和隐私说明。 |

Android 冷冻页是 Goule 在目标 App 上显示的遮罩。目标 App 进程仍由系统管理。核心验收是在普通全屏场景下，遮罩可见期间目标内容无法继续接收触摸，同时系统导航、紧急功能和关闭服务的路径保持可用。Android 上不承诺与 iOS 系统屏蔽同等的覆盖范围。 [无障碍覆盖窗口](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY) · [返回桌面动作](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#GLOBAL_ACTION_HOME)

## 3. 技术方案

候选实现使用 Kotlin、Jetpack Compose、Room 和单进程 `AccessibilityService`。先在 Android 11（API 30）及以上做原型，发布时按实际渠道要求确定目标 SDK 和支持设备。

| 组件 | 责任 |
| --- | --- |
| App 列表 | 用 `PackageManager` 查询 `MAIN / LAUNCHER` Activity。通过 manifest 的 `<queries>` 声明所需范围，按包名去重，排除 Goule 自身、桌面、设置、来电和紧急等系统入口。 [包可见性](https://developer.android.com/training/package-visibility/declaring) |
| 规则引擎 | 独立于 Android 界面的纯 Kotlin 状态转换。输入为前台切换、锁屏/唤醒、到期、编辑和权限状态；输出为累计更新及拦截决策。 |
| 前台识别 | 验证 `TYPE_WINDOW_STATE_CHANGED` 是否足以区分目标 App、离开和系统窗口。监听包名和必要时间信号；其他包只用于判断是否离开，随即丢弃。关闭窗口内容读取，不遍历节点、不读取输入、不截图。 [事件与能力配置](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo) |
| 拦截 | 服务确认当前目标 App 正处于冷冻时，附着可触摸的 `TYPE_ACCESSIBILITY_OVERLAY`。切换到其他应用、服务断开或到期时撤下。主按钮只在用户点击后请求 `GLOBAL_ACTION_HOME`。操作失败时保留有效遮罩并允许系统导航。 |
| 存储与时间 | Room 保存规则 ID、包名、窗口开始时间、累计时长和冷冻截止时间。进程内用 `elapsedRealtime` 计算活跃会话增量，冷冻保留可跨重启的墙钟截止时间；同一次开机用单调时钟校验时间跳变。 |

### 计时和恢复

1. 从已确认的前台事件开启会话，用单调时钟为剩余额度安排一次进程内唤醒。切出、锁屏时结算并取消唤醒。唤醒时重新检查前台包名、设备交互状态和当前规则，避免把过期事件作用到新界面。
2. 达到额度时用一次事务保存累计和冷冻截止时间，再展示遮罩。对同一 App 重复触发按规则版本和会话处理，避免重复开始冷冻。
3. 遮罩可见时安排到期移除；进程休眠、重启或从桌面重新打开目标 App 时，先按已保存的截止时间判断是否已解冻。只恢复已提交的会话增量，不补算未观察到的活跃时间。用户改系统时间、服务重连或设备重启时重新校验。服务未连接或前台归属不明确时，清除不可靠的遮罩并展示可确认的服务状态。
4. 若真机发现前台事件漏报或重连后难以确认活动 App，再验证 `UsageStatsManager.queryEvents` 作为补偿。该方案需要额外的「使用情况访问权限」，要说明用途并防止和实时会话重复计时。首轮原型只请求无障碍服务。 [UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager)

### 发布边界

Google Play 版本应准确披露无障碍用途，提供单独的应用内显著披露、明确同意、Play Console 声明与演示。Goule 的主要用途是个人自律，服务不声明 `isAccessibilityTool=true`。覆盖范围限于用户设置规则的前台 App；设备设置和卸载流程保持可用。声明能否获批由渠道审核决定。 [Google Play AccessibilityService 政策](https://support.google.com/googleplay/android-developer/answer/10964491)

## 4. 验证顺序

| 阶段 | 通过条件 |
| --- | --- |
| 能力原型 | 在 Pixel 或接近原生的设备，以及至少一款国产 ROM 上，授权后能识别普通 App 进入、离开、锁屏。用满后遮罩可阻止触摸，「返回桌面」正常，再打开仍拦截。验证系统设置、来电和紧急入口可以访问。 |
| 规则引擎 | 自动化覆盖分段累计、两个 App 冷冻独立、额度刷新、重复回调、编辑保留冷冻、进程重启恢复、设备时钟变化。 |
| 用户链路 | 真机跑首次拒绝和恢复、App 选择、A 用满后 B 可用、锁屏跨过 A 截止后 A 解锁且 B 仍冷冻、A 第二轮重新计时。记录机型、Android/ROM 版本、安装渠道与观测延迟。 |
| 兼容和发布 | 验证服务被杀、从通知进入、最近任务、键盘和系统弹窗等切换路径，明确分屏与画中画边界；若目标为 Google Play，完成无障碍声明和审核验证。 |

优先做能力原型。只有确认前台事件和遮罩能稳定覆盖核心路径，再把完整规则界面和更多机型支持接上。
