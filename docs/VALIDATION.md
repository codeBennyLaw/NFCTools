# 0.2.0 验证记录

日期：2026-10-27。包名 `com.benny.nfctools`，versionCode 20。
本地安装包：`dist/NFCTools-release-v-0-2-0.apk` 和 `dist/NFCTools-debug-v-0-2-0.apk`。本次通过功能分支提交 PR，未发布 0.2.0 Release。

| 检查 | 结果 |
| --- | --- |
| 构建 | Debug、AndroidTest、Release 构建通过 |
| 单元测试 | 43 项通过，其中新增 8 项覆盖同卡判断、最新记录选择及内容摘要 |
| Android 设备测试 | Android 16 / API 36 模拟器，16 项全部通过，其中新增 4 项卡包流程测试 |
| Lint | 0 错误，3 条现有依赖版本更新建议 |
| 安装包 | 0.2.0 / 20，签名验证与 16 KB ZIP 对齐通过；Release 不可调试，沿用原有证书 |

新增验证覆盖：

- 同一卡号的格式差异不影响内容匹配；其他卡片、示例和缺失 UID 不参与自动匹配。
- 成功写入后同步更新已有卡包内容，保留名称、分类和 ID；点击卡片进入读取详情，同时显示应用与链接等多条记录。
- 清空历史后，卡包仍保存最新内容；旧版卡包可以从较新的本机历史补齐。
- 使用旧管理弹窗的卡片对象改名，不会将内容回退到旧版本。
- 失败写入保留旧卡包与待写入草稿；部分 NDEF 读取失败不覆盖最近已知内容。
- 新的成功读取与擦除会更新卡包；摘要区分空内容、无内容与不可解析内容，列表摘要不包含 Wi-Fi 密码等具体值。

新增设备测试通过替代 NFC 执行器和无射频服务的 Tag 测试数据验证实际 ViewModel、Compose 导航和 Android 本地存储。**没有使用 NFC 实卡，也没有验证荣耀 MagicOS 11 / Android 17 的系统触发行为。**

已查看模拟器真实截图（其中卡片及写入内容为测试数据）：[卡包列表](screenshots/0.2.0/cards.png)、[卡包详情](screenshots/0.2.0/card-detail.png)。本机详情优先显示内容，不显示实体扫描状态区；时间与本机来源有明确说明，打开卡片不会自动执行记录中的应用或链接。

测试输出见 [validation/0.2.0/](validation/0.2.0/)。多条 NDEF 数据的写入和展示不等于自动执行任务链，依据：[Android NFC 分发](https://developer.android.com/develop/connectivity/nfc/nfc)、[应用记录处理](https://developer.android.com/reference/android/nfc/NdefRecord#createApplicationRecord(java.lang.String))。

# 0.1.1 历史验证记录

日期：2026-10-27。包名 `com.benny.nfctools`，versionCode 11。
安装包：`dist/NFCTools-debug-v-0-1-1.apk`。

| 检查 | 结果 |
| --- | --- |
| 构建 | assembleDebug、assembleDebugAndroidTest 通过 |
| 单元测试 | 35 项全部通过，其中 7 项为写入完成与草稿清理回归测试 |
| Android 设备测试 | Android 16 / API 36 模拟器，原有 12 项全部通过 |
| Lint | 0 错误，3 条依赖版本更新建议 |
| 安装包 | 版本号 0.1.1 / 11、签名验证、16 KB ZIP 对齐检查通过 |
| GitHub 发布包 | assembleRelease 通过；与此前 Debug 包证书 SHA-256 一致，模拟器覆盖安装成功，应用标志不含 DEBUGGABLE |
| Release 设备回归 | Android 16 / API 36 模拟器，Release 构建下同一组 12 项测试全部通过，见 release-instrumentation-results.txt |

新增回归覆盖：

- 单次成功写入后清空多类型列表、录入框及本地保存的草稿，同时保留读卡结果与历史。
- 原始 NDEF 备份成功写入后清除导入内容。
- 写入或回读校验失败后保留草稿，重试成功后清除。
- 取消等待、切后台后保留草稿。
- 连续写卡保留内容，自动等待下一张，正确累计数量。
- 普通读取与擦除卡片不清理无关的待写入草稿。
- 写入期间新收到的分享内容不会被完成后的清理删除。

上述 7 项使用替代 NFC 执行器验证 ViewModel 的实际状态转换和本地持久化，不模拟射频通信；未连接实卡或荣耀 MagicOS 11 真机。本次保持原有 NFC 写入与回读校验实现，真实标签行为仍需真机确认。

测试输出见 [validation/0.1.1/](validation/0.1.1/)。下面保留 0.1.0 的界面及兼容性验证记录。

# 0.1.0 历史验证记录

日期：2026-10-26。包名 `com.benny.nfctools`，versionCode 10。
安装包：`dist/NFCTools-debug-v-0-1-0.apk`。

## 已完成

| 检查 | 结果 |
| --- | --- |
| Android 构建 | 通过，compile / target SDK 37，最低 API 26 |
| 单元测试 | 28 项通过：编解码 12、快捷识别与 Dump / 比较 11、持久化 5 |
| Android 设备测试 | Android 16 / API 36 模拟器，12 项全部通过 |
| Lint | 0 错误、3 条版本更新建议：Gradle、协程和 Robolectric；没有功能或权限错误 |
| APK 签名 | apksigner 验证通过，v2 签名 |
| 16 KB 打包检查 | zipalign `-c -P 16 4` 通过 |
| 原生库对齐 | arm64-v8a、armeabi-v7a、x86、x86_64 的 graphics.path ELF LOAD 段均为 16384 字节对齐 |
| Android 17 NFC 入口 | APK 中确认 v37 的 `DISPATCH_NFC_MESSAGE` 配置；桌面和分享入口独立 |
| 启动、截图过程 | 未发现 AndroidRuntime 崩溃 |

测试结果保存在 [validation/0.1.0/](validation/0.1.0/)。

模拟器用例覆盖：

- 文本添加、编辑、删除；网址错误提示；永久只读确认与取消；示例不进入历史。
- 系统分享直接打开可编辑网址；从已安装应用选择包名；旋转和切换页面后保留未提交输入。
- 读取结果转为可编辑记录，再修改文本。
- 真实 ContentResolver 文件导入、NDEF 导出、重新导入，逐字节比较。
- 批量写入使用开始时固定的数据；切后台取消，返回后不会自动恢复待写入操作。
- 历史连续保存、50 条上限与清空；卡包改名和分类后重新读取。

本机通过 AndroidJUnitRunner 直接运行设备测试：

```powershell
adb install -r app/build/outputs/apk/debug/NFCTools-debug-v-0-1-0.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.benny.nfctools.test/androidx.test.runner.AndroidJUnitRunner
# OK (12 tests)
```

## 界面检查

已查看以下真实模拟器截图。常用输入直接展示，写入按钮固定；大字号下标题与操作按钮自动分行；页面和弹窗可滚动；键盘打开时隐藏底部导航并避让输入区。

| 场景 | 截图 |
| --- | --- |
| 标准手机 | [首页](screenshots/0.1.0/home.png)、[快捷写入](screenshots/0.1.0/write.png)、[卡包](screenshots/0.1.0/cards.png)、[工具](screenshots/0.1.0/tools.png) |
| 应用选择 | [已安装应用列表](screenshots/0.1.0/app-picker.png) |
| 深色 | [深色首页](screenshots/0.1.0/home-dark.png) |
| 约 375 dp 小屏 | [小屏](screenshots/0.1.0/small-375.png) |
| 小屏、2 倍字号、关闭动画 | [首页](screenshots/0.1.0/large-text-375.png)、[写入](screenshots/0.1.0/large-text-write.png) |
| 手机横屏 | [横屏](screenshots/0.1.0/landscape.png) |
| 平板 | [竖屏](screenshots/0.1.0/tablet.png)、[横屏](screenshots/0.1.0/tablet-landscape.png) |

主文字和次级文字沿用已检查的语义配色：浅色主文字对白底 15.48:1，浅色次级文字对次级底 5.45:1；深色分别为 13.60:1 和 7.26:1。控件使用 Material 原生触摸反馈，目标最小 48 dp。独立图标按钮提供中文名称。TalkBack 人工遍历未执行。

## MagicOS 11 的验证边界

荣耀说明 MagicOS 11 基于 Android 17。本版已升级 targetSdk 37，配置受保护的 NFC 分发入口、前台 Reader Mode、NFC 开关更新、等待期间亮屏、键盘和安全区域、输入自动保存与系统选择器。

**当前未连接荣耀手机，也没有 Android 17 / MagicOS 11 运行环境。因此不能把编译、静态检查和 Android 16 模拟器测试视为荣耀实机验证。**

仍需在 NFC 真机上检查：

1. 空白、已写入、只读及非 NDEF 卡的 UID、协议和容量。
2. 写入后回读校验、容量不足拒绝、中途移卡恢复和单次复制。
3. 连续写入多张不同标签，切后台后停止等待；NFC 快捷开关恢复读卡。
4. 擦除、格式化和永久只读（仅对可牺牲的测试标签）。
5. Classic 的已知 Key A / B 扇区认证、失败扇区标记；Ultralight 公开页范围与 Dump 导出。
6. 手机与标签匹配时打开应用；Android 17 的系统 NFC 分发和链接通知行为。
7. 荣耀联系人、文件管理器、系统钱包以及分屏 / 大字号实际表现。

门禁模拟与钱包开卡由系统、硬件和发卡方支持决定，本 App 不提供任意卡片模拟或 Dump 写回。

依据：[荣耀 MagicOS 11 说明](https://iknow-dl.service.hihonor.com/ctkbfm/applet/simulator/zh-cn15907510/index.html)、[Android NFC](https://developer.android.com/develop/connectivity/nfc/nfc)、[Android 17 SDK](https://developer.android.com/about/versions/17/setup-sdk)。
