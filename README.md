# NFC 工具箱

中文 Android 原生 NFC 工具，Kotlin + Jetpack Compose / Material 3，支持 Android 8.0（API 26）及以上。

## 功能

| 入口 | 可用操作 |
| --- | --- |
| 读取 | 自动扫描；UID、正反序十进制卡号、协议、容量、状态、ATQA / SAK；读后直接编辑、保存到卡包或复制到新卡 |
| 内容解析 | UTF-8 / UTF-16 文本、URI、联系人、Wi-Fi WSC、MIME 文件、HEX 原始记录 |
| 快捷写入 | 文本、网址、Wi-Fi、打开 App、联系人、电话、邮件、短信、位置、文件、自定义 URI、HEX、等待；上下调整顺序；连续写多张卡 |
| 顺序任务 | 编排与等待写入卡片；预览后按顺序处理；外部应用返回后继续；暂停、重试、跳过与停止 |
| 选择与分享 | 从已安装应用、通讯录选择；从浏览器或文件管理器的系统分享菜单导入 |
| 卡包 | 最多 100 张，改名与分类；点击直达本机最新内容；同卡读写成功后自动更新；编辑、复制、比较 |
| 文件 | 系统文件选择器导入任意类型小文件；从 MIME 记录取出文件；导入 / 导出原始 `.ndef` |
| 工具 | 擦除、格式化、永久只读；MIFARE Classic 已知密钥扇区读取；Ultralight 页读取；Dump 导入导出；卡片比较；系统钱包入口 |
| 记录 | 最近 50 次操作保存在本机；搜索、查看、导出 JSON 报告、复用 NDEF 内容 |
| 外观 | 浅色 / 深色 / 跟随系统；平板宽度限制；横屏滚动；系统字号支持 |

所有数据留在本机，App 没有网络权限。未提交的录入文字也会自动保存，切换页面或重开 App 可继续填写。演示标签有明确标记且不进入历史或卡包。

## 安装与构建

当前开发版本为 **0.2.0**（versionCode 20），本地发布构建为 `dist/NFCTools-release-v-0-2-0.apk`。已经发布的版本从 [GitHub Releases](https://github.com/codeBennyLaw/NFCTools/releases) 下载，也可以用 Android Studio 打开工程运行。现有签名保持一致，可覆盖安装此前版本，保留本机草稿与历史。

构建环境：JDK 17、Android SDK Platform 37.0、Build Tools 36.0.0、AGP 9.1.1、Gradle 9.4.1、内置 Kotlin 2.2.10。compileSdk / targetSdk 均为 37（Android 17），最低 API 26。
将本机 SDK 路径填入未纳入版本管理的 `local.properties`：

```properties
sdk.dir=D\:/Android_SDK
```

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
# 启动模拟器或连接手机后执行界面测试
.\gradlew.bat :app:connectedDebugAndroidTest
```

标准构建产物：`app/build/outputs/apk/release/NFCTools-release-v-0-2-0.apk`；调试包为 `app/build/outputs/apk/debug/NFCTools-debug-v-0-2-0.apk`。发布后的 APK 在 Release 附件中提供，不纳入 Git 源码。
命名规则：`NFCTools-<构建类型>-v-<版本号，点替换为连字符>.apk`，后续构建自动使用此格式。
当前 Debug 和 Release 使用本机相同的 Android 开发签名，保证覆盖安装；Release 构建关闭调试。密钥不进入仓库，后续更换发布签名需要安排升级路径。

## 使用

1. 在支持 NFC 的 Android 手机上启用 NFC，解锁后打开 App。
2. 将标签贴近手机背面 NFC 感应区；读取页自动展示信息。
3. 在写入页选类型直接填写，或点“粘贴识别”；点“贴卡写入”，再贴近目标标签。网址可直接输入 `example.com`。需要多条内容时点“添加到列表，继续录入”。
4. 保持卡片不动，等待回读校验和完成提示。单次写入成功后自动清空待写入列表与录入框，失败或取消时保留内容；失败时移开后重新扫描确认实际卡片内容。
5. “工具”中的操作需先确认，再对下一张靠近的卡片执行一次。取消或切到后台会撤销尚未执行的操作。
6. 需要碰标签打开 App：选“打开应用”，从手机应用列表选择，再写入标签。接收手机需安装该 App，具体启动行为由系统决定。
7. 连续写多张卡：先开启“连续写多张卡”，再点写入，逐张移开和贴近标签；切后台或点取消会停止。
8. 门禁等常用卡可保存到卡包，改名并分类；原卡没有 NDEF 内容时，可保存公开卡号和协议信息。
9. 点击卡包中的卡片，直接跳转读取详情，优先展示此卡在本机最近成功读取或写入的内容。同卡读写后自动更新已有卡片，保留名称和分类；旧版卡包也会从本机历史记录补齐较新的内容。详情标注本机记录时间，确认实体卡当前状态仍需重新贴卡。

一张卡可以在容量允许时一次写入多条 NDEF 记录：每条内容点“添加到列表，继续录入”，最后统一写入。本 App 最多录入 32 条，读取详情会显示全部内容，链接与应用提供各自的打开按钮。每次写入会替换原有 NDEF 消息，需要保留的旧内容应先读取并编辑后一起写入。

需要编排执行时，在列表中用上下箭头调整顺序，可以加入 1–600 秒的“等待”，并开启“工具箱顺序任务”（添加等待会自动开启）。点击“预览执行顺序”可以先试运行，再将任务写入卡片；接收手机需安装本工具箱。任务标识和路由占用的额外容量已计入字节数。读取顺序任务卡会打开任务预览，点击“开始执行”后才处理；卡包详情也提供“按顺序执行”入口。

文本步骤复制到剪贴板，等待结束后自动进入下一步；应用、链接、文件、联系人、拨号、邮件、短信与 Wi-Fi 会打开对应的应用或系统界面，返回后点击“已完成，继续下一步”。拨号、发送、保存联系人和连接网络由用户在系统界面确认。Android 11 以上 Wi-Fi 步骤提交系统网络确认，旧版打开 Wi-Fi 设置供手动连接。任务不会控制其他 App 内部操作，也不绕过系统确认。

切后台或旋转会暂停正在进行的等待，继续时保留剩余时间；返回工具箱不会自行开始下一步。失败可重试、跳过或停止，运行中的任务使用固定副本，进程结束后不自动重放。

多步处理由本工具箱执行。**普通 NDEF 模式下，写入多条记录仍不等于系统自动执行全部操作。** 依据：[Android NFC 分发](https://developer.android.com/develop/connectivity/nfc/nfc)、[Android Application Record](https://developer.android.com/reference/android/nfc/NdefRecord#createApplicationRecord(java.lang.String))、[Wi-Fi 系统确认](https://developer.android.com/develop/connectivity/wifi/wifi-save-network-passpoint-config)。

`.ndef` 是原始 NDEF 消息，可以恢复为待写入内容。JSON 报告用于查看公开信息，不是整卡镜像，不能用于恢复整卡。
MIME 记录只保存文件内容和类型，不保存原文件名；导出时使用生成的文件名。
原始内存支持 `.mct`、4 字节页的 HEX 文本，以及 320 / 1024 / 2048 / 4096 字节 `.bin` 镜像。未读取的块用 `?` 表示，导入仅供查看和比较。

## 荣耀 MagicOS 11

荣耀官方说明 MagicOS 11 基于 Android 17。本版升级到 targetSdk 37，并完成以下代码层适配：

- 前台 Reader Mode，NFC 开关状态变化后重启读卡；不要求常驻后台或电池白名单。
- 独立 NFC 分发 Activity，在 API 37 使用 `DISPATCH_NFC_MESSAGE` 保护；桌面启动和系统分享仍使用普通入口。
- 使用系统文件和联系人选择器，不申请整个存储空间或整个通讯录的读取权限。
- 等待贴卡和操作期间保持屏幕唤醒；切后台取消待执行操作；输入内容自动保存在本机。
- 边到边布局、键盘避让、系统大字体、深浅色和分屏宽度适配；原生库与 APK 打包检查 16 KB 页对齐。

**当前只有 Android 16 模拟器，未连接 MagicOS 11 真机。荣耀实机 NFC 天线、系统钱包和 Android 17 分发行为仍需实测，不能视为已通过荣耀认证。**

依据：[荣耀 MagicOS 11 说明](https://iknow-dl.service.hihonor.com/ctkbfm/applet/simulator/zh-cn15907510/index.html)、[Android NFC 分发要求](https://developer.android.com/develop/connectivity/nfc/nfc)、[Android 17 SDK](https://developer.android.com/about/versions/17/setup-sdk)。

## 能力范围

- 普通 NFC 标签通常只有数百字节；导入上限为 48 KiB，实际可写大小还受卡片容量和 NDEF 格式开销限制。大照片和视频应改为写入下载链接。
- 使用 Android `Ndef` / `NdefFormatable` 接口。非 NDEF 卡仍可显示公开 UID 和协议信息；加密门禁、银行卡、交通卡的业务区依赖其协议和授权，本版不提供破解、UID 改写或整卡克隆。
- 复用只复制 NDEF 内容，目标卡的 UID 保持其自身值。
- 擦除是写入空 NDEF 记录，不能保证物理存储的取证级清除，不改 UID、密钥或厂商区。
- 永久只读不可撤销。Android 会缓存发现标签时的能力信息，格式化后需重新扫描；写入通过读取最新 NDEF 消息比较二进制内容进行校验。
- Wi-Fi 使用标准 WSC MIME 记录，接收手机是否提供自动连接由系统支持决定。
- 扇区读取只使用用户提供的一个 Key A 或 Key B，不进行密码字典尝试。Ultralight 按公开容量限制读取，避免越界循环；保护区可能不可读。
- 系统钱包入口用于手动添加门禁卡。本 App 不提供任意门禁模拟、UID 改写或原始 Dump 写回。
- 历史和草稿可能包含 Wi-Fi 凭据、联系人或文件内容。应用禁用了系统备份；可手动清空历史与草稿。

## 工程结构

```text
app/src/main/java/com/benny/nfctools/
  MainActivity.kt        前台 Reader Mode 与 NFC 硬件状态
  NfcViewModel.kt        贴卡操作、草稿、历史和文件 I/O
  nfc/NfcEngine.kt       真实标签读取、写入校验、擦除、格式化、只读
  nfc/NdefCodec.kt       记录构造、输入校验和解析
  data/                 不可变状态、本机原子文件存储
  ui/                   四个页面、表单、设计组件、深浅色主题
```

设计说明见 [design-system/nfc-tools/MASTER.md](design-system/nfc-tools/MASTER.md)。
验证情况与真机检查步骤见 [docs/VALIDATION.md](docs/VALIDATION.md)。
更新内容见 [docs/CHANGELOG.md](docs/CHANGELOG.md)。

后续 commit、PR 与 Release 按 [CONTRIBUTING.md](CONTRIBUTING.md) 执行，参考用户指定的 WyuTimetable 仓库。

实现依据：[Android NDEF API](https://developer.android.com/reference/android/nfc/tech/Ndef)、[前台 Reader Mode](https://developer.android.com/reference/android/nfc/NfcAdapter)、[NDEF 格式化接口](https://developer.android.com/reference/android/nfc/tech/NdefFormatable)。
