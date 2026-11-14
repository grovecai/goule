# 斩心魔 v0.3.14

新增每日有代码更新时自动发布及手动阶段发布；沿用原发布签名，自动生成校验文件并同步 Gitee。应用功能及既有真机边界沿用 v0.3.13。

## 下载与升级

[APK](https://github.com/grovecai/goule/releases/download/v0.3.14/goule-v0.3.14.apk) · [SHA-256](https://github.com/grovecai/goule/releases/download/v0.3.14/goule-v0.3.14.apk.sha256)

Android 11 及以上，版本 0.3.14/versionCode 17。沿用原发布签名，可覆盖升级并保留数据。

## 自动验证与边界

发布前执行项目检查、Release 构建、版本元数据、原签名证书及 ZIP 对齐检查；两端发布后回下载核对 APK 和 SHA-256 文件。自动构建不代表完成新的真机行为回归。

[既有验证与已知边界](https://github.com/grovecai/goule/blob/v0.3.14/docs/verification.md)
