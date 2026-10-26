# 项目协作约定

- GitHub 仓库：<https://github.com/codeBennyLaw/NFCTools>。
- 用户指定以 [WyuTimetable](https://github.com/codeBennyLaw/WyuTimetable) 的 commit、PR 和 Release 风格为参考。具体规则见 [CONTRIBUTING.md](CONTRIBUTING.md)。
- commit 使用简洁中文说明具体变化；版本 PR 标题为 `版本号：具体变化`，正文只写用户可见的功能和修复，不写验证、测试、构建或签名记录。
- Release 标签为 `v版本号`，标题为 `NFCTools 版本号`；中文更新说明先概括变化，再列出用户可见改动，末尾链接相关 PR。
- APK 命名为 `NFCTools-<构建类型>-v-<版本号用连字符分隔>.apk`，APK 作为 Release 附件上传，不提交到 Git。
- Release 只上传一个 APK，不上传 SHA、校验文件或其他附件；正文只写功能变化，不写“安装包为”、文件名或安装说明。
- 发布时检查 Gradle 版本、更新说明、签名、APK 元数据和附件校验值一致。不得将模拟器测试表述为 NFC 实卡或 MagicOS 真机验证。
- 本机配置、缓存、临时文件和签名密钥不进入仓库。保持现有签名以支持覆盖安装，不随意更换。
