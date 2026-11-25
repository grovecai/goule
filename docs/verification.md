# Goule 验证手册

## 最新功能包保留数据安装（2026-11-18）

用户要求“把最新版斩心魔安装到手机上，保留现有戒律和数据”。从干净提交 `ab645208ef637a0b72731fd2d999446dd8699e21` 构建 Release，07:28:46（北京时间）在 OPPO PLJ110、Android 16/API 36、ColorOS V16.1.0（系统 `PLJ110_16.0.10.502(CN01)`）通过 `adb install --no-incremental -r` 成功覆盖安装。手机从 0.3.16/code 19 更新至最新功能构建 1.0.0/code 21，包含中断原因展示、后台图片内存优化和自启动指引；该包与既有 GitHub/Gitee v1.0.0 附件不同，本轮未发布或替换发行版。

Release 构建、apksigner 验证、版本/包名/最低 Android 11/非调试标记、包内源码提交与 16 KiB ZIP 对齐全部通过；新包证书与安装前回拉 APK 一致，证书 SHA-256 为 `a99c6b2900299c459fe1121dc7868762d8afb66ec9f5b4819a471a5b62103559`。安装后回拉 base.apk 与本地签名包摘要完全一致：`6f95d84eaee4756a31179d1020eb2e0757c47196efb302b976e56e3d90155bbc`，大小 5,269,900 字节。

安装前仅打开本应用让目标离开、保存进度，再采集即时服务与持久配置快照。三条戒律的 target/eligible/usedMs/remainingMs/cycleStart/blockedUntil 逐项一致，配置及当前周期额度/休息长度也一致；抖音已用 20801 ms、剩余 279199 ms，红果及小红书均已用 0、剩余 300000 ms，三者均无当前禁用截止。appId=10445、首次安装时间 `2026-11-14 12:00:57` 保持，未卸载、清数据、修改戒律、补算停服用量或使用目标 App 测试。安装前服务已经恢复正常，本轮没有替用户重新授权。

安装后 MainActivity 启动返回 Status: ok，原戒律列表显示正常，服务 connected=true、consent=true，Enabled/Bound 正常、Binding/Crashed 空，journal failures=0。设置页前后均显示“随机 · 6 个主题”；未修改主题模式、随机池、台词或图片。保留数据覆盖与安装身份核对通过，但本轮未通过系统权限边界逐字节导出全部私有主题文件，主题核对范围为可见摘要和既有迁移回归。

通过应用内设置打开“允许后台运行”，实机截图确认第 2 节自启动完整显示“设置 → 应用 → 自启动，找到「斩心魔」，开启开关”、系统差异说明及“打开系统设置”按钮，电池优化顺延为第 3 节。仅查看说明，没有打开系统设置或操作自启动、耗电管理、白名单、无障碍开关；未运行 uiautomator，未以临时抑制服务的状态判断失效。手机最后停留该后台指引页供用户操作。真正停服后的原因展示、实际开关往返、长期内存及 FastClear 是否减少仍待观察，不以安装成功当作故障修复验收。

原/新/回拉 APK、前后与末次服务/配置快照、截图、构建/安装验收 JSON 仅存本机 `/Users/caiyuanrui/.cache/goule-android/latest-install-20261118/`，目录 700，不提交签名资料或原始私有日志。安装后仅补文档，提交前 `./scripts/check` 通过（Python 46 项、Debug/Release 各 67 项、Lint 与 Debug/AndroidTest 构建，现有 Android 任务大部分复用缓存）；本轮没有重复隔离包全量回归或在正式手机消耗目标额度。

## 自启动配置指引（2026-11-18）

按用户补充的 OPPO 路径，共用“允许后台运行”页新增第 2 节“自启动”：进入“设置 → 应用 → 自启动”，找到「斩心魔」，开启开关。其他厂商提供系统设置/手机管家的自启动或应用启动管理说明，保留入口差异提示；电池优化和最近任务顺延为第 3/4 节。新增按钮名为“打开系统设置”，通过公开 ACTION_SETTINGS 打开设置首页，由用户继续操作；没有声称可直达厂商自启动页，启动失败提示手动查找。

`./scripts/check` 通过：Python 46 项，Debug/Release 各 67 项既有 JVM 测试，Lint、Debug 与 AndroidTest APK 构建通过。源码核对首次第 2 步与设置入口复用 `backgroundSettings()`，自启动说明不检测完成状态、不代开开关、不新增确认或门禁，不修改规则/权限/计时。

本轮未安装或操作手机、未运行设备回归；OPPO PLJ110（既有设备 Android 16、ColorOS V16.1.0）的实际新页面显示、打开设置往返及自启动开关状态仍待真机验证，不将用户提供路径写成本轮已实测通过。日志保存在本机 `/Users/caiyuanrui/.cache/goule-android/auto-start-guide-check-20261118.log`。

## 中断原因展示与后台图片内存优化（2026-11-18）

本轮按用户要求实现此前排查建议的前两项，未增加权限、通知或后台任务。无障碍恢复页在主恢复按钮之后展示“中断记录”，分别列出最近连接观测、断开记录、系统退出时间/标准原因/最多 240 字原始说明；匹配服务进程及时间顺序，后来仅运行主界面的进程退出不会遮掉原服务进程的相关记录。旧退出、更新、无服务观测和系统读取失败不冒充已知原因；正常连接后不继续显示故障。服务观测沿用已有 30 秒心跳，独立于戒律存储，不补算停服时间。

图片采用最多 8 MiB 的共享背景位图缓存，按实际目的尺寸保守采样；独立 Drawable 继续保持原构图与着色。Activity 停止时移除背景引用、释放闲置缓存，主题选择/预览/编辑视图在后台释放并于返回时恢复，保留草稿字段/图片文件和滚动位置。UI_HIDDEN/内存回收时淘汰闲置缓存，不主动 GC，不 recycle 在用图片；在用遮罩和规则截止继续保留。

### 自动与设备验证

- 最终 `./scripts/check` 通过：Python 46 项；Debug/Release 各 67 项 JVM 测试，失败/错误均为 0；Lint、Debug 与 AndroidTest APK 构建通过。新增 7 项健康证据和 2 项采样测试自动接入既有检查。
- Android SDK 模拟器，Android 15/API 35、x86_64；隔离包 `com.goule.uiverify`。本轮完整 12 组通过，覆盖固定周期/滚动位置、横竖屏与大字号遮罩、系统安全边距、前台/最近任务/音量/ColorOS 容器事件、持久诊断、首次三步、主题导入/管理/迁移及新健康记录和内存检查。最终构建另跑正常 1080×2400/fontScale=1.0、短屏 720×1280/fontScale=2.0 专项，均通过；这两轮包含最终恢复卡片位置、较晚 Activity 退出关联及后台返回避免重复解码的实现。
- 专项通过：长原始说明限长/换行、不超出卡片宽度，恢复记录实际渲染/Activity 重建；后台/前台真实任务往返后背景恢复，个人图片重新解码，主题预览返回、未保存草稿文字和图片保留并可保存。系统 UI_HIDDEN 后，在用图片未被 recycle，已有遮罩仍可绘制。正常及大字号卡片/页面渲染已人工核对。
- 专项中的服务观测及 reason=13 文案使用隔离数据，不能当作真实无障碍断连复现。另对该隔离包执行实际强停、重开：前后 `service_health.xml` 完全一致，诊断异步读取的系统退出 reason=10 及 description 已导出；这是系统历史读取和进程间持久化验证，不是 FastClear 或正式手机恢复验收。
- 两轮专项后的 fontScale 恢复 1.0，模拟器尺寸恢复 1080×2400；隔离包及测试包均已卸载。未操作正式手机的戒律、主题、服务开关或安装包。

### 内存测量与边界

| 项目 | 实际结果 |
| --- | --- |
| 原欢迎/戒律背景完整解码像素 | 6,290,856 + 4,601,600 = 10,892,456 字节；原 lazy Drawable 在两页都打开后会各自持有 |
| 359px 目的宽度的戒律背景 | 采样结果 360×800、1,152,000 字节，复用同尺寸位图；完整解码为 719×1600、4,601,600 字节 |
| 正常屏幕最终专项，切回戒律页 | 共享缓存 4,601,600 字节，已不由 Activity 同时持有欢迎页旧 Drawable |
| 页面停止后 | 背景缓存计数 0，Activity 背景引用为空；返回后可重新绘制 |
| 测试个人图片缓存 | 淘汰前 4,147,200 字节，淘汰后计数 0；已有遮罩持有的图片仍有效，下一次 resolve 可重载 |

以上是位图分配与缓存引用计数，正在显示的遮罩仍会持有其图片，系统何时回收对象及 GPU 资源由运行时决定；本轮未做同工况的整进程 PSS 前后对照，不能据此宣称进程 RSS/PSS 下降相同字节数。此前正式 OPPO 重开后的 PSS 约 109 MiB 也不能与模拟器数值直接比较。

### 正式手机与待验

本轮未安装或发布，OPPO PLJ110（Android 16、ColorOS V16.1.0）仍需原签名保留数据覆盖后观察：真正服务断连后的原因关联、恢复前后规则/截止保持、相机往返与长期后台，以及任务锁定/后台允许对 FastClear 的作用。不能保证普通 APK 避免所有厂商清理或自动重新授权。旧版本升级首次没有新增的服务观测，可能先显示安装更新退出及“不一定相关”，不会伪造旧正常时间。

检查日志、渲染及隔离退出材料只留本机 `/Users/caiyuanrui/.cache/goule-android/service-health-ui-20261118/`、`service-health-focused-20261118/` 和相邻检查日志。首次模拟器快照启动停在 offline，改为不加载快照冷启动后完成验证；专项入口曾补初始化异步诊断、重复运行时清理同名测试主题后重跑，最终两轮通过。上述测试准备问题不记为正式手机故障或产品修复通过。

## v1.0.0 正式版发布（2026-11-17）

用户要求发布正式 1.0 版本。通过 [Publish Android release](https://github.com/grovecai/goule/actions/runs/44105416189) 指定 `version=v1.0.0`、`prerelease=false`、`force=true`，功能沿用 v0.3.17。版本为 1.0.0/code 21，注解标签对应干净发布提交 `e606aab15558474f389bd155c10eb4c1d9f8c10d`。

发布准备提交前，本机使用已有 JDK 17 与 Android SDK 35 执行 `./scripts/check` 通过（Python 46 项、Debug/Release 各 58 项单元测试、Lint、Debug/测试 APK 构建）；首次未配置 JAVA_HOME 的尝试只完成 Python 检查，定位原有 JDK 后完整重跑通过。发布流水线另行执行项目检查、Release 构建、原签名及包验证。

22:42:49（北京时间）GitHub 正式公开，非草稿且 `prerelease=false`，`releases/latest` 指向 v1.0.0。独立回下载 APK/SHA-256，核对文件摘要、原证书、版本/包名/最低 Android 11/非调试标记、包内源码提交与 16 KiB ZIP 对齐通过。证书 SHA-256 为 `a99c6b2900299c459fe1121dc7868762d8afb66ec9f5b4819a471a5b62103559`，APK 为 5,249,420 字节，SHA-256 为 `174d0531eac45ee9ded9983eda4b950d6c7f6256c3f38f8fb9dd2c0ddb396a63`。

22:51:40（北京时间）流水线完成 Gitee 两项附件的上传与回下载验收，22:52:03 整体工作流成功。两平台均为正式版（`prerelease=false`），标签和发布提交一致，Gitee 正文除下载链接切换为国内地址外与 GitHub 一致；README 下载入口和发布完成状态已更新。本机再次匿名回下载 Gitee APK 与 SHA-256 文件，分别与 GitHub 原附件逐字节一致（5,249,420 与 83 字节），并核对 Gitee main 与 GitHub main 一致。

本轮不安装手机，不新增真机行为验收结论；OPPO PLJ110（Android 16、ColorOS V16.1.0）仅沿用此前功能构建结果，ColorOS 撤权、来电恢复、新主题自然禁用/到期与更多机型等边界继续保留，见 [正式版说明](release-v1.0.0.md)。下载与独立检查材料只存本机临时目录，不提交私有运行资料或签名密钥。

## 亲妈催婚最新功能包保留数据安装（2026-11-17）

用户连接手机并要求安装最新版、保留戒律和数据后，10:54:06（北京时间）将干净提交 `783c052d7ad861ad6472153d2d68c34cc40abd80` 的原签名Release覆盖安装至OPPO PLJ110，Android 16/API 36、ColorOS V16.1.0，仍为0.3.16/code 19。包含亲妈催婚和此前五项内置收敛；本包不同于已有GitHub v0.3.16附件，本轮没有发布或替换发行版。

Release构建通过。安装前回拉手机原APK并核对原证书，新包通过原签名、包名/版本/最低Android 11/非调试标记、包内源码提交及16 KiB ZIP对齐；资源含原五项与block_mother，不含此前六个退休资源。使用 `adb install --no-incremental -r` 成功，未卸载或清数据；安装后回拉base.apk与本地包SHA-256一致：`d87c1e5ea73370269ec6e006e4af65c79f677b09a5ee1cd721e4f7aba3746cca`。

安装前打开本应用使目标离开并保存进度，采集即时快照；安装前后三条target/eligible/usedMs/remainingMs/cycleStart/blockedUntil逐项完全一致。配置与当前周期参数均保留刷5/歇115分钟：抖音和红果已用0、各剩300000 ms；小红书已用6768 ms、剩293232 ms，三条均未禁用，周期起点不变。appId=10445、首次安装时间2026-11-14 12:00:57保持。未改戒律、切换主题、重传图片或开关权限，原私有目录保留；个人主题及图片没有另行导出逐文件比对。

安装后启动MainActivity返回Status: ok，服务connected=true、consent=true，Enabled/Bound正常、Binding/Crashed未含本服务，journal failures=0。没有打开目标App消耗额度；新主题实际选择、物理旋转、自然用满/退出/到期仍待用户体验，沿用亲妈催婚主题实现节的模拟器检查边界。安装前后状态、原/新/回拉APK及installation-verification.json仅存本机 `~/.cache/goule-android/mother-theme-install-20261117/`，签名资料和私有运行材料不提交。安装后补记录，提交前 `./scripts/check` 通过（Python 46项、Debug/Release各58项、Lint和构建）；本轮未在正式手机执行隔离测试或消耗目标额度。

## 亲妈催婚主题（2026-11-17）

按用户确认的原图与文案，在原五项末尾新增 `mother`「亲妈催婚」，不复用退休的 `marriage`。原图仅编码为1536×1024 WebP（质量95），保留妈妈「你是准备跟手机结婚啊？！」、女儿「它至少秒回我……」两句图内对白。页面喝止「还刷！对象呢？！」、提醒「手机都快盘出包浆了，对象倒是带一个回来啊！」，按钮「收起手机」「妈我起了」仍对应最近任务与返回桌面。仅新主题使用完整图片适配和最多36sp的标题，原五项显示行为不变。

最终 `GOULE_UI_DEVICE=emulator-5554 ./scripts/check` 通过：Python 46 项，Debug/Release 各58项单元测试零失败/错误/跳过，Lint、Debug及instrumentation APK构建通过；Android SDK x86_64 模拟器（Android 15/API 35）隔离包十一组 PASS。前台识别、计时/系统浮窗/锁屏出口、引导与既有主题回归继续通过，生产服务/规则逻辑未修改。新增检查并入原有检查入口。

新增检查覆盖：真实主题下拉选择/预览、`Activity.recreate()` 保留全部确认文案、预览按钮返回设置；旧 fixed=exam、随机模式、kitchen/exam池、marriage/work删除记录保持，新主题固定/单项随机、保存重读及删除回退，退休marriage继续无效。全部主题通过1/1.3/2倍字号、横竖屏/安全区和按钮回归；新图解码尺寸正确，360×640及640×320往返时图片矩阵完整落在图片区域，两个对白不被裁切。普通字号360×640新主题喝止单行，两按钮无需滚动。

首轮检查通过后，人工渲染发现新主题沿用大标题时产生孤行并挤出第二按钮，已仅收紧此主题标题并加入首屏检查；上述最终检查与人工查看 `theme-mother.png`、`theme-mother-landscape.png`、`mother-theme-preview.png` 均通过。原五项图片逐字节、目录原条目/顺序及生成清单原场景记录逐项对照修改前一致。渲染图仅存本机 `~/.cache/goule-android/mother-theme-20261117/`，检查日志为同级 `mother-theme-check.log`，不提交私有运行材料。

仅连接该模拟器，未检测到正式 OPPO 手机；隔离包已清理。本轮未安装正式包、未改手机主题/戒律/权限或发布发行版。OPPO PLJ110（既有设备：Android 16、ColorOS V16.1.0）新主题真实用满、最近任务/桌面出口、物理旋转与自然到期仍待真机验证，不能以模拟器视图及窗口重放代替。

## 五项内置主题收敛（2026-11-17）

按用户最新清单，内置目录仅保留并排序为戒律长老、锅要糊了、摸鱼警报、挂科预警、拯救网瘾少年，保留各自原图片、台词和两个按钮名。全局移除魔法劝退、菜地除草、月老催婚、出门晒太阳、戒刷心魔的目录、五张图片与生成清单，奶瓶继续退休。用户自定义主题及图片、有效随机勾选与删除记录均保留，未恢复用户主动删除的内置项；原长老仍不可删。

最终 `GOULE_UI_DEVICE=emulator-5554 ./scripts/check` 通过：Python 46 项，Debug/Release 各 58 项单元测试零失败/错误/跳过，Lint、Debug 与 instrumentation 构建，以及 Android SDK x86_64 模拟器（Android 15/API 35）十一组 PASS。原遮罩稳定性夹具使用的退休主题改为「挂科预警」，仍验证已显示遮罩不被设置变化或重复 tick 替换，离开后再次进入使用新主题。

新增兼容夹具覆盖 baby/magic/garden/marriage/sunlight/craving 在旧版与已统一存储下的固定项、单项随机池与陈旧预览全部回到长老，随机模式保持。混合池仅过滤退休项，保留 kitchen 与个人主题；已删除 work 仍不出现，个人元数据/原图可读取并可固定选择。全部有效主题继续通过多字号/横竖屏/安全区、按钮边界/回调和现有编辑/导入/删除回归；RuleStore 仍保持原配置。

已人工核对 `unified-theme-page.png` 的双下拉、图片/台词和操作区；共13张渲染图与完整检查日志仅存本机 `~/.cache/goule-android/five-themes-20261117/`。隔离包已卸载，模拟器已停止。未检测到正式 OPPO 手机，未执行正式覆盖安装或发布，未改正式戒律、主题、图片或权限；原签名 Release 已从干净提交 `588b65c92f259652dfb0b91c5cf7a21b8a7188b5` 构建并准备，仍为 0.3.16/code 19 的未发布功能包。包名、版本、最低 Android 11、非调试标记、包内源码、原证书与16 KiB ZIP 对齐核对通过，原证书与此前手机回拉 APK 一致。aapt 核对保留 discipline_elder/block_kitchen/block_work/block_exam/block_rescue，且不存在 block_magic/block_garden/block_marriage/block_sunlight/block_craving/block_baby 资源。

APK SHA-256：`e33378af8fe79a0d7b5d6a990bb5d9385ff830241a92ad21652b545c25fbae72`；本机文件 `~/.cache/goule-android/five-themes-20261117/prepared-release/goule-five-themes-588b65c.apk`，旁有 build-verification.json。签名资料未进入工作区、提交或日志。本包不同于已有 GitHub v0.3.16 发行附件；再次检查 ADB 无正式手机，未执行安装、卸载或清数据。


## 统一主题功能包保留数据安装（2026-11-17）

用户重新连接此前手机后，00:03:28（北京时间）将已准备的 `a64145c0818d1c5286270a64e446235799a099dc` 统一主题功能包原签名保留数据覆盖安装到 OPPO PLJ110，Android 16/API 36、ColorOS V16.1.0。仍为 0.3.16/code 19，替换 `4c66c91` 的下拉/删除功能包；本轮复用先前验收的 APK，未新建发行版或替换现有 GitHub v0.3.16 资产。

安装前重新回拉手机 APK，核对原证书与新包一致；重新验证待装文件摘要、版本/包名、最低 Android 11、非调试标记、16 KiB ZIP 对齐和包内源码提交。安装后回拉 base.apk 与待装文件 SHA-256 完全一致：`b5dc2b05fe1a364ed64dfc7bce257f5e414efb364f79394c654692f961322356`，继承构建时无 block_baby 资源的检查。未读取或更换签名密钥。

安装前只打开本应用使目标离开并保存进度，采集即时 Service 与包信息，再用 `adb install --no-incremental -r` 成功覆盖安装。三条 target/eligible/usedMs/remainingMs/cycleStart/blockedUntil 完全相同，配置与当前周期快照都保持 5/115 分钟：抖音已用 0、剩余 5 分钟；红果已用 300000 ms、剩余 0，原禁用至 00:47:34.060；小红书已用 300000 ms、剩余 0，原禁用至 01:29:52.852。没有重置用量、周期或禁用截止。appId=10445、首次安装时间 2026-11-14 12:00:57 保持，未卸载或清数据。

安装后启动 MainActivity 返回 Status: ok，触发新外观存储的兼容入口；服务 connected=true、consent=true，Enabled/Bound 正常、Binding/Crashed 为空，journal failures=0。没有打开目标 App、手改戒律/主题、重新上传图片或开关权限。保留原应用数据目录；主题私有文件及迁移字段未另行导出逐项比对，不把启动正常等同用户所有个人主题内容已完成真机验收。旧图/CUSTOM/勾选兼容已由上一节隔离夹具验证，手机具体主题内容、编辑器和随机画面、自然到期仍待用户体验。

原始安装前后状态、原/回拉 APK、verified-build.json 与 installation-verification.json 仅存本机 `~/.cache/goule-android/unified-themes-install-20261117/`；本轮未修改应用代码，安装后补文档并在提交前运行 `./scripts/check`。


## 统一主题集合、完整内容与奶瓶移除（2026-11-16）

按用户新要求，显示方式只保留固定/随机，内置与多个自定义主题共用一个目录与随机池。每项完整包含图片、喝止/提醒台词、两个按钮名称；新增与编辑表单保存后生效，图片先处理为草稿。原长老不可删；从目录、图片资源与生成清单移除奶瓶召唤，旧 baby 选择与预览回到长老。按钮动作仍为最近任务与桌面，名称跟随主题；内置场景也配置各自的两个名称。

最终 `GOULE_UI_DEVICE=emulator-5554 ./scripts/check` 通过：Python 46 项，Debug/Release 各 58 项单元测试零失败/错误/跳过，Lint、Debug 与 instrumentation APK 构建，Android SDK x86_64 模拟器（Android 15/API 35）十一组 PASS。既有计时/系统窗口/引导回归继续通过；统一主题、编解码、迁移与编辑器检查接入同一入口。

隔离包验证：两个个人主题分别可固定选择、单独或混合加入随机池；元数据持久保留图片指针、台词、两项按钮名。损坏/拒读/超限图片不改旧主题，取消替换草稿保留旧文件；保存替换使用新文件，提交后清理旧图，稳定 ID 与随机勾选保持。表单内容/草稿图与预览在真实 Activity 重建后保留；保存、编辑、放弃确认、取消选图和删除个人主题通过。原长老保护及删除其他项持久化通过；奶瓶不出现在候选目录，旧 fixed/pool/预览回退。

旧版单图迁移使用独立 Context/存储夹具：CUSTOM 转固定选择「我的主题」，原图、台词和按钮名保留，已保存的随机排除继续尊重，未支持个人随机的旧版补入一次；迁移后重新读取不丢图。没有图片、只有 baby 旧选择时保留随机模式、候选回到原长老。上述迁移没有在正式手机执行，未修改正式戒律或照片。

全部内置主题与六字按钮个人主题在 1/1.3/2 倍字号、多组横竖屏/安全区和窗口往返下通过原生测量、绘制、按钮边界与回调检查。渲染检查曾发现单行水平滚动布局使离屏绘制的按钮名不可见，已改为有界单行布局，并加入文字布局宽度/存在性回归；最终人工核对主题页和个人主题渲染图，按钮名可见。横屏长按钮用12sp，其他用14sp，仍随系统字体缩放。18 张渲染图和完整日志仅在本机 `~/.cache/goule-android/unified-themes-20261116/`，隔离测试包已清理。

本轮未检测到此前 OPPO 手机，已请求用户重新连接；目前未覆盖安装或发布，未修改手机戒律或图片。实际手机主题/图片迁移、物理旋转与自然禁用/到期仍待真机体验，不把隔离夹具当作最终真机验收。

已从干净提交 `a64145c0818d1c5286270a64e446235799a099dc` 构建原签名 Release，仍为 0.3.16/code 19 的未发布功能构建。构建、包名/版本/最低 Android 11/非调试标记、包内源码提交和16 KiB ZIP 对齐通过，证书与此前手机回拉 APK 的原证书 a99c6b29…62103559 一致；aapt 核对不存在 block_baby 资源。安装包 SHA-256 为 `b5dc2b05fe1a364ed64dfc7bce257f5e414efb364f79394c654692f961322356`，本机路径 `~/.cache/goule-android/unified-themes-20261116/prepared-release/goule-unified-themes-a64145c.apk`，旁有 build-verification.json。签名资料未进入工作区、提交或日志。该包不同于现有 GitHub v0.3.16 资产；没有手机连接，未执行 install、清数据或卸载。


## 主题管理与自定义随机保留数据安装（2026-11-16）

沿用用户此前「安装到手机体验，保留现有戒律和数据」的授权，2026-11-16 23:20:08（北京时间）将干净提交 `4c66c91f496cb4b7a935d4a53317ef2788b37bea` 的 Release 原签名保留数据覆盖安装到 OPPO PLJ110，Android 16/API 36、ColorOS V16.1.0。仍为 0.3.16/code 19，替换此前 `6de2e4e` 的多主题功能包；这是新的下拉/删除/自定义随机功能构建，不是现有 GitHub v0.3.16 资产，本轮未发布或替换发行附件。

Release 构建、原签名证书 a99c6b29…62103559、包名/版本/最低 Android 11/target 35、非调试标记、包内源码提交及 16 KiB ZIP 对齐通过。安装后回拉 base.apk 与本地签名包摘要一致：`1a8938975b6f4816e66a8ed539349c22d19ea2cc9c9865ef27b8f2d874ba5308`。签名资料未进入工作区、提交或日志。

安装前只打开本应用，让目标离开并保存进度，然后采集即时快照；未人为使用目标 App。`adb install --no-incremental -r` 成功，未卸载或清数据。三条戒律的 target/eligible/usedMs/remainingMs/cycleStart/blockedUntil 完全相同，配置及当前周期快照均保留 5/115 分钟：抖音 usedMs=58068、remainingMs=241932（显示 4 分 2 秒），红果已用 0、剩余 5 分钟，小红书已用 5 分钟、剩余 0，原禁用截止仍为 23:29:52.852。appId=10445、首次安装时间 2026-11-14 12:00:57 保持。

无障碍 Enabled/Bound 正常、Binding/Crashed 为空，connected=true、consent=true，journal failures=0；安装后 MainActivity 启动返回 Status: ok。未开关权限、修改戒律、替用户选择/删除正式主题或重新上传图片，沿用原私有目录；图片与外观首选项未另行导出逐文件比对。旧版已有图片加入随机池的兼容规则，以及用户显式取消后保持排除，已由前述模拟器检查验证。

这次真机结果覆盖安装身份/戒律状态保留、源码/签名/回拉摘要、启动与实际服务绑定；真机下拉弹出、确认删除、自定义图片随机画面、自然到期及系统临时面板仍交由用户体验，未将隔离包的删除全部等同正式数据验收。材料仅存本机 `~/.cache/goule-android/theme-management-20261116/phone-install/`，Release 与检查日志位于父目录。安装后仅补文档，提交前运行 `./scripts/check`。


## 主题下拉与内置删除（2026-11-16）

按用户反馈，将显示方式与主题选择改为原生下拉框，页面仅渲染当前主题的一张图；随机主题池使用多选弹窗，保存才生效、取消保留原值。按用户后续确认，原戒律长老始终保留且不可删除，其余十项可确认永久从本机主题库移除。固定/随机/预览及损图/失效回退统一排除已删项，删除记录跨保留数据重启/升级保留；APK 字节不能由运行中的 App 逐项移除，操作不会缩小 APK。

`GOULE_UI_DEVICE=emulator-5554 ./scripts/check` 全部通过：Python 46 项、Debug/Release 各 60 项单元测试零失败/错误/跳过，Lint、Debug 与 instrumentation APK 构建，以及 Android SDK x86_64 模拟器（Android 15/API 35）十组 PASS。新增 `ThemeManagementChecks` 已接入相同入口；既有计时、系统窗口、权限引导、图片导入、全部主题横竖屏/大字号与预览真实重建继续通过。

新增 Android 检查实际调用下拉回调与 AlertDialog：页面只有两个 Spinner/一张插画；原长老没有删除按钮且存储层拒绝；取消删除不变，删除固定项回到长老并移出随机池，旧预览 ID 也回到长老；删除随机池唯一项保留随机模式并改用长老；空池无法保存，弹窗取消保留旧值，保存正确应用所选项。新存储实例保留已删记录；其余十项全部删除后仅剩原长老，陈旧 fixed/pool 数据不会复活主题；自定义模式、个人图片和规则存储不受内置删除影响。删除全部的检查仅在隔离包执行，未操作正式手机主题。

用户补充的自定义随机已完成同一轮检查：导入/替换成功自动加入随机池，只有自定义图片的随机池能实际抽取图片；旧版已有图片自动补入一次，用户明确取消后在新存储实例仍保持排除；随机弹窗实际包含并勾选「自定义图片」，保存和新实例抽取均通过。删除唯一自定义图片保留随机模式并回到原长老，候选列表不再包含该图。此前不含自定义随机的初轮已通过，最终补充后的 `./scripts/check` 仍为十组 Android PASS，JVM 增至各 60 项。

已人工查看 `theme-dropdown-page.png`，两个下拉框、单张预览、台词与预览/删除操作显示正常。19 张检查渲染图和完整日志仅存本机 `~/.cache/goule-android/theme-management-20261116/`；隔离测试包已清理。随后沿用用户此前授权进行原签名保留数据覆盖安装，手机版本、证书、规则与实际结果另记，不将模拟器回归描述为最终 ColorOS 全路径验收。


## 多主题功能构建保留数据覆盖安装（2026-11-16）

按用户「安装到手机，保留现有戒律和数据」要求，从干净提交 `6de2e4e7ed0554a9b16cff10684cb91afd3c309a` 构建 Release，22:51:21（北京时间）在 OPPO PLJ110、Android 16/API 36、ColorOS V16.1.0 使用 `adb install --no-incremental -r` 成功覆盖安装。手机从 0.3.15/code 18 更新至多主题功能构建 0.3.16/code 19；该包不同于此前 GitHub v0.3.16 发布附件，本轮未新建发行版或替换资产。

Release 构建、原发布签名证书（SHA-256 a99c6b29…62103559）、包名 com.goule.prototype、最低 Android 11/target 35、非调试标记、包内源码提交及 16 KiB ZIP 对齐通过。手机原 APK 与新包证书一致；安装后回拉 base.apk 与本地签名包 SHA-256 完全一致：`9bdb0eee04c0478bf58dba9bf19d9922f44df59ce4178dc6e05a6c3696d504ee`。签名资料仅在本机私有目录使用，未进入工作区或日志。

安装前即时 Service 与安装后 Service 的三条 target/eligible/usedMs/remainingMs/cycleStart/blockedUntil 逐项完全相同。抖音、红果各已用 0、剩余 5 分钟；小红书已用 300000 ms、剩余 0，仍禁用至当天 23:29:52.852。三条配置及当前周期快照均保持可刷 5 分钟/休息 115 分钟，未重置用量或周期。appId=10445、首次安装时间 2026-11-14 12:00:57 保持；未卸载或清除数据。

系统自动重新连接无障碍服务，Enabled/Bound 正常、Binding/Crashed 为空，connected=true、consent=true，journal failures=0。随后启动本应用 MainActivity，`am start -W` 返回 Status: ok，服务仍保持连接。未打开目标 App、消耗额度、改戒律/主题或开关权限。用户可从「设置 → 禁用页主题」自行体验。

这次验证覆盖原签名安装、数据/安装身份保留、启动和实际服务绑定；OPPO 真实主题选择、系统图片选择/取消、物理旋转、自然用满/到期与面板往返仍待体验，不以安装成功代替完整功能验收。材料仅存本机 `~/.cache/goule-android/block-themes-20261116/phone-install/`，包括安装前后状态、原/新/回拉 APK、build-verification.json、installation-verification.json；Release 构建日志在其父目录。安装后仅补充文档，提交前执行 `./scripts/check`。


## 多主题禁用页（2026-11-16）

范围为设置中的固定主题、可排除场景的随机主题池、自定义本机图片及复用真实布局的预览。保留原长老，新增十张紫金漫画插画及对应台词，资源与完整生成提示词见 [主题说明](block-themes.md) 和 [提示词清单](block-theme-prompts.json)。只改变外观，不写规则配置、已用量、周期或禁用截止。

已在 Android SDK x86_64 模拟器（Android 15/API 35）隔离包 `com.goule.uiverify` 执行：所有内置主题与自定义图在 1/1.3/2 倍字号、360×640、640×320、800×360、640×240 dp 及往返重排时的截止保持、系统栏/刘海/手势安全边距、不透明边缘与横屏退出按钮触摸；图片限边解码、替换、损坏/拒读/超过 20 MiB 的旧图保留及新存储实例读取；随机单主题池、空池拒绝和最后一个复选框恢复。设置入口/模式切换、预览返回、真实 `Activity.recreate()` 后保留厨房主题、系统选图请求的取消结果和原图/模式保留、删除回退以及私有图片损坏回退全部通过。取消用例由匹配完整 MIME/category 的 Instrumentation Monitor 返回，未操作实际厂商选择器。

服务事件重放使用生产 `FreezeService`，仅注入捕获 add/remove 的 WindowManager（未绑定服务没有系统 overlay token），检查随机厨房主题在重复事件和改选后仍保持，退出再进入采用菜地主题，原截止和多 App 用量保持。原有计时/系统临时窗口/最近任务/通知栏/屏幕出口和首次三步引导继续回归。该捕获不是系统真实遮罩附着或 ColorOS 自然事件验收。

开发中首次图片夹具进程缺少 Kotlin 运行库，已改为只依赖 Android/Java 的测试 APK Provider；事件重放原先无系统 token，改为在测试中替换窗口依赖，生产 Service 无额外接口；预览需要等待 Android measure/layout 后才检查内容。此前失败运行不记为通过。

最终 `GOULE_UI_DEVICE=emulator-5554 ./scripts/check` 全部通过：Python 46 项，Debug/Release 各 56 项单元测试零失败/错误/跳过，Lint、Debug 与 instrumentation APK 构建、九组 Android PASS；新增选择策略回归与主题 Android 检查已接入同一入口。渲染产物共 18 张（原布局六张、新旧主题及自定义图十二张），人工查看厨房、救援和自定义图，主体、对白、截止及退出区正常。测试包已由脚本清理。已快进合入远端 v0.3.16/code 19 的自动发布记录；这些新主题不在此前发布的 v0.3.16 附件中。

本轮无实体设备连接，未覆盖安装正式包、未发布新版本或修改正式手机戒律/权限。真机待验：OPPO PLJ110（Android 16、ColorOS V16.1.0）的系统图片选择/取消往返、不同图片方向、物理旋转、真实用满拦截与退出、自然到期及系统临时面板。不能用模拟器视图绘制和合成事件代替上述真机结果。原始检查与渲染图只留本机 `~/.cache/goule-android/block-themes-20261116/`。


## ColorOS 容器修复包保留数据覆盖安装（2026-11-16）

沿用用户此前授权的原签名保留数据安装方式，从干净提交 `be9ff240b2c22cf2e4a0b86efd42f1ad2f3a8967` 构建 Release；10:40:29（北京时间）在 OPPO PLJ110、Android 16/API 36、ColorOS V16.1.0 使用 `adb install --no-incremental -r` 成功覆盖安装。正式包从 `732fb56` 的 0.3.14/code 17 诊断构建更新到 0.3.15/code 18 的系统容器修复功能构建。该 APK 与现有 GitHub v0.3.15 发布资产不同，本轮未创建新发行版或替换资产。

Release 构建、包名 com.goule.prototype、最低 Android 11/target 35、非 debuggable、包内源码提交与 16 KiB ZIP 对齐通过。安装前 APK 与新包证书均为原发布证书 a99c6b29…62103559；签名资料未进入工作区、日志或提交。安装后回拉 base.apk 与本地签名包摘要完全一致：`23436da63208d8e059d5d56b8b08a62c6799aad5d020a1b3c6d3484062401347`。

对比安装前即时 Service 与安装后 Service，三条原戒律的 target/eligible/usedMs/remainingMs/cycleStart/blockedUntil 全部一致。红果已用 13510 ms、剩余 286490 ms（4 分 47 秒），抖音与小红书均已用 0、剩余 5 分钟；没有补算本次漏记或重启周期。appId=10445、首次安装时间 2026-11-14 12:00:57 保持。

安装后系统自动重新绑定服务，Enabled/Bound 正常，Binding/Crashed 为空，connected=true、consent=true。持久历史包含新进程 version=0.3.15/18、service connected 与原有历史，journal failures=0、pendingDropped=0，诊断持续有效。未启动目标或手工启动页面、未修改权限/戒律/白名单、未强停或重启手机；覆盖安装本身导致原进程退出，由系统重新连接。

修复后的持续计时、通知栏和原截止由前述 Android 15 隔离重放验证；本轮没有在正式手机人为消耗额度、制造低电量或等待 SeedlingCard 再次自然触发。真实 ColorOS 新包的低电量/通知栏/其他临时面板端到端复测仍待后续使用，不能仅凭安装成功宣称所有 SystemUI 路径均已通过。

材料仅存本机 `~/.cache/goule-android/timing-recurrence-20261116/phone-install/`，含安装前后采集、原/新/回拉 APK、签名与元数据、源码记录、build-verification.json 和 installation-verification.json；Release 日志在其父目录。安装后仅补文档记录，提交前 `./scripts/check` 通过（Python 46 项、既有 JVM/Lint/Debug 与测试 APK 构建检查）；这不代表额外的真机行为回归。


## ColorOS 系统容器导致漏计（2026-11-16）

10:29（北京时间）OPPO PLJ110、Android 16/API 36、ColorOS V16.1.0，manufacturer=OPPO，只读采集；仍是 `732fb56` 的 0.3.14/code 17 诊断构建。持久日志和系统日志确认：10:17:26.565 电量达到 20%，.874 系统创建 SeedlingCard 窗口，.888 WindowManager 焦点仍为红果，.895 SystemUI FrameLayout/fullScreen=false/changes=0 事件让服务 .897 清空前台并取消 tick。红果直至 10:27:27 才离开；其间心跳正常、samples=6788/usedMs=13510 不变，剩余向上取整为 4 分 47 秒。当前周期未跨界、saved=true、journal failures=0，没有中途退出或存储失败证据；主线程此前一次 2066 ms 样本拒绝不足以解释持续停表。完整时间线与归因边界见 [本轮诊断](timing-recurrence-2026-11-16.md)。

新策略仅在 OPPO + com.android.systemui + android.widget.FrameLayout + fullScreen=false + contentChangeTypes=0 保留已有目标，照常检查锁屏/权限/周期；不从空前台恢复，不扩展到其他厂商/全屏/其他类/包或带变化标记的事件。本机通知栏出现/收起使用相同类但带 changes=16/32，保留暂停逻辑；已有音量和普通 Dialog 逻辑不变。无新权限或窗口内容读取，不补算历史漏记。

修复前新回归确实失败：JVM 50 项中一个精确组合检查失败；Android 15/API 35 x86_64 模拟器在生产 FreezeService 重放通知栏→目标恢复→音量→无变化标记 FrameLayout 时，前台被清空，新的持续计时断言失败。保留旧行为的策略先返回 false，再修改为上述精确组合；失败位置及状态与真实错误一致。

修复后 `GOULE_UI_DEVICE=emulator-5554 ./scripts/check` 通过：Python 46 项、Debug/Release 各 50 项单元测试零失败/错误/跳过，Lint、Debug 与 instrumentation APK 构建通过，Android 八组 PASS。新增生产 Service 检查覆盖同一序列后无新目标事件仍持续累计、通知栏暂停、空前台不恢复、全屏窗口/设置/中断退出、另一 App 用量独立、禁用原截止保持；既有最近任务、屏幕/锁屏、音量、页面和遮罩布局回归同时通过。OPPO 策略在隔离模拟器注入，不能称为真实 SeedlingCard 的修复后端到端验收。

本轮已同步 GitHub main 的既有 v0.3.15 发布文档/版本更新，保留其历史；本次代码修复仍为后续功能构建，发布或手机覆盖安装另记。原始手机材料与修复前后检查日志只留本机 `~/.cache/goule-android/timing-recurrence-20261116/`，不提交完整系统日志。首次取证未操作手机界面、目标、权限或戒律；最终正式包安装、真实通知栏/低电量浮层自然触发及新周期行为仍需按实际结果续记。


## 诊断功能构建保留数据覆盖安装（2026-11-15）

按用户「安装到手机」要求，从干净提交 `732fb564c6c1d93953dc1e8bb6a7a36eeaa03158` 构建 Release，沿用原 PKCS12 证书覆盖安装，保留数据。08:01:47（北京时间）OPPO PLJ110、Android 16/API 36、ColorOS V16.1.0 安装成功，正式包从 0.3.13/code 16 更新到 0.3.14/code 17 的诊断功能构建。该 APK 包含本次持久日志，与此前 GitHub v0.3.14 发布附件不同；本轮未创建新发行版或替换发布资产。

Release 构建通过；包名 com.goule.prototype、最低 Android 11/target 35、非 debuggable、包内提交及 16 KiB ZIP 对齐核对通过。原发布证书 SHA-256 与安装前一致（a99c6b29…62103559），签名资料未写入工作区或日志。新 APK 与安装后回拉 base.apk 的 SHA-256 完全一致：`ba44c8158014bafe5b0d6af2b4aa073efbedc06f8e132ad4778d3342c2e08d90`。

安装前后 appId=10445、首次安装时间 2026-11-14 12:00:57 保持。Service 仍有原三条戒律，当前实际/配置参数均为 5/115；逐条对比已用量、剩余额度、eligible 和禁用截止一致：红果 usedMs=11998、remainingMs=288002（向上取整 4 分 49 秒），抖音和小红书均剩余 5 分钟，三者无有效禁用截止。红果和小红书周期起点不变；抖音旧周期已在 07:59:52.023 结束，新服务刷新后按原相位推进一个 120 分钟周期至该边界，不以 08:01 安装时间重新起算，也未清零红果漏计现场。

安装后系统自动重新绑定服务，Enabled/Bound 正常、Binding/Crashed 为空，connected=true、consent=true，三个目标均 eligible=true。真实 Service 的 `--history` 导出包含 process start version=0.3.14/17、原进程 PACKAGE UPDATED 退出、service connected、锁屏状态及三条周期/计时快照；journal failures=0、pendingDropped=0，证明手机已经启用本机持久诊断。

全程未解锁、启动 Activity、打开红果或其他目标、修改戒律、清数据、卸载或开关权限，手机仍保持锁屏。未做新的真实计时/横屏/自然用满回归，不能将安装与日志可读写记为今早漏计根因已确认或已修复。

材料仅保存在本机 `~/.cache/goule-android/timing-diagnostic-install-20261115/`，含安装前后只读采集、原/新/回拉 APK、签名/元数据/源码记录、构建日志和 installation-verification.json；不提交原始手机材料。此次只补安装记录，提交前 `./scripts/check` 通过（Python 46 项、既有单元测试/Lint/Debug 与测试 APK 构建），自动检查结果不代表新的真机行为回归。


## 红果漏计候选机制与持久诊断（2026-11-15）

07:42–07:43（北京时间）OPPO PLJ110（Android 16、ColorOS V16.1.0）只读取证，正式版本 0.3.13/code 16。系统 events/UsageStats 记录红果 07:13:44–07:25:00 前台，Service 仅记录 usedMs=11998、remainingMs=288002，与用户「4 分 49 秒」一致；当前 Enabled/Bound 正常、Binding/Crashed 为空，没有今天新增进程退出、采样拒绝或跨该使用时段的周期重置证据。07:13:57 切入横屏页与约 12 秒累计量接近，但故障瞬间详细无障碍事件已被 32 条内存记录覆盖，不能认定真机根因。完整候选矩阵、证据局限和后续取证方法见 [本轮排查](timing-diagnosis-2026-11-15.md)。

实现仅增加诊断：异步单线程、队列有界、4 × 256 KiB 本机私有日志；服务前台变更原因、事件水位与目标/系统类、每 30 秒快照、周期/额度/计时水位、拒绝样本、tick 延迟、存储失败和生命周期；进程启动读取前三条系统退出原因。无新增权限、无节点/文本读取、不保存未配置 App 身份、不上传。日志失败与诊断丢弃单独报告，不修改前台判定、计时算法、系统窗口白名单或历史已用量。

`JAVA_HOME=<本机 JDK 17> ANDROID_HOME=<本机 SDK> GOULE_UI_DEVICE=emulator-5554 ./scripts/check` 通过：Python 46 项；Debug/Release 各 48 项单元测试零失败/错误/跳过，Lint、Debug 及 instrumentation APK 构建通过。新纯 Kotlin 回归验证超过 32 条日志与重新打开保留、容量/多字节/换行限制，以及无法写入时不抛错；Python 回归验证非 UTF-8 系统材料原样保存、私有权限、命令只读和超时保留部分结果。

Android SDK x86_64 模拟器，Android 15/API 35，七组 Android 检查全部 PASS。新增生产 Service 重放通过同包竖屏→横屏持续累计、迟到非目标忽略、未知非全屏 SystemUI FrameLayout 后停止且没有新目标事件不恢复、目标返回恢复、空包名/中断/亮屏后清空、旧事件不恢复以及 2.2 秒主线程停顿被拒绝；持久历史包含已超出 32 条内存窗口的前后原因。既有最近任务、音量面板、通知栏/设置/屏幕出口及页面/遮罩布局回归同时通过。这些是隔离事件重放，不是真实 ColorOS 现场复现。

另在同一模拟器独立包验证真实系统绑定 Service 的 `dumpsys ... --history` 导出；关闭该测试服务、强停测试进程并重新打开 MainActivity 后，Activity 导出仍包含前会话 service connected、新旧两个进程会话与 previous_exit，journal failures=0。首次验证发现 Activity 导出命令多写了一个 activity 参数，已改为直接组件名并实际验证；一次清理空系统设置值的测试命令失败，改用 settings delete 后恢复初始设置并完成验证。独立测试包已卸载，不改正式手机的设置或数据。

模拟器首次冷启动持续 offline，关闭后禁用 Vulkan、显示内核日志重新冷启动，确认 RUNNING_UNLOCKED 后才开始 Android 检查。原始材料位于本机 `~/.cache/goule-android/wake-timing-20261115/`，包含原手机证据、检查/模拟器日志、Service/Activity 历史导出与 emulator-journal-verification.json；不提交完整系统材料。

用户本轮表示暂不方便手机复现，已停止只读观察；观察期间未增加可用于定因的红果前台片段。本轮未操作正式手机界面或修改其设置、未覆盖安装或发布 APK，手机仍为 0.3.13，不具有新增持久日志。待验收：ColorOS 实际竖屏转横屏/沉浸式临时窗口、自然长时间后台/锁屏、真实异常后日志恢复，以及最终停止事件的归属。不能将模拟机制的复现描述为今早根因已确认或已修复。

## GitHub 定时与手动发布（2026-11-14）

范围为每日北京时间 08:17 的有变化发布及手动阶段发布，默认继续预发布，自动递增版本和 Android versionCode，原签名构建后先验证 GitHub 附件再公开，再直接同步 Gitee。发布与同步共用不会取消待运行任务的串行队列。未完成发布状态冻结标签/提交/版本并支持续发；普通原子推送拒绝构建期间的 main 竞争，不强制覆盖历史。

已完成 44 项 Python 回归，覆盖文档/机器人提交不循环发版、应用与构建代码更新、手动强制/指定版本、版本递增与冲突、未完成版本恢复、状态篡改、无共同历史、签名缺失/证书不符、已公开附件缺失拒绝覆盖、推送失败不创建 Release，以及部分上传草稿复用原 APK 并仅补齐校验文件；新增 Gitee Git 128 错误有界重试与 ref 冲突不重试检查。新增回归已接入 `./scripts/check`。通过新 APK 验证器核对现有 v0.3.13/code 16：原证书、包名/版本、非调试标记、Android 11 最低版本、ZIP 对齐及包内提交 `9549f70` 均通过。

本机现有 PKCS12 只有 `goule-release` 一个 PrivateKeyEntry，证书 SHA-256 与原发布一致：`a99c6b2900299c459fe1121dc7868762d8afb66ec9f5b4819a471a5b62103559`。使用临时 APK 副本实际执行签名并核对证书，两项密码输入均通过，临时副本已移除。未输出密码或私钥；云端签名 Secrets、实际工作流计划及发布/跳过验收待配置后补记。仅改发布流程，未安装手机或改变应用行为。

真实环境首轮计划检查 [43567003679](https://github.com/grovecai/goule/actions/runs/43567003679) 在 SDK 设置步骤失败：runner 已预装 Android SDK，但 `sdkmanager` 不在 PATH。改为检查固定 SDK/Build Tools 35.0.0 路径，缺失时从 cmdline-tools 的明确路径安装；计划检查使用独立只读并发组，不等待真实发布队列。Gitee 代码同步 [43566543894](https://github.com/grovecai/goule/actions/runs/43566543894) 遇到 300 秒连接超时，增加退出码 128 的最多两次原参数重试；ref 冲突仍立即失败，不强制推送。后续预览/发布结果待续记。

针对 runner 到 Gitee Git HTTPS 的反复连接超时，推送增加 Git 原生 `--ipv4`，保留正常 TLS 校验；这是兼容性重试措施，尚未据此确认网络故障根因。尝试取消旧的重复 main 代码同步任务时，这些任务已经自行结束，未实际取消它们，也未取消 Release 同步或实际发布任务。

云端只读预览 [43567451703](https://github.com/grovecai/goule/actions/runs/43567451703) 成功：固定提交的 checkout、JDK 17 设置、SDK/Build Tools 35 定位及真实 GitHub 发布查询全部通过。预览计划为 `mode=new`、`v0.3.14`、versionCode `17`、预发布，基线 v0.3.13；预览未修改版本、创建标签、构建或上传附件。最新自动代码同步 [43567626158](https://github.com/grovecai/goule/actions/runs/43567626158) 成功，证明队列与修正后的 Git 推送能在实际环境执行。

44 项回归及完整 `./scripts/check` 通过；actionlint v1.7.12 对除官方 `queue: max` 新字段外的工作流配置检查通过，该字段由 GitHub 的实际运行验收。每日 cron 已部署，尚未等待次日的真实定时触发；同一代码变化判断已覆盖离线回归及下述实际运行。用户明确批准后才配置原私钥及密码的云端 Secrets。

16:09（北京时间）按用户授权，将现有原发布 PKCS12 的 Base64、store password 和 key password 通过标准输入分别保存到 `ANDROID_RELEASE_KEYSTORE_BASE64`、`ANDROID_RELEASE_STORE_PASSWORD`、`ANDROID_RELEASE_KEY_PASSWORD`，GitHub 列举确认三个加密 Secret 存在。密钥和密码未写入工作区、提交或日志，原 `GITEE_TOKEN` 保留。

[真实手动发布 43574433249](https://github.com/grovecai/goule/actions/runs/43574433249) 全部通过，耗时 7 分 49 秒：从干净发布提交 `75e039dbed4e72a6c630fa690385cf2357893513` 构建 v0.3.14/code 17，44 项 Python 回归、Android 单元测试/Lint/Debug/测试 APK 检查及 Release 构建通过。原签名证书、包名、版本、Android 11 最低版本、非调试标记、ZIP 对齐及包内源码提交核对通过；普通原子推送后，GitHub 两项草稿附件回下载一致才公开为预发布，发布时间 16:11:40。

Gitee 发行版 ID `1308052` 同步成功，预发布状态、名称与正文一致（附件链接替换为 Gitee）。APK 3,950,703 字节，SHA-256 为 `131f04b74e8cd34b543d9ae396e072ee7fe2e34795abff5177e78b60a071b677`；校验文件 84 字节，自身 SHA-256 为 `c587d1032dcddd932276612c9e8d6f31970f07f3ece55d5d7d13e079530cd623`。工作流和本机 16:18 匿名回下载都确认两项附件与 GitHub 逐字节一致，本机再次验证原证书、版本、源码提交和对齐。完成状态 `.github/release-state.json` 为 `complete=true`，自动验收记录的 commit/摘要与实测一致。

工作流随后自动提交 README 下载区、PROGRESS、完成状态和 `docs/latest-release-verification.json`，生成 main 提交 `33ece64620d83764facc492e8bb893286d75909e` 并同步 Gitee。再以默认参数真实触发 [43575136535](https://github.com/grovecai/goule/actions/runs/43575136535)，16:17:57 返回 `mode=skip previous_tag=v0.3.14`、原因无应用/构建代码变化，16:18:04 核对 main 镜像后成功；没有新构建、版本或 v0.3.15 标签，main 仍为上述提交，证明发布机器人记录不会导致循环发版。

本机复核资料位于 `/Users/caiyuanrui/.cache/goule-releases/actions-v0.3.14-20261114/`，含两端回下载、`publication-verification.json`、`publish-success.log` 和 `no-change-success.log`。本轮仅修改发布流程及版本元数据，未安装手机；自动检查不替代新的真机功能回归，v0.3.13 已记录的设备边界继续保留。签名配置、真实发布和无变化跳过验收均已完成，定时规则等待下一次自然触发。

## v0.3.13 Gitee 补发（2026-11-14）

用户明确要求配置令牌后补发 GitHub v0.3.13。已确认 `GITEE_TOKEN` Secret 存在（不读取/输出令牌），重新下载两项 GitHub 附件：APK 3,950,703 字节，SHA-256 为 `9fbee80b8ff788f943216728d1232ce207a9db2510e21f023ae5bfbeab9383e9`，校验文件 84 字节，内容对应同一 APK。

13:27（北京时间）触发首次 [手动同步](https://github.com/grovecai/goule/actions/runs/43561652497)，同步步骤长时间未返回，已请求取消。增加推送前的仓库可访问/公开状态预检、阶段日志和外部命令 180 秒超时；API/下载维持 120 秒超时。新增四项回归覆盖仓库不可见、私有、只读及公开可写，`./scripts/check` 完整通过，15 项 Python 回归零失败，Android 检查任务复用缓存。真实发布和回下载结果仍待重试验证，尚未更新 README 为国内 APK 下载。

原附件和摘要材料位于本机 `/Users/caiyuanrui/.cache/goule-releases/gitee-v0.3.13-20261114-g0dulvrg/`。本轮不重新构建/签名 APK，不另装手机。

13:33 的 [第二次同步](https://github.com/grovecai/goule/actions/runs/43562056701) 通过令牌认证，因仓库不是公开状态而按预检停止。用户确认该仓库私有，并明确授权改为公开后继续。新增默认关闭的手动 `make_public` 输入及本机 `--make-public` 参数，只有显式启用才修改可见性，修改后回读确认公开；自动 Release 事件不改变可见性。相关回归覆盖未授权不修改、授权后修改并回读、仍未公开时停止和已公开时不修改。

13:36 的 [公开设置尝试](https://github.com/grovecai/goule/actions/runs/43562314632) 返回 HTTP 422。登录仓库管理页选择开源时，页面明确提示「空仓库不支持设置为公开仓库」，尚未修改可见性。调整授权模式为先推送现有 GitHub 代码及标签，再公开并创建发行版，增加空仓库处理顺序回归；后续结果待记录。

13:39 的 [首次代码传输](https://github.com/grovecai/goule/actions/runs/43562542736) 实时日志显示对象持续写入，约 15 KiB/s，49% 时已传 1.69 MiB。本地完整 main pack 为 10,035,919 字节，原 180 秒上限不足；Gitee 推送单独延长至 1,200 秒并尝试 HTTP/1.1/32 MiB 缓冲，附件上传上限延长至 600 秒，工作流总上限 40 分钟。上述属于跨区域实际传输边界，尚不能写为代码或发行版已同步成功。

该轮在写入 54% / 2.50 MiB 后按 180 秒上限失败，没有完成 Git ref 更新；已保留超时日志，按延长上限和兼容传输参数重试。

用户随后明确要求本机只推送 GitHub。main push 自动触发仅代码同步，对应 Release 发布/编辑时继续同步标签和两项附件；两类任务使用同一串行队列。新增两项回归覆盖无需 Release/附件调用的代码同步，以及仅推 main、不带标签或强制参数，21 项 Python 回归通过。工程约定及发布文档已取消本机双推要求，后续验收实际 push 自动触发及两端 main 一致。

13:52 首次完整历史传输完成：[工作流](https://github.com/grovecai/goule/actions/runs/43562835997) 核对 Gitee main=`d5a77e82cfb18c5cc9b762a1e85352dd399cd103` 与 v0.3.13 注解标签对象一致，未强制推送。随后公开 API 仍返回 422；通过已登录管理页选择开源并保存，Gitee 返回「您的帐号安全评级较低，发布公开内容前请在『个人设置』完成2FA设置, 或绑定可靠第三方帐号」，仓库仍私有。已要求用户亲自完成账号安全设置，未代设认证凭据，未创建发行版或上传 APK。

仅代码同步允许私有仓库，公开发布仍严格检查公开状态。共享队列采用 GitHub 官方 `queue: max`，避免新 push 替换待运行的发行版任务；actionlint v1.7.12 暂不识别该新字段，该字段以 GitHub 服务器实际校验为准，其余字段继续通过 actionlint 验证。

账号安全设置由用户本人完成，随后 13:57 通过仓库管理页保存开源状态，页面显示公开仓库，匿名 API 确认 `public=true private=false default_branch=main`。13:56 的 [main push 自动同步](https://github.com/grovecai/goule/actions/runs/43563856712) 已成功，将 `d5a77e8` 增量更新为 `eb5554b`；本机只推了 GitHub，Gitee main 与 GitHub 完全一致，共享队列配置已被 GitHub 实际接受并执行。

14:03:49，[v0.3.13 补发工作流](https://github.com/grovecai/goule/actions/runs/43563990410) 通过，Gitee Release ID 为 `1307564`，公开可访问的预发布版；名称、版本及正文与 GitHub 一致，正文中 APK/校验文件链接改为实际 Gitee 地址。APK 附件 `3694297` 为 3,950,703 字节，SHA-256 附件 `3694300` 为 84 字节，工作流公开回下载逐字节核对通过。14:05 本机再次不带登录态回下载两项附件，字节、大小和摘要与 GitHub 原附件完全一致：APK SHA-256 为 `9fbee80b8ff788f943216728d1232ce207a9db2510e21f023ae5bfbeab9383e9`，校验文件自身 SHA-256 为 `bf5a111bc6335d8787968f4651fb6b8549d265f8a13d2d46bbe6c3bb4d0db763`。

两端注解标签对象均为 `114e032c319ca4f3017505d4d5ec9fd17f362cc2`，解引用为原发布提交 `9549f7066eb7faa8c948e8ccf5e50cfc46316f07`；验收时 main 均为 `eb5554b4907cf731573ec30b9700d94b978cb2fe`。没有重建或重签 APK，原发布证书与版本身份由原包字节一致性保留。README 国内下载入口已补齐并保留 GitHub 备用入口；21 项 Python 回归及完整 `./scripts/check` 通过，Android 任务复用缓存。本轮不另装手机。

结果保存在上述本机材料目录的 `publication-verification.json`、`gitee-downloads/`、`github-only-auto-sync.log` 和 `release-sync-success.log`。后续提交仅推送 GitHub，依赖 main push 自动同步并再核对两端最新提交；未来发行版由 GitHub Release published/edited 触发，无需本机双推。

## Gitee 同步配置（2026-11-14）

已新增本地 `gitee` 远端 `https://gitee.com/grovecai/goule.git`，GitHub origin 保持原值。发布工作流监听 GitHub Release published/edited，并支持指定标签手动补发；复制已发布的原签名 APK/SHA-256，不重新构建。推送 main/本次标签后核对远端对象，上传后通过公开下载链接逐字节核对附件，更新正文中的附件链接并回读核对元数据。

自动检查：`./scripts/check` 完整通过。新增 11 项 Python 离线回归覆盖原附件校验、APK 被改动、校验文件名称错误、首次同步、重复同步与说明更新、中断续传、同名异内容、上传损坏、重复附件名、缺少令牌和拒绝草稿；已接入统一检查。Android 单元测试、Lint、Debug/测试 APK 构建任务复用缓存，未修改应用代码。工作流通过 actionlint v1.7.7。首次检查因 shell 未配置 Java 停止，指定本机缓存 Zulu JDK 17.0.14 和 Android SDK 后完整通过。

外部验证尚未通过：公开 `GET /api/v5/repos/grovecai/goule` 返回 HTTP 404 / `Not Found Project`，可能为仓库未创建或当前不可见，不能确认具体原因。HTTPS `ls-remote` 和 `git push gitee main` 缺少可用凭据而失败；SSH 返回 `Permission denied (publickey)`。GitHub Actions Secrets 未配置 `GITEE_TOKEN`，本轮没有实际创建 Gitee 发行版、上传 APK 或完成附件回下载；离线模拟通过不等于真实 API 验收通过。README 不提供尚未验证的国内 APK 链接。

后续确认 Gitee 仓库已创建且可公开访问，配置本机 Git 权限和 GitHub `GITEE_TOKEN` Secret 后，手动运行 v0.3.13 同步。核对两端 main/注解标签、预发布状态及正文，APK 和 SHA-256 回下载与 GitHub 完全一致后，记录实际结果并更新 README 下载地址。本轮未安装手机或发布新版本。

## v0.3.13 发布包保留数据覆盖安装（2026-11-14）

12:34（北京时间）按用户要求，将 GitHub v0.3.13/code 16 发布附件保留数据覆盖安装至 OPPO PLJ110（Android 16、ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`）。安装前为 0.3.12/code 15 音量修复构建；重新下载 APK/SHA-256，摘要与发布记录一致，apksigner、与手机原包证书一致性、ZIP 对齐及包名/版本核对通过。`adb install --no-incremental -r` 返回 Success，未卸载、清数据或修改戒律。

安装后回拉 APK SHA-256 与发布附件一致：`9fbee80b8ff788f943216728d1232ce207a9db2510e21f023ae5bfbeab9383e9`。appId `10445`、首次安装时间 `2026-11-14 12:00:57` 保持；前后服务 dump 中三条记录逐项完全一致：抖音已用 `60000 ms`、剩余 `0`、周期起点 `1794629032023`、禁用截止 `1794635992023`（13:59）；红果与小红书已用 `0`、剩余 `300000 ms`，周期起点与禁用截止仍为空。

`am start -W` 返回 Status: ok、LaunchState: WARM，戒律页截图确认现有条目与时长显示，抖音仍禁用至 13:59、小红书仍可刷 5 分钟。服务 `connected=true consent=true`，Enabled/Bound 正常、Binding/Crashed 为空。未改变系统授权/白名单，手机停留戒律页。本轮只验收发布包安装、运行数据/安装身份保留及启动，不代表新建表单、音量键、锁屏或完整周期已在该发布包重新验收。

材料位于本机 `/Users/caiyuanrui/.cache/goule-releases/v0.3.13-phone-install-20261114/`，含下载/原包/回拉 APK、签名、前后包与服务/授权记录、戒律页截图及 `installation-verification.json`。

## v0.3.13 发布包验证（2026-11-14）

版本 0.3.13/versionCode 16，包含音量面板导致禁用页消失的修复及新建戒律默认 5/115 分钟。欢迎页与新建表单复用新默认；已有戒律编辑/替换仍带入原配置，最早单目标原型迁移保留历史 20/30 分钟。`./scripts/check` 通过，Debug/Release 各 45 项单元测试零失败/错误/跳过，Lint、Debug 和回归测试 APK 构建通过；Release 从干净提交 `9549f7066eb7faa8c948e8ccf5e50cfc46316f07` 重新构建通过，包内 `version-control-info` 与提交/标签一致。包名 `com.goule.prototype`、最低 Android 11、target SDK 35、版本及非调试标记通过；apksigner、原发布证书一致性和 ZIP 对齐通过，证书与本轮回下载 v0.3.12 一致。

12:31:52（北京时间）GitHub [v0.3.13](https://github.com/grovecai/goule/releases/tag/v0.3.13) 发布完成，非草稿预发布，注解标签指向上述提交。APK 与 SHA-256 两项附件回下载字节、大小和摘要与本地完全一致，GitHub 附件摘要匹配；校验文件仅含正确摘要与 APK 名。最终 APK SHA-256 为 `9fbee80b8ff788f943216728d1232ce207a9db2510e21f023ae5bfbeab9383e9`。远程 README、标签版本说明及 Release 正文一致；材料位于本机 `/Users/caiyuanrui/.cache/goule-releases/v0.3.13/`，含检查/构建/签名记录、包内提交信息、前版及本版回下载文件、`build-summary.json` 和 `publication-verification.json`。

本次版本包不另装手机，新默认值的真机页面未复测。音量修复同功能 0.3.12/code 15 构建已在 OPPO PLJ110（Android 16、ColorOS V16.1.0）通过音量增减/收起/连续按键、桌面/设置退出及重进拦截，原用量、周期和截止保留；Android 15 模拟器六组回归及真实物理锁屏/来电等未覆盖边界见 [音量修复记录](#2026-11-14-音量面板导致禁用页消失修复)。本轮未新写仅重复默认常量的测试，不将既有音量回归当作新默认界面的实测。

## 2026-11-14 音量面板导致禁用页消失修复

设备为 OPPO PLJ110，Android 16、ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`。用户反馈抖音禁用页出现后，按音量键会消失。初始生产服务记录 `connected=true consent=true`，抖音已用 `60000 ms`、剩余 `0`，周期起点 `1794629032023`、禁用截止 `1794635992023`（13:59）均有效，但 `foreground=null overlay=null`，排除额度到期及服务失联。

生产服务补充仅含 SystemUI 类名/全屏标记的本机诊断后复现：音量键产生 `TYPE_WINDOW_STATE_CHANGED`，包为 `com.android.systemui`，类为 `com.oplus.systemui.volume.view.OplusVolumeDialogView$CustomDialog`，`fullScreen=true`；旧处理将其归为非目标并撤下遮罩，面板收起后没有目标恢复事件。该面板使用透明全屏窗口，不能仅用非全屏标记识别。现按已确认的厂商类名及普通非全屏 SystemUI Dialog 保留底层目标，仍核对锁屏、权限和到期；不读取事件文字/窗口内容，不新增权限或拦截音量键，未知窗口仍按原逻辑处理。

`GOULE_UI_DEVICE=emulator-5554 ./scripts/check` 通过，Debug/Release 各 45 项单元测试零失败/错误/跳过，Lint、Debug/测试 APK 及另行 Release 构建通过。Android 15/API 35 模拟器独立包六组 PASS，新增生产服务事件重放覆盖 OPPO 精确类名/全屏组合、普通系统弹窗、可刷期间持续计时、禁用目标/截止保留、通知栏/设置退出及屏幕广播后迟到弹窗不能恢复旧目标。纯 Kotlin 检查覆盖未知类、其他包、全屏普通 Dialog 不误保留。首轮模拟器在冷启动解锁前发起 instrumentation，因应用非 Direct Boot 应用未启动；待 `RUNNING_UNLOCKED` 后重跑通过，未为此改变应用启动能力。设备回归由 `scripts/check-ui` 接入 `scripts/check`。

12:22 原发布签名覆盖安装最终修复构建，仍为 0.3.12/code 15，未发布 GitHub Release。apksigner、原包证书一致性、ZIP 对齐、版本及安装后回拉摘要通过，最终 APK SHA-256 为 `46925193d446c7287c7f7c0771e9a7ef145e3c991bb071bb15eaf24e398927c0`。appId `10445` 与首次安装时间 `2026-11-14 12:00:57` 保留，本轮未卸载或清除正式数据，未修改戒律/白名单或系统授权。

真机通过 ADB 注入音量增减键：禁用页、音量面板出现期间及面板收起后均显示同一抖音遮罩；连续减/减/增/增仍保留。桌面退出、进入系统设置时 `foreground=null overlay=null`，重进抖音恢复遮罩。用量 `60000 ms`、剩余 `0`、周期起点和原禁用截止逐项一致，Enabled/Bound 正常、Binding/Crashed 为空。截图核对禁用页显示 13:59，手机最终停留该页。

本轮没有人为改短正式戒律或等待完整周期；真实物理锁屏往返、来电、通知栏实际展开/收起、完整自然到期及其他品牌仍待复测，其中屏幕广播、通知栏分类和期限保留由隔离包事件重放覆盖。临时诊断测试包已卸载，模拟器测试包由脚本清理；材料位于本机 `/Users/caiyuanrui/.cache/goule-android/volume-overlay-20261114/`，含修复前后服务/窗口记录、签名/安装包、截图、检查日志及 `final-verification.json`。

## v0.3.12 发布包手机安装（2026-11-14）

本轮 `./scripts/check` 通过，仓库检查、单元测试、Lint、Debug 和测试 APK 构建均成功（既有 Gradle 任务复用缓存）；未执行设备自动回归。

12:00:57（北京时间）按用户要求，将 GitHub 最新发布版 v0.3.12/code 15 安装至 OPPO PLJ110（Android 16、ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`）。安装前当前用户未检测到 `com.goule.prototype`，本轮未卸载或清数据；`adb install --no-incremental -r` 返回 Success，安装后的 appId 为 `10445`，首次安装与最后更新时间均为上述时间。

重新下载 APK 与 SHA-256 文件，摘要与 GitHub 附件及发布记录一致：`08ef2097ac11e9d2ef1b302d26bd81f04f3f5b2f4fc9dece3e724014552a6711`。apksigner、原发布证书一致性、ZIP 对齐及包名/版本核对通过；安装后回拉 APK 与下载包摘要完全相同。`am start -W` 返回 Status: ok、LaunchState: WARM，截图确认欢迎页及「立下第一条戒律」入口正常。

无障碍 Enabled/Bound/Binding/Crashed 均为空，未代用户同意说明、开启权限或修改手机管家白名单，手机停留欢迎页。本轮仅验证发布包安装与欢迎启动，不代表完整三步、真实授权往返、计时/遮罩或白名单效果已验收。材料位于本机 `/Users/caiyuanrui/.cache/goule-releases/v0.3.12-phone-install-20261114/`，含下载与回拉 APK、包状态/签名、服务记录、欢迎页截图及 `installation-verification.json`。

## v0.3.12 发布包验证（2026-11-13）

版本 0.3.12/versionCode 15，发布三步顺序欢迎引导与未完成步骤恢复，沿用原签名及预发布方式。`./scripts/check` 通过，Debug/Release 各 41 项单元测试零失败/错误/跳过，Lint、Debug 和回归测试 APK 构建通过；Release 构建通过。最终 APK 从干净发布提交 `708f12477db021ad1e3c7ed1ae84a5795c8398e5` 构建，包内提交记录与标签一致；包名 `com.goule.prototype`、最低 Android 11、target SDK 35、版本和非调试标记核对通过，apksigner 与 ZIP 对齐通过，证书与本轮从 GitHub 回下载的 v0.3.11 一致。

23:40:56（北京时间）GitHub [v0.3.12](https://github.com/grovecai/goule/releases/tag/v0.3.12) 已发布，非草稿预发布，注解标签指向上述提交。APK 和 SHA-256 文件两项附件回下载后与本地字节、大小和摘要完全一致，GitHub 附件摘要也匹配，校验文件仅含摘要与正确 APK 名。最终 APK SHA-256 为 `08ef2097ac11e9d2ef1b302d26bd81f04f3f5b2f4fc9dece3e724014552a6711`。远程 README、标签版本说明与 Release 正文核对一致；材料位于本机 `/Users/caiyuanrui/.cache/goule-releases/v0.3.12/`，包含构建/签名记录、包内提交信息、回下载附件及 `publication-verification.json`。

本次版本包不另装手机。同功能 `c4bd8ce` 已通过 Android 15/API 35 模拟器独立包五组回归，新增导航回归包含三步顺序、返回、仅同意说明后重开恢复、最后完成、断连门禁及非首次入口，测试连接状态为模拟值，不代替系统真实授权。同功能 0.3.11/code 14 已在 OPPO PLJ110（Android 16、ColorOS V16.1.0）按用户要求清除旧数据后全新安装，欢迎冷启动、安装身份和签名/摘要核对通过；未开始完整三步或代用户授权。详见 [引导回归](#2026-11-13-首次三步顺序引导) 和 [全新安装](#三步引导清除旧数据后全新安装2026-11-13)。真实授权往返、进程被杀/配置重建、白名单效果与既有遮罩/系统入口等待验范围继续保留。

## 2026-11-13 首次三步顺序引导

首次流程改为欢迎 → 无障碍服务（1/3）→ 后台运行（2/3）→ 风险行为拦截白名单（3/3）→ 添加心魔。首次隐藏直达白名单的快捷入口，后台只进入第 3 步，最后一步继续才记录完成；没有该厂商服务仍可继续，无障碍未连接则不可完成。未完成步骤单独持久保存，避免仅同意说明后重开跳到戒律首页；旧用户未开始新流程时继续沿用原完成状态，规则和计时数据不变。

`./scripts/check` 通过，Debug/Release 各 41 项单元测试零失败/错误/跳过，Lint、Debug 与 Android 回归 APK 构建通过。首轮新增测试捕获可变 Activity 的空值类型导致编译失败，已修正为各次启动的固定引用，最终检查通过。新增 `OnboardingFlowChecks` 接入既有 instrumentation 和 `scripts/check-ui`，运行 `GOULE_UI_DEVICE=emulator-5554 ./scripts/check`，Android SDK x86_64 模拟器（Android 15/API 35）五组 PASS：原列表/详情刷新、横屏按钮、边缘安全区、前台事件计时，以及三步顺序引导。

新增回归实际点击欢迎、下一步与返回，断言不能从第 1 步跨到白名单、不能由后台直接添加；仅设置同意记录并关闭 Activity 后，以无 savedInstanceState 的新 Activity 恢复第 1 步。走到第 3 步返回第 2 步，再关闭/重开仍恢复第 3 步；断连时继续不可用，模拟连接后继续进入添加页并清除待完成步骤。完成后重开不强制引导，设置独立白名单页不带步骤标记且可返回设置。测试只在独立包 `com.goule.uiverify` 中操作，连接状态由测试夹具设置，仅验证导航与门禁，不代替真实授权往返或系统绑定验证。

最终日志位于本机 `/Users/caiyuanrui/.cache/goule-android/onboarding-three-step-final-check.log`；测试包由脚本清理，本次启动的模拟器已关闭。本轮无实体手机连接，未覆盖安装正式包或发布版本。待真机验证：OPPO 首次安装与真实授权/手机管家往返、配置重建和真实进程被杀后的恢复、短屏/大字号以及白名单防撤权效果；未请求系统无障碍授权或变更用户白名单。

### 三步引导清除旧数据后全新安装（2026-11-13）

用户明确要求不保留现有戒律和数据。初始无设备，用户连接后确认 OPPO PLJ110，Android 16、ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`。先完成源码 `c4bd8ce` 的 Release 构建、原发布签名一致性、ZIP 对齐和包名/版本/非调试标记检查，再执行不带 `-k` 的 `adb uninstall com.goule.prototype`，返回 Success，随后全新安装，不恢复旧应用数据。ColorOS 显示未验证开发者提醒，按本次安装授权确认继续安装后，ADB 返回 Success。

23:32:56（北京时间）安装完成，版本仍为 0.3.11/code 14 的未发布三步引导构建。新的 appId `10444`，首次安装与最后更新时间均为 `2026-11-13 23:32:56`；安装 APK 回拉后 SHA-256 与本地签名包相同：`d675cc15c42f8e1925870f48751a09d3049b96484d4427eb7797d653c49f4914`。包内源码提交一致，签名与 v0.3.11 发布证书相同。

`am start -W` 为 COLD、Status: ok，截图确认欢迎页及「立下第一条戒律」入口。本次卸载未保留旧戒律、用量、同意记录或引导进度；系统 accessibility dump 不再包含本应用的服务授权。未开始新的欢迎引导、未同意权限说明或代替用户开启无障碍，未修改手机管家白名单，手机留在欢迎页。只记全新安装、数据清除和欢迎冷启动通过，不能视为 OPPO 上完整三步及真实授权往返已经验收。

材料位于本机 `/Users/caiyuanrui/.cache/goule-releases/three-step-clean-install-c4bd8ce/`，含卸载结果、版本/签名、前后包元数据、新 APK、欢迎页截图及 `installation-verification.json`；构建日志位于 `/Users/caiyuanrui/.cache/goule-android/onboarding-three-step-release.log`。本轮未发布新版本。

## v0.3.11 发布包验证（2026-11-13）

版本 0.3.11/versionCode 14，发布手机管家风险行为拦截独立设置项及来源说明，沿用原签名及预发布方式。`./scripts/check` 通过，Debug/Release 各 41 项单元测试零失败/错误/跳过，Lint、Debug 与回归测试 APK 构建通过；Release 构建通过。最终 APK 从干净发布提交 `bb46001d0552196e9db72d0e4b62654d23780115` 构建，包内提交记录与标签一致；包名 `com.goule.prototype`、最低 Android 11、target SDK 35、版本和非调试标记核对通过，apksigner 和 ZIP 对齐通过，证书与本轮从 GitHub 回下载的 v0.3.10 一致。

22:33:48（北京时间）GitHub [v0.3.11](https://github.com/grovecai/goule/releases/tag/v0.3.11) 已发布，非草稿预发布，注解标签指向上述提交。APK 和 SHA-256 文件两项附件回下载字节、大小及摘要与本地完全匹配，GitHub 附件摘要也一致；校验文件仅含摘要与正确 APK 名。最终 APK SHA-256 为 `11a3a7c92cd6cd516f3d197083a8a67dfded8953ff5fa4989f0f688c39525b9b`。远程 README、标签版本说明与 Release 正文核对一致；材料位于本机 `/Users/caiyuanrui/.cache/goule-releases/v0.3.11/`，包含构建/签名记录、包内提交信息、回下载附件及 `publication-verification.json`。

本次版本包不另装手机。同功能 `a0183af` / 0.3.10/code 13 已原签名覆盖安装 OPPO PLJ110（Android 16、ColorOS V16.1.0），保留三条戒律和参数、当前额度及原周期相位，服务绑定正常，设置独立条目和完整手机管家指引已显示，详见 [安装记录](#独立手机管家指引覆盖安装2026-11-13)。各来源返回/重建、首次、短屏/大字号的完整回归和白名单防撤权效果仍待验证，既有真实遮罩与系统入口的未覆盖范围继续保留。

## 2026-11-13 手机管家风险拦截独立指引

按用户纠正调整来源描述和信息位置：OPPO/ColorOS 的「风险行为拦截」是系统自带「手机管家」中的服务，其他品牌仅说明部分厂商提供，不再描述为独立程序或 Android 通用功能。设置页新增并列条目与独立指引页，无障碍页常显入口，「遇到问题」与恢复页不再展开该文案；后台第 4 组改为独立指引入口。README 同步并说明 v0.3.10 发布包仍为旧布局，已发布的历史版本说明保持不变。

源码核对：设置、无障碍和后台页均记录来源并进入独立页，顶部/系统/页底返回均使用该来源，Activity 保存与恢复该字段；首次步骤标记仅用于无障碍/后台两页。指引页仍使用可滚动容器，未增加状态检测或设置门禁，未修改规则、存储或服务行为。`./scripts/check` 通过，Debug/Release 各 41 项单元测试零失败/错误/跳过，Lint 和 Debug/回归测试 APK 构建通过，日志位于本机 `/Users/caiyuanrui/.cache/goule-android/risk-guide-independent-check.log`。本轮为指引和导航调整，未新增复述文案/实现的测试。

本轮未安装或操作手机，OPPO PLJ110（此前确认 Android 16、ColorOS V16.1.0）的现有 v0.3.10 不包含此布局调整。待真机核对：设置并列入口、无障碍未展开帮助时的入口、后台跳转、三种来源的返回/页面重建、首次两步往返，以及短屏/大字号可读性。手机管家内的准确菜单层级与白名单防撤权效果尚未实测，文案只按用户说明指向该服务，不编造具体层级。

### 独立手机管家指引覆盖安装（2026-11-13）

22:28:18（北京时间）按用户要求，将源码 `a0183af` Release 构建以原发布签名覆盖安装至 OPPO PLJ110，Android 16、ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`。版本仍为 0.3.10/code 13，包含独立指引调整的未发布功能构建，不是 GitHub 原发布附件。

`assembleRelease` 通过，包内提交记录与源码一致；apksigner、与手机原包证书一致性、ZIP 对齐和包名/版本/非调试标记核对通过。`adb install --no-incremental -r` 返回 Success，未卸载或清数据。appId `10381`、首次安装时间 `2026-11-11 20:28:04` 保持；回拉 APK 与本地签名包 SHA-256 相同：`8a77e5e310391dfb88b5280dd8735cc74c077926136b62f4a74614b8358fbe87`。

三条戒律与各自 10/120 参数在列表截图中完整显示。安装前服务保留小红书已用 30139 ms、红果已用 600000 ms 的旧周期对象，但当前 `remainingMs` 已均为 600000，红果旧截止 22:24 已过。安装后服务按既有刷新逻辑将两者的周期起点各推进恰好 7800000 ms，分别为 `1794579903519` 和 `1794579884009`，已用量变为 0，过期截止消失；抖音的起点 `1794579170942` 和已用 0 保持。三者当前额度安装前后均为 600000 ms，不能将旧周期原始字段不等写成数据完全相同，也不属于按安装时间重开周期。初次逐字段相等断言因此失败，后续按原周期边界和有效剩余量核对通过。

启动 Status: ok，手机截图确认设置中的独立白名单条目、完整指引页及手机管家/厂商差异文案，另采集到指引返回设置的页面，最后停留戒律列表。Enabled/Bound 正常，Binding/Crashed 为空。未改戒律、系统权限或白名单，未进入目标消耗额度；全部来源往返/页面重建、首次流程、短屏/大字号及白名单实际防撤权效果仍待专门复测。

材料位于本机 `/Users/caiyuanrui/.cache/goule-releases/independent-guide-a0183af/`，含前后 APK、签名/包状态/服务记录、页面截图和 `installation-verification.json`；Release 构建日志位于 `/Users/caiyuanrui/.cache/goule-android/risk-guide-independent-release.log`。本轮未发布新版本。

## v0.3.10 发布包验证（2026-11-13）

版本 0.3.10/versionCode 13，发布风险行为拦截白名单和手动恢复指引，沿用原证书及预发布方式。`./scripts/check` 通过，Debug/Release 各 41 项单元测试零失败/错误/跳过，Lint、Debug 与回归测试 APK 编译通过；Release 构建通过。包名 `com.goule.prototype`、最低 Android 11、target SDK 35 和无 debuggable 标记核对通过，apksigner 与 ZIP 对齐检查通过，证书与本次从 GitHub 回下载的 v0.3.9 一致。

本次版本包不另装手机。同功能 `b4bac9e` / 0.3.9/code 12 已在 OPPO PLJ110（Android 16、ColorOS V16.1.0）原签名覆盖安装，三条戒律运行数据及服务绑定核对、新增后台白名单指引滚动显示通过，详见 [安装记录](#风险行为拦截指引覆盖安装2026-11-13)。白名单防撤权效果、首次故障帮助/恢复页、短屏/大字号及既有真实遮罩/系统入口等未覆盖范围继续保留。

最终 APK 从干净发布提交 `72b9b5b162423329e7f1f6073bbd3aa6274b40db` 构建，包内版本控制元数据确认该提交；原签名、元数据和 ZIP 对齐再次核对通过。准备构建与干净提交构建只在 `META-INF/version-control-info.textproto` 的提交记录不同，发布使用后者。最终 APK SHA-256 为 `882e2d326424fdfbf4173a0eea3e86c4819669f2de3c894235dcaa0dc3106a1e`，校验文件仅含该摘要与 APK 名。

21:52:36（北京时间）GitHub [v0.3.10](https://github.com/grovecai/goule/releases/tag/v0.3.10) 已发布，非草稿预发布，注解标签指向上述干净提交。APK 和 SHA-256 文件两项附件回下载后与本地字节、大小及摘要完全一致，GitHub 附件摘要也匹配；远程 README、标签版本说明和 Release 正文均核对一致。材料位于本机 `/Users/caiyuanrui/.cache/goule-releases/v0.3.10/`，含构建/签名日志、上一版与本次回下载文件、包内提交信息和 `publication-verification.json`。

### v0.3.10 发布包覆盖安装（2026-11-13）

22:06:31（北京时间）按用户要求，从 GitHub v0.3.10 下载 APK 和 SHA-256 文件，核对后原签名覆盖安装 OPPO PLJ110（Android 16、ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`）。由原 0.3.9/code 12 功能构建升至发布版 0.3.10/code 13；apksigner、与手机原包证书一致性、ZIP 对齐及包名/版本/非调试标记核对通过。`adb install --no-incremental -r` 返回 Success，未卸载或清数据。

下载包、校验文件和安装后回拉 APK 摘要一致：`882e2d326424fdfbf4173a0eea3e86c4819669f2de3c894235dcaa0dc3106a1e`，与发布附件相同。appId `10381` 和首次安装时间 `2026-11-11 20:28:04` 保持。前后生产服务 dump 的三条目标记录逐项完全一致：抖音、小红书已用 0 ms/剩余 600000 ms，红果已用 600000 ms/剩余 0，周期起点均保持，红果禁用截止仍为 `1794579884009`（22:24）。服务 `connected=true consent=true`，Enabled/Bound 正常，Binding/Crashed 为空。

手机安装前后均锁屏，未解锁、打开目标或修改任何戒律/权限/白名单。本轮验证覆盖发布附件安装、已记录运行数据保留及服务实际绑定，不代表解锁后的页面、真实遮罩或白名单防撤权效果已复测。材料位于本机 `/Users/caiyuanrui/.cache/goule-releases/v0.3.10-phone-install/`，含下载附件、前后 APK、包状态/服务记录及 `installation-verification.json`。

## 2026-11-13 风险行为拦截白名单指引

根据用户反馈补充指引：手机上的「风险行为拦截」程序或功能可能关闭无障碍服务，需要将「斩心魔」加入该功能的白名单。后台页第 4 组、首次无障碍页「遇到问题」及服务恢复页复用同一文案；恢复页直接显示。步骤包含查找程序/系统安全功能、加入白名单、服务关闭后手动开启或中断后关闭再开启、返回确认「已设置」，并提示入口名称因系统而异、没有可略过。README 同步，未增加后台状态检测或门禁。

`./scripts/check` 首次因当前 shell 未配置 Java Runtime 停止；指定本机缓存 JDK 17 与 Android SDK 后完整通过，Debug/Release 各 41 项单元测试零失败/错误/跳过，Lint、Debug 和测试 APK 构建通过。检查日志保存在本机 `/Users/caiyuanrui/.cache/goule-android/risk-interception-guide-check.log`。仅调整帮助文案与展示，未新增复述文案的测试。

本轮未安装手机或发布版本，未修改系统白名单或无障碍开关。此前 OPPO PLJ110（Android 16、ColorOS V16.1.0）的风险拦截撤权现场见 [覆盖安装记录](#顶部遮罩修复包覆盖安装2026-11-13)，不作为白名单效果已验证的证据。待在该设备核对新指引滚动与短屏/大字号显示、实际白名单入口和加入后的撤权是否复现；不承诺白名单可避免所有系统撤权。

### 风险行为拦截指引覆盖安装（2026-11-13）

21:43:37（北京时间）按用户要求，将源码 `b4bac9e` 的 Release 构建用原发布密钥覆盖安装至 OPPO PLJ110，Android 16、ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`。由手机原 0.3.8/code 11 升至 0.3.9/code 12；该功能构建包含发布版之后的白名单指引，本轮未发布 GitHub 版本。

`assembleRelease`、apksigner 验证、与手机旧 APK 证书 SHA-256 一致性、ZIP 对齐和包名/版本/非调试标记检查通过。`adb install --no-incremental -r` 返回 Success，未卸载或清数据。appId `10381`、首次安装时间 `2026-11-11 20:28:04` 保持；手机回拉 APK 与本地签名包 SHA-256 一致：`df44977ab31bfc6beea18c674ce76924d639fc3f70f4f025a3d6419a1a3fb83a`。

安装前后生产服务 dump 的三条目标记录逐项完全一致：抖音和小红书均已用 0 ms/剩余 600000 ms，红果已用 600000 ms/剩余 0，三者周期起点保持，红果禁用截止仍为 `1794579884009`（22:24）。启动 Status: ok，列表确认红果禁用至 22:24 及小红书可刷，10/120 参数显示；Enabled/Bound 正常，Binding/Crashed 为空。进入设置显示无障碍「已设置」，经后台指引向下滚动，新第 4 组标题、白名单三步、系统差异说明及底部「返回戒律」完整可见，随后返回戒律页。

材料保存在本机 `/Users/caiyuanrui/.cache/goule-releases/risk-guide-b4bac9e/`，含前后 APK、包状态/服务 dump、摘要核对结果及截图。未修改用户戒律、用量、白名单或无障碍授权，未打开目标消耗额度或触发遮罩；短屏/大字号、首次故障帮助/恢复页及加入白名单后的实际防撤权效果仍未复测。

## v0.3.9 发布包验证（2026-11-13）

版本 0.3.9/versionCode 12，包含最近任务返回可刷目标后的漏计修复、顶部遮罩背景延伸及本机服务诊断，继续预发布并沿用原证书。`./scripts/check` 通过：Debug/Release 各 41 项单元测试零失败/错误/跳过，Lint、Debug 与 instrumentation APK 编译通过；Release 从干净发布提交 `29bdc0390b852b45fad747a0dd3ef7c13c0c5801` 构建，包名 `com.goule.prototype`、最低 Android 11、target SDK 35、无 debuggable 标记核对通过。apksigner、ZIP 对齐及与回下载 v0.3.8 发布包的证书一致性检查通过。

同功能 `5d35c4a` / 0.3.8/code 11 已在 OPPO PLJ110（Android 16、ColorOS V16.1.0）原签名覆盖安装、独立包四项回归和两轮真实最近任务返回计时复测通过，详见下节。本次 0.3.9/code 12 版本包未另装手机，真实顶部遮罩、物理旋转及系统入口、自然用满与厂商风险提示等未覆盖范围继续保留，版本说明已披露撤权现场和手动恢复边界。

21:24:36（北京时间）[GitHub v0.3.9](https://github.com/grovecai/goule/releases/tag/v0.3.9) 发布完成，非草稿预发布，仓库为 Public。远端注解标签解析至上述提交；正文与 `docs/release-v0.3.9.md` 一致，远程 main 的 README 与本地下载入口一致，标签内版本说明内容一致。发布附件仅 `goule-v0.3.9.apk`（3,946,607 字节）及 `goule-v0.3.9.apk.sha256`（83 字节），回下载与本地逐字节一致，GitHub digest、大小及校验文件内容全部匹配。APK SHA-256：`179d8ce2d495c6525722aba562f94b79bac87baa4d2bee63d52e2253fabc8942`。

材料保存在本机 `/Users/caiyuanrui/.cache/goule-releases/v0.3.9/`，包含构建/检查日志、签名包/校验文件、上一版本回下载包、证书/对齐/元数据报告、Release API 信息及发布后回下载核验记录。本轮未操作手机，后续提交仅补记实际发布结果。

## 红果计时停滞诊断（2026-11-13）

用户反馈使用红果后剩余时间长时间不变，随后补充偶尔恢复、实际列表和最近任务预览都观察过。21:05（北京时间）OPPO PLJ110，Android 16、ColorOS V16.1.0，正式 0.3.8/code 11 服务 Enabled/Bound 正常，Binding/Crashed 为空；前台计时进程未冻结，RUN_ANY_IN_BACKGROUND 为 allow。起始列表红果剩余 8:46，另一观察点 8:40；21:07 约 24 秒真实红果前台后显示 8:16。25 次约 1 秒间隔的焦点采样前 24 秒均为红果 ShortSeriesActivity，随后经过桌面及戒律页；这一轮扣时符合实际停留，不代表间歇停滞已修复。

源码检查列表在 onResume 立即刷新并每秒原位更新，内存规则由 Activity/Service 共用，约 5 秒落盘不应造成相同长度的显示延迟。为继续定位，增加 Service dump：只保留配置目标的身份，输出服务/前台/采样状态、已记用量和最近 32 条分类观察；其他 App 只记 other/self/launcher 分类，不读事件文本或窗口内容，诊断只驻内存。`./scripts/check` 通过，产品识别及计时算法尚未修改。材料目录为本机 `/Users/caiyuanrui/.cache/goule-android/redfruit-timing-20261113/`，手机材料不提交仓库。

21:10 原签名覆盖安装诊断构建 `999051e` 后捕获明确复现：红果目标事件 uptime `115606356` 开启计时，约 602 ms 后桌面发送 PANE_DISAPPEARED（changes=32），服务将 `foreground` 从红果清为 null、取消 tick。此时系统焦点仍为红果 ShortSeriesActivity，eligible=true、服务 connected/consent=true，连续约 15 秒 dump 的 usedMs 固定为 144146，samples 固定为 1、rejected=0。根因为服务仅在 overlay 存在时调用 `recents.targetResumed`，可刷目标没有获得最近任务返回的尾随事件保护；不是本轮页面刷新延迟或采样大间隔丢弃。

修复将最近任务返回保护用于所有已配置目标，真正离开仍正常停止。新增独立包 `ForegroundTimingChecks` 在生产 FreezeService 重放最近任务出现→可刷目标→600 ms 后最近任务消失，断言累计继续超过 1 秒，再进入最近任务后累计停止；接入默认构建与 `scripts/check-ui`。测试只修改独立包数据，不请求真实无障碍授权、不创建遮罩。

自动验证：Debug/Release 各 41 项单元测试、Lint、Debug 与测试 APK 构建通过。首次附加设备检查在既有列表滚动位置断言失败，计时事件回归尚未执行；未修改相关产品代码，重新执行 `scripts/check-ui` 后，同设备四项 PASS（页面固定重置/原视图与滚动、横屏按钮、背景及安全区、可刷目标最近任务返回计时和离开停止）。测试包自动移除，日志分别为 `fix-check.log` 与 `ui-retry.log`。该结果验证本次生产 Service 接线，不能将它扩展为所有厂商临时窗口路径已覆盖。

21:17:23（北京时间）同设备原签名 Release 覆盖安装 `5d35c4a`，仍为 0.3.8/code 11。Release 构建、包名/版本/非调试标记、证书与安装前一致、apksigner/ZIP 对齐及回拉 APK 摘要通过。首次安装时间 `2026-11-11 20:28:04`、appId `10381` 保持，红果原已用 144146 ms 和三个 App 的周期起点、10/120 参数保留；未卸载、清数据、修改戒律或补算缺少可靠前台记录的历史漏计。安装 APK SHA-256：`2c4bd09b6cea881d6c98ce17f13e07e08f58c418ff2637eb8a1c2efee0da93b8`。

正式包真机复测：从戒律页打开最近任务，再恢复红果 ShortSeriesActivity，连续两轮前台观察均保持 `foreground=com.phoenix.read`、持续约 1 秒采样、rejected=0。每轮测量区间约 14.18 秒，已用量分别增加 14.053 和 14.071 秒，余下亚秒尾段在下一次采样或离开时结算。两轮最近任务内各约 2 秒已用量均不增加；再次返回目标恢复累计。第二轮明确收到目标恢复后约 219 ms 的 launcher changes=0/32 尾随事件，计时未再清空。最后返回戒律页已用 173184 ms、剩余 426816 ms，截图为「还可刷 7 分 7 秒」，与向上取整的服务读数一致；另外两条仍各剩余 10 分钟，三条参数保留。服务 Enabled/Bound 正常、Binding/Crashed 为空，本轮没有出现撤权通知。

手机停留戒律列表。安装和真实采样材料在上述目录的 `fix-5d35c4a/`，含前后 APK/包状态、签名/对齐、各阶段 service dump、`real-timing-summary.json` 和 `after-scroll.png`。未发布新版；更多系统临时窗口、锁屏/重连与自然用满后的顶部遮罩和 ColorOS 风险提示仍待继续验证。

## 顶部遮罩修复包覆盖安装（2026-11-13）

20:08:21（北京时间）按用户要求，将源码 `10f2d53`（功能修复 `6c9a6bc`）的 Release 构建沿用原发布签名覆盖安装至 OPPO PLJ110，Android 16、ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`。版本 0.3.8/versionCode 11，为未发布修复构建，不替换 GitHub v0.3.8 资产。

`assembleRelease`、apksigner、与安装前手机 APK 的证书一致性、ZIP 对齐、包名/版本及无 debuggable 标记核对通过。`adb install --no-incremental -r` 返回 Success，未卸载或清除正式数据；首次安装时间 `2026-11-11 20:28:04`、appId `10381` 保持。回拉手机 APK 与本地签名包的 SHA-256 一致：`1c6cbf919eef1bcf10853732f51af9d760fa595810d9a12b9c2cf66a3ed9535d`。

启动 `am start -W` 返回 Status: ok。前后列表截图确认红果免费短剧和小红书戒律均保留 10/120 参数，红果仍禁用至 20:14/还可刷 0 秒，小红书仍还可刷 10 分钟；第三条位于首屏外，本轮不记为安装前后逐项截图核对通过。安装后 FreezeService 曾正常 Bound，Binding/Crashed 为空。

实际遮罩尝试未通过：20:09 打开仍在禁用的红果时，ColorOS 显示「已拦截应用异常操控设备行为」通知，称斩心魔存在后台高风险操作并已关闭相关权限。20:09:08 系统记录 enabled services 变为空，随后 Enabled/Bound/Binding/Crashed 均为空，未留存有效遮罩。已返回正式戒律页并提示用户按系统流程重新开启；未通过 ADB 写授权，不能宣称顶部真实窗口或系统入口验收通过。当前证据不能确定是新增窗口参数、覆盖升级后的厂商策略或其他条件触发，不能将通知直接归因于某一标记。

20:19（北京时间）用户报告已重新开启，同设备确认 Enabled services 含 FreezeService，Bound services 显示斩心魔，Binding/Crashed 为空。返回正式戒律页截图确认暂停提示消失，小红书、抖音、红果免费短剧三条戒律及各自 10/120 参数仍在，均为可刷/还可刷 10 分钟；红果原 20:14 截止已自然结束。当前无禁用目标，未修改正式戒律或消耗额度制造遮罩，故只将手动恢复和数据核对记为通过，顶部真实窗口及厂商风险提示是否复现仍待下次自然禁用验证。手机停留戒律页，恢复截图为材料目录中的 `recovered-rules.png`。

安装后再次运行默认 `./scripts/check` 通过，含仓库检查、单元测试、Lint、Debug/测试 APK 构建；本轮未再装独立包或修改产品代码。本机材料目录 `/Users/caiyuanrui/.cache/goule-releases/fullscreen-6c9a6bc/` 包含签名包、前后 APK、证书/对齐/元数据、包与服务状态、列表截图、系统拦截现场和 `check.log`；Release 构建日志为 `/Users/caiyuanrui/.cache/goule-android/fullscreen-release-build.log`。原始手机材料不提交仓库。

## 2026-11-13 禁用遮罩顶部漏底修复

用户截图中状态栏露出目标 App 背景。源码确认原窗口以 `setFitInsetsTypes(systemBars | displayCutout)` 整体避让，背景无法绘制到屏幕顶部。现取消窗口 inset 裁缩，添加屏幕内布局及全边缘刘海延伸；根视图仍绘制不透明深紫背景，内容按系统栏、刘海与必要手势的最大边距避让，显隐不重复叠加。

自动检查：不指定设备的 `./scripts/check` 通过，Debug/Release 各 41 项单元测试零失败/错误/跳过，Lint、Debug APK 与 instrumentation APK 编译通过。扩展既有独立包布局回归，使用 WindowInsets.Builder 分发系统栏可见/隐藏/恢复、竖屏顶部与横屏侧边刘海及手势边距，断言四侧最大值不叠加、边缘背景像素不透明，并保留三档字号、短横屏按钮触摸、同一视图尺寸往返及截止文案检查；新增明确 PASS 标记由 `scripts/check-ui` 检查。默认 `./scripts/check` 编译此检查，指定 `GOULE_UI_DEVICE` 后才实际运行 Android 布局断言。最终无设备检查日志为本机 `/Users/caiyuanrui/.cache/goule-android/fullscreen-insets-final-check.log`。

设备尝试：OPPO PLJ110，Android 16、ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`。独立 `com.goule.uiverify` 安装停在系统确认并超时，ADB 返回 `Failure [-99]`；没有运行 instrumentation，不能记为布局或实际服务窗口通过。会话已失败，独立应用及 `.test` 包均未安装；未覆盖正式 `com.goule.prototype`、未修改正式戒律或无障碍授权。

随后用户要求重装并完成两次系统安装确认。同设备独立 Debug 包 0.3.8/code 11 的 `scripts/check-ui` 返回成功，instrumentation 输出三项 PASS：列表/详情固定重置保留原视图和滚动位置；横屏按钮在三档字号和尺寸往返下完整可点；边缘背景及稳定的系统栏/刘海/手势边距。运行期间另用 `am start -W` 唤起测试 MainActivity（Status: ok），最终全部断言通过。导出六张 360×640 与 640×320 dp 渲染图，核对常规竖屏和两倍字号横屏：顶部/侧边均为不透明紫底，横屏操作完整，对白独立裁剪/滚动。该证据为 Android 实际测量及离屏绘制，不是服务窗口截图。脚本自动卸载两个测试包；正式 FreezeService 仍在 Bound services 中，Binding/Crashed 为空。

重试日志为本机 `/Users/caiyuanrui/.cache/goule-android/fullscreen-insets-retry.log`，渲染图目录 `/Users/caiyuanrui/.cache/goule-android/fullscreen-insets-20261113/`。

待复测：原签名正式包覆盖安装后，核对真实窗口顶部背景、横竖屏刘海、通知栏下拉、系统返回/桌面/最近任务、两种退出操作及触摸拦截。布局回归不创建真实无障碍窗口，不能代替服务窗口与系统入口的端到端检查。本轮未覆盖安装正式包、无新版本发布。首轮构建与安装尝试日志保存在本机 `/Users/caiyuanrui/.cache/goule-android/fullscreen-insets-check.log`。


## v0.3.8 发布包验证（2026-11-13）

版本升级至 0.3.8/versionCode 11，发布横屏禁用页分栏与实际窗口尺寸重排，继续预发布并沿用原证书。`./scripts/check` 通过：Debug/Release 各 41 项单元测试零失败/错误/跳过，Lint、Debug 和页面回归 APK 构建通过；版本说明及 README 下载入口已更新。Release 从干净发布提交 `07b3ea3fe0387d1a807697af9d18ebc714ad5df7` 构建，包名 `com.goule.prototype`、0.3.8/code 11、最低 Android 11 及无 debuggable 标记核对通过，apksigner 和 ZIP 对齐通过；证书与从 GitHub 回下载的 v0.3.7 发布 APK 一致。

OPPO PLJ110（Android 16、ColorOS V16.1.0）同功能 afb5f23 / 0.3.7/code 10 原签名覆盖安装及独立布局回归已通过，见下节；本次 0.3.8/code 11 版本包不另装手机，不以离屏布局的尺寸往返代替真实服务遮罩的物理旋转/系统全局操作验收。真实禁用遮罩端到端用例和更多设备仍待复测。

16:28（北京时间）[GitHub v0.3.8](https://github.com/grovecai/goule/releases/tag/v0.3.8) 发布完成，非草稿预发布，远端注解标签解析到上述发布提交。仓库为 Public，Release 正文与 `docs/release-v0.3.8.md` 一致；远程 main 的 README 与本地一致，APK/Release 下载入口已更新。资产仅 `goule-v0.3.8.apk`（3,942,511 字节）和 `goule-v0.3.8.apk.sha256`（83 字节），均回下载与本地逐字节一致，GitHub asset digest、大小及校验文件内容全部匹配。APK SHA-256：`745a3eb8fb3364ef029a2b910e79621f03eb346d2e6e6d9010960efa8bf87e89`。

材料保存在本机 `/Users/caiyuanrui/.cache/goule-releases/v0.3.8/`，包括构建日志、签名 APK/校验文件、上一版回下载包、签名/对齐/元数据报告、发布 API 信息及回下载验证结果。本轮没有重新安装手机或修改戒律，后续提交仅补记实际发布结果。

## 2026-11-13 禁用页横屏适配

禁用页改为按遮罩的实际可用窗口尺寸布局：横屏左侧插画，右侧对白和固定在滚动区外的解禁时刻/两个退出按钮；竖屏保持上下漫画构图。同一个窗口收到尺寸变化后重排，服务计时和系统操作继续使用既有回调；系统栏/刘海仍由窗口避让，额外保留四侧必要手势边距。

`./scripts/check` 通过：仓库检查、Debug/Release 各 41 项 JVM 单元测试（零失败）、Lint、Debug APK 和 instrumentation APK 构建通过。新增 `FreezeOverlayLayoutChecks` 接入既有独立包 `scripts/check-ui`，`./scripts/check` 指定 `GOULE_UI_DEVICE` 时自动运行；可用 `GOULE_UI_OUTPUT_DIR` 导出渲染图。布局断言在 Android 上直接实例化生产 `FreezeOverlayView`，不读取或修改正式戒律。

OPPO PLJ110，Android 16、ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`，本轮独立 Debug 包 `com.goule.uiverify`（0.3.7/code 10）实测通过。用户完成两个系统安装确认后，instrumentation 输出两条 PASS：既有列表/详情固定周期刷新保留根视图及滚动位置；横屏按钮在窗口重排和大字号下可见并可点。

| 布局场景 | 实际结果 |
| --- | --- |
| 同一视图尺寸往返 | 每档字号依次测量/布局 360×640 → 640×320 → 800×360 → 640×240 → 360×640 → 640×320 dp，截止文案始终保留；这是实际 Android 布局重排，未代替物理旋转服务窗口的实测 |
| 短横屏与大字号 | 1、1.3、2 倍字号均通过；长中文 App 名和跨日中文 12 小时时间正常换行，对白可滚动 |
| 按钮与安全区 | 每轮横屏两个按钮均完整包含于扣除四侧预置安全边距的区域，触摸高度至少 48 dp，祖先不含 ScrollView；从遮罩根节点向按钮中心分发 DOWN/UP 均到达按钮，分别验证退出回调。离屏布局下点击回调另以 performClick 检查，未触发系统最近任务或回桌面 |
| 渲染核对 | 导出 640×320 dp 三档字号 PNG，核对正常与两倍字号：插画、解禁时刻和两个按钮均完整；大字号下对白在独立区域裁剪/滚动，不挤掉操作 |
| 清理与正式服务 | 两个独立测试包已卸载，正式 `com.goule.prototype` 保留；Bound services 仍含 FreezeService，Binding/Crashed 为空。未覆盖安装正式 APK、未修改正式数据或发布新版本 |

首轮 OPPO 安装等待系统确认，取消后按用户要求重新安装并完成上述检查。并行尝试的 Android 15 x86_64 模拟器冷启动期间 instrumentation 未完成，已停止、移除独立测试包并关闭本轮模拟器，不记为通过。材料保存在本机 `/Users/caiyuanrui/.cache/goule-android/landscape-oppo-check.log` 和 `/Users/caiyuanrui/.cache/goule-android/landscape-oppo-20261113/`。

待复测：原签名正式包覆盖安装后，真实禁用目标上的横竖屏物理旋转、系统安全区/刘海与两种导航、最近任务退出/点回、回洞府、触摸不透传、到期撤罩及锁屏/重连。当前布局测试不创建实际无障碍遮罩或执行系统全局操作，不据此宣称上述端到端路径通过。

### 横屏优化包覆盖安装（2026-11-13）

15:58（北京时间）按用户要求，将横屏优化源码 `afb5f23` 的 Release 包沿用原发布证书覆盖安装至 OPPO PLJ110，Android 16、ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`，版本 0.3.7/versionCode 10，属于未发布功能构建，未替换 GitHub v0.3.7 资产。

`assembleRelease`、apksigner 验证、与安装前回拉 APK 的证书一致性、ZIP 对齐、包名和版本信息检查通过，Release 无 debuggable 标记。`adb install --no-incremental -r` 返回 Success，未卸载或清除正式数据；首次安装时间 `2026-11-11 20:28:04`、appId `10381` 均保持，最后更新 `2026-11-13 15:58:06`。回拉手机 base.apk，SHA-256 与本地签名包一致：`20178ddce0b1c004b144958d64dc64c1bdb990593d3aef30f1a61eecbdf63781`。

启动 `am start -W` 返回 Status: ok。安装后列表截图确认既有红果免费短剧和小红书戒律仍在，均为 10/120；红果禁用至 16:03、还可刷 0 秒，小红书还可刷 10 分钟，与安装前相同。第三条抖音在安装后首屏以下，本次不把未完整采集的第三条参数写为逐项保留核对通过。无障碍 Bound services 含 FreezeService，Binding/Crashed 为空。未主动修改戒律或打开目标 App 消耗额度，本轮只完成覆盖安装、启动、已采集数据和服务核对，真实禁用遮罩的横屏物理旋转及全局操作仍待复测。

安装材料保存在本机 `/Users/caiyuanrui/.cache/goule-releases/landscape-afb5f23/`，含签名包、前后 APK、证书/摘要、包和服务状态、前后列表截图及安装摘要。后续提交仅更新本轮安装记录，不改变已安装代码。

## v0.3.7 发布包验证（2026-11-13）

版本升级为 0.3.7/versionCode 10，发布固定周期及页面稳定修复，包含 v0.3.6 后的连接中断恢复和后台锁定指引。`./scripts/check` 通过：Debug/Release 各 41 项单元测试零失败/错误/跳过，Lint、Debug 和页面回归 APK 构建通过；`assembleRelease` 通过。原有 OPPO PLJ110（Android 16、ColorOS 16.1.0）57f4d11 功能构建的安装与独立页面回归记录保留，本次版本包未另装手机，不以旧构建结果代替版本包全量实测。

最终签名 APK 从干净发布提交 `3fe48659fc035660d182aeef6cb11a1047828b81` 构建，包名 `com.goule.prototype`、版本 0.3.7/code 10、最低 Android 11 核对通过，无 debuggable 标记。apksigner 验证和 ZIP 对齐通过，证书 SHA-256 与从 GitHub 回下载的 v0.3.6 发布包一致。

06:18（北京时间）[GitHub v0.3.7](https://github.com/grovecai/goule/releases/tag/v0.3.7) 发布完成，非草稿且为预发布，远程注解标签解析到上述提交。仓库为 Public；Release 正文与版本说明文件一致，main 上 README 的版本说明及 APK 下载 URL 核对通过。资产仅 `goule-v0.3.7.apk`（3,938,415 字节）和 `goule-v0.3.7.apk.sha256`（83 字节）。发布后回下载两项文件，与本地逐字节一致，GitHub 资产 digest、文件大小及校验文件内容全部匹配。APK SHA-256：`dcd56b3d8967f76469f158d19d68a6e9f3fe160a1ab47a36829ec44ffec33bfb`。

材料保存在本机 `/Users/caiyuanrui/.cache/goule-releases/v0.3.7/`，包含回下载旧包、最终 APK/校验文件、证书报告、版本元数据、发布 API 信息和回下载验证结果。本轮仅发布和校验资产，没有重新安装手机或改变戒律；旧功能构建的真机记录与未覆盖范围继续保留。

## 2026-11-13 固定周期与页面闪缩修复

用户确认改为每条戒律按自身可刷＋休息时长建立固定周期，首次实际使用开始起算；例如 10/120 是 130 分钟、20/30 是 50 分钟，未用满也在同一边界一次重置。周期内闲置不增长额度，长期闲置不移动原周期相位、不叠加剩余额度；修改保留当前周期，下一周期采用最新参数。列表/详情的计时文字原位更新，不再因剩余秒数变化重建整个页面和滚动容器。

自动检查：`./scripts/check` 的 Debug/Release 各 41 项单元测试、Lint、Debug APK 及 instrumentation APK 编译通过。独立固定周期模型核对 58,800 个秒级采样点（1/15、10/120、20/30）；另覆盖分段累计、未用满重置、长时间闲置相位、精确毫秒和跨界采样、重复与重叠观察、参数修改、序列化/重启时钟及两类旧 JSON 的迁移。固定周期允许使用周期尾和下一周期头两份额度，此项是已确认新行为。

迁移：保留滚动版已保存区间的总用量和原额度/长度，合并为固定周期累计；原起点加周期仍在保存时间之后时保留起点，否则从最后保存时间起建立保留当前已用量的周期，避免切换规则直接清零。早期累计版同样保留用量；旧有效禁用截止作为迁移下限。迁移缺少原始完整起点时不能还原已被滚动裁剪的时间。

页面自动回归已加入 `app/src/androidTest`，`./scripts/check` 默认编译，带 `GOULE_UI_DEVICE=<adb序列号>` 时自动运行 `scripts/check-ui`。脚本使用独立应用 ID `com.goule.uiverify`，测试入口拒绝正式包名；仅写入独立测试包数据，结束后卸载该包和 instrumentation 包。列表/详情分别预置距固定重置点 12/24 秒的不同周期并实际跨过，检查原 TextView 被原位更新、根视图仍为同一对象、列表滚动位置不变；不是完整等待 50 或 130 分钟。OPPO 安装新测试包会要求厂商扫描及用户锁屏密码确认，须在手机上完成，不能绕过。

OPPO PLJ110（Android 16、ColorOS V16.1.0，`PLJ110_16.0.10.502(CN01)`）独立包实际执行通过，输出 `PASS: list/detail fixed resets update existing views and preserve scroll`：列表及详情在各自预置的重置点一次恢复额度，同一个原有 TextView 收到更新，根视图对象保持，列表滚动位置保持；测试包和组件已清理。初次执行的包装脚本额外要求系统打印结束代码，而此系统在有 stream 结果时只打印 PASS 文本，导致包装层误报非零；已改为检查测试入口仅在所有断言通过后输出的明确 PASS，并拒绝失败标记。正式包覆盖安装结果见下节。

本轮没有打开正式目标 App 消耗用量、没有自然等待完整 50/130 分钟；前台识别、真实用满后的遮罩/锁屏/重连和更多 ROM 仍需另测。

### 固定周期修复包覆盖安装（2026-11-13）

06:03（北京时间）按已授权的保留数据覆盖流程，将源码 57f4d11 的 Release 构建安装至 OPPO PLJ110（Android 16、ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`），版本仍为 0.3.6/code 9，未发布 GitHub 新版。

Release 构建、原发布签名校验及 ZIP 对齐通过；与安装前回拉 APK 的签名证书一致。`adb install --no-incremental -r` 返回 Success，`am start -W` 返回 Status: ok。首次安装时间 `2026-11-11 20:28:04`、appId `10381` 保持，未卸载或清除正式包数据。回拉安装 APK 的 SHA-256 与本地签名包一致：`60e4500cfbc50a8d1ef0deb3d2c567e1e369bf5014bd9a2d75a7ef78dacbe854`。

安装前后截图确认小红书、抖音、红果免费短剧三条戒律及 10/120 参数保留。安装前旧滚动用量已自然释放、三条均显示剩余 10 分钟，安装后读数相同；不以本次空用量页面核对代替非零迁移的自动测试。详情完整显示固定周期说明及首次使用开始周期提示；有已开始周期时显示下次重置时间，相关路径由独立页面回归覆盖。正式列表停留的两张间隔 4 秒截图，在排除状态栏、手势区和临时滚动条后内容像素完全一致，未观察到持续缩放或额度增长。

最终 Enabled/Bound 均有 FreezeService，Binding/Crashed 为空，独立测试包及组件均已移除。正式手机停留在显示三条戒律的列表。材料位于本机 `/Users/caiyuanrui/.cache/goule-releases/fixed-cycle-57f4d11/`，包含 APK、证书/摘要报告、包和服务状态、列表与详情截图及页面回归 PASS 记录。完整自然周期、正式目标实际用满后的遮罩/锁屏/重连、更多 ROM、短屏/大字号仍待单独复测。

## 2026-11-13 滚动窗口计时

用户确认采用任意连续 U+B 分钟最多刷 U 分钟，旧区间逐毫秒过期释放额度。当前源码替代旧的 24 小时重置和固定禁用后整轮恢复；已有 GitHub v0.3.6 APK 仍为旧行为，本轮未发布或覆盖安装。

`./scripts/check` 通过：仓库检查、Debug/Release 各 38 项单元测试，零失败/错误/跳过；Lint 及 Debug APK 构建通过。首次定向测试因未配置 Android SDK 未进入测试，补齐本机 JDK 17/SDK 环境后运行；开发过程发现一项移除保护测试错把同步释放阶段当作再次禁用，修正用例为完全耗尽替换额度后通过。新增检查已接入既有 Gradle 单元测试，由 `./scripts/check` 执行。

| 场景 | 自动回归结果 |
| --- | --- |
| 连续刷满 10/120 | 第 130 分钟前禁用，恰好第 130 分钟可开始同步替换但剩余仍为 0；过 1ms 释放 1ms，第 135 分钟释放 5 分钟，第 140 分钟全部释放 |
| 分段 0–5、60–65 分钟 | 在 130–135、190–195 分钟分别释放对应额度，不统一禁到第 185 分钟 |
| 未用满即离开 | 短暂离开保留剩余，长时间离开使记录正常过期；读列表不计时、不改变原对象 |
| 周期尾/周期头集中刷 | 第 0–1、121–130 分钟已用满后，第 130 分钟起只能先替换 1 分钟，不能再领完整 10 分钟 |
| 恢复时持续刷 | 可以 1:1 替换同步过期用量；到释放间隙准确再次禁用，保存最近禁用开始 |
| 跨过期边界的采样 | 区间内分段计算；超额部分准确截断，重复/重叠区间不重计，零/延迟采样按观察条件排除 |
| 毫秒边界和分片等价 | 截止前 1ms/恰好截止/之后 1ms，23ms 采样与完整区间计算一致 |
| 服务调度 | 等待首次释放，持续同步释放时不做毫秒忙轮询，尾段不足一秒时仍按边界检查 |
| 多 App、午夜和旧 24 小时边界 | 额度独立，跨午夜和旧周期不额外重置 |
| 增减可刷/休息时长、连续修改 | 保留区间、原可刷额度与窗口长度，全部有效用量过期后采用最新配置；持续替换时仍保留原快照；未使用则立即采用 |
| 序列化及进程重载模型 | 区间、快照、最近禁用、观察水位保存读取一致，重报最后区间不增加用量 |
| 旧 JSON 迁移 | 无分段数据时按最后保存的规则时间保守重建；已有禁用沿用旧起点/截止；缺快照、缺时钟、零累计但仍禁用及已过期数据均覆盖，不假装还原分段历史 |
| 系统时间与休眠 | 原有同次开机单调时钟、跨重启时钟不回退、休眠/延迟采样排除继续通过；跨重启前调墙钟的防作弊不在现有时钟保证内 |
| 修改与移除保护 | 保留 5 分钟修改间隔，最近真正开始禁用后重新等待移除；明确移除仍解除规则，不新增防删除/重加机制 |
| 独立时间轴模型 | 1/15、10/120 两组规则共 78,840 个秒级样本，独立布尔环形时间轴逐步比对剩余量和禁用判定，覆盖持续/随机分段/长期空闲及多次窗口滚动；窗口内用量从不超过额度 |

本轮只读检测到 OPPO PLJ110，Android 16，系统 `PLJ110_16.0.10.502(CN01)`；未安装 APK、修改戒律或操作权限。上述为纯 Kotlin/序列化/构建结果，不是手机上实际观察到的行为。仍待在该 OPPO 及实体 Pixel/接近原生设备上使用独立测试包验证：真实使用后剩余量、分段释放时遮罩撤下与再次恢复、锁屏跨释放边界、服务重连和进程重启、旧正式数据的升级迁移，以及详情/表单短屏和大字号文案。系统断连时只能保留已提交记录，既有约 5 秒进度保存间隔及无法观察期间不计时的边界沿用原设计。

### OPPO 滚动窗口修复包覆盖安装（2026-11-13）

05:35（北京时间）按用户要求，将源码 82a8fb6 的 Release 构建以原发布证书覆盖安装到 OPPO PLJ110，Android 16、ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`。版本仍为 0.3.6/versionCode 9 的未发布功能构建；未更新 GitHub 发布资产。

- `assembleRelease`、apksigner 签名校验和 ZIP 对齐检查通过；与安装前回拉旧 APK 的证书 SHA-256 一致。
- `adb install --no-incremental -r` 返回 Success，未卸载或清除数据。首次安装时间 `2026-11-11 20:28:04` 和应用 appId `10381` 均保持，最后更新为 `2026-11-13 05:35:11`。
- `am start -W` 返回 Status: ok。安装前后截图核对 3 条戒律（抖音、小红书、红果免费短剧）均保留，参数均为 10/120；抖音仍禁用至 05:41、剩余 0 秒，小红书剩余 31 秒，红果免费短剧剩余 10 分钟。详情新增「任意连续 130 分钟内，最多刷 10 分钟。旧用量过期后逐步恢复额度」说明，当前屏幕完整显示。未改动戒律或打开目标 App 消耗额度。
- 最终无障碍 Enabled 和 Bound 均确认 FreezeService 已启用并实际连接，Binding 和 Crashed 为空。手机停留在可见全部 3 条戒律的列表。
- 回拉安装后的 base.apk，与本地签名 APK 的 SHA-256 完全一致：`fa42ee6c59ee2fa53874be9b3f1a7137c32b32eefafb11660a80d2e95a98b866`。

材料保存在本机 `/Users/caiyuanrui/.cache/goule-releases/rolling-82a8fb6/`，含安装前后 APK、证书报告、包和服务状态、前后列表及详情截图。本次通过的是覆盖安装、数据保留的页面核对和服务绑定；没有实际等待释放边界，不将自动回归结果当作真机分段释放、锁屏/重连、进程重启或短屏/大字号的验收结果。

## 2026-11-13 任务锁定步骤补充

后台运行指引第 3 组新增逐步操作。OPPO/ColorOS 使用本轮此前已实机确认的卡片「···」→「锁定」路径，明确底部上滑停顿/最近任务键，以及应用名称旁的小锁图标确认；其他品牌使用通用卡片菜单/长按菜单说明，保留无对应选项可略过的提示。厂商文案集中在 BackgroundSupport，后台页复用原可滚动布局。

`./scripts/check` 通过：仓库检查、Debug/Release 各 28 项单元测试、Lint 和 Debug APK 构建。本轮仅补充帮助文案，没有新增复述文案的测试，也没有安装、发布或操作手机。此前 OPPO PLJ110（Android 16、ColorOS 16.1.0）的实际任务锁定及清理结果见 [恢复验证](recovery-verification-2026-11-13.md)；新增步骤在手机页面、短屏和大字号下的完整显示尚未验证，不能将此前系统操作通过记为本轮页面通过。

### 同日覆盖安装与页面核对

05:06（北京时间）按用户要求，将 edeabcb 的 Release 构建用原发布签名覆盖安装到 OPPO PLJ110，Android 16、ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`。版本仍为 0.3.6/code 9 的未发布功能构建。assembleRelease、apksigner 及 ZIP 对齐通过，安装前与手机旧包比对证书 SHA-256 一致；`adb install --no-incremental -r` 成功，未卸载或清除数据。

启动成功，原有戒律仍显示。经设置进入后台运行指引并向下滚动，截图确认第 3 组包含最近任务手势/按键、卡片「···」菜单、「锁定」、小锁图标确认及系统差异说明，文字正常换行，底部「返回戒律」完整可见。UI 层级采集结束后等待服务重新绑定，05:07 核对 Enabled services 仍含 FreezeService、Bound 实际绑定、Crashed 为空。手机 base.apk SHA-256 与本地签名包一致：`2c1f2d78399e3519447b32e44778d39d24f5bea032a6f93621449f1478bebdc8`。

手机停留已滚动到任务锁定步骤的后台指引。截图及安装材料位于本机 `/Users/caiyuanrui/.cache/goule-releases/task-lock-guide-edeabcb/`。本轮未改手机设置或戒律，未重测清理场景，未发布 GitHub；新增文案的短屏和大字号显示仍待验证。

## 2026-11-13 清理后恢复修复与真机验证

详见 [完整矩阵](recovery-verification-2026-11-13.md)。OPPO PLJ110（Android 16、ColorOS V16.1.0）原签名修复包：完全允许后台行为时，正式包划掉任务保持绑定；未锁定一键清理明确触发 `o-kill(2) ... with oneclick` 并留下异常服务，新中断提示和关闭再开启恢复通过；锁定最近任务后再次清理保持绑定。独立包确认 20 秒已用量及达到阈值后的禁用截止不重置，手动重开后重进目标恢复遮罩。测试包已卸载，正式服务绑定及任务锁定保留。自动检查通过，Debug/Release 各 28 项测试无失败；本轮不承诺自动恢复，不替换 GitHub 发布包。

## 2026-11-13 戒律暂停诊断

OPPO PLJ110、Android 16、ColorOS V16.1.0，0.3.5/code 8 的 72a7263 功能构建，只读采集。最新退出为 04:00:28.751 系统杀进程 `o-kill(2)`；组件仍登记、Bound/Binding 为空、Crashed 含 FreezeService，主界面重开未恢复。用户补充此前刚执行一键清理或划掉任务，与系统退出时间吻合，最可能为手动清理触发。连续开机约 30 小时，本次不同于旧 F02 的重启撤权。未取得触发时的详细系统日志，具体清理类型及厂商策略仍未确定；未重新授权或改变手机设置，未验证恢复，也未修改产品代码。完整证据、源码解释及局限见 [本轮诊断](pause-diagnosis-2026-11-13.md)。

## 本地检查

运行 `./scripts/check`。需要 Bash、Git、Python 3、JDK 17 和 Android SDK。将 `JAVA_HOME` 指向 JDK 17，将 `ANDROID_HOME` 指向 SDK，或在本地 `local.properties` 配置 `sdk.dir`。使用 Android Studio 的 SDK Manager 或 `sdkmanager` 安装 `platforms;android-35`、`build-tools;35.0.0`。真机安装另需 `platform-tools`。首次构建需要下载 Gradle 和 Maven 依赖。

仓库检查涵盖 Git 改动中的空白错误和 Markdown 行内相对链接。Gradle Wrapper 固定为 8.11.1，运行 `check assembleDebug`，执行 Lint、JVM 单元测试并构建调试 APK。规则测试覆盖固定周期累计与统一重置、不同参数的独立周期、精确边界、重复与重叠区间、修改与移除保护、序列化迁移、独立时间轴模型、休眠采样和规则时钟恢复。设备拦截行为由下面的真机流程验证。

## 修改戒律保留本轮额度（2026-11-12）

0.3.5/versionCode 8 未发布修复构建。`./scripts/check` 通过，含仓库检查、Debug/Release 各 23 项 JVM 单元测试（零失败/错误/跳过）、Lint 和 Debug APK 构建。初次检查因当前终端未配置 Java 未进入 Gradle；显式使用本机缓存 Zulu JDK 17.0.14 和 Android SDK 后完整检查通过。

- 只修改禁用时长保留已用量和窗口起点，达到原额度后使用新的禁用时长。
- 原 20 分钟已用 8 分钟，将可刷时长改为 40 或 5 分钟后仍剩 12 分钟，按原额度触发禁用；到期后使用最新额度。
- 连续修改仍使用最早计时额度，多 App 用量独立；24 小时窗口起点不变，跨界累计及边界前用满仍按原额度执行。
- 尚未开始计时，或禁用/24 小时窗口已结束，采用最新可刷配置；已有禁用截止及 5 分钟修改间隔保持。
- JSON 保存读取保留本轮额度、窗口、累计及禁用时间；旧数据缺少新字段时可读取并在修改时固定旧配置。新建和刷新后的空快照可往返读写。列表与详情共用的显示函数按本轮原额度计算，下一轮显示新额度。

本轮仅执行本机自动检查。ADB 检测到 OPPO PLJ110，但未安装、未改动手机戒律或设置；不以历史 Android 16 / ColorOS 16.1.0 结果代替本轮真机验收。待在 OPPO 和实体 Pixel/接近原生设备上执行：实际分段使用后分别修改两种时长，核对列表/详情保持剩余量；重启进程及锁屏后保持原额度；用满按原额度出现遮罩且采用新禁用时长；禁用中修改保留截止，到期后采用新可刷时长；核对编辑提示在短屏和大字号下完整可读。设备实测时另记实际机型、系统和构建。旧版本编辑时已清空的历史用量没有可恢复的数据，本次修复不补造用量。

### OPPO 修复包覆盖安装（2026-11-12）

21:11（北京时间），按用户要求将 72a7263 的 Release 修复构建覆盖安装至 OPPO PLJ110。实际设备 Android 16、ColorOS V16.1.0，系统构建 `PLJ110_16.0.10.502(CN01)`。原包为 0.3.4/code 7，新包为 0.3.5/code 8，沿用原发布签名；assembleRelease、apksigner 验证、与设备旧包证书 SHA-256 比对及 zipalign 检查通过。

`adb install -r` 成功，未卸载或清除数据。`am start -W` 返回 Status: ok，截图确认原有戒律列表正常，小红书仍显示「还可刷 31 秒」、抖音「还可刷 10 分钟」。Enabled services 与 Bound services 均确认 FreezeService 已启用并实际绑定。回拉设备 base.apk，SHA-256 与本地签名 APK 一致：`abca8c46608a92164619c3d75a6dc31f3fdb7fdcb05293e100baedc366fcbeac`；另外核对 APK 中包含本轮新增额度快照字段及编解码类。

手机停留戒律列表。本次仅安装、启动及现有界面核对，未修改用户戒律或系统设置，未执行真实额度修改、锁屏、重启恢复或用满后的遮罩回归。该构建尚未发布，不替换 GitHub v0.3.5 资产，前述待验证项保留。

## 列表剩余可刷时长（2026-11-12）

本轮 Debug 0.3.4/versionCode 7 功能构建。`./scripts/check` 通过，含 Debug/Release 各 12 项单元测试、Lint 和 Debug APK 构建。新增回归随现有 Gradle 单元测试执行，覆盖分段用量、多 App 独立、查看不扣时、禁用期间编辑后仍为 0、禁用到期与 24 小时窗口刷新，以及不足一秒和整分钟的向上取整显示。

Pixel 6 AVD，Android 15 AOSP x86_64（API 35，AE3A.240806.019 / 12368160），仅操作模拟器及专用目标，未安装或修改 OPPO 正式包。

- 360 × 640 dp 短屏预置两条独立用量，列表分别显示「还可刷 12 分 48 秒」「还可刷 1 分 42 秒」，配置刷/禁时长与未生效状态保留，滚动后两条剩余时长完整可见。
- 开启模拟器无障碍服务，实际打开第一个目标约 5 秒，返回列表显示扣减后的剩余秒数；第二个目标累计不变。继续停留列表，累计数据不增加。
- 预置仍在禁用但已修改额度的状态（额度 30 分钟、累计 0），列表显示「还可刷 0 秒」；跨过预置的约 20 秒截止后，停留页面自动刷新为「还可刷 30 分钟」。这是边界预置检查，未等待完整 15 分钟禁用。
- 同一短屏设置 1.3 倍系统字体，滚动后新增的两条剩余时长均完整可读。原有固定高度刷/禁印章在大字号下存在字形裁切，本轮未修改该组件，后续单独处理。

uiautomator 会临时抑制服务，采集时的正在连接/未生效不作为真实服务故障。OPPO PLJ110（Android 16、ColorOS 16.1.0）的新列表布局、实际分段使用及锁屏后读数尚待真机复测，24 小时刷新本轮由纯 Kotlin 边界测试覆盖。未发布新的 APK。

### OPPO 覆盖安装与列表核对（2026-11-12）

按用户要求，将 fce27b5 的 Release 功能构建安装到 OPPO PLJ110，Android 16、ColorOS 16.1.0，系统构建 `PLJ110_16.0.10.502(CN01)`。沿用原发布密钥，证书与手机旧包一致；Release 构建、apksigner 与 zipalign 校验通过。`adb install -r` 成功，从手机原 0.3.3/code 6 升级到 0.3.4/code 7，未卸载或清除数据。

启动 `MainActivity` 返回成功，截图确认原有戒律仍显示，新增「还可刷 10 分钟」「还可刷 3 分 10 秒」等读数完整可见。系统 Enabled services 及 Bound services 均含 FreezeService，实际绑定保持。回拉已安装 base.apk 的 SHA-256 与本地签名 APK 一致：`cbf0570f35370656f111657cb8934db5784cc93b3e6ca0126b5f0157e45a3156`。

这是未发布的功能构建，未替换 GitHub v0.3.4 资产。本轮只完成覆盖安装、启动、已有列表显示及服务绑定核对，未重新执行真实分段计时、锁屏、禁用到期或全部回归。手机停留戒律列表供查看。

## 详情剩余可刷时长（2026-11-12）

详情页在「当前戒律」卡片的刷/禁参数下显示金色「还可刷」读数，与列表复用 remainingUseLabel，详情刷新签名也包含此文案。计时与存储逻辑未变，沿用现有禁用、窗口刷新、独立额度和取整回归。`./scripts/check`（Debug/Release 各 12 项单元测试、Lint、Debug 构建）及 assembleRelease 通过。

OPPO PLJ110，Android 16、ColorOS 16.1.0，系统构建 `PLJ110_16.0.10.502(CN01)`，0.3.4/versionCode 7 未发布功能构建。证书与旧包一致，apksigner 和 zipalign 通过，`adb install -r` 成功，未卸载或清除数据。启动返回成功，截图核对可刷详情的「还可刷 10 分钟」与列表一致，卡片文字完整、修改和移除入口仍可见。系统 Enabled services 与 Bound services 均含 FreezeService。

设备 base.apk 与本地签名 APK 的 SHA-256 一致：`f1b662756bad06c67537d6fb8121e5d170804399529fbe18758f390eb8fbd07b`。未替换 GitHub 发布资产。详情禁用到期、24 小时刷新及短屏/大字号的实机显示未在本轮复测，不以共享计算测试代替页面完整验收。

## v0.3.6 发布包验证（2026-11-12）

版本 0.3.6/versionCode 9，包名 com.goule.prototype，最低 Android 11（API 30），源代码提交 f65f2c33cc1e6cb9f14325326e505c664817afc4。`./scripts/check` 通过，Debug/Release 各 23 项单元测试零失败/错误/跳过，Lint、Debug 及 Release 构建通过。apksigner、aapt 版本元数据与 zipalign 校验通过；从 GitHub v0.3.5 重新下载的发布 APK 与新包证书 SHA-256 一致，沿用原签名。

资产 `goule-v0.3.6.apk` 为 3,926,127 字节，SHA-256：`1a4754254777090d026b187ccd5f9c4df676125a6ab86528e09ca29adfae37cc`；附 `goule-v0.3.6.apk.sha256`，83 字节。2026-11-12 21:16（北京时间）[GitHub v0.3.6](https://github.com/grovecai/goule/releases/tag/v0.3.6) 发布完成，非草稿且为预发布；远程标签及发布目标均指向上述提交，资产仅包含 APK 与对应校验文件。回下载两项资产与本地逐字节一致，GitHub asset digest、文件大小及校验文件中的 APK 摘要全部匹配。

OPPO PLJ110（Android 16、ColorOS 16.1.0）的本次修复功能构建安装/启动/列表/绑定记录来自 72a7263、0.3.5/code 8，见上文。本轮没有另装 0.3.6/code 9 版本包，没有重做实际修改、锁屏或计时禁用真机回归，相关待验证项及既有已知问题保留。

## v0.3.5 发布包验证（2026-11-12）

版本 0.3.5/versionCode 8，包名 com.goule.prototype，最低 Android 11（API 30）。`./scripts/check` 通过，Debug/Release 各 12 项单元测试无失败，Lint 和 Debug 构建通过；assembleRelease 通过。apksigner、ZIP 对齐和 aapt 元数据核对通过。重新从 GitHub 下载 v0.3.4 发布 APK，确认新包的发布证书 SHA-256 一致。

签名 APK 为 `goule-v0.3.5.apk`，3,926,127 字节，SHA-256：`3764ebfdad2650359248d3e66ca4a09b16070335768a78606ea8de198020917a`，附同名 `.sha256` 文件。既有 OPPO 实机验证来自 0.3.4 功能构建，见上文；本次更新版本号后的发布包未另装手机，未将其记为完整真机回归通过。

2026-11-12 20:15（北京时间）[GitHub v0.3.5](https://github.com/grovecai/goule/releases/tag/v0.3.5) 发布完成，非草稿、预发布，远程标签解析至提交 `51c7b2f698e127c021e4b78772f053bd726d7c88`。资产仅包含 APK 和 `.sha256` 文件，大小分别为 3,926,127 / 83 字节。重新下载两项资产，APK 哈希与本地和 GitHub 资产 digest 一致，校验文件逐字节一致且包含正确摘要。

## 2026-11-11 OPPO 完整回归

最新结果见 [逐项矩阵与失败复现](oppo-regression-2026-11-11.md)。OPPO PLJ110、Android 16、ColorOS 16.1.0，产品源码 63d36ac。规则、权限、可选后台、计时、锁屏、最近任务与清理等已实测；来电返回漏拦截、重启关闭服务、首次引导恢复三项失败尚未修复。未复现受限设置，过夜待机及其他品牌未覆盖。本轮临时包已清理、正式服务已恢复，不能标为全量通过。

## 首版真机验收

在 Pixel 或接近原生的设备以及至少一款国产 ROM 上执行。设备需运行 Android 11 或以上，并安装两个普通第三方 App。用 `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装「斩心魔」。若系统阻止侧载 App 开启无障碍，按系统提示查看应用信息中的受限设置。

1. 全新安装从欢迎进入无障碍说明，未连接时不能下一步；通过系统入口开启服务，返回确认已设置。第 2 步后台指引可不操作而直接继续，不显示后台完成状态。设置页与戒律缺项只针对无障碍。清除应用数据只用于专门的测试环境。
2. 为 A 和 B 分别设 1 分钟可刷、15 分钟禁用。打开 A 约 20 秒，切到桌面等待，再返回 A。确认用完剩余约 40 秒才出现禁用页。另起一轮测试锁屏，分别记录直接解锁回到目标和从桌面重新进入时的剩余时长。
3. A 禁用时确认触摸无法传到目标区域。「返回桌面」后重新打开 A，确认仍禁用。打开 B，确认 B 可刷且有自己的计时。确认系统导航、设置、来电和紧急入口可用。
4. 保持 A 在前台等到期，确认遮罩撤下，目标可接收触摸且新一轮从零开始。锁屏跨过禁用截止时，确认重新进入也可正常使用。
5. 分段使用后分别修改禁用和可刷时长，确认已用量、原可刷额度及窗口起点保留，按原额度用满才禁用，新可刷时长从下一轮生效。再次进入详情检查 5 分钟等待。创建与每次开始禁用时都重新检查 1 小时移除条件。修改禁用中的规则时确认原截止时间保留。
6. 关闭无障碍服务后确认规则显示「未生效」，目标可正常交互。重新连接时确认仍有效的禁用恢复，已到期规则可刷。进程重启后确认已保存的规则和使用量仍可读取。
7. 记录构建版本、机型、Android 和 ROM 版本、安装来源、各步骤结果、观测延迟及异常复现步骤。

## 首版模拟器记录

2026-11-10，Pixel 6 AVD，Android 15 AOSP x86_64（API 35，AE3A.240806.019），首版调试构建。使用两个专用触摸测试 App。

- 走通欢迎、暂缓授权、添加规则、默认时长、修改后的等待、创建后的移除等待和进程重启后读取规则。确认授权说明能打开系统设置。服务开关由模拟器 ADB 设置，原生界面随后显示「可刷」。
- 分别保存两条规则，A 禁用时 B 仍接收触摸。漫画遮罩阻止目标点击，「返回桌面」生效，重新进入仍禁用，系统设置可打开。
- 以模拟器专用持久化数据将规则置于阈值和截止时间附近。确认重新进入和直接唤醒两条路径均保留剩余时长，仍有效的禁用可在进程重启后恢复，并在截止后自动恢复触摸。
- 用能力原型格式的本地设置验证迁移，旧目标保留并采用首版默认时长。实体 OPPO 与 Pixel、系统来电和紧急入口的首版结果待记录。

## 界面对照复测

2026-11-10，Pixel 6 AVD，Android 15（API 35），使用新版调试 APK 与专用目标 App。

- 以交互原型逐页核对欢迎、授权、规则列表、选择器、规则表单、规则详情、确认页、设置和禁用页。重点检查欢迎插画与按钮间距、规则横幅裁切、强调词、规则状态与时长印章、底部导航标签和漫画对白。
- 模拟器中完成授权延后、添加及编辑规则，验证放弃修改和移除规则的继续、取消及确认操作。服务开关由 ADB 设置。新版禁用页通过触摸拦截、返回桌面、重新进入和自动到期检查。测试使用专用规则，预置禁用截止时间。
- 本地检查通过。原型的演示按钮、模拟系统栏和示例列表保留在原型中。OPPO 对照记录见下文。

## 能力原型模拟器记录

2026-11-10，Pixel 6 AVD，Android 15 AOSP x86_64（API 35，AE3A.240806.019）。通过 ADB 安装调试 APK 和记录触摸的测试 App。应用内同意后打开系统设置，测试通过 ADB 切换服务开关，并确认 Goule 显示服务已连接。

- 切出至桌面等待 10 秒、息屏等待 11 秒均暂停累计。该轮回到目标后约 2.4 秒出现遮罩。
- 遮罩拦截目标区域的点击。「返回桌面」可离开目标，冷冻期间重新打开目标会再次拦截。到期后目标恢复接收点击，下一轮用完后可再次冷冻。
- 冷冻期间打开系统设置时遮罩撤下。返回目标后遮罩恢复，关闭服务时撤下。

## 真机记录

### OPPO PLJ110 最近任务

2026-11-10，Android 16（API 36），ColorOS 16.1.0。在原行为中，最近任务卡片显示目标 App 的系统快照。点回专用目标后，目标前台事件先恢复遮罩，约半秒后，启动器在窗格消失时发出一组迟到事件，导致遮罩撤下。目标的禁用截止时间仍有效。

修复版以启动器窗格关闭信号识别收尾事件。构建检查与回归测试通过，Android 15 模拟器验证「关闭 App」打开最近任务，点回仍被禁用的目标会恢复遮罩。修复版在 OPPO 上复测通过。「关闭 App」打开最近任务，点回专用目标后，超过原来漏拦截的时间仍显示遮罩，点击未传到目标。底边上滑可返回桌面。原有规则已恢复，无障碍服务已连接。

### OPPO PLJ110 守道心文案

2026-11-10，Android 16（API 36），ColorOS 16.1.0。实机核对欢迎页、权限入口、规则标语、戒律保护和禁用对白。使用「短视频」测试规则时，新增飞升台词保持单行，独立于恢复时间和底部按钮。遮罩拦截触摸，系统手势和按钮均可返回桌面。测试后恢复原有规则和授权，无障碍服务保持连接。

### 辅助页面与桌面图标

2026-11-10，`./scripts/check` 通过。Android 15 模拟器覆盖安装后以测试数据查看欢迎、授权、选 App、表单、详情、放弃修改、移除确认、开启帮助、计时和隐私说明，确认深紫底色、暖白输入文字和金色主按钮。APK 的启动器资源与戒律长老图标源文件校验一致。OPPO PLJ110（Android 16，ColorOS 16.1.0）覆盖安装成功，无障碍服务仍已连接。设备锁屏，ColorOS 启动器的新图标显示待解锁查看。

### OPPO PLJ110 全屏修仙版

2026-11-10，Android 16（API 36），ColorOS 16.1.0，构建 `PLJ110_16.0.10.502(CN01)`。1080 × 2374，有效密度 480 dpi，系统字体缩放 1.0，手势导航。USB ADB 覆盖安装 0.2 调试 APK。

- 四页均实机截图核对。欢迎页的救援主体和下部入口可见，点击「开始设置」到权限说明。规则页的双脸、剑光、透明规则行和「＋」可见，点击「＋」进入选择器。设置页可从底部导航打开，纯渐变背景和规则保护说明可读。
- 使用专用目标与临时禁用规则验证戒律长老插画、对白、恢复时间和底部留白。触摸未传给目标。系统底边手势和「返回桌面」按钮都回到启动器，禁用中重进目标会恢复遮罩。复测后恢复原有规则和授权数据，确认无障碍服务已连接。
- Android 15 模拟器以 360 × 640 dp 核对欢迎页和未授权规则页，短屏下可滚动查看规则。最终构建通过 `./scripts/check`。390 × 844 和 412 × 915 为原型参考尺寸。

### OPPO PLJ110 原生界面对照

2026-11-10，Android 16（API 36），ColorOS 16.1.0，构建 `PLJ110_16.0.10.502(CN01)`。设备 1080 × 2374，有效密度 480 dpi，字体缩放 1.0，手势导航。USB ADB 覆盖安装调试 APK，首次使用和禁用场景使用独立测试包与专用目标，原有规则保留。

- 对照原型查看欢迎、授权、空列表、已有规则、选择 App、表单、详情、两种确认页、设置、帮助、计时和隐私说明。验证全宽救援横幅保留人物与伸手位置，欢迎插画圆角完整，禁用插画显示完整。
- ColorOS 的中文粗体偏细。加粗原型中的强调文字，并将欢迎页改为右上角径向金光。覆盖安装后在 OPPO 复核欢迎页和规则列表。
- 普通页面使用真实状态栏和手势栏。禁用遮罩按系统栏及刘海区域留出边距，状态栏和手势指示器样式由 ColorOS 与当前目标窗口决定。最终手势边距版本在 OPPO 实机和 Android 15 模拟器上通过。OPPO 截图显示状态栏图标完整，按钮与屏幕底边留有手势区。遮罩拦截目标触摸，从底边上滑和点「返回桌面」均回到启动器。复测后恢复原有规则，确认无障碍服务已连接。

### v0.2 发布包验证

2026-11-10，Git 标签 `v0.2` 对应构建提交 `a02f85b`。运行 `./scripts/check` 和 `./gradlew assembleRelease` 均通过。使用独立发布密钥签名，APK 签名及 ZIP 对齐检查通过。包名为 `com.goule.prototype`，版本为 0.2（versionCode 2），最低支持 Android 11。

- Android SDK built for x86_64 模拟器，Android 15（API 35），系统构建 12368160。通过 ADB 在独立的全新模拟器安装发布 APK 并启动，确认主界面进入前台。
- 签名发布包的 OPPO 安装、无障碍授权和禁用遮罩见下方独立记录。

### OPPO PLJ110 v0.2 签名发布包

2026-11-10，OPPO PLJ110，Android 16（API 36），ColorOS 16.1.0，系统构建 `PLJ110_16.0.10.502(CN01)`。

- **安装通过。** 通过 USB ADB 全新安装 GitHub v0.2 的签名 APK，启动到欢迎页。核对设备上安装的 `base.apk`，SHA-256 与发布文件一致，版本为 0.2（versionCode 2）。
- **无障碍授权通过。** 从应用内说明进入系统无障碍设置，选择「已下载的应用 → 斩心魔」，打开开关并在系统弹窗选择「允许」。ColorOS 随后另弹安全提示，选择「保持开启」后服务保持绑定。应用在采集界面树期间曾显示「正在连接」，实际绑定和执行分别由系统服务状态及遮罩测试确认。
- **禁用遮罩通过。** 在应用内只给专用触摸测试 App 设置可刷 1 分钟、禁用 15 分钟。遮罩出现前，测试位置的点击产生目标 App 日志。首次计时期间设备按原设置自动锁屏。解锁后切回目标，等剩余额度耗尽，确认无障碍遮罩已绘制，目标仍在前台，同一位置的点击未产生目标 App 日志。通过系统 Home 返回桌面后遮罩撤下，重新打开目标时恢复遮罩。
- **数据与清理。** 测试开始时设备没有已安装或由包管理器保留的同包名调试版，本次没有卸载、覆盖或清除调试版数据。测试结束后移除本次全新安装的签名 APK 和触摸测试 App，确认测试服务已撤销，并恢复原来的 30 秒息屏设置。

### OPPO PLJ110 首版 0.2

2026-11-10，Android 16（API 36），ColorOS 16.1.0，系统构建 `PLJ110_16.0.10.502(CN01)`。通过 USB ADB 覆盖安装 0.2（versionCode 2），保留旧目标与授权，无障碍服务连接成功。用专用触摸测试 App 和 1 分钟可刷规则测试。

- 基线在目标前台约 12.22 秒，记录 12.37 秒。直接解锁回到目标的用例中，息屏至少 15 秒后，解锁后约 8.32 秒内使用量由 22.43 秒增至 31.00 秒，确认恢复计时，息屏时间未计入。
- 目标达到额度后，禁用页可见时点击未传到目标。打开系统设置时遮罩撤下，回到目标后恢复遮罩。「返回桌面」按钮成功离开目标。
- 从桌面重新进入通过。首次执行未观察到桌面，放弃该轮结果。复测仅重置临时测试规则的累计值，ColorOS 在强制停止应用后需要手动重开无障碍服务。再次息屏至少 15 秒，解锁后经「够了」回桌面。到桌面时累计仍为 37.83 秒，与息屏前最后一次保存一致。预计剩余 22.17 秒，从桌面重新打开后 22.73 秒出现禁用页，相差约 0.56 秒。
- 测试后移除临时测试规则，保留个人规则，确认无障碍服务仍已连接。来电和紧急入口待验证。

### OPPO PLJ110 能力原型


2026-11-10，提交 `a9d3055`，Android 16（API 36），ColorOS 16.1.0，系统构建 `PLJ110_16.0.10.502(CN01)`。通过 USB ADB 安装调试 APK 和触摸测试 App，ColorOS 要求在手机上确认安装。Goule 启动成功，应用内同意已记录。

初次开启无障碍服务时，系统记录了 Goule 的 `ACCESS_RESTRICTED_SETTINGS` 拒绝。随后在手机端开启服务，ADB 确认服务已连接。用户手测确认可出现冷冻页面。ADB 使用专用触摸测试 App 验证如下。

- 切到桌面等待 12 秒后回到目标，7.84 秒后出现遮罩，符合分段累计。
- 遮罩绘制完成后，目标 App 没有收到注入点击。「返回桌面」正常，冷冻期内重进会恢复遮罩。
- 复测中，遮罩消失约 0.33 秒后，目标 App 收到点击。下一轮用满后再次冷冻，进入系统设置时遮罩撤下。
- 初次注入点击仅以窗口已创建为条件，目标仍收到点击。另一次在遮罩刚撤下时注入点击，目标未收到。复测改为检查遮罩绘制状态和目标窗口焦点。

- 息屏用例未通过验收。首次息屏后 USB ADB 断开。一次重连后，目标在重新打开约 0.19 秒时即出现遮罩，预期应还有约 7 秒。控制唤醒路径的复测需要解锁，脚本未能确认回到桌面，因此息屏计时异常尚未定因。
- 关闭无障碍服务、来电及紧急入口仍待在 OPPO 上验证。

## 后台稳定性适配（2026-11-11）

新增通用系统设置入口、实时电池优化状态和 OPPO ColorOS 操作指引，服务显式不随任务停止，任务移除回调保存进度。当前 ADB 设备列表为空，以下真机项目均待验证，尚无划掉任务后持续执行的通过结论。

- OPPO PLJ110，Android 16、ColorOS 16.1.0 待重新连接，记录本轮实际系统构建及 APK 版本。
- 设置页进入后台运行页，打开两个系统入口并返回，检查状态刷新及短屏滚动。
- 目标禁用中单独划掉 Goule，重新进入目标应继续遮罩且截止时间不变。
- 目标尚有剩余额度时划掉 Goule，再进目标核对累计连续且不补算后台时间。
- 分别记录默认设置、允许后台活动、自启动、任务锁定后的单独划掉与一键清理表现。
- 锁屏后恢复、无障碍关闭再开启、系统重连及重启后检查规则和截止时间，恢复时等待新的前台事件。
- 检查系统设置、电话与紧急入口。强行停止单列记录，随后手动恢复服务。
- 其他厂商验证通用指引，系统入口缺失或被限制时检查回退与失败提示。

本轮自动检查通过 `./scripts/check`，使用本机缓存 Zulu JDK 17 和 Android SDK，包含仓库检查、Debug/Release 单元测试、Lint 与 Debug APK 构建。页面视觉与系统跳转未进行设备验收。

### OPPO PLJ110 后台运行实测

2026-11-11，OPPO PLJ110，Android 16、ColorOS V16.1.0，构建 `PLJ110_16.0.10.502(CN01)`，测试 APK 为提交 `092b290` 的 Debug 0.2（versionCode 2）。手机原先未安装本包，本轮全新安装并由用户完成安装扫描及无障碍授权。

- 设置 → 后台运行设置可进入，指引与两个按钮均可见。应用信息入口打开「应用详情」，耗电管理实际选项为「完全允许后台行为」「智能优化后台运行（推荐）」「限制后台运行」。电池优化入口打开「应用的电池使用情况」应用列表。依据实机名称修正文案。
- 用专用 `com.goule.fixture` 设置 1 分钟可刷、30 分钟禁用，实际累计到 60000 ms 后显示无障碍遮罩。
- 默认后台设置下，上滑移除 Goule 卡片后无障碍启用列表变为 null，重新打开目标没有遮罩，重新打开 Goule 显示暂停。此场景未通过。
- 在耗电管理确认「完全允许后台行为」后，系统电池优化白名单出现 Goule。用户重新开启无障碍服务，目标仍按原截止时间禁用。再次上滑 Goule 卡片，等待动画后确认最近任务无本应用任务，无障碍启用列表仍含 FreezeService。再进目标，遮罩存在且 `isOnScreen=true`、`isVisible=true`。持久化截止时间前后均为 `1794367610507`。点击测试区域后未观察到 `GouleFixture: Target clicked`，通过本次触摸拦截检查。
- 本轮未操作一键清理，未验收最近任务锁定、自启动独立开关、长期后台、重启或来电。无障碍状态采集时，uiautomator dump 会临时抑制服务并造成页面状态及布局变化，核心禁用检查使用 dumpsys、截图及日志，避免据该临时状态误判。
- 本轮暂将息屏时间由 30000 ms 改为 600000 ms，结束恢复原值。移除专用测试 App 及唯一临时规则，保留后台允许设置供继续使用。修正文案后再次运行 `./scripts/check` 通过。

清理测试规则时强行停止测试进程以避免内存快照覆盖磁盘，重新打开后请求用户重新启用服务。最终规则列表已核对为空。

用户完成恢复后，ADB 确认无障碍已启用且服务已绑定，测试 App 已不存在、规则列表为空、息屏时间恢复 30000 ms。

### 双步骤引导检查（2026-11-11）

OPPO PLJ110，Android 16、ColorOS 16.1.0，构建 `PLJ110_16.0.10.502(CN01)`，Debug 0.2 覆盖安装，保留现有戒律。已核对设置页「允许后台运行」说明、强调按钮、后台页第 2 步说明、实际电池豁免状态和返回戒律入口，滚动操作正常。uiautomator 临时抑制无障碍服务，采集内容中的「正在连接」不作为实际服务失效结论。

`./scripts/check` 通过。欢迎页及第 1 步的全新安装视觉、首次同意/稍后、授权往返与首次继续添加心魔分支尚待设备复测，本轮未清除用户数据。

### 全新安装双步骤引导复测（2026-11-11）

OPPO PLJ110，Android 16、ColorOS 16.1.0，系统构建 `PLJ110_16.0.10.502(CN01)`。使用 `79f96fe` 源码复制出的独立 Debug 测试包 `com.goule.prototype.onboardingtest`，仅更改 applicationId 与显示名「斩心魔引导测试」，版本 0.2。正式包数据未清除。

- 全新安装欢迎页显示两项设置提示，第 1 步显示无障碍用途和后续后台运行说明。
- 暂缓分支通过，点击「稍后」进入第 2 步，服务显示未开启、电池优化未豁免，点击「继续添加心魔」进入 App 选择器。
- 卸载并全新安装测试包后，同意分支成功打开系统无障碍设置。用户手动开启测试服务，ADB 确认其在已启用列表，返回应用后观察到 App 选择器。此次系统返回后的瞬时页面未直接采集，不能单独据此确认第 2 步是否曾被跳过。
- 从应用设置进入第 2 步复核，截图确认服务已连接，应用信息入口准确打开测试包详情，耗电管理可开启「完全允许后台行为」。返回后截图确认电池优化状态由未豁免变为已豁免。第 2 步内容与继续按钮可见。
- 末次点击继续后 UI dump 为空，未将该次采集记为通过，继续跳转的有效证据来自前面的暂缓分支。
- 测试包已卸载，正式包服务仍处于系统启用列表。未修改正式包戒律或后台设置。完整同意分支的系统返回到第 2 步仍需补充直接观测。

### 设置按钮视觉统一（2026-11-11）

OPPO PLJ110，Android 16、ColorOS 16.1.0，系统构建 `PLJ110_16.0.10.502(CN01)`，Debug 0.2 覆盖安装。截图核对设置页两项金色开启按钮样式一致，帮助按钮与背景文字可区分；点击后台运行按钮进入指引页，电池优化和应用信息按钮均有底色及边框，文字与底部返回戒律按钮完整可见。服务截图显示已连接。`./scripts/check` 通过。未重复首次全新安装或禁用逻辑用例。

### 配置状态与隐藏测试入口（2026-11-11）

OPPO PLJ110，Android 16、ColorOS 16.1.0，Debug 0.2 覆盖安装。设置页显示服务未开启、电池未豁免、厂商待用户确认。长按设置标题成功返回欢迎页并保留状态；欢迎进入权限说明，点稍后进入后台指引；滚动可见厂商确认与重新检查按钮；缺项时点继续仍停留配置页。未操作用户系统开关或确认标记。`./scripts/check` 通过。三项完成后的继续、确认撤销和首次完整授权往返仍待复测。

### 统一设置引导复核（2026-11-11）

OPPO PLJ110，Android 16、ColorOS 16.1.0，Debug 0.2 覆盖安装。截图确认设置页两项均采用标题、状态与去设置按钮，状态齐全时显示必要设置已完成。分别进入无障碍及后台引导，核对状态、用途、步骤、打开系统设置按钮和继续操作。无障碍「遇到问题」可展开，后台「更多稳定性设置」可展开，首屏不再同时铺开可选设置。已确认后台状态明细为电池优化已豁免、厂商设置用户已确认。uiautomator 抑制服务造成的瞬时正在连接不作为服务故障。`./scripts/check` 通过。本轮未重新执行完整首次授权、缺项恢复或修改系统权限。

### 首次必配门禁（2026-11-11）

OPPO PLJ110，Android 16、ColorOS 16.1.0，Debug 0.2 覆盖安装。长按设置进入欢迎，点击开始后显示第 1 步，页面其余内容复用设置入口；UI 采集显示服务正在连接时下一步 enabled=false，页面无稍后或跳过。uiautomator 临时抑制服务，因此此项仅确认未连接 UI 门禁，不作为真实服务断开结论。`./scripts/check` 通过。首次未完成标记、重启与完整授权返回尚待独立全新安装复测。

### 自动后台状态与完整首次验收准备（2026-11-11）

状态更新后 `./scripts/check` 通过，独立测试包 `com.goule.prototype.onboardingtest` 构建成功，源自本轮修改，显示名为斩心魔引导测试。OPPO PLJ110（Android 16、ColorOS 16.1.0）安装扫描完成，系统要求锁屏密码确认，等待用户操作。未安装成功前不记完整首次引导通过。待验证服务未开启时下一步不可用、授权返回第 1 步并刷新、后台未设置时继续不可用、开启完全允许后台行为后自动显示已设置、继续进入选择器及重启/返回不能跳过缺项。

### 完整全新安装双步骤与门禁通过（2026-11-11）

OPPO PLJ110，Android 16、ColorOS 16.1.0，构建 `PLJ110_16.0.10.502(CN01)`。独立 Debug 测试包 `com.goule.prototype.onboardingtest`，显示名「斩心魔引导测试」，源码对应 `c500ae2`，仅包名及显示名不同，0.2/versionCode 2。用户完成安装密码确认及无障碍授权。正式包数据未清除。

1. 全新启动欢迎页无配置说明，点击开始进入第 1 步，未设置时下一步 enabled=false，无稍后/跳过。返回仅回欢迎，停止测试进程后重开仍是欢迎，未提前写入完成标记。
2. 点击同意并打开系统设置，用户开启测试服务后按系统返回，直接截图确认测试 App 停留第 1 步并显示已设置。此轮补齐了此前未直接观察的授权返回路径。
3. 点击下一步进入第 2 步，后台未设置、电池优化未设置，继续按钮 enabled=false。系统返回仅回第 1 步，不能越过后台步骤。
4. 重新进入第 2 步，通过应用内系统入口打开测试包应用信息，耗电管理选择完全允许后台行为并确认。按系统返回后截图确认自动刷新为已设置，明细为电池优化已设置、后台限制未受限制，没有手动确认按钮。
5. 继续按钮变为可用，点击后直接进入添加心魔选择器。磁盘规则配置 welcomed=true，表明完成标记在两步完成后保存。
6. 卸载独立测试包清理测试设置，正式包无障碍仍在系统启用列表。未新增或修改正式包戒律。此前 `./scripts/check` 已通过，本轮仅追加验收记录。

结论为此机型本轮完整首次引导及上述缺项门禁通过。系统全机重启、更多型号以及系统设置在后台变更等场景未纳入本轮。

### 欢迎页构图平衡（2026-11-11）

OPPO PLJ110，Android 16、ColorOS 16.1.0，Debug 0.2 覆盖安装。通过长按设置标题打开欢迎页，截图核对标题下移、主体放大上移、左侧手机完整保留，示例卡片减薄和按钮底部位置。首次截图发现手机裁切，已调整图像横向位置后重建复测。`./scripts/check` 通过，最终版本已安装并停留欢迎页供用户查看。短屏与大字号未在本轮重新验证。

2026-11-11 在同台 OPPO PLJ110（Android 16、ColorOS 16.1.0）复核欢迎主体比例 1.24 版，Debug 0.2 覆盖安装成功，截图确认握手与手机完整可见、文字和按钮无覆盖。自动检查通过，短屏及大字号未复测。

### 欢迎页字体实机（2026-11-11）

OPPO PLJ110，Android 16、ColorOS 16.1.0，Debug 0.2 覆盖安装，通过长按设置标题打开欢迎页。截图确认马善政标题两行完整，霞鹜文楷文案和按钮显示正确，时长数字保持原字体。字体子集 cmap 覆盖所有对应固定文字，OFL 及版权随包保存。`./scripts/check` 通过。本轮未复测短屏与大字号。字体来源为 google/fonts 的 ofl/mashanzheng 与 lxgw/LxgwWenKai 的 fonts/TTF/LXGWWenKai-Medium.ttf，下载于 2026-11-11。

欢迎页大标语已合入生成插画 welcome_composed.webp，使用粗笔行书，保留透明语义标题供无障碍朗读。底部仍为原生文案与操作。2026-11-11 OPPO PLJ110（Android 16、ColorOS 16.1.0）覆盖安装并截图核对字形、手机、握手及底部按钮均完整；短屏与大字号未复测。图片按完整窗口铺展，不再使用双层主体叠加。

2026-11-11 欢迎标语按约 70% 目标重新生成缩小版，校正水平排列及「滑」字上扬。OPPO PLJ110（Android 16、ColorOS 16.1.0）Debug 0.2 覆盖安装并截图核对，标题、人物和底部内容完整，`./scripts/check` 通过。生成式调整为视觉近似比例，短屏与大字号未复测。

2026-11-11 按用户重新指定的早期样图恢复标语字形、比例与位置，基于参考图清理系统和底部 UI 后作为背景，底部文案及操作仍原生渲染。OPPO PLJ110（Android 16、ColorOS 16.1.0）覆盖安装与截图核对通过，`./scripts/check` 通过。生成式清理可能带来背景纹理细节变化，短屏和大字号未复测。

2026-11-11 戒律列表页首两行标语改为透明底书法字图，保留道心浅青与斩断金色，带无障碍朗读描述。OPPO PLJ110（Android 16、ColorOS 16.1.0）Debug 0.2 覆盖安装并截图确认字图无矩形底色、标语与添加按钮完整可见，列表行布局正常。`./scripts/check` 通过，短屏及大字号未复测。

## v0.3 发布包验证

2026-11-11，版本 0.3/versionCode 3，最低 Android 11。`./scripts/check` 与 `assembleRelease` 通过。发布 APK 使用已有发布密钥签名，apksigner 验证与 zipalign 检查通过，证书 SHA-256 与 v0.2 下载发布包一致。APK 元数据核对包名 com.goule.prototype、版本和最低 SDK 均正确。附带 SHA-256 校验文件。本次签名发布包尚未真机安装，源码调试及独立包的 OPPO 结果见前文。

## v0.3.1 后台检测修复验证

2026-11-11，OPPO PLJ110，Android 16、ColorOS 16.1.0。v0.3 签名发布版实际复现耗电管理完全允许后台行为单选项为 checked=true，但系统 deviceidle 豁免名单无该包。此前将这两者等同的判定不成立。

v0.3.1/versionCode 4 使用原发布密钥签名，覆盖 v0.3 安装成功。系统后台未受限、未豁免时页面显示待确认；根据已核对的系统开关点确认后显示已设置，继续按钮可用并进入添加心魔。无卸载或清数据。新增纯 Kotlin 单元测试覆盖所有豁免/确认组合、明确后台限制始终阻止继续，接入现有 Gradle 单元测试，`./scripts/check` 与 assembleRelease 通过。

2026-11-11 用户反馈当前 OPPO 受限设置入口需要先进入权限管理，已据此修正帮助文案。`./scripts/check` 通过。本轮未重新开启受限设置或进行真机路径复测，现有 v0.3.1 发布附件未更新。

## v0.3.2 发布包验证

2026-11-11，0.3.2/versionCode 5。修正 OPPO 帮助为应用信息 → 权限管理 → 右上角菜单 → 允许受限制的设置。相同功能修正构建（版本号仍为 0.3.1）已在 OPPO PLJ110（Android 16、ColorOS 16.1.0）使用发布签名覆盖安装成功。本次版本号更新包已通过 `./scripts/check`、assembleRelease、签名及对齐校验，未重复手机安装与受限设置路径验收。

2026-11-11 根据用户 v0.3.2 截图确认，系统豁免已满足时清除手动确认不会改变完成状态。已将操作限定为未豁免且有手动记录的场景，改名重新核对并补充 Toast。`./scripts/check` 通过，本轮未安装或发布，真机交互未复测。

## v0.3.3 发布包验证

2026-11-11，0.3.3/versionCode 6，构建、单元测试、Lint、发布签名与 ZIP 对齐检查通过。发布包附 SHA-256 校验。后台确认入口此前功能构建已安装 OPPO，本轮版本包安装结果另记。

v0.3.3 签名发布包已在 OPPO PLJ110（Android 16、ColorOS 16.1.0）覆盖安装成功，ADB 核对 versionName 0.3.3/versionCode 6，启动成功。安装后无障碍服务未开启，需用户重新开启。该记录只覆盖安装和启动，未重复禁用流程。

## 后台状态假阳性修复（2026-11-11）

用户反馈当前 OPPO 安装版本 v0.3.3 在「智能优化后台运行」下仍显示已设置。本轮未重新采集系统单选状态，不将用户报告写成自动化复现通过。检测实现审查确认电池豁免可绕过用户确认，已移除此分支。

纯 Kotlin 回归覆盖：无系统限制但未确认不能继续；明确核对且无系统限制可以继续（不依赖电池豁免）；系统明确限制时无论确认与否均拦截。测试由 `./scripts/check` 的 Gradle 单元测试执行。界面全部读取新确认键，旧版记录不参与判断；系统跳转成功清除新确认，显示用户确认而非实时检测成功。

`./scripts/check`（单元测试、Lint、Debug 构建）通过。ADB 检测到 OPPO PLJ110；本轮未覆盖安装或变更手机后台设置。Android 16 / ColorOS 16.1.0 上待验证：升级后旧确认失效、电池豁免下待确认与门禁、智能优化/完全允许两选项核对、返回后的确认重置、明确受限拦截。未发布新版本。

2026-11-11，OPPO PLJ110（Android 16、ColorOS 16.1.0），源码 aa5e008 修复构建使用既有 Release 签名覆盖安装成功，无卸载或清数据。版本仍为 0.3.3/versionCode 6，未更新 GitHub Release。assembleRelease、签名与 ZIP 对齐通过；手机安装 base.apk 的 SHA-256 与本地签名 APK 一致，启动成功，FreezeService 仍在 Enabled services 中。本轮仅验证安装和启动，未代用户确认完全允许后台行为，也未将后台配置交互记为已通过。

## 可选后台指引验证（2026-11-11）

后台完成状态与门禁按用户要求移除，不读取系统电池/限制状态或用户确认记录。源码复核：设置页后台条目无状态标签；后台页仅两组说明和入口，无确认、重置或折叠；setupMissing 仅依赖无障碍连接，故后台设置不影响添加及继续。删除已废弃 BackgroundReadiness 及对应测试，原有规则/计时等单元测试继续由 `./scripts/check` 执行。

`./scripts/check`（仓库检查、单元测试、Lint、Debug 构建）通过。当前检测到 OPPO PLJ110（既有记录 Android 16 / ColorOS 16.1.0），本轮未安装、未修改设备设置，不能将源码复核作为真机通过。待测：首次无障碍授权后不操作后台即可继续；两个系统入口往返后无确认状态；已有戒律添加不因后台设置受阻；无障碍未连接仍拦截添加。

2026-11-11 安装核对：OPPO PLJ110（Android 16、ColorOS 16.1.0）成功覆盖安装 63d36ac 的可选后台指引 Release 构建，沿用既有签名及 0.3.3/versionCode 6。assembleRelease、签名校验、ZIP 对齐通过；设备 base.apk SHA-256 与本地签名包完全一致，启动成功，FreezeService 在 Enabled services 列表。未卸载或清除数据，本轮未复测页面交互或全新安装流程。

### OPPO F02 定位与恢复提示

2026-11-11，OPPO PLJ110，Android 16、ColorOS 16.1.0，正式 0.3.3/code 6。原版本重启对照（bootCount 7 → 8）确认首次解锁前授权仍在，服务进程启动后由 `OplusRiskAccessibilityController` 以 `sideload_app` 原因撤权；无应用崩溃。已确认完全允许后台行为和二次保持开启，仍不能避免撤权。详细时间线见 [F02 根因复核](oppo-regression-2026-11-11.md#f02-根因复核2026-11-11bootcount-7--8)。

用户选择完善恢复交互，未采用自动重授权方案。新构建沿用发布签名覆盖安装，无卸载、无清除正式数据，版本仍 0.3.3/code 6，未发布 GitHub Release。设备检查覆盖服务关闭后的首页「戒律已暂停」、设置/权限页「已关闭」、暖色状态和重新开启入口，权限页包含 OPPO 撤权及二次提醒说明。实际走通权限页 → 系统开启 → 返回，完成延迟的保持开启确认后，首页暂停提示清除，设置页截图为已设置，Bound services 中确认斩心魔绑定。安装包 SHA-256 与本地一致（56392500df60fa0f4a343e1237d93f1c980f8129850aa833611a3f255ced0101）。`./scripts/check`、Release 构建和签名校验通过。uiautomator 采集时暂时正在连接，以采集结束后的截图及绑定核对为准；本轮不重复完整首次流程、计时引擎或其他品牌测试。

### v0.3.4 发布包验证

2026-11-11，0.3.4/versionCode 7，包名 com.goule.prototype，最低 Android 11。`./scripts/check`、assembleRelease、apksigner 和 zipalign 校验通过；下载 v0.3.3 发布资产并比对证书 SHA-256，一致。APK SHA-256：`77abf3db1b9460bb96d371eda233df2c3ef2847503344f983f8d255c8ff84e4c`。发布附独立校验文件和更新说明。版本号更新包未另装手机，相关功能真机结果见上一节及完整回归矩阵，失败项未宣称修复。

## ColorOS FastClear 导致无障碍中断（2026-11-18）

OPPO PLJ110，Android 16/API 36，ColorOS V16.1.0，系统 `PLJ110_16.0.10.502(CN01)`。当前实际安装为 11 月 17 日 10:54 的 0.3.16/code 19 亲妈催婚功能构建，未安装仓库 v1.0.0。本次只读采集，未启动/强停/安装、开关权限或修改戒律，未使用 uiautomator。

06:35:45.256 系统 events 明确 `o-kill(4013)` / `FastClear[4013]` 杀死本包 pid=28181，06:35:45.447 进程死亡，退出历史 06:35:45.474 为 reason=13 / OTHER KILLS BY SYSTEM；不是本应用 crash/ANR 的证据。用户明确当时未清理、正常使用，确认系统自动清理后异常断连。06:56:56 采集时 Enabled 含 FreezeService、Bound/Binding 空、Crashed 含服务；主界面新进程已运行仍未恢复。boot_count=9，连续开机约 6 天 8 小时，未发生本轮重启撤权。清理前持久日志仍 connected=true，无服务正常收尾；相机使用与内存状态变化仅是触发线索，具体厂商策略未确定。

当前服务未恢复，末次只读绑定复核结果相同；未验收恢复后数据保持或自动清理修复，未实施产品增强或发布。原始资料在本机 `/Users/caiyuanrui/.cache/goule-android/service-stop-20261118/`，不提交整份日志。`./scripts/check` 通过：Python 46 项、Gradle Debug/Release 单元测试与 Lint 检查、Debug/AndroidTest APK 构建（现有 Android 任务大部分复用缓存）；本轮没有运行设备回归，不据此宣称恢复或 FastClear 修复通过。详见 [完整诊断及增强建议](service-stop-diagnosis-2026-11-18.md)。

## 最近任务延迟收尾漏拦截（2026-11-25）

只读采集 OPPO PLJ110（Android 16、ColorOS V16.1.0）正式包服务持久日志，原始系统资料仅保留本机私有缓存，未改戒律、权限或操作抖音。08:10:20.166 收到目标恢复后的 launcher changes=0，决定 DEFER；08:10:20.295 执行 launcher settle 并撤下遮罩，此时抖音 usedMs=300000、remainingMs=0、blockedUntil 未变。08:13:58 与 08:16:51 同类路径再次出现。成功路径 08:17:24.986/989 显示收尾事件约在目标恢复 480 ms 后才到，PANE_DISAPPEARED 会取消待退出回调。证据确认至少这条漏拦截由 120 ms 提前撤罩造成，不把其他未知系统窗口一并归因。

修复：关闭前等待改为 1500 ms，关闭后 120 ms 尾随事件窗口保持；新窗格出现、其他 App、锁屏与断开保留退出路径。生产 Service 重放新增三次 450 ms 延迟收尾、遮罩与原截止保持、缺失关闭事件的有界退出检查。`JAVA_HOME=<本机 JDK 17> ANDROID_HOME=<本机 SDK> GOULE_UI_DEVICE=emulator-5554 ./scripts/check` 通过：Python 46 项、Debug/Release 各 68 项零失败，Lint/构建通过；Android 15/API 35 模拟器十二组全部 PASS，包含新增生产 Service 延迟收尾重放、连续三次返回遮罩/截止保持及缺失关闭信号退出。首次检查因拆分测试代码后的局部变量作用域编译失败，已修正并完整重跑通过。正式手机尚未安装修复包，真实 ColorOS 动画与连续返回仍待验证；本轮未发布。

## 最近任务修复包保留数据安装（2026-11-25）

用户明确要求安装。08:29:21（北京时间）在 OPPO PLJ110（Android 16、ColorOS V16.1.0）通过 `adb install --no-incremental -r` 成功保留数据覆盖安装：从 1.0.0/code 21 更新为 `d9cfcdc` 的 1.0.1/code 22 最近任务延迟收尾修复功能构建，与既有 v1.0.1 发行资产不同，本轮未发布。

Release 构建通过，APK 嵌入源码、包名、非调试标记、Android 11 最低版本、原证书 SHA-256 与 16 KiB ZIP 对齐检查通过。签名过程首次因同一单行密码文件被重复读取而失败，随后改为环境变量传入并验证成功，未更换证书。安装前启动本应用取得稳定空前台快照；安装后回拉 APK 与本地 SHA-256 相同，appId/首次安装时间保持，三条戒律配置及当前周期参数、usedMs/remainingMs/cycleStart/blockedUntil/eligible 逐项完全一致。启动 Status: ok，服务 connected=true consent=true，journal failures=0。

私有安装资料位于本机 `~/.cache/goule-android/recents-install-20261125/`，未提交设备原始日志、密钥或密码。未卸载/清数据，未修改戒律/主题或开关权限，未操作抖音验证真实动画。前轮 Android 15/API 35 事件重放通过不替代本轮 ColorOS 自然连续返回验收，该项待用户使用确认。

## 禁止画面完整回归与窗格误判修复（2026-11-25）

安装 `d9cfcdc` 后用户再次报告卡片上误弹遮罩、实际返回抖音却可用。新持久日志中反复出现目标非全屏 CustomRelativeLayout 的 PANE_DISAPPEARED → overlay attached → launcher DEFER → 1500 ms 后撤罩；随后目标真正 PANE_APPEARED / MainActivity 返回时最近任务状态已重置，launcher 收尾 PASS 再撤罩。另有成功请求最近任务后、启动器首个事件前的旧目标 Activity 回调重新加罩。说明上轮仅延长等待的修复不完整，不能继续按已解决处理。

修复限定于事件语义：目标窗格消失只复核已有前台，不建立新前台、不取消待处理的 launcher 回调；最近任务请求成功后先等启动器响应再接受目标恢复。失败的系统操作仍保留原遮罩，读取窗口内容能力仍关闭，不改规则/截止或扩展系统窗口白名单。启动器诊断补记类名和全屏标记，不读取文字/节点。

旧生产事件处理路径配合新重放明确 FAIL：launcher PANE_APPEARED 后目标 PANE_DISAPPEARED 仍导致 foreground=fixture.timing、overlay 非空。基线首次在模拟器启动阶段的 instrumentation 未启动，停止独立包后重试取得该失败；未把启动问题当作回归结论。

| 场景 | 检查方式与验收 |
| --- | --- |
| 额度未满 / 自然用满 | 服务实际 tick：前 300 ms 无罩，用满后自动加罩，累计量不超过额度 |
| 禁用期重入 / 重复事件 | 重复进入保持禁用截止、已用量和现有主题 |
| 最近任务停留 | 重放真实目标窗格消失事件，停留超过 1500 ms 始终无罩 |
| 点击卡片返回 | 出现标记与无出现标记的 Activity 返回均恢复罩，延迟 450 ms 的收尾不撤罩，连续三轮 |
| 点击关闭 App 的过渡 | 复用生产成功回调，首个启动器响应前旧 Activity 不重新加罩 |
| 缺少关闭信号 | 有界退出仍执行，目标消失事件不得取消退出回调 |
| 桌面 / 未配置 App / 设置 | 离开立即撤罩，再进禁用目标恢复，原截止不变 |
| 多目标切换 | A 禁用/B 可刷不串罩；A 到期/B 仍禁用，各自状态独立 |
| 禁用自然到期 | 当前罩按服务真实延时自动撤下，新周期可用 |
| 系统弹窗 / 通知栏 | 音量/已确认弹窗保留底层目标，未知系统窗口/通知栏清空；ColorOS 被动容器保留已有目标但不复活空前台 |
| 息屏 / 唤醒 / 断开 | 屏幕回调/中断清空，旧事件和窗格消失不复活前台，新事件重新确认 |
| 横竖屏 / 大字号 / 系统安全区 | 全主题布局与按钮回归，背景覆盖/手势安全边距保持 |
| 规则恢复 / 编辑 / 跨周期 | 规则与存储单元回归覆盖固定周期、独立用量、编辑保留、恢复时钟与到期 |

自动结果：Python 46 项、Debug/Release 各 69 项、Lint/构建通过；Android 15/API 35 模拟器完整十二组 PASS，含表中新增生产服务生命周期回归。OPPO PLJ110（Android 16、ColorOS V16.1.0）独立包最终前台/遮罩专项 PASS。OPPO 整套第一次到 BlockThemeChecks.kt:21 的图片导入尺寸检查失败，未改图片产品代码，也不记整套真机 PASS；随后不显示 Activity 的专项被系统以 Cached(nirvana)[(instrumentation)] 终止，改为让独立测试 Activity 保持可见后重跑专项通过。锁屏/断连/到期在专项中为屏幕回调/中断与短截止事件重放，不能代替真实重启、来电或权限开关验收。正式包安装及实际往返结果续记。原始资料仅保留本机 `~/.cache/goule-android/recents-recurrence-20261125/`，不提交设备原始日志、图片或签名资料。


正式包安装与实际回归：08:44:16（北京时间）将干净 `4cf18f6` 原签名 Release 保留数据覆盖安装至同一 OPPO PLJ110 / Android 16 / ColorOS V16.1.0，仍为功能构建 1.0.1/code 22，不等同已发布 v1.0.1 附件。Release 构建、原证书、源码、非调试/版本、16 KiB ZIP 对齐通过；回拉 APK 摘要与本地一致，appId/首次安装时间和三条戒律配置、周期快照、用量/剩余/截止逐项一致，启动成功、服务连接、journal failures=0。

直接在正式抖音上执行六轮真实操作（随机主题保持）：点击关闭 App 主按钮→等待最近任务→点击右侧抖音卡片。每次卡片及返回阶段均连续采样八次、间隔 250 ms，卡片阶段 foreground/overlay 始终为空，返回阶段抖音 overlay 持续存在并超过旧的 1500 ms 退出点，每轮截止相同；首轮截图人工核对卡片/遮罩，后五轮保留截图并逐次核对服务状态。持久日志同一新会话记录六次最近任务动作、十次目标消失事件被忽略，不再由它们建立前台。

八项相邻真实检查也通过：遮罩返回桌面按钮、桌面重新进抖音、进入系统设置、设置返回抖音、小红书禁用罩、两受限 App 切回抖音、系统 Home、本应用戒律页。每项状态稳定后再等待 1700 ms 复核，没有迟到事件重新加/撤错罩；全程未点目标内容、改规则/主题或开关权限。结束时三条戒律全部状态与安装后快照逐项相同，Enabled/Bound 正常，Crashed 空。独立包 `com.goule.uiverify(.test)` 已卸载，手机停在正式戒律页；未清理其他最近任务或更改系统设置。

本轮未实际锁屏/解锁、来电、重启或等待正式长周期自然到期，这些边界采用独立包屏幕回调/中断/短截止及规则回归，不记新的真实全路径通过。OPPO 完整图片导入尺寸断言失败仍单列待查，不能把本次遮罩专项与十二组模拟器 PASS 表述为整套 OPPO 全通过。本轮未新建发行版。


## OPPO 图片导入测试源初始化与主题回归（2026-11-25）

设备为 OPPO PLJ110（Android 16、ColorOS V16.1.0）。旧用例的 `draftImage` 实际为 null，`hasDraft=false`，直接读取固定测试 URI 得到 `FileNotFoundException: No content provider: content://com.goule.uiverify.images/valid`；安装登记中 provider 存在，`.test` APK 却仍为 `stopped=true`。这说明失败在取得测试输入之前，不能据尺寸断言认定生产解码或缩放失败。

对照：保持测试客户端 Activity 可见仍失败，补 Debug provider queries 也失败，故撤回该声明；测试客户端跨包启动来源又被 OPPO 的 ActivityStartController 忽略。测试部署端显式启动来源 APK 后，来源为 stopped=false，同一生产图片导入与渲染检查 PASS。Instrumentation 的代码执行在目标包进程，不等于启动独立 `.test` provider APK。本机结论限于此机此路径；Android 关于 stopped 状态与直接/间接启动的通用说明见 [官方文档](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state)。

最终修复仅在测试层：新增 androidTest 内的无界面初始化 Activity；`scripts/check-ui` 安装两包后从部署端显式启动来源并核对 Status: ok，再执行 instrumentation；每次导入预检 provider，来源不可用时给出明确测试环境错误。图片尺寸断言补上实际值和导入结果，保留 1440×720 的验收要求；图像处理不改、不放宽断言、不新增 Release 权限/queries。导入/渲染阶段保留可见测试 Activity，避免 ColorOS 将只有 instrumentation 的进程按缓存清理。新增 themesOnly 入口方便对全新独立包复测迁移、导入/管理/渲染与缓存生命周期；完整入口仍执行这些检查。

重复诊断中复用旧测试数据会触发迁移用例的前置状态断言，系统也拒绝 shell 的 `pm clear`；最终按标准脚本卸载并重装独立包取得干净状态，没有对正式包清数据或改规则/主题。中间 Kotlin 因可变 Activity 捕获失去智能类型转换而编译失败，改用固定的 timingActivity 引用后重编译通过。最终执行结果见下文；私有日志和渲染图位于本机 `~/.cache/goule-android/image-import-20261125/`，不提交原始设备材料。


最终验收：`GOULE_UI_DEVICE=emulator-5554 ./scripts/check` 通过，Python 46 项、Debug/Release 各 69 项零失败，Lint、Debug/测试构建通过；Android 15/API 35 模拟器与 `GOULE_UI_DEVICE=3B168R00ANY00000 ./scripts/check-ui` 的 OPPO PLJ110 / Android 16 / ColorOS V16.1.0 各完整十二组全部 PASS。两端均从脚本要求的干净独立包开始，部署端初始化返回 Status: ok，不依赖应用跨包启动，也没有放宽原尺寸和错误保留断言。前轮 OPPO 图片导入失败待查项至此关闭。

相关功能通过：有效 PNG 归一化为 1440×720；第二张/替换图片、保存后旧文件清理、编辑放弃保持旧图、损坏/拒绝读取/超过20 MB 均保留已保存主题；多个个人主题、固定/随机池、删除当前与最后项的回退、旧图片与旧模式迁移、主题预览、横竖屏/大字号及按钮回调、后台释放/返回重载/草稿保留和在用遮罩图片不被 recycle 均通过。整套还包含前台/最近任务/额度用满/到期/多 App 独立回归，而非只复测失败断言。这里的导入使用独立合成图片 URI 和生产处理管线，未操作用户的真实照片或系统相册选择流程。

两端各回拉22份渲染与内存材料；人工核对 OPPO 个人替换图片（90×300 蓝色样本）遮罩的图片比例、文本及两个退出按钮，以及主题管理页无裁切。OPPO 个人图片缓存 trim 前4,147,200字节、trim 后0，在用遮罩仍可绘制，返回可重新载入；计数不等同进程 PSS 变化。调试期间新增可见测试宿主页曾与后续 startActivitySync 重复启动相撞而等待，已在导入渲染组结束后及时 finish、等待 idle 并解除引用；旧运行被停止后从干净安装完整重跑取得上述 PASS，不把人为停止的 Process crashed 记作产品崩溃。

`assembleRelease` 通过，Release 清单/DEX 不含 ThemeImageFixtureActivity 或测试 authority，`app/src/main` 与产品构建配置无改动。正式手机保持前轮原签名 `4cf18f6` 功能包，本轮无必要覆盖安装、无新发布，戒律/主题未手动修改。最终 Enabled/Bound 正常、Crashed 空，独立包及其 `.test` 已清理，手机停在正式戒律页。相比08:44安装快照，红果固定周期于08:47:34.060自然推进一轮、用量归零，抖音/小红书原禁用状态保持；未将正常周期刷新误报为测试改数据。


## 卡片过渡夹杂窗口导致偶发漏拦（2026-11-25）

用户确认本次为15:19–15:21的序列。OPPO PLJ110（Android 16、ColorOS V16.1.0）正式服务同一会话未断开、额度已用满，15:19:44.301 最近任务 ListView 出现后，44.302 的 other 窗口事件把 recents 状态 reset；45.845 目标 DetailFeedActivity 恢复、45.909 加罩，46.312 启动器收尾 PASS，46.314 撤罩，随后才收到 ListView 的关闭事件。15:21:13 的重试没有夹杂窗口，收尾 DEFER→IGNORE 正常保持；日志另有15:21:16与抖音14:55:18的“关闭信号先于目标恢复”顺序。不能把 launcher PASS 一律当作漏拦，其他真实桌面切换仍应撤罩；本轮以确认过的事件组合复现。

修复把前台与过渡元数据分开：非目标窗口始终清空前台/遮罩；只有未恢复过目标的卡片阶段保留元数据，并在下次目标恢复时检查最近启动器事件是否在既有1500 ms边界内。已恢复目标后再遇非目标窗口继续重置。关闭先于目标时暂存关闭标记，过时不保护后来的启动；关闭后的120 ms短尾随窗口不扩大。锁屏、断开、配置变化仍清空所有状态，目标窗格消失仍不能建立前台；不增加权限、读取窗口内容或记录未配置App身份，不改戒律/截止。

新生产Service重放已使旧生产路径明确FAIL：夹杂窗口后目标刚恢复，紧接收尾事件使 foreground=null、overlay=null。纯Kotlin增加夹杂窗口保留/已恢复后离开清空、过时状态失效、关闭先于目标恢复的检查，Service重放同时核对卡片无罩、目标持续有罩、截止保持、设置退出和旧元数据不得保护后来的无关启动。自动检查与设备结果续记，原始私有资料仅在本机 `~/.cache/goule-android/recents-intermittent-20261125/`。

自动验收：Python46项、Debug/Release各72项零失败，Lint/构建及 Android15/API35 模拟器完整十二组 PASS，包含新夹杂窗口/两种关闭顺序/失效及已有所有遮罩和图片回归。OPPO完整回归两次安装在手机 locked=1、Dozing 时被 OplusPackageInstallInterceptManager 以 OPLUS_ADB_INSTALL_CANCEL/-99 取消，未进入 instrumentation，不计产品回归失败或通过；已请求用户解锁后继续，正式修复包尚未安装。

用户解锁后，OPPO PLJ110 / Android16 / ColorOS V16.1.0 完整十二组全部 PASS，包含新增真实顺序重放、设置/桌面退出、失效、全部既有计时遮罩及图片用例；脚本已清理独立包。正式包原签名覆盖及实际快慢卡片往返结果续记。

正式 fc02525 中间构建于15:40:01原签名保留数据安装，前六轮真实快慢卡片往返通过；第七轮插入通知栏后失败，故未计十轮通过。15:42:27.635 目标恢复、27.737加罩，27.779收到 SystemUI 非全屏 FrameLayout 的 PANE_DISAPPEARED、27.783撤罩，启动器随后收尾已失去返回状态。该关闭来自本轮明确执行的通知栏展开/收起，不能当作未知窗口打开。继续增加精确的 OPPO 关闭容器例外，且必须已有目标和恢复后1500 ms内的卡片返回；通知栏关闭允许先于或晚于卡片关闭，启动器自身的120 ms尾随不扩大。过时返回、再次展开、混合标记、其他厂商或空前台均不保留/复活。新增纯Kotlin及生产Service正反例并重新完整验证，后续结果另记。


最终自动验收：Python46项、Debug/Release各74项零失败，Lint/Debug与测试构建通过；最终 `final-emulator-ui.log`（Android15/API35）和 `final-oppo-ui.log`（OPPO PLJ110 / Android16 / ColorOS V16.1.0）各完整十二组全部PASS，含夹杂窗口、先关闭后恢复、通知栏关闭在卡片关闭前/后、过时和空前台/再次展开的反例，以及旧计时、遮罩、主题、图片与内存回归。新增关闭例外只在确认目标恢复后的1500 ms内适用，启动器关闭后120 ms保护保持原值，正常Home仍立即退出；无新权限或窗口内容读取。

15:54:27（北京时间）最终 `4afcc6a` 原签名 Release（功能构建1.0.1/code22）覆盖安装成功。Release构建、包名/非调试/最低版本、嵌入源码、原证书、16 KiB对齐通过，安装后回拉APK与本地SHA-256相同，appId/首次安装时间、三条戒律配置/当前周期参数及usedMs/remainingMs/cycleStart/blockedUntil/eligible逐项一致，启动与服务连接正常。最终包不同于 fc02525 中间构建及既有发行附件，本轮未新建发行版。

最终包重新执行十轮真实抖音操作，五轮较快点回、五轮卡片停留后点回，第7/9轮在卡片中展开再收起通知栏：卡片/通知栏中无罩，点回后连续十次约200 ms间隔核对遮罩保持，超出旧1500 ms撤罩点，各轮截止一致，十轮全部通过。第7/9轮各实际命中一次 `closing ColorOS pane during recents return; retain confirmed target`，不是只测了未触发路径。首张卡片截图与最终禁用画面人工核对通过，图像及逐次采样仅在私有缓存。另六项真实检查（遮罩桌面按钮、桌面重入、系统设置、设置返回、系统Home、本应用页面）稳定后再等1700 ms复核，全部通过。fc02525 的前六轮与失败第七轮不计入这十轮。

最终三条戒律所有状态与15:54安装后的快照逐项完全相同，journal failures=0，Enabled/Bound正常、Binding/Crashed空。用户戒律/主题和系统权限未手工修改，未清理其他卡片；独立包已由脚本清理，手机停在正式戒律页。本轮未实际重启、来电或等到正式禁用长周期到期，这些边界沿用独立事件/短截止及规则回归，不记新的自然真机全路径通过。
