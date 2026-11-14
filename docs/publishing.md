# GitHub 与 Gitee 发布

GitHub 是唯一日常推送和发布入口，本机只推 main。`Publish Android release` 在每日定时或手动触发后检查、构建原签名 APK、生成 SHA-256 并发布 GitHub，接着直接执行 Gitee 同步与回下载验证，成功后更新 README 和发布记录。main 更新及外部手工发布的 GitHub Release 仍由同步工作流处理；Gitee 使用相同安装包。

Gitee 自带镜像按 [官方说明](https://help.gitee.com/repository/settings/sync-between-gitee-github) 同步分支、标签和提交；发行版正文及二进制附件由本项目 Actions 单独处理，无需另设 Gitee 镜像或本机双推。

## 一次性配置

- GitHub：`origin` → `git@github.com:grovecai/goule.git`。
- Gitee：`gitee` → `https://gitee.com/grovecai/goule.git`，供查看和验收；不是日常推送入口，新克隆工作区可按需添加该远端。
- 确认 Gitee 仓库已创建并可供公众访问；同步账号需要代码及发行版写权限。
- Gitee 若提示账号安全评级较低，需要账号所有者亲自完成 2FA 或绑定平台认可的第三方账号，才能发布公开内容；仅代码同步可以在私有状态继续，公开发行版和附件验收必须等该要求满足。
- 在 [Gitee 私人令牌](https://gitee.com/profile/personal_access_tokens) 创建具有该仓库代码和发行版写权限的令牌，保存至 [GitHub Actions Secrets](https://github.com/grovecai/goule/settings/secrets/actions)，名称为 `GITEE_TOKEN`。不要写入仓库、远端 URL、命令参数或日志。
- Gitee 写入凭据只需配置在 Actions Secret，本机无需另配 Gitee Git 凭据或 SSH 公钥。
- 云端原签名构建还需三个仓库级 Secrets：`ANDROID_RELEASE_KEYSTORE_BASE64`（原 PKCS12 发布密钥的 Base64）、`ANDROID_RELEASE_STORE_PASSWORD`、`ANDROID_RELEASE_KEY_PASSWORD`。原 alias 为 `goule-release`；原证书 SHA-256 固定为 `a99c6b2900299c459fe1121dc7868762d8afb66ec9f5b4819a471a5b62103559`，缺少密钥或证书不符时停止，不创建替代签名。签名资料只在临时目录解码，密码通过环境变量传给 apksigner。

## 每日发布

每天北京时间 **08:17**（UTC cron `17 0 * * *`）从 main 检查上次已发布标签以来的净变化。应用、资源、Gradle、脚本及工作流变化计为代码更新；仅 README、进度、验证记录或机器人完成标记变化不发新版。无代码变化则跳过构建与新版本创建，顺带检查 main 代码镜像。GitHub 定时任务可能排队延后，以实际 Actions 时间为准。

默认递增最近发布的补丁版本（例如 `v0.3.13` → `v0.3.14`），versionCode 严格递增；若 main 已声明更高版本，则沿用该版本名称。定时发布始终为预发布。新版发布说明默认整理应用/构建代码提交，既有真机边界继续引用验证手册。

## 阶段性手动发布

打开 [Publish Android release](https://github.com/grovecai/goule/actions/workflows/publish-release.yml)，选择 `Run workflow`，分支选择 main：

- `version`：可留空自动递增，或填 `v0.4.0` 等更高版本；不允许重用已有标签或降级。
- `notes`：可填写阶段说明，留空时整理代码提交；若 main 已有该版本说明文件则保留原稿。
- `prerelease`：默认开启；明确要发布正式版时关闭。
- `force`：默认关闭；无代码变化也要发阶段版本时开启。
- `dry_run`：只输出计划，不修改版本、构建或发布，适合先核对配置。

通常只需点击手动运行，其他参数保持默认。也可在本机执行 `gh workflow run publish-release.yml --repo grovecai/goule --ref main`。传统的本机原签名构建、在 GitHub 创建 Release 并上传 APK/SHA-256 仍可用，其发布/编辑事件会触发 Gitee 同步；不需要本机推 Gitee。

## 流水线验收与失败恢复

工作流先更新版本、生成说明和未完成状态，运行 `./scripts/check` 后建立干净发布提交，再构建并验证原签名、包名、版本、最低 SDK、非调试标记、包内源码提交和 ZIP 对齐。全部通过后以普通原子推送发布 main 和注解标签；构建期间 main 有其他提交或标签冲突时拒绝推送，不覆盖他人历史。APK 命名 `goule-vX.Y.Z.apk`，校验文件为同名 `.sha256`。

GitHub 先创建草稿，附件上传及回下载一致后才公开。流水线随后直接调用 Gitee 同步：GitHub 官方 `GITHUB_TOKEN` 创建的 Release 不会再触发其他发布事件工作流，因此不依赖事件串联。Gitee 复制原 APK/SHA-256、替换正文下载链接并通过匿名回下载验证，完成后才更新 README 的受管理下载区、PROGRESS、`docs/latest-release-verification.json` 及 `.github/release-state.json`，再同步最终 main；本轮检查见 [验证手册](verification.md#github-定时与手动发布2026-11-14)。

中途失败后再次运行会优先恢复同一未完成版本，冻结其标签、源码提交、versionCode 与预发布状态，不另起版本，也不覆盖同名附件。已有 APK 会核对原证书、版本和包内提交后复用；已有校验文件不匹配则停止。GitHub 已发布但 Gitee 失败时，下次运行继续同步该版本；最终文档提交的代码镜像失败可由下一次无更新检查补齐。自动验证不等于新的真机行为验收。

查看 [发布工作流](https://github.com/grovecai/goule/actions/workflows/publish-release.yml) 和 [Gitee 同步结果](https://github.com/grovecai/goule/actions/workflows/sync-gitee-release.yml)。普通外部命令保留超时，跨区域 Gitee Git 推送上限 1,200 秒、附件上传 600 秒；单独 Gitee 同步工作流上限 40 分钟，完整发布上限 75 分钟。

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
