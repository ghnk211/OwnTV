# OwnTV 与 OwnTV_Mobile 两端并行维护方案

> 原题：当前分支「保留多档案 · 其余功能对齐 Own 项目」改造方案
> 编写日期：2026-11-14
> 修订日期：2026-11-14（v2）
> 需求：TV 端保留多档案；TV 专属功能只在 `OwnTV` 端实现，不进入手机端
> 决策：**两端并行维护，不做跨端回填，不做 TV 专属能力移植**
> 依据：`Own/项目说明.txt`（10 条）＋ 三个仓库的实际代码、文档与 2026-11-14 实测
> 性质：职责边界与维护方案，不含代码改动。

---

## 0. 结论先行

### 0.1 一句话

**`OwnTV`（TV 端）与 `OwnTV_Mobile`（手机 / 平板端）各自独立演进：**
TV 端继续维护多档案与 TV 专属能力，并把设置收敛到 8 组；
手机端保持现有单档案、触摸优先的产品形态；
两端共享的只有 `OwnTV_Core`，app 层不共享、不回填、不互相移植。

### 0.2 与原始需求的关系

原始需求是「当前分支保留多档案，其他功能对齐 `Own` 项目」。v2 明确把它拆成两件事，并给出不同结论：

| 需求片段 | v2 处理 | 说明 |
|---|---|---|
| 当前分支保留多档案 | **保留在 TV 端** | TV 端多档案能力完整，不回填到手机端 |
| TV 专属功能保留 | **只在 `OwnTV` 端实现** | 自启、遥控器快捷键、频道翻页等不移植到 `mobile-mini` |
| 其他功能对齐 Own 项目 | **本轮不执行** | 两端按各自产品定位独立演进；如业务仍要求 TV 端吸收手机端能力，需另立需求重新评估（见 §3.4） |

「其他功能对齐」不再执行，不是遗漏，而是 2026-11-14 的明确决策。v1 曾论证过反向移植的成本，结论仍然成立：
两端包名、导航、组件库、依赖底座全不同，TV 端若要吸收 TVBox（CMS / JS / PY）、下载接入 Core、搜索并入、
直播分类菜单等 42 个提交的能力，等于重写整个 app 层，并同时处理 Core `1.0.69` 与 vendor `mobile` 的不兼容改动。
这部分分析保留在 §2.2、§2.3 与 §8，作为「重新立项时的成本基线」，不再作为施工计划。

### 0.3 三条边界

1. **产品边界**：TV 端面向 leanback / D-pad / 遥控器；手机端面向触摸 / 底部栏 / 手势。两端 UI 与交互不做统一。
2. **能力边界**：多档案与 TV 专属能力只属于 TV 端；TVBox 点播源、下载接入 Core、三语言、单档案应用锁只属于手机端。
3. **共享边界**：只有 `OwnTV_Core` 是共享资产。Core 改动只走 `OwnTV_Core/mobile` 分支；两端 app 代码互不引用。

### 0.4 v1 → v2 修订摘要

| v1 内容 | v2 处理 | 原因 |
|---|---|---|
| 以 `mobile-mini` 为主干、回填多档案 9 项（v1 §3.1、v1 §5 施工单） | **删除** | 决策改为两端并行，手机端保持单档案现状 |
| 把自启 / 遥控快捷键 / 频道翻页移植到 `mobile-mini`（v1 §3.3、v1 §6 第 12–14 项、v1 P4） | **删除** | TV 专属功能只在 `OwnTV` 端实现 |
| `QwnTV-mini` 冻结为只读捐赠分支（v1 §0.1、v1 §4、v1 §9） | **改为** TV 端持续维护分支 | 两端并行后 TV 端仍是交付主线之一 |
| 当前分支「不要求可编译」（§2.1、P0） | **改为** TV 端必须可编译 | 持续维护的前提是可构建、可回归 |
| 设置 12 组 → 8 组「工作区正在改」 | **改为** 工作区已收敛为 8 组，待提交 | 2026-11-14 实测 `SettingsGroup.kt` 已是 8 组 |
| 当前分支 10 个已改 + 8 个未跟踪 | **改为** 11 个已改 + 8 个未跟踪 | 实测 `git status` |
| Manifest `BOOT_COMPLETED` receiver `387-395` | **改为** `391-397` | 实测 `AndroidManifest.xml` |
| v54 → v55 表述 | **补注**：Core `mobile` 仍 v54，v55 在 `vendor` 工作区 | 避免把未提交快照当成 Core 分支事实 |
| 多档案相关约 1025 行 | **改为** 约 980 行 | 实测 `features/profiles/` 820 行 + `ProfileSettingsRows.kt` 162 行 |

---

## 1. `项目说明.txt` 的硬约束与适用范围

| # | 约束 | 对本方案的影响 |
|---|---|---|
| 1 | `OwnTV_Mobile` 同步 `ghnk211/OwnTV_Mobile` 最新代码 | 手机端仍以 fork 最新 `mobile-mini` 为基线 |
| 2 | `OwnTV_Core` 同步 `ghnk211/OwnTV_Core` 最新代码 | Core 只从自己的 fork / 本地 `mobile` 取，不碰 `upstream` |
| 3 | 只改 `OwnTV_Mobile` 的 `mobile-mini`、`OwnTV_Core` 的 `mobile`；`vendor/OwnTV_Core` 是 Core `mobile` 的快照 | **仅覆盖 `Own` 工作区内的两个仓库**；`OwnTV` 是独立 TV 仓库，不在该条约束内（见下方说明） |
| 4 | 提交流程见 `OwnTV_Mobile/docs/Core源码集成方案.md` | 仅手机端与 Core 遵循；TV 端不引入 vendor 机制 |
| 5 | 正式包 keystore 在 `Own/keyStore` | TV 端出正式包时同样使用该目录 |
| 6 | 模拟器流程见 `安卓模拟器使用流程.md` | 两端回归均可使用 |
| 7 | JDK、Gradle 在 E 盘 | 两端共用工具链，构建前先核对 |
| 8 | 需求完成后清理构建文件（脚本除外） | 两端各自清理 `build/`、`.gradle/`、`.kotlin/`、临时 APK / 截图 |
| 9 | 测试直播源 / 点播源在 `E:\develop\androidPoject\yuan` | 两端验收共用数据源 |
| 10 | 真机安装需锁屏密码 `0000` | 两端真机环节 |

**关于第 3 条的适用范围（v2 新增）**

`项目说明.txt` 位于 `Own` 工作区，第 1–10 条约束的对象是 `OwnTV_Mobile` 与 `OwnTV_Core`。
`OwnTV`（TV 端）是独立仓库，远程为 `ghnk211/OwnTV`，不参与 `vendor` 快照机制。
因此「TV 专属功能只在 `OwnTV` 端实现」与第 3 条不冲突，但**建议在 `项目说明.txt` 补一条显式约定**：

> 11. `OwnTV`（TV 端）独立维护：多档案与 TV 专属能力只在 `OwnTV` 实现；两端不做跨端功能回填或 UI 移植。

在补充该条之前，TV 端改动按本方案 §9 的分支规范执行，不触碰 `Own` 工作区的两个仓库。

---

## 2. 现状盘点（2026-11-14 实测）

### 2.1 三个仓库与分支

| 仓库 | 分支 / HEAD | 工作区 | 角色 |
|---|---|---|---|
| `E:\develop\ideaPoject\git\OwnTV` | `QwnTV-mini` @ `9a05e570`（领先 `main` 4 提交） | 11 个已改 + 8 个未跟踪，设置 8 组对齐已改完待提交 | **TV 端主线** |
| `E:\develop\ideaPoject\git\Own\OwnTV_Mobile` | `mobile-mini` @ `0805cf5b`（领先 `main` 42 提交） | 「TVBOX 下载接入 Core」P0 改动待收口 | **手机端主线** |
| `E:\develop\ideaPoject\git\Own\OwnTV_Core` | `mobile` @ `e0556f0f`，Room `version = 54`，`coreVersion 1.0.70` | 干净 | **两端共享 Core 主线** |

TV 端 4 个 mini 提交：`c53612ce`（横屏打开 / 中文 only / 阿里云镜像）、`e51d482b`（删天气）、
`ff9e454e`（导航溢出滚动）、`9a05e570`（Glass 与 accent 收敛）。

### 2.2 两端能力边界矩阵

| 功能域 | `OwnTV`（TV 端） | `OwnTV_Mobile`（手机端） | 归属结论 |
|---|---|---|---|
| 交互形态 | leanback / D-pad / 遥控器 / 横屏 | 触摸 / 底部栏 / 平板 rail / 横屏双栏 | 各端独立 |
| 档案 | **多档案完整**（选人门、增删改、Kids、PIN、下载权限、每档案启动页） | 单档案 + 应用锁 PIN（有意设计） | **多档案只属 TV 端** |
| 设置 | 工作区已收敛为 8 组 + `App › Profiles` 叶子页 | 8 组 + `SettingItem` 行目录 + slug 路由 + 派生计数 / 搜索 / Quick | 各自 8 组，行模型不共享 |
| 点播源 | 仅 Xtream / M3U / Stalker | + TVBox CMS、drpy2 JS（QuickJS）、PY（Chaquopy）、换源、筛选 JSON | 手机端独有 |
| 点播数据 | Core 常规目录 | + `SourceType.TVBOX`、索引目录、Core 身份（P0 已完成）；历史 / 进度 / 搜索 / 儿童条目级过滤（P1–P5 待推进） | 手机端独有 |
| 下载 | 有下载能力（app 层） | 已接入 Core（vendor v55） | 实现独立 |
| 搜索 | 全局搜索；点播内搜索未并入 | 点播搜索并入全局搜索 | 手机端独有 |
| 直播 | 频道列表 + 分类筛选 + 遥控器频道翻页 | 分类页点分类即播、播放页左三列 / 长按设置 | 各端独立；频道翻页属 TV |
| 语言 | 中文 only | en / zh-CN / zh-TW | 各端独立 |
| Sports / 天气 | **Sports 仍在**；天气已删 | 两者皆无 | 不跨端拉齐；TV 端 Sports 保留现状 |
| 开机 / 唤醒自启 | `features/autostart/` 7 文件 + Manifest receiver | **零实现** | **只属 TV 端** |
| 遥控器快捷键 / 频道翻页 / 菜单文字 | `RemoteShortcutLabels.kt`、`ChNavSettingsScreen.kt`、`RemoteShortcutInput.kt`、`ChNavPaging.kt` | **零实现** | **只属 TV 端** |
| 面板 / 指南宽度、Cinematic、应用图标、DRM、多画面 | 有（多 launcher activity、`MultiviewDialogs.kt` 等） | 部分精简 | 各端按自身形态保留 |
| Core | Maven `1.0.69`（可 `-Powntv.corePath` 换源码） | `vendor/OwnTV_Core` 源码，`includeBuild`，`1.0.70` | 共享，但接入方式不同 |
| i18n | 中文单语言 + `tools/i18n` 基线 | 3 语言 + `verifyI18nLiterals` + `strings_mobile.xml` | 各端独立 |

### 2.3 Core 底座差异与长期并行的影响

| 项 | TV 端 | 手机端 |
|---|---|---|
| 依赖方式 | Maven `tv.own.owntv:core:1.0.69`，`owntv.corePath` 可替换为源码 | `includeBuild("vendor/OwnTV_Core")` 无条件替换 |
| Room | 上游 v53（v1 记录，未解包 1.0.69 制品复验） | `mobile` v54 → vendor 工作区 v55（下载改造） |
| 模块 | 含 Sports / 多语言资源（天气已在 mini 提交中删除） | 已删 Sports / 天气，只留 3 语言 |
| 新增能力 | — | `TVBOX` 源、索引目录、`extKey`、下载源解析 |
| 字符串 | 全在 Core 的 24 个 `values-*` | Core 3 语言 + app 侧 `strings_mobile.xml` 覆盖 |

**关键事实**：本机 `E:/develop/ideaPoject/git/` 下**没有** `OwnTV_Core` 上游检出，只有 `Own/OwnTV_Core`。
后者已删 Sports / 天气 / 多语言，**不能**直接通过 `-Powntv.corePath` 替换 TV 端用的 `1.0.69`：
TV app 仍引用 Sports、Weather、多语言资源与部分设置键，替换后会编译失败。

因此两端并行后的 Core 策略是：

1. `OwnTV_Core/mobile` 是唯一共享 Core 主线，手机端通过 vendor 快照跟随。
2. TV 端默认固定 `1.0.69`，**不随 `mobile` 分支自动升级**；日常只改 app 层。
3. TV 端升级 Core 属于独立工作项：需单独立项，评估 Sports / Weather / 多语言 API 删除的适配成本，
   并在 TV 端完成 `app` 层迁移后再升级版本号。
4. 不允许两端在同一时间各自修改 Core 的非兼容 API；Core 变更仍只在 `mobile` 分支提交并记录 `docs/core-changes.md`。

### 2.4 多档案在两端的状态

| | `OwnTV`（TV 端） | `OwnTV_Mobile`（手机端） |
|---|---|---|
| 数据 / 行为 | Core：`ProfileManager`、`ProfileDao`、`profileGateRequired`、`downloadsAllowed` | **同样存在**（Core `mobile` 未删） |
| ViewModel | `features/profiles/ProfilesViewModel.kt`（149 行，create / edit / delete / switch / setKids 全量） | `ui/profiles/ProfilesViewModel.kt`（只剩读取与 `verifyPin`） |
| 启动门 | `MainActivity.kt` + `ProfileGate.kt`（148 行，`androidx.tv.material3`）、`ProfileGateSessionViewModel.kt`（114 行） | `MobileShell.kt` 只对单档案 PIN 弹 `ProfileGate` |
| 编辑 UI | `ProfileComponents.kt`(237)、`ProfilePictureChooser.kt`(67)、`PinLockedScreen.kt`(89)、`ProfileSettingsRows.kt`(162) | `ProfileSheets.kt`（已删增删改）、`SettingsAppLock.kt`（只改 PIN / Kids） |
| 设置入口 | `Settings › App › Profiles` 叶子页（`SettingsProfilePage.kt`，工作区新增），8 组内一行 | 无 `Profiles` 页；档案锁在 `App` 组 |
| 单档案守卫 | 无 | `BackupProfiles.kt:102`（`Block.MULTIPLE`）、`LocalSyncViewModel.kt:399`、`SlimPlayerDefaults.kt:152` 的单 profile 假设 |

TV 端多档案相关约 **980 行**。手机端的单档案不是缺陷，而是 `057ea24f` 之后的有意产品选择；
v2 **不解除**这些守卫，也不恢复档案 CRUD。

### 2.5 TV 端专属能力清单（保留现状，不做移植）

| 能力 | 实现位置 | 维护要求 |
|---|---|---|
| 开机自启 / 唤醒自启 | `features/autostart/`：`AutoStart.kt`、`AutoStartLaunchMark.kt`、`AutoStartOptions.kt`、`AutoStartPolicy.kt`、`AutoStartProfileGate.kt`、`AutoStartReceiver.kt`、`WakeListenerService.kt`；`AutoStartSettingsRows.kt`；Manifest `391-397` | 自启后必须先过档案门与 PIN，再按档案启动页进入 |
| 遥控器快捷键 | `RemoteShortcutLabels.kt`、`RemoteShortcutInput.kt`、`ChNavSettingsScreen.kt` | 仅外接键盘 / 蓝牙遥控器接入时生效；不干扰焦点体系 |
| 频道翻页 / 菜单文字 | `ChNavPaging.kt`、`MenuLabels.kt`、`MenuLabelsOption.kt`、`RemoteShortcutLabels.kt` 的键位提示 | 保留现有键位映射；设置页内提供说明 |
| 面板 / 指南列宽、Cinematic 布局 | `PanelWidthSettingsScreen.kt`、`GuideWidthSettingsScreen.kt`、Layout / Movies 页 | 保留参数行，入口在 `Appearance › Layout` |
| 应用图标切换 | 多个带 `LAUNCHER` / `LEANBACK_LAUNCHER` 的 launcher activity（Manifest 多组） | 沿用现有 `ui/components/AppIconPicker.kt` |
| DRM、多画面、每档案启动页 | `MultiviewDialogs.kt`、Player / Watching / Profile | 保留能力，参数行并进现有 8 组 |
| 代理 / DNS、Diagnostics、应用锁 PIN | `NetworkSettingsScreen.kt`、`DnsSettingsScreen.kt`、`DeveloperScreen.kt`、应用锁 | 沿用现有实现 |

以上能力**只在 `OwnTV` 仓库演进**。手机端不新增这些入口，不复制键位表，不改触摸交互。

---

## 3. 职责边界

### 3.1 `OwnTV`（TV 端）

- 继续作为 TV 交付主线：leanback / D-pad、多档案、TV 专属能力、中文单语言。
- 设置保持 8 组；`App › Profiles` 一个叶子页承载全部档案级能力，不新增顶层分组。
- Core 固定 `1.0.69`，除非单独立项升级。
- 日常改动只触碰 app 层；不引入 `vendor` 快照机制，不改 `Own` 工作区的两个仓库。

### 3.2 `OwnTV_Mobile`（手机端）

- 保持现有产品形态：单档案 + 应用锁 PIN、触摸优先、8 组设置、3 语言。
- 继续维护 TVBox 点播源（CMS / JS / PY）、下载接入 Core、全局搜索、直播分类菜单。
- **不新增**多档案 UI、多档案备份 / 同步、TV 专属能力入口。
- 继续按 `项目说明.txt` 第 3、4 条在 `mobile-mini` 与 `OwnTV_Core/mobile` 上工作。

### 3.3 共享 Core

- `OwnTV_Core/mobile` 是唯一共享主线；手机端通过 `vendor` 快照跟随，TV 端通过固定版本制品跟随。
- Core 的兼容性承诺只覆盖手机端当前使用的 API；TV 端升级 Core 时需自行完成适配。
- 新增 Core 能力默认先服务手机端；若 TV 端也需要，按「TV 端独立升级项」处理，不做跨端 app 代码共享。

### 3.4 明确不做，以及重新评估的触发条件

**本轮不做**：

- 不把多档案回填到 `mobile-mini`，不解除 `BackupProfiles.MULTIPLE` / `LocalSyncViewModel` / `SlimPlayerDefaults` 的单档案假设。
- 不把自启、遥控快捷键、频道翻页、菜单文字、面板 / 指南宽度、Cinematic、应用图标、DRM、多画面移植到手机端。
- 不把 TVBox / JS / PY 源、下载接入 Core、搜索并入、直播分类菜单移植到 TV 端。
- 不统一 `applicationId`，不做旧 TV 包数据迁移，不做旧备份跨端恢复。
- 不统一两端的设置行模型、导航、组件库与语言集合。

**只有同时满足以下条件，才重新评估跨端移植**：

1. 业务明确要求 TV 端具备手机端的某项能力（例如 TV 端要接 TVBox 点播源），且愿意承担 app 层重写成本；
2. 该项能力不依赖触摸手势或手机端专属组件；
3. Core 版本差异已单独立项解决；
4. 有独立的分阶段计划与验收口径，而不是并入本次维护。

---

## 4. 维护路线

### 4.1 版本与发布

| 端 | applicationId | 版本基线 | 签名 | 发布物 |
|---|---|---|---|---|
| TV | `tv.own.owntv` | `QwnTV-mini` HEAD + 设置 8 组收尾 | `Own/keyStore` | TV APK |
| 手机 | `tv.own.owntv.mobile` | `mobile-mini` 收口后的 HEAD | `Own/keyStore` | 手机 APK |

两端 `applicationId` 不同，数据目录天然隔离。TV 端继续按新装基线维护，不承诺读取手机端数据，反之亦然。

### 4.2 分支

- TV 端：`OwnTV` 仓库的 `QwnTV-mini`（当前分支）；需要并行开发时从该分支切功能分支，合回后继续维护。
- 手机端：`OwnTV_Mobile/mobile-mini`。
- Core：`OwnTV_Core/mobile`；`vendor/OwnTV_Core` 只由 subtree 更新。

### 4.3 允许的跨端改动

只有 Core 是允许的跨端改动通道。任何「把 A 端 app 代码搬到 B 端」的动作都必须先经过 §3.4 的重新评估，
不允许以「顺手对齐」的名义直接搬运。

---

## 5. TV 端维护清单（核对现状，不新增功能）

### 5.1 设置 8 组收尾

工作区已完成的改动（待提交）：

- `SettingsGroup.kt`：12 组收敛为 8 组（`QUICK / SOURCES / PLAYER / SOUND / LIVE / WATCHING / APPEARANCE / APP`）。
- `SettingsPage.kt`（新增）：组页 / 行目录。
- `SettingsProfilePage.kt`（新增）：`Settings › App › Profiles` 叶子页，档案 CRUD / Kids / PIN / 下载权限全部收进该页。
- `SettingsStage.kt`、`SettingsScreen.kt`、`MorePages.kt`、`MoreScreen.kt`、`VideoPlayerSettingsScreen.kt`：
  行入口、计数、搜索与跳转适配。
- `ProfilesViewModel.kt`、`SettingsSearchCoverageTest.kt`、`docs/features-zh.md`、
  `tools/i18n/hardcoded_baseline.txt`、`tools/i18n/safe_literals.txt`：配套调整。

收尾要求：

1. 提交前跑 TV 端编译与单测，确认 8 组、行目录、搜索覆盖、i18n 门禁通过。
2. 确认 `App › Profiles` 是唯一档案入口，未新增顶层分组、未新增手写搜索条目、未把 `SettingsHelpTable` 扩成按页手写文案表。
3. 更新 `docs/features-zh.md` §9.2 为最终形态。

### 5.2 多档案

TV 端多档案能力保持完整，不在本轮新增或删减：

| # | 能力 | 现状 |
|---|---|---|
| 1 | 多档案创建 / 改名 / 头像 / 删除 | 完整（`ProfilesViewModel` + `ProfileComponents`） |
| 2 | 「谁在看」选人门 + 会话内切换 | 完整（`ProfileGate` + `ProfileGateSessionViewModel`） |
| 3 | 每档案 PIN 锁、错误冷却 | 完整（`PinLockedScreen` + Core `PinAttemptLimiter`） |
| 4 | Kids 模式（核心目录的条目级过滤） | Core 已具备；TV 端按现有目录链路生效（TV 端无 TVBOX 内容） |
| 5 | 每档案独立收藏 / 历史 / 续播 / 布局 | Core 按 `profileId` 隔离 |
| 6 | 每档案播放列表关联 | `ProfileSourceCrossRef` |
| 7 | 每档案下载权限 | `downloadsAllowed(profileId)` |
| 8 | 每档案启动页 / Quick 钉选 | `SettingsRepository` 偏好键 |
| 9 | 备份 / 本地同步支持多档案 | 现有实现原生支持 `profiles[]` |

本轮只做回归核对，不改行为。若后续要改多档案，按 TV 端功能分支提交，不涉及手机端。

### 5.3 TV 专属能力

保持 §2.5 的实现与入口。回归重点：

- 自启后先过档案门与 PIN，再进入当前档案的启动页或直播；
- 外接键盘 / 遥控器快捷键不干扰焦点移动与触摸设备；
- 频道翻页在直播页与指南页行为一致；
- 应用图标切换后 `LEANBACK_LAUNCHER` 入口正常。

### 5.4 不清理项

- TV 端 **Sports 模块保留**：不因手机端已删而删除；是否下线由 TV 产品单独决定。
- TV 端中文单语言保留：不恢复 26 语言，也不引入手机端三语言。
- TV 端 `SettingsHelpTable` 保留现状：它是设置行的文案 / 帮助映射，不是手机端要收敛的「手写搜索条目」，不扩表也不删除。

---

## 6. 手机端维护清单（保持现状）

### 6.1 现有能力

- TVBox 点播源：CMS、drpy2 JS（QuickJS）、PY（Chaquopy）、换源、筛选 JSON。
- 点播接入 Core：P0 已完成（索引底座 / 收藏闭环 / 播放路由）；P1–P5（历史 / 进度 / 搜索 / 儿童条目级过滤等）按既有方案推进。
- 下载接入 Core（vendor v55）、`DownloadSourceResolver`、`HlsUrls`。
- 点播搜索并入全局搜索、直播分类菜单。
- 8 组设置 + `SettingItem` 行目录 + slug 路由 + 派生计数 / 搜索 / Quick。
- 三语言（en / zh-CN / zh-TW）与 i18n ratchet。

### 6.2 单档案是有意设计

- `ProfilesViewModel` 只保留 `profiles`、`activeProfileId`、`verifyPin` 三个读取。
- `MobileShell` 的 `ProfileGate` 只处理当前档案 PIN，不渲染选人门。
- `BackupProfiles.kt:102` 明确拒绝多档案备份（`Block.MULTIPLE`）；
  `LocalSyncViewModel.kt:399` 对多档案同步给出 `mobile_multi_profile_sync`；
  `SlimPlayerDefaults.kt:152` 按单 profile 处理语言偏好。
- 这些不是待修缺口。若手机端将来要支持多档案，需单独立项，并同时改备份 / 同步 / 播放默认值 / 设置页。

### 6.3 收口基线

以「TVBOX 下载接入 Core」P0 收口后的 `mobile-mini` HEAD 为准：

- P0 已完成的 Core v55 数据层、下载链路与 App 侧入口；
- `vendor` 工作区改动仍需回推 `OwnTV_Core/mobile` 并更新 `docs/core-changes.md`；
- 收口前不并行开始其他跨端工作。

### 6.4 不新增项

- 不新增多档案 UI、档案 CRUD、选人门。
- 不新增自启、遥控快捷键、频道翻页、菜单文字、面板宽度、Cinematic、应用图标、DRM、多画面入口。
- 不为 TV 端保留「兼容分支」或条件编译开关。

---

## 7. 分阶段实施

### P0 · 基线登记与归档（两端先做，不动功能）

1. 两端各自 `git fetch`，记录 HEAD；确认 `mobile-mini` 是 P0 收口后的可编译基线。
2. **TV 端可编译验证**（v2 新增硬门槛）：用 `1.0.69` 制品跑一次
   `:app:compileStandardDebugKotlin`，确认工作区 11 个已改 + 8 个未跟踪改动可构建。
   若 Maven 制品解析失败，先解决依赖通道，**不要**用 `Own/OwnTV_Core` 顶替 `1.0.69`。
3. **TV 端成果归档**：把设置 8 组改动导出为文档 + patch，记录基线 commit hash；
   归档的是规格与代码参考，不是发布包。tag 由人工执行，不纳入自动流程。
4. 产出本文与 `docs/multi-profile-contract-zh.md`（TV 端多档案能力清单）。
5. 验收：三仓 `git status` 有明确记录，基线 commit hash 写进文档，无构建产物残留（说明第 8 条）。

### P1 · TV 端设置 8 组收尾

- 提交 §5.1 的 11 个改动 + 2 个新增页面文件，确认 `SettingsGroup.kt` 为 8 组。
  `docs/features-zh.md` 与两份 i18n 基线随功能提交一并落库；本轮新增的方案文档单独走 `docs:` 提交。
- 跑 TV 端编译、单测、i18n 门禁；更新 `docs/features-zh.md`。
- 验收：8 组、`App › Profiles` 一行一页、搜索覆盖通过、无新增顶层行。

### P2 · 两端回归（不改功能）

- TV 端：多档案 9 项、自启两条路径、遥控快捷键、频道翻页、图标切换、8 组设置。
- 手机端：TVBox 三源、下载接入 Core、搜索、直播分类、8 组设置、三语言。
- 验收：两端各自回归矩阵通过，无串档、无崩溃。

### P3 · 文档与流程

- 更新 `OwnTV/docs/features-zh.md`、`OwnTV/README.md`（TV 端能力与边界）。
- 更新 `OwnTV_Mobile/docs/features-zh.md`（单档案是有意设计）。
- 建议在 `Own/项目说明.txt` 补第 11 条（见 §1）。
- 验收：文档不再把「回填 / 移植 / 冻结」列为工作项，两端职责边界表述一致。

### P4 · 发布与清理

- TV 端用 `Own/keyStore` 出正式包，核对签名与 `applicationId=tv.own.owntv`。
- 手机端按既有流程出包，核对 `applicationId=tv.own.owntv.mobile`。
- 按说明第 8 条清理构建产物；只保留脚本与文档。
- 验收：两端安装包可装、可启动；仓库无临时 APK / 截图 / patch。

---

## 8. 风险与对策

| # | 风险 | 对策 |
|---|---|---|
| R1 | 两端 `applicationId` 不同，数据目录隔离 | 按新装基线处理，不迁移旧 TV 包数据；两端备份不跨端恢复 |
| R2 | Core 版本分叉：TV `1.0.69` vs 手机 `1.0.70` / vendor v55 | TV 端固定版本，不自动升级；升级单独立项并评估 Sports / Weather / 多语言 API 删除 |
| R3 | 本机无 `OwnTV_Core` 上游检出，误用 `Own/OwnTV_Core` 顶替 `1.0.69` | TV 端构建只用 Maven 制品；`owntv.corePath` 不指向 `Own/OwnTV_Core` |
| R4 | TV 端工作区 11 改 + 8 未跟踪成果被误回滚 | P0 先导出 patch + tag；禁止 `checkout --` / `reset --hard` |
| R5 | TV 端长期不吸收手机端功能，能力差距扩大 | 接受该结果；业务确有需要时按 §3.4 重新立项 |
| R6 | 两端设置都叫 8 组，但行模型 / slug / 计数实现不同 | 明确「各自 8 组、不共享行模型」；文档不把两端设置混为一套 |
| R7 | TV 端 `androidx.tv` UI 与手机端 Material 3 混用 | 各端只用自己的组件库；禁止把 TV 焦点组件带进手机端 |
| R8 | TV 端 Sports / 多语言与 Core `mobile` 已删资源不一致 | TV 端不升级 Core；如升级，先盘点 Sports / Weather / 多语言引用 |
| R9 | `Own/项目说明.txt` 第 3 条被误读为禁止改 `OwnTV` | §1 明确适用范围；建议补第 11 条 |
| R10 | 手机端 TVBox / Chaquopy / QuickJS 与 Core 升级叠加 | 先收口「TVBOX 下载接入 Core」P0，再做 Core 改动；不并行叠加 |
| R11 | TV 端开机 / 唤醒自启受 Android 12+ 后台启动与厂商白名单限制 | 用 `BOOT_COMPLETED` + 前台服务 / 全屏 intent 合规路径；提供厂商白名单引导；自启后必须过档案门与 PIN |
| R12 | 遥控器快捷键在纯触摸设备无物理按键，或与系统键冲突 | 仅在外接键盘 / 蓝牙遥控器接入时生效；映射表限定 spare 键；无设备时该行给出说明而非隐藏能力 |
| R13 | TV 端设置再次被撑回 12 组 / 多套行模型 | §5.1 为硬约束：只准在既有 8 组内加行或叶子页；P1 用分组数与行模型一致性做门禁 |
| R14 | 两端分别发布后版本号 / 签名混乱 | 统一 keystore 目录；两端各自版本号独立递增；发布前核对 `applicationId` |

---

## 9. 分支与提交规范

- TV 端改动只在 **`OwnTV` 仓库的 `QwnTV-mini`**；手机端只在 **`OwnTV_Mobile/mobile-mini`**；
  Core 只在 **`OwnTV_Core/mobile`**；`vendor/OwnTV_Core` 只由 subtree 更新。
- TV 专属能力（自启、快捷键、频道翻页）在 `OwnTV` 实现，不在 `OwnTV_Mobile` 另开分支。
- 一条功能一个提交，前缀区分：`feat(tv-settings):`、`feat(profiles):`、`feat(autostart):`、
  `feat(shortcuts):`、`fix(backup):`、`core-add:`、`docs:`。
- Core 改动循环（仅手机端与 Core）：本地改 → Core 自身 test/lint/i18n → 提交 →
  `git push origin mobile` → `git subtree pull --prefix=vendor/OwnTV_Core <fork> mobile --squash` →
  Mobile 验证矩阵 → 记 `docs/core-changes.md`。`subtree pull` 前 `git status --short` 必须为空。
- 两端各自 `fetch`；TV 端按 `项目说明.txt` 第 1、2 条只同步自己的 fork，不碰手机端仓库。

---

## 10. 验收矩阵

| 层 | 检查 | 通过标准 |
|---|---|---|
| TV 构建 | `:app:compileStandardDebugKotlin` | 全绿，`1.0.69` 制品可解析 |
| 手机构建 | `compileStandardDebugKotlin` / `assembleStandardDebug` / `assembleStandardRelease` | 全绿；Release 需先注入 `Own/keyStore` 的签名属性，未注入时产物为 unsigned，不能记为通过 |
| Core | `./gradlew -p ../OwnTV_Core test lint` + vendor 逐文件一致 | 全绿，无手工编辑差异 |
| TV 设置 | 分组数、行目录、`App › Profiles` 叶子页、搜索覆盖 | 8 组、单一档案入口、无手写搜索条目 |
| TV 多档案 | 创建 / 切换 / 删除 / PIN / Kids / 独立收藏历史 / 源归属 / 下载权限 / 启动页 / 备份 / 同步 | 真机 + 模拟器通过，无串档 |
| TV 专属 | 开机自启、唤醒自启、自启后档案门、外接键盘快捷键、频道翻页、图标切换 | 真机通过；不干扰焦点与触摸设备 |
| 手机端 | §6.1 能力逐项 + 单档案锁定 | 与 `mobile-mini` 现状一致；多档案仍被明确拒绝 |
| 数据 | 两端各自安装 / 升级；TV 端不读手机端数据 | 无崩溃、无串档 |
| 卫生 | 构建产物、临时截图 / APK / patch 清理 | 仓库只留源码、脚本、文档 |

---

## 11. 修订记录

### v2（2026-11-14）

- 决策变更：从「以 `mobile-mini` 为主干、回填多档案 + 移植 TV 专属能力」改为「两端并行、互不回填」。
- 删除 v1 §3.1 的多档案回填目标、§5 的 12 步施工单、§6 第 12–14 项、P4 的移植任务。
- 删除 v1 §3.3「TV 专属能力按 8 组收纳到 mobile」整节，改为 §2.5 / §5.3「TV 端保留现状」。
- v1 的 R7 / R11 / R12 / R13 保留编号并按两端并行重写，不再包含跨端移植相关表述。
- `QwnTV-mini` 从「冻结的捐赠分支」改为「TV 端持续维护分支」；删除「不要求可编译」结论。
- 修正事实：设置工作区已 8 组、已改 11 个文件、Manifest receiver `391-397`、多档案约 980 行、
  Core `mobile` 仍 v54 而 vendor 工作区 v55。
- 新增：§1 第 3 条适用范围与建议第 11 条、§2.3 Core 升级规则、§3.4 重新评估触发条件、§4.3 跨端改动规则。

### v1（2026-11-14）

- 初版：以 `mobile-mini` 为主干，提出多档案 9 项回填与 TV 专属能力移植的完整施工方案。
- 保留价值：§0.2 的两端成本分析、§2.3 的 Core 差异盘点，作为后续重新立项时的参考。

---

## 附录 A · 基线证据

| 事实 | 位置 / 命令 |
|---|---|
| TV 端 4 个 mini 提交 | `git log main..QwnTV-mini`（`c53612ce` / `e51d482b` / `ff9e454e` / `9a05e570`） |
| TV 端设置 8 组（工作区已改） | `features/shell/components/SettingsGroup.kt`：8 个枚举值 |
| TV 端新增设置页 | `features/settings/SettingsPage.kt`、`SettingsProfilePage.kt` |
| TV 端多档案实现 | `features/profiles/` 820 行 + `features/shell/components/ProfileSettingsRows.kt` 162 行 |
| TV 端自启实现 | `features/autostart/`（7 文件）、`AutoStartSettingsRows.kt`、`AndroidManifest.xml:391-397` |
| TV 端快捷键实现 | `RemoteShortcutLabels.kt`、`ChNavSettingsScreen.kt`、`RemoteShortcutInput.kt`、`ChNavPaging.kt` |
| 手机端零实现（TV 专属） | 在 `OwnTV_Mobile/app/src` 搜索 `BOOT_COMPLETED`、`AutoStart`、`remoteShortcut`、`ch_paging` 均无结果 |
| 手机端单档案守卫 | `backup/BackupProfiles.kt:102`、`ui/screens/settings/LocalSyncViewModel.kt:399`、`playback/SlimPlayerDefaults.kt:152` |
| TV 与手机 Core 版本 | `OwnTV/gradle/libs.versions.toml:82` = `1.0.69`；`OwnTV_Mobile` = `1.0.70` |
| Core Room 版本 | `OwnTV_Core/core/.../OwnTVDatabase.kt:126` = 54；`OwnTV_Mobile/vendor/.../OwnTVDatabase.kt:126` = 55 |
| Core 多档案仍存在 | `OwnTV_Core/core/.../profile/ProfileManager.kt`、`database/entity/ProfileEntities.kt`、`settings/SettingsRepository.kt:781` |
| 两端 applicationId | `OwnTV/app/build.gradle.kts:47`、`OwnTV_Mobile/app/build.gradle.kts:50` |
| 手机端单档案化提交 | `OwnTV_Mobile@057ea24f Slim to three languages, drop Sports, and lock one profile` |
| 分支与提交流程 | `Own/项目说明.txt` 第 3、4 条；`OwnTV_Mobile/docs/Core源码集成方案.md` §4、§6、§7 |
| 本机 Core 检出 | `E:/develop/ideaPoject/git/` 下无 `OwnTV_Core`；只有 `Own/OwnTV_Core`（`mobile` 分支） |

---

## 12. 实施记录（2026-11-14，v2.1）

> 本章记录本文的首次实施结果。正文 §0–§11 是决策依据，予以保留；**与本章冲突的事实以本章实测为准**，
> 待下一版修订时统一回填正文。本轮不改功能代码、不改 Core 版本号。

### 12.1 本机路径映射

文档中的 `E:\develop\ideaPoject\git\` 在本机对应 `D:\AndroidStudioProjects\git\Qwn\`（TV 仓单独检出）：

| 文档路径 | 本机实际路径 |
|---|---|
| `E:\...\git\OwnTV` | `D:\AndroidStudioProjects\git\OwnTV-mini` |
| `E:\...\git\Own\OwnTV_Mobile` | `D:\AndroidStudioProjects\git\Qwn\OwnTV_Mobile` |
| `E:\...\git\Own\OwnTV_Core` | `D:\AndroidStudioProjects\git\Qwn\OwnTV_Core` |
| `E:\...\git\Own\项目说明.txt` | `D:\AndroidStudioProjects\git\Qwn\项目说明.txt` |
| `Own/keyStore` | `D:\AndroidStudioProjects\git\Qwn\keyStroe`（目录名即如此） |
| JDK / Gradle | `D:\jdk`、`D:\android\gradle` |
| 测试源目录 | `C:\Users\Admin\WorkBuddy\2026-11-08-22-47-57` |

### 12.2 P0 基线登记（§7 P0 第 1、5 项）

三仓按 §9 各 `fetch` 自己的 fork（TV `origin`、Mobile `fork`、Core `origin`），均无新提交、工作区无异常：

| 仓库 | 分支 @ HEAD | 状态 |
|---|---|---|
| `OwnTV-mini` | `QwnTV-mini` @ `422181d Settings: fold 12 groups into 8 and open More cards by stable group id` | 实施前干净；本轮仅有文档改动 |
| `Qwn/OwnTV_Mobile` | `mobile-mini` @ `e2e42b5 build: sync vendored core (b3549819)` | 干净，「TVBOX 下载接入 Core」P0 **已收口** |
| `Qwn/OwnTV_Core` | `mobile` @ `b3549819 feat(download): resolve app-owned sources at transfer time` | 干净，Room `version = 55`，`coreVersion 1.0.70` |

`OwnTV_Mobile/docs/core-changes.md` 已记录 Core 改动回填（`e0556f0f..b3549819`）、逐文件校验与
`git subtree pull … --squash` 同步，vendor 快照的 Room 也为 v55。§2.1 的「11 改 + 8 未跟踪 / P0 待收口」
与 §0.4 的「Core mobile 仍 v54，v55 在 vendor 工作区」均已是历史状态。

### 12.3 P0 编译验证（§7 P0 第 2 项）—— **硬门槛未通过**

- 环境：Gradle 9.7.1（工程内 wrapper dist）、AGP 9.4.0 / Kotlin 2.4.20 / `compileSdk 37`、
  JDK 25 工具链。foojay 首次尝试联网下载 JDK 25 失败，改为本地 `D:/jdk/jdk-25` 探测
  （`org.gradle.java.installations.paths` + `auto-download=false`）后工具链正常。
- 命令：`:app:compileStandardDebugKotlin --no-build-cache --offline --console=plain`，
  使用工程内临时 `GRADLE_USER_HOME` 与只读依赖缓存，**未设置 `owntv.corePath`**（遵守 R3）。
- 结果：**FAILED**。`> Task :app:compileStandardDebugKotlin` 真实执行，报出大量 `Unresolved reference`
  （`AppModule.kt:57` 的 `viewModelOf` 无匹配为首因，级联到 `R.string.settings_*`、
  `PlayerRemoteOptions`、`MenuLayout`、`SealedCategoryAction`、`ChannelVersion` 等）。
- 证据：线上 `core-1.0.69.aar` 与本机缓存 sha256 同为 `11a16d0d…f133`，其 `R.txt` **不含**
  `settings_customize_settings_pin` / `player_tool_sources` / `sports_hint_day_matches`，`classes.jar`
  **不含**上述类；这些类在 `Qwn/OwnTV_Core/mobile` 中存在，而该分支已删除 Sports 资源。
  上游 Maven 的 `core` 版本列表已有 `1.0.70`、`1.0.71`（latest `1.0.71`，`lastUpdated 20261109100817`）。
  补验 `1.0.71` 制品（sha256 `ca9c9dfc…d123f1`）：`classes.jar` 已含上述 4 个类，`R.txt` 覆盖抽样 11 个
  缺失字符串中的 10 个（Sports / 帧率 / 菜单文字 / 录制 / 同步 / 播放器源）；仅
  `settings_customize_settings_pin` 仍缺。该引用**不是 8 组改动新增**（最早见 `2cdc3f1`，`422181d`
  只改了它的分组参数），因此 Core 升级立项时还需一并盘点 app 层 / Core 的字符串缺口。
- 结论：**不是缓存、不是网络、不是 8 组改动引入**，而是 `gradle/libs.versions.toml` 固定的
  `owntvCore = "1.0.69"` 落后于当前 app HEAD 所依赖的 Core API。§2.3 / R2 / R3 要求 Core 升级单独立项，
  本轮**不擅自改版本号、不用 `Qwn/OwnTV_Core` 顶替**。
- 诊断性尝试：`-Powntv.corePath=D:/…/Qwn/OwnTV_Core` 走源码复合构建，在依赖解析阶段因 offline 缺
  `tv.own.owntv:libmpv:2026.11.0` 中止；且该分支已删 Sports 资源，按 §2.3 结论也不应作为 TV 端底座。
- 影响：P0 第 2 项记为「未通过」；P1 的编译 / 单测验收、P2 回归、P4 出包均不得记为通过。

### 12.4 门禁结果

| 检查 | 结果 |
|---|---|
| `tools/i18n/check_hardcoded_strings.py verify --bootstrap` | OK（45 baseline + 1427 safe entries） |
| `tools/i18n/check_number_locale.py` | OK（285 Kotlin 文件、3 处已审 display call） |
| `tools/i18n/check_text_overflow.py` | OK（285 Kotlin 文件、0 Clip 例外） |
| Gradle `:app:verifyI18nLiterals` | 成功（编译任务链中执行） |
| `:app:testStandardDebugUnitTest` | **未执行**：受 12.3 编译阻塞，JVM 单测依赖 app 编译产物 |

### 12.5 本轮交付物

| 项 | 位置 | 对应文档条目 |
|---|---|---|
| TV 端多档案契约（新增） | `docs/multi-profile-contract-zh.md` | §7 P0 第 4 项 |
| 功能文档收尾 | `docs/features-zh.md`（§8 / §9.2 / §10 / §13.1 / §15 / 附表） | §7 P3 |
| README 两端边界 | `README.md`「Maintenance boundary」 | §7 P3 |
| 约束补条 | `Qwn/项目说明.txt` 追加第 9 条 | §1 建议项（见 12.7） |
| 设置 8 组 patch 归档 | `.workbuddy/archive/settings-8groups-422181d.patch`（90 KB，仅 app 代码 + i18n 基线）与完整 `format-patch`（298 KB） | §7 P0 第 3 项；本地归档、不进仓库 |
| 实施记录 | 本章 | §7 P0 第 5 项 |

`tag` 按 §7 P0 第 3 项由人工执行，本轮未打 tag。

### 12.6 阻塞与下一步

1. **P0（阻塞项）**：立项处理 TV 端 Core 版本——评估升级 `owntvCore` 到 `1.0.71`（已验证其制品含缺失的
   4 个 Core 类与绝大多数字符串），并补齐 `settings_customize_settings_pin` 等剩余资源缺口；同时核对
   Sports / Weather / 多语言 API，完成迁移后重跑 `:app:compileStandardDebugKotlin` 与 JVM 单测。
   **该项不做，P1–P4 无法验收。**
   → **2026-11-15 已按 §13 变更**：不再升级 `1.0.71` 制品，改为删除 Sports + `Qwn/OwnTV_Core` 源码复合构建，
   编译与 250 个 JVM 单测已通过；本节余下项仍待人工执行。
2. `Qwn/项目说明.txt` 实际只有 8 条（§1 表格按 10 条编写）；§1 建议的「第 11 条」实为**第 9 条**，已按原意补入。
3. `OwnTV_Mobile/docs/features-zh.md` 在手机端仓库中**不存在**，§7 P3 的该项未执行，需确认是新建还是并入其它文档。
4. P2 真机 / 模拟器回归、P4 两端出包与安装验证需设备与人工放行；本机未执行。
5. `app/build.gradle.kts` 的 release 签名属性默认指向 `E:/MEGA/CODE/OwnTV_Gradle/owntv-signing.properties`
   （本机不存在），实际 keystore 在 `Qwn/keyStroe`；出正式包前需注入签名属性，否则产物为 unsigned。
6. 本轮文档改动**未提交**（不 push、不打 tag），待确认后按 §9 以 `docs:` 前缀落库。

### 12.7 正文事实校正清单（下一版修订时回填）

| 正文位置 | 原记载 | 实测 |
|---|---|---|
| §2.1 / §0.4 | TV @ `9a05e57`，11 改 + 8 未跟踪，设置 8 组待提交 | 已提交为 `422181d`，实施前工作区干净，P1 的提交项已完成 |
| §2.1 / §0.4 | Core `mobile` @ `e0556f0f`、Room v54；v55 在 vendor 工作区 | `b3549819`、Room v55；Core 与 vendor 均为 v55 |
| §2.1 | 手机端「P0 改动待收口」 | `e2e42b5` 已收口，`docs/core-changes.md` 有完整记录 |
| §1 | 「项目说明.txt 10 条」+ 建议补第 11 条 | 实际 8 条 + 本轮补第 9 条；keystore 目录名为 `keyStroe`，无第 9/10 条原文 |
| §2.2 / §5.4 | TV 端「中文 only」/「中文单语言」 | `tools/i18n/locales.json` 的 `packaged` 为 3 项（默认英文 + `zh-rCN` + `zh-rTW`）；`c53612c` 删除的是上游 24 个 `values-*` 目录 |
| §2.3 / R2 / R3 | TV 端固定 `1.0.69` 可编译 | 当前 app HEAD 用 `1.0.69` 编译失败（见 12.3）；上游已有 `1.0.70` / `1.0.71` |
| 附录 A | `ProfileSettingsRows.kt` 在 `features/shell/components/` | 实际在 `features/settings/`（162 行） |
| §13.1 相关 | 功能文档写「12 组设置 / 26 语言」 | 已按 8 组与 3 语言修正（见 `docs/features-zh.md`） |

---

## 13. 决策变更（2026-11-15，v3）：Sports 下线 + Core 源码复合构建

> 用户决策：**TV 端改用 `Qwn/OwnTV_Core` 源码复合构建；缺失的 Sports 资源对应功能从本分支直接删除。**
> 本章是最新口径；§0–§12 保留为决策与实施历史，冲突处以本章为准。

### 13.1 与前序结论的关系

| 前序结论 | v3 处理 |
|---|---|
| §0.3 / §2.2 / §5.4「TV 端 Sports 保留、Core 固定 `1.0.69`、不做跨端移植」 | **被取代**：Sports 从 TV 端下线；TV 端不再固定制品，改为与手机端共享 `OwnTV_Core/mobile` 源码 |
| §2.3 / R2 / R3 / R8「Core 升级须单独立项、不用 `Own/OwnTV_Core` 顶替」 | **不再适用**：本次即用户指定的复合构建立项；2026-11-15 起 `includeBuild(../Qwn/OwnTV_Core)` 已固化为 `settings.gradle.kts` 默认行为，无需 `-Powntv.corePath` |
| §12.3 / §12.6 第 1 项「P0 编译门槛未通过，需升级到 `1.0.71`」 | **已解决，但路径不同**：不升制品版本，改为删 Sports + 源码复合构建；P0 硬门槛由本章验收 |
| §3 两端 app 层不共享、多档案只在 TV 端、TV 专属能力不移植、只有 Core 是共享资产 | **继续有效**，本轮未改这些边界 |

### 13.2 构建口径（2026-11-15 二次调整：默认复合构建，零 `-P` 参数）

**不再需要任何 `-P` 参数**：`settings.gradle.kts` 默认 `includeBuild(../Qwn/OwnTV_Core)`
（相对本仓根目录解析，即 `D:/AndroidStudioProjects/git/Qwn/OwnTV_Core`）。

```
gradle :app:compileStandardDebugKotlin :app:testStandardDebugUnitTest
```

- `includeBuild` 按 group/module 替换 `tv.own.owntv:core` / `:player-core`；`gradle/libs.versions.toml`
  的 `owntvCore = "1.0.69"` 只是制品回退路径的占位版本，默认路径下不生效，**未改**。
- 解析规则（`settings.gradle.kts`）：`-Powntv.coreArtifact=true` → 走发布制品（CI / 无 Core 检出的机器）；
  `-Powntv.corePath=<dir>` → 覆盖默认目录；否则自动使用同级 `../Qwn/OwnTV_Core`；
  该目录不存在时打印警告并回退制品（本机存在，不会触发）。
- 本机 `GRADLE_USER_HOME=D:\android\gradle`（用户级环境变量，项目说明第 6 条），其中已有
  JDK 25 探测（`org.gradle.java.installations.paths`）、代理、Gradle 9.7.1 与
  `libmpv:2026.11.0` 缓存，因此裸命令即可构建，不再需要 `GRADLE_RO_DEP_CACHE`。
- 工程内 `.gradle-cache/gradle.properties` 中过期的 `owntv.corePath=D:/AndroidStudioProjects/git/OwnTV_Core`
  （该目录不存在）已删除，避免覆盖默认解析。
- Core 源码：`mobile` @ `b3549819`，Room v55，`coreVersion 1.0.70`；工具链为
  Gradle 9.7.1 / AGP 9.4.0 / Kotlin 2.4.20 / `compileSdk 37` / JDK 25（见 §12.3）。
- 旧的 `1.0.69` / `1.0.71` 制品不再是本分支的构建通道。

### 13.3 删除范围（Sports 下线）

- 整目录删除 `app/src/main/java/tv/own/owntv/features/sports/`：**15 个文件 / 5268 行**
  （Hub / Calendar / Leagues / Match / Team / Settings / ViewModel / Playback 等）。
- 外部引用清理（21 个 Kotlin 文件、共 66 组精确替换）：
  `OwnTVApp`（启动自重建）、`di/AppModule`（3 个 Koin 绑定）、`HomeScreen` / `HomeRowText` /
  `HomeSettingsScreen`（首页体育行与 `HomeRow.LIVE_SPORTS`）、`SettingsPage` / `SettingsScreen` /
  `NavigationSettingsPopup`（`App › Sports` 页面、搜索条目、导航隐藏项）、`StageRail`（导航项）、
  `OwnTVShell`（Sports 内容分支、播放会话、比分行、进球提示、换台面板）、`SyncStatusPill`（重建状态行）、
  `PlayerHud` / `PlayerHudChrome`（比分行、Other channels、Goal toast 参数链）、`OwnTVIcon.SPORTS`。
- Core mobile 已删 `MainSection.SPORTS` / `HomeRow.LIVE_SPORTS` / `StageGlyph.SPORTS` 及全部
  `R.string.sports_*`，因此 app 侧穷尽 `when` 分支也一并删除，不留兜底。
- `MoveOrderOverlay` 原先共用 `sports_move_instructions`，改为 app 层新增的通用
  `move_order_instructions`（`values` / `values-zh-rCN` / `values-zh-rTW` 三语言各 1 条）。
- `tools/i18n/safe_literals.txt` 用 `prune-safe` 清理 144 行 stale 分类；`hardcoded_baseline.txt`
  保持 45 条不变。频道分类着色 `GenreColor` 的 sport 关键词、测试中的 `"Sports"` 频道名均**保留**（非功能引用）。

### 13.4 顺带适配的 Core API 差异（非 Sports，复合构建必需）

| 差异 | 处理 |
|---|---|
| `SourceType.TVBOX` 新增，`ManageSourcesScreen` 两处 `when` 不穷尽 | `typeName` 增加 `TVBOX -> R.string.setup_tvbox`；`countsLabel` 增加 TVBOX 全 0 计数分支（TVBOX 走索引、不走同步） |
| `SealedCategoryAction.run` 新增 `play: (LiveKey) -> Unit` | Live / Movies / Series 三处调用补空回调；TV 端 UI 不产生 `PLAY` 动作 |
| `R.string.content_key_schedule` 在 Core mobile 已删 | 直播与搜索的按键提示改用 `content_key_programmes` |
| 导航列表与 `StageRailOrder` 的级联类型推断错误 | 随 Sports 引用删除自然消除 |

### 13.5 验证结果（2026-11-15 实测）

| 检查 | 结果 |
|---|---|
| 复合构建 `:app:compileStandardDebugKotlin` | **BUILD SUCCESSFUL**（1m 43s，实际执行编译；仅 1 条与本次无关的 `ChannelLogoTile` 恒真警告） |
| `:app:testStandardDebugUnitTest` | **250 tests / 0 failures / 0 errors / 0 skipped**（36 个报告文件，含 `SettingsSearchCoverageTest`） |
| `check_hardcoded_strings.py verify --bootstrap` | OK（45 baseline + 1283 safe；prune 后无 STALE） |
| `check_number_locale.py` / `check_text_overflow.py` | OK（270 Kotlin 文件 / 3 reviewed / 0 Clip 例外） |
| Gradle `:app:verifyI18nLiterals` | 随 `preBuild` 通过 |
| 代码残留复核 | `grep -rniE 'sport|…' app/src/main/java` 过滤误报后 **0 命中** |
| 改动规模 | 44 个已跟踪文件：+193 / −5696 行；另新增 `docs/multi-profile-contract-zh.md` |
| 基线编译证据 | `.workbuddy/archive/baseline-sports-compile.log`（2950 条错误，删除前）；`.workbuddy/archive/sports-removed-compile.log`（SUCCESS，删除后） |
| **零 `-P` 参数构建**（二次调整后） | **BUILD SUCCESSFUL**（2m 30s，日志 `.workbuddy/archive/no-corepath-compile.log`）：未传 `-Powntv.corePath`、未设 `GRADLE_RO_DEP_CACHE`，仅用用户级 `GRADLE_USER_HOME=D:\android\gradle`；日志含 `:OwnTV_Core:core:*` / `:OwnTV_Core:player-core:*` 任务、无制品回退警告，Kotlin 编译与单测全部 `UP-TO-DATE`（证明 classpath 与带参数时一致） |

### 13.6 文档同步与遗留

**已同步**：`docs/features-zh.md`（删 §5.5 体育、导航/设置/模块计数/第三方服务）、
`docs/multi-profile-contract-zh.md`（Core 口径与回归矩阵）、`README.md`（2026-11-15 更新段）、
`CHANGELOG.md`（Unreleased 一条 bullet）、`extras/USER_GUIDE.md`（Sports 章节替换为下线说明）、
本章。

**遗留 / 影响**：

1. **构建强依赖已消除**（2026-11-15 二次调整）：`settings.gradle.kts` 默认 includeBuild 同级
   `../Qwn/OwnTV_Core`，零 `-P` 参数构建已通过（见 §13.5）。剩余影响只在 **CI**：`android.yml`
   没有 Core 检出，会走制品回退路径；该路径当前无法编译本分支源码，需补 Core 检出或等上游发布
   匹配的制品。
2. **用户数据孤儿**：`navMenuHidden` 中的 `"SPORTS"`、Quick 钉选 `"tab_SPORTS"`、`SportsConfig` 偏好、
   旧深链 `settings/APP/sports`（`fromRoute` 返回 null）残留但不崩溃；未做数据迁移。
3. **功能降级**：Core `PLAY` 分类动作在 TV 端为空实现；`content_key_schedule` 提示改为
   `Programs` 文案。二者是编译适配，不是产品需求。
4. `extras/OwnTV_Master_Product_Brief.md` 及其它历史方案文档仍保留 Sports 描述（历史资料，未清）。
5. P2 真机 / 模拟器回归、P4 出包与签名注入仍未执行；签名属性默认路径见 §12.6 第 5 项。
6. 本轮代码与文档改动**未提交**（不 push、不打 tag）；建议按 §9 拆成
   `refactor(tv): drop Sports and build against shared Core source` 与 `docs:` 两次提交。