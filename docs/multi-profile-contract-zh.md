# OwnTV TV 端多档案契约

> 编写日期：2026-11-14
> 来源：`docs/alignment-to-own-project-zh.md` §5.2、§2.4、§7 P0 第 4 项
> 适用仓库：`OwnTV` 的 `QwnTV-mini`（本机 `D:\AndroidStudioProjects\git\OwnTV-mini`）
> 基线：`422181d Settings: fold 12 groups into 8 and open More cards by stable group id`
> Core：默认源码复合构建 `includeBuild(../Qwn/OwnTV_Core)`（无需 `-P` 参数；Core `mobile` @ `b3549819`，Room v55）；TV 端 Sports 已下线
> 性质：能力清单与回归契约，不改代码、不改行为。

---

## 0. 一句话

**多档案是 `OwnTV`（TV 端）的专属能力，完整保留、只在 TV 端演进；`OwnTV_Mobile` 保持单档案，两端不回填、不移植。**

本文把「多档案到底包含什么、代码在哪、怎么算回归通过、改动时遵守什么」固定下来，供 TV 端后续功能分支与验收直接引用。两端职责边界、分支规范与风险清单以 `docs/alignment-to-own-project-zh.md` 为准，本文不重复。

---

## 1. 契约边界

| 项 | 约定 |
|---|---|
| 归属仓库 | 只在 `OwnTV/QwnTV-mini`；手机端 `OwnTV_Mobile/mobile-mini` 不新增档案 CRUD、选人门、多档案备份 / 同步 |
| 共享资产 | 只有 `OwnTV_Core`（`mobile` 分支）是共享的；TV 端通过源码复合构建跟随，不再固定 `1.0.69` 制品 |
| 设置承载 | 8 组内 `App › Profiles` 一行一页；不新增顶层分组，不新增手写搜索条目 |
| 语言 | TV 端中文单语言契约不变；档案能力与语言集合无关 |
| 移植纪律 | 任何把档案 UI 搬到手机端、或把手机端单档案假设搬回 TV 端的动作，须先按对齐方案 §3.4 重新立项 |

---

## 2. 能力清单（9 项，本轮全部为「保留 + 回归」）

| # | 能力 | TV 端实现位置 | 依赖 | 回归要点 |
|---|---|---|---|---|
| 1 | 创建 / 改名 / 头像 / 删除 | `features/profiles/ProfilesViewModel.kt`（`create` / `edit` / `rename` / `setPicture` / `clearPicture` / `delete`）、`ProfileComponents.kt`、`ProfilePictureChooser.kt` | Core `ProfileManager`、`ProfileDao`、`Pin` | 新建档案继承现有播放列表（单账号多观众），但收藏 / 历史为空；头像写入按 id 重读整行，避免回写旧值；删除当前档案会使门会话失效，删除非当前档案不影响会话 |
| 2 | 「谁在看」选人门 + 会话内切换 | `MainActivity.kt`、`features/profiles/ProfileGate.kt`（`androidx.tv.material3`）、`ProfileGateSessionViewModel.kt` | `profileGateRequired` | 单个无 PIN 档案直接进壳；多档案或含 PIN 时必须出选人门；会话内切换后按新档案刷新首页 / 直播 / 下载权限 |
| 3 | 每档案 PIN 锁 + 错误冷却 | `features/profiles/PinLockedScreen.kt`、`ProfilesViewModel.setPin` / `verifyPin` | Core `Pin`、`PinAttemptLimiter` | 错误次数触发冷却；认证绑定 `authenticatedProfileId`，为 A 档案认证的会话不能授权 B；唤醒自启后重新上锁 |
| 4 | Kids 模式（条目级过滤） | `ProfilesViewModel.setKids`、Core 目录链路 | Core `isKids` | 儿童档案只看到允许的分类 / 条目；TV 端无 TVBOX 内容，按现有 Core 常规目录链路生效；切回普通档案立即恢复 |
| 5 | 每档案独立收藏 / 历史 / 续播 / 布局 | Shell 与内容页按 `profileId` 取数 | Core Room | 切换档案后不串档；续播点与布局偏好各自独立；冷启动首帧前不得提前渲染另一档案的数据 |
| 6 | 每档案播放列表关联 | Core `ProfileSourceCrossRef` | Core Room | 新增档案继承源；删除档案不误删共享源；一源可挂多档案 |
| 7 | 每档案下载权限 | `ProfilesViewModel.downloadsAllowed` / `setDownloadsAllowed`；设置在 `App › Profiles` | Core `SettingsRepository` | 关闭权限后下载入口与在途任务按权限收敛；切换档案即时生效 |
| 8 | 每档案启动页 / Quick 钉选 | `SettingsRepository` 偏好键、Quick 组、启动页设置行 | Core 偏好存储 | 每档案各自的启动页；Quick 钉选不与其它档案串值 |
| 9 | 备份 / 本地同步支持多档案 | `features/settings` 备份与本地同步链路 | Core `profiles[]` 容器 | 备份含全部档案；恢复后 id 重映射不串档；本地同步按档案边界应用，删除传播不被合并复活 |

### 2.1 冷启动门的四条不变量

`ProfilesViewModel.kt` 的 `shellMayCompose` / `ProfileLoadState` 注释把门策略提纯为可单测规则，回归时逐条确认：

1. `Loading` 与 `Loaded(emptyList())` 是两种状态，空列表不等于「允许进壳」；
2. 仅凭持久化的 `activeProfileId` 不足以进壳，必须等 Room 的档案列表也到达；
3. 认证结果与 `activeProfileId` 绑定，会话不能跨档案复用；
4. `profileGateRequired(profiles)` 由 Core 统一裁决，TV 端不另写一套判断。

对应单测：`app/src/test/java/tv/own/owntv/features/profiles/ProfileGateSessionViewModelTest.kt`、`ProfileLaunchGateTest.kt`。

---

## 3. 数据边界：隔离什么、共享什么

| 维度 | 规则 |
|---|---|
| 按 `profileId` 隔离 | 收藏、历史、续播进度、下载与下载权限、布局 / 启动页 / Quick 偏好、认证会话 |
| 跨档案共享 | 播放列表与 EPG 源（单账号多观众）、应用级设置、语言、主题、网络 / DNS、应用图标 |
| 不共享 | 手机端 `applicationId=tv.own.owntv.mobile` 的数据目录；两端备份不跨端恢复，不做旧 TV 包数据迁移 |
| PIN | 明文不落库（`Pin.hash`）、备份不设密码时省略 PIN 哈希等全部机密 |

---

## 4. 设置承载（8 组收尾后的最终形态）

- `features/shell/components/SettingsGroup.kt`：8 个枚举值 `QUICK / SOURCES / PLAYER / SOUND / LIVE / WATCHING / APPEARANCE / APP`，More 卡片按稳定 `slug` 打开，不按位置。
- `features/settings/SettingsProfilePage.kt`：`Settings › App › Profiles` 叶子页，`parents = [App]`，面包屑回退；组页只保留一行 `Profiles`（带档案计数）。
- `features/settings/ProfileSettingsRows.kt`：档案行集合（新增 / 编辑 / 删除 / Kids / PIN / 下载权限），全部收在该叶子页。
- 硬约束：以后加档案能力，只能在既有 8 组内加行或加叶子页；不扩 `SettingsHelpTable` 为按页手写文案表，不新增手写搜索条目。

> 事实校正：对齐方案附录 A 把 `ProfileSettingsRows.kt` 记在 `features/shell/components/`，实际位于 `features/settings/`。本文与后续文档以实际路径为准。

---

## 5. 与 TV 专属能力的衔接

多档案不是孤立模块，以下 TV 专属路径必须按档案语义回归：

| 路径 | 文件 | 与档案的关系 |
|---|---|---|
| 开机自启 / 应用更新自启 | `features/autostart/AutoStartReceiver.kt`（Manifest `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` receiver） | 自启后先过档案门与 PIN，再按当前档案启动页进入 |
| 唤醒自启 | `WakeListenerService.kt`、`AutoStartProfileGate.kt` | 每次唤醒重新上锁；PIN 档案必须重新认证 |
| 自启策略 | `AutoStart.kt`、`AutoStartOptions.kt`、`AutoStartPolicy.kt`、`AutoStartLaunchMark.kt` | 启动页偏好按档案读取 |
| 遥控器快捷键 / 频道翻页 / 菜单文字 | `RemoteShortcutLabels.kt`、`RemoteShortcutInput.kt`、`ChNavSettingsScreen.kt`、`ui/components/ChNavPaging.kt`、`MenuLabels.kt` | 只改交互，不改变档案会话；快捷键不干扰焦点体系 |

---

## 6. 回归矩阵（对齐方案 P2 的 TV 端口径）

| 层 | 检查 | 通过标准 |
|---|---|---|
| 构建 | `:app:compileStandardDebugKotlin` | 全绿；默认 `includeBuild(../Qwn/OwnTV_Core)`，**零 `-P` 参数**；`1.0.69` 制品回退路径不兼容本分支 |
| 单测 | `ProfileGateSessionViewModelTest`、`ProfileLaunchGateTest`、`SettingsSearchCoverageTest` 等 | 全绿 |
| 门禁 | `tools/i18n` 的三项静态检查 | 字面量清单、数字 locale、文本溢出均 OK |
| 设置 | 分组数 / 行目录 / `App › Profiles` / 搜索覆盖 | 8 组、单一档案入口、无新增顶层行 |
| 档案行为 | 9 项能力逐项 | 真机 + 模拟器通过，无串档、无空列表误进壳 |
| 自启 | 开机、唤醒、自启后档案门 | 两条路径都先过门与 PIN，再进档案启动页 |
| 数据 | 安装 / 升级、备份恢复、本地同步 | 档案不丢、id 重映射正确、不跨端恢复 |
| 卫生 | 构建产物 / 临时 APK / 截图 / patch | 仓库只留源码、脚本、文档 |

---

## 7. 改动纪律

1. 多档案改动只在 `OwnTV/QwnTV-mini`；从该分支切功能分支，合回后继续维护。
2. 提交前缀 `feat(profiles):` / `fix(profiles):`，一条功能一个提交；Core 改动只在 `OwnTV_Core/mobile`，不直接改 TV 端 app 层去绕开 API。
3. TV 端改用 `Qwn/OwnTV_Core` 源码复合构建；Core 变动需两端一起验证，不在 TV 端单独改 Core API。Sports 已在本分支下线。
4. 每次改动至少跑：Kotlin 编译、相关单测、i18n 三项门禁、8 组与搜索覆盖核对。
5. 禁止把 TV 端档案 UI、选人门、`ProfileGate` 复制进 `OwnTV_Mobile`；也禁止解除手机端 `BackupProfiles.MULTIPLE`、`LocalSyncViewModel`、`SlimPlayerDefaults` 的单档案守卫。

---

## 8. 证据

| 事实 | 位置 / 命令 |
|---|---|
| 档案实现 7 文件 / 820 行 | `features/profiles/`（`PinLockedScreen` 89、`ProfileComponents` 237、`ProfileGate` 148、`ProfileGatePlayback` 16、`ProfileGateSessionViewModel` 114、`ProfilePictureChooser` 67、`ProfilesViewModel` 149） |
| 设置入口 | `features/settings/SettingsProfilePage.kt`、`ProfileSettingsRows.kt` |
| 8 组 | `features/shell/components/SettingsGroup.kt` 的 8 个枚举值 |
| 自启 | `features/autostart/` 7 文件 + `AndroidManifest.xml` `BOOT_COMPLETED` receiver |
| Core 版本 | `settings.gradle.kts` 默认 `includeBuild(../Qwn/OwnTV_Core)`（`mobile` @ `b3549819`，Room v55）；`libs.versions.toml` 的 `owntvCore = "1.0.69"` 仅为 `-Powntv.coreArtifact=true` 回退时的占位版本 |
| 手机端单档案 | `OwnTV_Mobile` 的 `BackupProfiles.kt`、`LocalSyncViewModel.kt`、`SlimPlayerDefaults.kt`；见对齐方案 §6.2 |

---

## 9. 与其他文档的关系

| 文件 | 职责 |
|---|---|
| `docs/alignment-to-own-project-zh.md` | 两端并行维护的决策、边界、分阶段实施与风险（上位文档） |
| **本文** | TV 端多档案能力清单、实现位置与回归契约 |
| `docs/features-zh.md` | 全量功能整理与代码位置映射（§8 档案、§9.2 设置） |
| `docs/settings-simplification-zh.md` | 设置 8 组收敛的规格说明 |
