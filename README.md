<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" width="96" alt="斩心魔图标">

# 斩心魔

**斩断无尽的下滑，守住道心。**

本想刷五分钟，回过神来已经半小时。下一次打开那个让你停不下来的 App 之前，先给它立一条戒律。你决定能刷多久、用满后歇多久，到了时间，戒律长老就会出场，帮你停下来。

**[下载斩心魔 v0.2 签名 APK](https://github.com/grovecai/goule/releases/download/v0.2/goule-v0.2.apk)** · Android 11 及以上 · [预发布说明与校验文件](https://github.com/grovecai/goule/releases/tag/v0.2)

## 一条戒律怎么生效

| ① 在戒律页设好时长 | ② 额度用满，禁用页挡住目标 App |
| --- | --- |
| <img src="docs/screenshots/rules.webp" width="260" alt="戒律页显示短视频演示的可刷时长、禁用时长和当前状态"> | <img src="docs/screenshots/blocked.webp" width="260" alt="戒律长老禁用页显示解禁时刻、关闭 App 和返回桌面按钮"> |
| 点右上角的「＋」添加 App。保存后，可以在这里查看每条戒律的时长和状态。 | 戒律长老出场，页面显示解禁时刻。点「回洞府」即可返回桌面。 |

以短视频 App 为例，给它设一条 **「刷 20 分钟，禁 30 分钟」** 的戒律。

- **刷多久，由你定。** 午休刷了 5 分钟，晚上再打开，还能刷 15 分钟。切到其他 App 或锁屏时，计时暂停。
- **额度用满，长老出场。** 禁用页盖住目标 App，并显示预计恢复时间。你可以返回桌面，其他 App 仍可照常使用。
- **到点自动恢复。** 30 分钟后可以再次使用，下一轮从零开始。短视频和社交 App 可以各设一条戒律，分别计时、分别禁用。

## 立下第一条戒律

1. **安装 APK。** 在 Android 手机上打开下载的文件，按系统提示安装。
2. **开启无障碍服务。** 打开「斩心魔」，点「立下第一条戒律」，阅读权限说明，再到系统设置开启服务。回到应用后，可以在「设置」里确认服务显示「已连接」。
3. **选一个心魔。** 挑一个最容易刷过头的 App，填入「可刷时长」和「禁用时长」，点「保存戒律」。

遇到系统的「受限制的设置」提示，可以在应用内查看「开启帮助」。在 OPPO 上，系统授权后可能再弹出安全提示，选择「保持开启」即可继续。

## 权限与隐私

无障碍服务用来识别前台 App 的切换，给你选中的 App 计时，并在用满额度时显示禁用页。窗口内容读取保持关闭，戒律和用量保存在手机上。你可以随时在系统设置关闭服务，戒律会暂停执行。

## 当前版本

v0.2 是预发布版。签名 APK 已在 OPPO PLJ110（Android 16、ColorOS 16.1.0）验证安装、无障碍授权和禁用遮罩。 [真机验证记录](docs/verification.md#oppo-plj110-v02-签名发布包)

## 开发与验证

在仓库根目录运行 `./scripts/check`，需要 Bash、Git、Python 3、JDK 17 和 Android SDK 35。查看 [Android 设计](docs/android-design.md)、[交互设计](docs/interaction-design.md)、[实施计划](docs/implementation-plan.md)和[验证手册](docs/verification.md)。
