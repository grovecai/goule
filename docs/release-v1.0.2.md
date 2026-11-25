# 斩心魔 v1.0.2

- 4afcc6a fix: retain confirmed recents target through ColorOS pane dismissal
- fc02525 fix: retain fresh pending recents transitions across intervening windows
- 4bf30ba test: initialize image provider before OPPO theme regressions
- 4cf18f6 fix: distinguish departing panes from resumed apps and regress overlay lifecycle
- d9cfcdc fix: wait for delayed recents close before clearing overlay

## 下载与升级

[APK](https://github.com/grovecai/goule/releases/download/v1.0.2/goule-v1.0.2.apk) · [SHA-256](https://github.com/grovecai/goule/releases/download/v1.0.2/goule-v1.0.2.apk.sha256)

Android 11 及以上，版本 1.0.2/versionCode 23。沿用原发布签名，可覆盖升级并保留数据。

## 自动验证与边界

发布前执行项目检查、Release 构建、版本元数据、原签名证书及 ZIP 对齐检查；两端发布后回下载核对 APK 和 SHA-256 文件。自动构建不代表完成新的真机行为回归。

[既有验证与已知边界](https://github.com/grovecai/goule/blob/v1.0.2/docs/verification.md)
