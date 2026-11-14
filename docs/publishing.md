# GitHub 与 Gitee 发布

GitHub 为主仓库，Gitee 提供国内代码和 APK 下载。两端使用同一发布标签、发布说明、预发布状态、原签名 APK 与 SHA-256 校验文件，不为 Gitee 另行构建或签名。

## 一次性配置

- GitHub：`origin` → `git@github.com:grovecai/goule.git`。
- Gitee：`gitee` → `https://gitee.com/grovecai/goule.git`。新克隆的工作区执行 `git remote add gitee https://gitee.com/grovecai/goule.git`。
- 确认 Gitee 仓库已创建并可供公众访问；同步账号需要代码及发行版写权限。
- 在 [Gitee 私人令牌](https://gitee.com/profile/personal_access_tokens) 创建具有该仓库代码和发行版写权限的令牌，保存至 [GitHub Actions Secrets](https://github.com/grovecai/goule/settings/secrets/actions)，名称为 `GITEE_TOKEN`。不要写入仓库、远端 URL、命令参数或日志。
- 本机直接推送 Gitee 另需配置 Git 凭据或已获 Gitee 授权的 SSH 公钥；Actions Secret 不会自动配置本机认证。

## 每次发布

1. 按实施计划完成版本递增、发布说明、`./scripts/check`、Release 构建、版本元数据、原签名及 ZIP 对齐检查，提交并合入 main；推送 GitHub 与 Gitee，禁止强制覆盖远端历史。
2. 从干净发布提交构建一次签名 APK，命名为 `goule-vX.Y.Z.apk`，生成 `goule-vX.Y.Z.apk.sha256`（内容为 `64 位摘要  goule-vX.Y.Z.apk`）。创建并推送对应注解标签。
3. 在 GitHub 发布包含这两项附件的 Release，保持发布说明和预发布标记正确；草稿不对外同步。GitHub 发布完成或编辑后触发 [Gitee 同步工作流](../.github/workflows/sync-gitee-release.yml)。必须等两项附件上传完毕后再验收；若先发布再传附件导致流程失败，附件齐全后手动重跑。
4. 工作流先检查令牌能访问 Gitee 仓库且仓库公开，再从 GitHub 下载既有附件，校验 APK 摘要，将 main 和本次标签以普通推送同步至 Gitee，创建/更新发行版及附件。上传后通过不带令牌的公开链接回下载，逐字节核对 APK 和校验文件，将正文内的附件链接替换为 Gitee 实际下载地址。日志按阶段输出，外部命令单次最多等待 180 秒，API/附件请求超时为 120 秒。
5. 查看 [Actions 结果](https://github.com/grovecai/goule/actions/workflows/sync-gitee-release.yml)，核对两端 main/标签、发布说明及预发布状态。仅在成功后将实际 Gitee APK/校验下载地址加入 README，保留 GitHub 入口，并在 PROGRESS 和验证手册记录结果。失败时不可标为双平台发布完成。

## 补发和故障恢复

在 Actions 的 `Sync release to Gitee` 页面选择 `Run workflow`，分支选 main，填入已发布的标签（例如 `v0.3.13`）。也可在已配置 `gh`、GitHub `origin` 和 `GITEE_TOKEN` 环境变量的本机执行：

```bash
python3 scripts/sync_gitee_release.py v0.3.13
```

手动运行会同步 GitHub main 和指定标签；不会发布本地未提交或尚未推送的代码。重复运行会核对已有附件并补齐缺失附件，不重复上传。若已有同名附件内容不同，流程报错并保留原文件，先核查来源，勿直接覆盖。Gitee API 不提供草稿参数，创建后若附件上传中断，发行版可能暂时不完整；补发成功前不提供国内 APK 下载入口。

若 `GITEE_TOKEN` 缺失、仓库不存在/不可见、权限不足、远端有独立提交或下载失败，流程会失败并保留 GitHub 发布。修正实际原因后重跑；不要强制推送，也不要换签名重新打包。按 [Gitee 官方说明](https://help.gitee.com/repository/release/create)，普通项目单附件上限为 100 MB、仓库附件总容量为 1 GB，后续需留意容量。
