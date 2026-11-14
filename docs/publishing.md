# GitHub 与 Gitee 发布

GitHub 是唯一日常推送和发布入口，本机只推 GitHub。Actions 在 main 更新时自动同步代码，在 GitHub Release 发布/编辑时同步发布标签、发布说明、预发布状态、原签名 APK 与 SHA-256 校验文件到 Gitee，供国内用户下载。不为 Gitee 另行构建或签名。

Gitee 自带镜像按 [官方说明](https://help.gitee.com/repository/settings/sync-between-gitee-github) 同步分支、标签和提交；发行版正文及二进制附件由本项目 Actions 单独处理，无需另设 Gitee 镜像或本机双推。

## 一次性配置

- GitHub：`origin` → `git@github.com:grovecai/goule.git`。
- Gitee：`gitee` → `https://gitee.com/grovecai/goule.git`，供查看和验收；不是日常推送入口，新克隆工作区可按需添加该远端。
- 确认 Gitee 仓库已创建并可供公众访问；同步账号需要代码及发行版写权限。
- Gitee 若提示账号安全评级较低，需要账号所有者亲自完成 2FA 或绑定平台认可的第三方账号，才能发布公开内容；仅代码同步可以在私有状态继续，公开发行版和附件验收必须等该要求满足。
- 在 [Gitee 私人令牌](https://gitee.com/profile/personal_access_tokens) 创建具有该仓库代码和发行版写权限的令牌，保存至 [GitHub Actions Secrets](https://github.com/grovecai/goule/settings/secrets/actions)，名称为 `GITEE_TOKEN`。不要写入仓库、远端 URL、命令参数或日志。
- Gitee 写入凭据只需配置在 Actions Secret，本机无需另配 Gitee Git 凭据或 SSH 公钥。

## 每次发布

1. 按实施计划完成版本递增、发布说明、`./scripts/check`、Release 构建、版本元数据、原签名及 ZIP 对齐检查，提交并合入 main，只推送 GitHub。main push 自动触发代码同步，普通推送并核对 Gitee main；支持私有仓库，不要求已有 Release，不创建或修改发行版，禁止强制覆盖远端历史。
2. 从干净发布提交构建一次签名 APK，命名为 `goule-vX.Y.Z.apk`，生成 `goule-vX.Y.Z.apk.sha256`（内容为 `64 位摘要  goule-vX.Y.Z.apk`）。创建注解标签并只推送 GitHub，Gitee 标签随对应 Release 同步。
3. 在 GitHub 发布包含这两项附件的 Release，保持发布说明和预发布标记正确；草稿不对外同步。GitHub 发布完成或编辑后触发 [Gitee 同步工作流](../.github/workflows/sync-gitee-release.yml)。必须等两项附件上传完毕后再验收；若先发布再传附件导致流程失败，附件齐全后手动重跑。
4. 工作流先检查令牌能访问 Gitee 仓库且仓库公开，再从 GitHub 下载既有附件，校验 APK 摘要，将 main 和本次标签以普通推送同步至 Gitee，创建/更新发行版及附件。上传后通过不带令牌的公开链接回下载，逐字节核对 APK 和校验文件，将正文内的附件链接替换为 Gitee 实际下载地址。日志按阶段输出，普通外部命令超时为 180 秒，Gitee 全历史推送为 1,200 秒，附件上传为 600 秒，其余 API/下载请求为 120 秒。工作流总上限 40 分钟；跨区域首次推送可能较慢，查看对象写入进度判断实际情况。
5. 查看 [Actions 结果](https://github.com/grovecai/goule/actions/workflows/sync-gitee-release.yml)，核对两端 main/标签、发布说明及预发布状态。仅在成功后将实际 Gitee APK/校验下载地址加入 README，保留 GitHub 入口，并在 PROGRESS 和验证手册记录结果。失败时不可标为双平台发布完成。

## 补发和故障恢复

在 Actions 的 `Sync GitHub to Gitee` 页面选择 `Run workflow`，分支选 main，填入已发布的标签（例如 `v0.3.13`）补发。代码同步与发行版同步共用串行队列（`queue: max`，最多 100 个待运行任务），不以新 push 替换待运行的发行版任务，也不中断正在执行的发布。通常不需在本机运行同步；故障排查时也可在已配置 `gh`、GitHub `origin` 和 `GITEE_TOKEN` 环境变量的本机执行：

```bash
python3 scripts/sync_gitee_release.py v0.3.13
# 只同步 GitHub main，不发布或更新发行版：
python3 scripts/sync_gitee_release.py --code-only
```

手动入口的 `make_public` 默认为关闭。只有仓库所有者明确批准公开仓库全部内容时才启用（本机对应 `--make-public`）；Gitee 不允许空仓库公开，因此该模式先推送已批准的 GitHub main/标签，再修改并回读可见性，仍未公开时停止发行版创建，自动 Release 事件不会更改可见性。

手动运行会同步 GitHub main 和指定标签；不会发布本地未提交或尚未推送的代码。重复运行会核对已有附件并补齐缺失附件，不重复上传。若已有同名附件内容不同，流程报错并保留原文件，先核查来源，勿直接覆盖。Gitee API 不提供草稿参数，创建后若附件上传中断，发行版可能暂时不完整；补发成功前不提供国内 APK 下载入口。

若 `GITEE_TOKEN` 缺失、仓库不存在/不可见、权限不足、远端有独立提交或下载失败，流程会失败并保留 GitHub 发布。修正实际原因后重跑；不要强制推送，也不要换签名重新打包。按 [Gitee 官方说明](https://help.gitee.com/repository/release/create)，普通项目单附件上限为 100 MB、仓库附件总容量为 1 GB，后续需留意容量。
