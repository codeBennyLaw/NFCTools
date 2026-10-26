# 提交与发布约定

按用户指定，参考 [WyuTimetable](https://github.com/codeBennyLaw/WyuTimetable) 的实际提交、PR 和发布格式。

## Commit 与分支

commit 标题用简洁中文说明具体变化，例如 `修复写入成功后待写入列表残留`、`版本升级至 0.1.1`。一个提交围绕一个明确目的；正文只补充有助于理解行为或取舍的信息。

默认分支为 `main`。功能、修复和发布准备可分别使用 `feat/<主题>-<版本>`、`fix/<主题>-<版本>`、`chore/release-<版本>` 分支，通过 PR 合入。仓库初始化单独提交，不补造以前版本的提交历史。

## Pull Request

版本 PR 标题为 `0.1.1：修复写入后列表残留` 这样的格式。正文以 `版本更新至 0.1.1。` 开头，用中文说明用户可见的功能与修复，再用简短列表列出改动。有关联 Issue 时使用 `Closes #编号`。不要添加验证、测试、构建、签名或检查记录，也不要罗列实现细节。

合并提交保持中文版本标题，并关联 PR。描述最终实现，不保留讨论过程或已放弃的方案。

## Release

- 标签：`v0.1.1`，指向已合入的发布提交。
- 标题：`NFCTools 0.1.1`。
- 正文：先用一句话概括用户可见变化，再列出具体改动，末尾添加 `相关 PR：[#编号](链接)`。只写功能与修复，不添加验证、测试、构建等内容，也不写“安装包为”、APK 文件名或安装说明。
- APK：`NFCTools-release-v-0-1-1.apk`。调试包继续使用 `NFCTools-debug-v-0-1-1.apk`；不把 Debug APK 改名伪装成 Release。
- 附件：只上传一个 APK，不上传 SHA、校验文件或其他附件；APK 不进入源码历史。
- 当前与参考项目一样沿用本机 Android 开发签名，Release 构建关闭调试，支持覆盖安装此前的 Debug 包。签名密钥不提交；更换签名前先明确现有用户的升级路径。

发布准备：

1. 更新 `app/build.gradle.kts` 的 `versionName` 和递增的 `versionCode`，同步 README 与 `docs/CHANGELOG.md`。
2. 执行适合变更的检查。本项目常用：

   ```powershell
   .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
   ```

3. 检查 APK 版本、签名、不可调试状态与 ZIP 对齐；验证与已有版本的签名一致。
4. 完成 PR 合入，为对应提交创建版本标签，只上传一个 APK，再发布 Release。检查结果留在内部验证文档，不放进 PR 或 Release 说明。

源码、文档和测试进入 Git；`local.properties`、构建缓存、`.opencode/`、编辑器个人设置与密钥留在本机。NFC 射频通信与 MagicOS 兼容性以真实设备验证为准。
