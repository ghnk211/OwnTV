# 设置项分类与子项对齐方案（QwnTV-mini → OwnTV_Mobile mobile-mini）

> 编写日期：2026-11-07
> 当前仓库：`E:\develop\ideaPoject\git\OwnTV`，分支 `QwnTV-mini`
> 参考仓库：`E:\develop\ideaPoject\git\OwnTV_Mobile`，分支 `mobile-mini`
> 依据：两端 `app/src/main/java` 实际代码（`SettingsGroup`、`SettingsScreen`、`SettingsGroupRows`、
> `SettingsLeaf`、`VideoPlayerSettingsScreen`、`MorePages`）逐行核对。
> 本文只给方案，不改代码。

---

## 一、结论先行

当前项目是 **12 个设置分组**，参考项目是 **8 个设置分组**。差异不是"少四张卡"，
而是参考项目按**用户任务**重组了分类，并把"分组 → 子项"统一成同一套行模型：

| 当前 12 组 | 参考 8 组 | 处理方式 |
|---|---|---|
| Quick | **Quick** | 保留，语义对齐（默认钉选、别名、长按菜单） |
| Profile | —（取消分组） | 多档案是 TV 独有能力，降为 **App 内一行**，打开原 `ProfileSettingsRows` |
| Sources & guide | **Sources & guide** | 保留，吸收原 Content 组 |
| Appearance | **Appearance** | 保留，吸收原 Layout 组（成为 `Layout` 分区） |
| Layout | —（并入 Appearance） | 原 10 行成为 Appearance 的 `Layout` 分区 |
| Content & metadata | —（拆分） | Customize / Metadata / Kids mode → Sources；OpenSubtitles → Sound |
| Player | **Player** | 保留 |
| Picture | —（并入 Player） | 原 Picture + Frame rate 成为 Player 的首个分区（参考项目顺序：Picture 在前） |
| Sound & subtitles | **Sound & subtitles** | 保留，吸收 OpenSubtitles |
| Live TV | **Live TV** | 保留 |
| Watching & recording | **Watching & recording** | 保留 |
| App | **App** | 保留，吸收 Profiles；Network / Diagnostics 成为分区 |

**一句话：8 个分组、组内分区、分区内行；行目录成为分组页、Quick、计数、搜索的唯一数据源。**
所有既有偏好键（`vp_*`、`tab_*`、`quick_*`、`rec_*`、`epg_*`）**保持不变**，因此备份、Quick 钉选、
跨端同步不受影响。

与仓库里已有的 [settings-simplification-zh.md](settings-simplification-zh.md) 第六节相比：
那份文档建议把 Player + Picture + Sound 合成"播放与画面"、Live + Watching 合成"直播与观看"；
**本方案以参考项目为准**，保留 Sound、Live、Watching 为独立组，只做上述四处归并。
两者不要同时实施。

---

## 二、现状对比与差异分析

### 2.1 分类结构差异

**当前**（`features/shell/components/SettingsGroup.kt:10`，顺序即 More › Settings 卡片序号）：

```
QUICK, PROFILE, SOURCES, APPEARANCE, LAYOUT, CONTENT,
PLAYER, PICTURE, SOUND, LIVE, WATCHING, APP
```

**参考**（`mobile/ui/screens/settings/SettingsCommon.kt:33`）：

```
QUICK, SOURCES, PLAYER, SOUND, LIVE, WATCHING, APPEARANCE, APP
```

关键差异：

1. **分组依据不同**。当前 PLAYER / PICTURE / SOUND / LIVE / WATCHING 按播放器内部模块切分；
   参考项目把 PICTURE 并入 PLAYER（Picture 分区在前），把原 LAYOUT 并入 APPEARANCE，
   把原 CONTENT 拆到 SOURCES 与 SOUND。
2. **PROFILE 不再是分类**。参考项目只有单档案，个人档案编辑被移除；
   当前项目支持多档案、Kids、PIN，删除分组会丢能力，因此降为 App 内一行。
3. **子页面归属不同**。参考的 `SettingsLeaf` 每个页面都带 `group` 与稳定的 `slug/route`
   （`SettingsLeaf.kt:11`）；当前是扁平的 `SettingsTab` 枚举（`SettingsScreen.kt:109`），
   页面与分组的从属关系只存在于 `rootItems` 的排列里。
4. **根页形态不同**。当前 More › Settings 是 12 张卡、每行 3 张、计数写死
   （`features/more/MorePages.kt:206-222`）；参考是 8 张卡、每行 2 张、计数从行目录实时求和
   （`SettingsScreen.kt:96-107`）。
5. **分区概念不同**。当前只有视频组有"一级分区标题"（`VideoGroup.parts`，
   `VideoPlayerSettingsScreen.kt:69`）；参考的**每个**分组都由 `SettingSection` 组成
   （`SettingsModel.kt:113`），非视频组也有分区标题（Sources / Guide & catch-up / Content & metadata）。

### 2.2 子项归属差异（需要搬家的行）

| 行 / 子页 | 当前所在 | 参考归属 | 说明 |
|---|---|---|---|
| `tab_CUSTOMIZE`、`tab_METADATA` | Content & metadata | **Sources › Content & metadata** | 与参考一致 |
| Kids mode（当前在档案编辑器内） | Profile | **Sources › Content & metadata** | 参考是独立开关行；TV 建议新增一行复用现有档案 API |
| `tab_OPEN_SUBTITLES` | Content & metadata | **Sound & subtitles › Languages & subtitles** | 参考把它视为声音/字幕能力 |
| `tab_HOME`、`browsing_lists`、`tab_CONTENT_MENUS` | Layout | **Appearance › Layout 分区** | 参考 APPEARANCE = appearanceSections + layoutSections |
| `navigation`、`live_layout`、`live_opens_in`、`vod_layout`、`tab_PANEL_WIDTH`、`tab_GUIDE_WIDTH`、`tab_CH_NAV` | Layout | **Appearance › Layout 分区** | TV 专属项保留，只换归属 |
| Picture 组全部行（含 Frame rate 分区） | Picture | **Player › Picture / Frame rate 分区** | 参考顺序：Picture → Player → Streaming |
| Profile 组（多档案列表 / 新增 / 编辑 / 删除） | Profile | **App › Profiles（一行一页）** | 保留完整能力，取消顶层分类 |
| `sports`、`auto_start_*`、Diagnostics | App（已有） | **App** 各分区 | 参考无这些能力，保留为 TV 专属 |
| Downloads（允许下载 / 文件夹 / 仅 Wi-Fi） | 当前不在 Settings | **参考 App › Downloads** | TV 无下载能力，**不新增** |

### 2.3 层级与导航差异

| 维度 | 当前 | 参考 | 对齐动作 |
|---|---|---|---|
| 根入口 | More › Settings 的 12 张卡（位置驱动 `SettingsStart.group: Int`） | 8 张卡 + 路由 `settings/{slug}` | 8 张卡；入口改用稳定标识而非序号 |
| 组页 | `StageSettingsPage` 单页；组由 `selectedGroup` 下标选中 | 组页由路由决定；组内 `SettingSection` 分区 | 保留单页形态，组内补齐分区标题 |
| 子页 | `SettingsTab` 扁平枚举，`tab` 状态切换 | `SettingsLeaf(group, slug, titleRes)`，真实路由 | 引入带 `group/slug/route` 的页面注册表 |
| 面包屑 | 组标题 + 页内自述，格式不统一 | `Settings · 组 · 页`；带计数 | 统一 `设置 · 组` / `设置 · 组 · 页` |
| 返回 | `lastTab` + 焦点恢复 | 路由返回 | 保留焦点恢复，补"返回组页并选中原组" |

### 2.4 子项交互与数据模型差异

| 维度 | 当前 | 参考 | 对齐判断 |
|---|---|---|---|
| 行模型 | 根行 `RootRow`（`chip/chevron/heading`）+ 视频行 `KeyedSettingRow` + 手写页面行，三套 | `SettingItem`（key/icon/title/line/value/sheet/onClick/pinnable）一套（`SettingsModel.kt:91`） | **必须统一**，否则计数/搜索/Quick 永远三处维护 |
| 值类型 | `chip` 字符串 + 点击开弹窗；少数原地开关 | `Switch / Choice / Opens / Stepper / Segmented / Saved / Action / Tag`（`SettingsModel.kt:59`） | 语义对齐；TV 仍用面板/弹窗呈现 |
| 开关 | 多数原地切换 | 行内直接切换 | 已基本一致 |
| 选择项 | `PickerDialog` / 专用弹窗 | 底部 sheet：眉题=组名、说明、CHOICES、推荐标记，选中即关闭 | TV 保留弹窗，补齐"说明 + 推荐"信息 |
| 数字 | 对话框内步进 | 行内 `StageStepper` | TV 可保留对话框，但需边界禁用 |
| 分段 | 对话框/`Segmented` | 行内分段，立即写入 | 语义对齐 |
| 计数 | 组计数多处写死；`videoSectionCounts` 已有偏差 | 从当前行目录求和 | **必须改为派生** |
| Quick | 钉选键 + `VIDEO_QUICK_ROWS` 物化 + 手写搜索条目 | 钉选键 → 别名映射 → 行目录解析；长按移动只看已绘制行 | 对齐别名与 GC 规则，保留 TV 的 deep-jump |
| 搜索 | ~78 条手写 + 派生视频条目 + 子页条目，测试强制覆盖 | 组行 + 34 条子页行，全部从行目录/Leaf 列表派生 | 分阶段改为派生，保留少量"动作型"特例 |
| 分区说明 | `StageSettingsNote` 偶发使用 | `SettingSection.note`（如录音时间不准） | 对齐：分区级说明挂在 section 上 |

### 2.5 计数现状（对齐前已知问题）

`VideoPlayerSettingsScreen.kt` 的 `videoSectionCounts` 与实际绘制行已不一致：
PICTURE 少算 1、FRAME_RATE 少算 1、LIVE 少算 2、SOUND 少算 1。
本次改造把计数改为**从行目录求和**，顺带消除这类偏差。

---

## 三、改造目标与对齐范围

### 3.1 目标结构（8 组）

> 标注：`[保]` 现有能力保留 · `[移]` 从别组移入 · `[并]` 合并分区 · `[增]` 为对齐新增 · `[TV]` TV 专属保留

#### 1. Quick
- 仅渲染已钉选行（当前已是）。
- 对齐：`QUICK_ALIASES`（`quick_hdr → vp_hdr`、`quick_check_update → update_startup`、
  `detailed_diagnostics → vp_logging`）；未钉选时空态提示；长按 Pin / Unpin / Move up / Move down；
  移动只在"已绘制行"之间进行；未知键在启动时清理。
- `[TV]` 保留 deep-jump：钉选行若是需要选择器的行，点击跳到它的原组并聚焦该行。

#### 2. Sources & guide
- **Sources**：Playlists `[保]`、EPG Sources `[保]`。
- **Guide & catch-up**：EPG time offset、Program reminders、Reminder time、Guide days、Catch-up、
  Catch-up time zone per playlist（有播放列表时）`[保]`。
- **Content & metadata** `[移]`：Kids mode `[增]`、Customize `[移]`、Metadata `[移]`。

#### 3. Player
- **Picture** `[并]`：Hardware decoding、Software decode above 1080p、Prefer SDR、Max video quality、
  Tunneled playback（条件行）、Default zoom、Saved zoom。
- **Frame rate** `[并]`：AFR films、AFR live、AFR pause、AFR match resolution。
- **Player** `[保]`：Live engine、Live engine per playlist、Reset live pins、VOD engine、
  VOD engine per playlist、Reset engine pins、External player。
- **Streaming** `[保]`：VOD buffer、Network timeout、Reconnect attempts。

#### 4. Sound & subtitles
- **Sound**：Default volume、Reset saved volume、Surround、Passthrough、FFmpeg audio、Night mode、
  Volume leveling、Audio sync、Output audio delay、Reset saved delay。
- **Languages & subtitles**：Preferred audio language、Preferred subtitle language、
  Subtitle appearance（页面）、**OpenSubtitles（页面，从 Content 移入）** `[移]`。

#### 5. Live TV
- **Live TV**：Channel numbers、Live preview、Preview audio（条件行）、Pause & rewind、
  Rewind length（条件行）、Resume a saved channel（条件行）、Left/Right rewinds。
- **Live tuning**：Live latency、Latency per playlist、Pre-roll、Pre-roll per playlist、
  Tune timeout、Tune timeout per playlist。

#### 6. Watching & recording
- **Controls & resume**：Seek step、Live rewind step、Resume playback、Autoplay next。
- **Multiview & mini player** `[TV]`：Multiview、Max tiles（条件行）、Mini player（页面/面板）、
  Picture-in-picture（条件行）。
- **Recording**：Keep one stream free、Record what I'm watching、Start early、Keep going after the end。
  分区说明沿用 `settings_recording_timers_inexact`（仅当定时不精确）。

#### 7. Appearance
- **（无标题）主题与外观**：Theme、Accent、Focus highlight、Glass & background（页面）、
  Fonts（页面）、Popup size、UI zoom、Animations。
- **Layout** `[并]`：Navigation、Menu labels（TV 可继续折在 Navigation 内）、Live layout、
  Live opens in、VOD layout、Panel width（页面）、Guide width（页面）、Browsing & lists（页面）、
  Home screen（页面）、Long-press menus（页面）、Remote shortcuts / Ch paging（页面）。

#### 8. App
- **App**：Profiles（一行一页，`[TV]` 来自 Profile 组）、Language、App icon、App startup、
  Auto start on boot `[TV]`、Auto start on wake `[TV]`、Check updates、Check updates on startup、
  Sports `[TV]`、App lock（`[增]` 可选：把 Customize ⋯ 里的设置 PIN 提升为一行）。
- **Network**：Proxy（页面）、DNS（页面）。
- **Diagnostics** `[TV]`：Measured stream stats、Detailed playback logging。
- 不新增参考项目的 Downloads 分区。

### 3.2 对齐口径（做与不做）

**对齐**：
1. 分类数量、顺序、名称、组内分区顺序。
2. 每个子项的归属、层级深度（组页行 / 子页）、入口文案语义。
3. 交互语义：开关原地切换；选择项带说明与推荐、选中即生效；数字步进有边界；
   分段立即写入；页面行有当前值；长按钉选；搜索按"行标题"匹配。
4. 数据单一来源：分组页、Quick、根卡计数、搜索都读同一份行目录。

**不对齐**：
1. 视觉组件：TV 继续用 10-foot 面板/弹窗，不移植手机 `MobileBottomSheet`、`StageSettingRow`。
2. 手机专属行：Downloads、移动数据录音、手机精确闹钟、手机电池等不引入。
3. TV 专属能力：引擎选择、Panel/Guide Width、Remote shortcuts、Sports、Auto start、Diagnostics、
   Multiview、多档案，全部保留，只换归属。
4. Backup / Local sync / Error log / About 仍在 More，不回到 Settings。
5. 偏好键、DataStore 结构、备份格式：完全不动。

### 3.3 验收标准

- More › Settings 恰好 8 张卡，顺序与参考一致；卡片计数 = 该组行目录实际行数。
- 12 个旧分组全部有明确去向，无能力丢失（Profile 降为 App 行、Picture/Layout/Content 完成归并）。
- 任意行的搜索命中后跳转到其**所在组/页**，面包屑为 `设置 · 组 [· 页]`。
- Quick 钉选、排序、跨端恢复（手机写入的 6 个 TV 默认键）行为符合 2.1/Quick 规则。
- `SettingsSearchCoverageTest`、`VideoQuickRowCatalogueTest` 等全部通过或有等价替代测试。

---

## 四、具体实施步骤

### 阶段 0：基线固化（0.5 天，无用户可见变化）

1. 新增行目录完整性测试：每个组至少一个分区；所有行 key 全局唯一；子页标题唯一；
   `VideoGroup → SettingsGroup` 映射覆盖全部 section。
2. 记录改造前 Quick 钉选、备份文件、各偏好键作为回归基线。

### 阶段 1：分组收敛 12 → 8（1 天，结构变化）

**改动点**

| 文件 | 改动 |
|---|---|
| `features/shell/components/SettingsGroup.kt:10` | 枚举改为 `QUICK, SOURCES, PLAYER, SOUND, LIVE, WATCHING, APPEARANCE, APP`；删除 PROFILE / LAYOUT / CONTENT / PICTURE；`of(VideoGroup)` 改为 PICTURE/PLAYER/STREAMING → PLAYER，DIAGNOSTICS → APP |
| `features/settings/VideoPlayerSettingsScreen.kt:69` | 新增复合 `VideoGroup.PLAYBACK`，`parts = [Picture, Frame rate, Player, Streaming]`；原 PICTURE / PLAYER 组可保留给映射与 Quick 路径，但不再作为根组 |
| `features/more/MorePages.kt:206-233` | `SettingsCards` 由 12 项改为 8 项；计数改为读取行目录；保留 `lastOpenedCard` 焦点恢复 |
| `features/shell/components/SettingsScreen.kt:240-252`、`:377`、`:782-834`、`:1110-1124`、`:1152-1188` | `selectedGroup` 由下标改为稳定组标识（见风险 R2）；`categories` 切片同步 8 组；移除 PROFILE/LAYOUT/CONTENT/PICTURE 分支；`jumpVideo` 的组映射改走 `SettingsGroup.of` |
| 组页渲染 | SOURCES 追加 Content 分区；PLAYER 渲染 `VideoGroup.PLAYBACK`；APPEARANCE 的 `RootGroup("group_layout")` 改成组内二级分区；APP 首行加 Profiles；SOUND 末尾加 OpenSubtitles 行 |
| 搜索跳转 | 所有 `selectedGroup = SettingsGroup.X.ordinal` 改为新的组标识；WATCHING 搜到的录音行仍落 WATCHING；OpenSubtitles 搜索落 SOUND |
| 测试 | `SettingsSearchCoverageTest`、`SettingsSearchRowsTest`、`VideoQuickRowCatalogueTest`、`LockedSettingsExitTest` 同步新枚举 |

**注意**：本阶段只搬入口，不搬行内部实现；每完成一组可单独截图验收。

### 阶段 2：子项归位与页面注册（1～1.5 天）

1. **引入页面注册表**：新增 `features/settings/SettingsPage.kt`（对齐 `SettingsLeaf`）：
   ```kotlin
   enum class SettingsPage(val group: SettingsGroup, slug: String, @StringRes val titleRes: Int) {
       PLAYLISTS(SOURCES, "playlists", R.string.settings_playlists),
       EPG(SOURCES, "epg", R.string.settings_epg_sources),
       CATCHUP_SOURCES(SOURCES, "catchup-playlists", ...),
       CUSTOMIZE(SOURCES, "customize", ...),
       METADATA(SOURCES, "metadata", ...),
       EXTERNAL_PLAYER(PLAYER, "external", ...),
       SUBTITLE_APPEARANCE(SOUND, "subtitles", ...),
       OPEN_SUBTITLES(SOUND, "opensubtitles", ...),
       GLASS(APPEARANCE, "glass", ...),
       FONTS(APPEARANCE, "fonts", ...),
       HOME(APPEARANCE, "home", ...),
       BROWSING(APPEARANCE, "browsing", ...),
       MENUS(APPEARANCE, "menus", ...),
       PANEL_WIDTH(APPEARANCE, "panel-width", ...),
       GUIDE_WIDTH(APPEARANCE, "guide-width", ...),
       REMOTE_SHORTCUTS(APPEARANCE, "remote", ...),
       LANGUAGE(APP, "language", ...),
       PROFILES(APP, "profiles", ...),
       PROXY(APP, "proxy", ...),
       DNS(APP, "dns", ...),
       SPORTS(APP, "sports", ...),
       ; val route get() = "${group.route}/$slug"
   }
   ```
   用 `SettingsPage` 取代 `SettingsTab`；`Backup` / `LocalSync` 不再属于 Settings 页面表。
2. **补分区标题**（不新增字符串，全部复用 core 既有 key）：
   `settings_sources_title`、`settings_part_guide_catchup`、`settings_group_content_metadata`、
   `settings_vp_cat_picture`、`settings_vp_cat_frame_rate`、`settings_vp_cat_player`、
   `settings_vp_cat_streaming`、`settings_vp_section_sound`、`settings_vp_cat_languages`、
   `settings_live_tv`、`settings_vp_cat_live_tuning`、`settings_vp_cat_controls_resume`、
   `settings_vp_cat_multiview`、`recording_settings_group`、`settings_group_layout`、
   `settings_group_app`、`settings_group_network`、`settings_diagnostics`。
3. **Kids mode 行**：在 Sources › Content & metadata 增加开关，写现有 active profile 的 `isKids`；
   关闭时若档案有 PIN，沿用 `ProfilePinSheet` 先验证（与参考一致）。
4. **Profiles 行**：App 首行"Profiles"，值显示档案数，点击进入现有 `ProfileSettingsRows`；
   保留新增/编辑/删除/Kids/PIN/下载权限。原 PROFILE 组的 `profileCount()` 计数迁到该行。
5. **App lock 行**（可选）：把 `CustomizeScreen` 的 `SETTINGS_PIN` 菜单项提为 App 行，复用同一开关，
   不改变 `SettingsPinGate` 的会话解锁逻辑。
6. **面包屑**：组页 `设置 · <组>`；子页 `设置 · <组> · <页>`；带计数的页面沿用现有复数资源。

### 阶段 3：统一行目录，派生计数与搜索（2～3 天，收益最大）

1. 新增 `features/settings/SettingsCatalogue.kt`，定义 TV 版行模型（语义对齐参考）：
   ```kotlin
   sealed interface SettingValue { Switch, Choice, Opens, Stepper, Segmented, Saved, Action, Tag }
   class SettingItem(key, icon, title, line, value, onClick, panel, pinnable = true)
   class SettingSection(titleRes: Int?, items: List<SettingItem>, noteRes: Int? = null)
   fun groupSections(group: SettingsGroup, vm: SettingsViewModel): List<SettingSection>
   fun allGroupSections(vm): Map<SettingsGroup, List<SettingSection>>
   ```
   `panel` 对应参考的 `sheet`：TV 里是"打开设置面板/弹窗"的 lambda。
2. 把 `rootItems` 中非视频行、视频组的 `VIDEO_QUICK_ROWS` 绑定、各子页入口统一迁到这份目录。
   迁移顺序：Sources → Appearance/Layout → App → 五个视频组 → Quick。
3. **根卡计数**：`MorePages` 不再写死，改为 `allGroupSections(vm)[group].sumOf { it.items.size }`；
   Quick 计数 = 已解析钉选行数。删除 `videoSectionCounts`。
4. **搜索派生**：从 `allGroupSections` 生成组行条目；从 `SettingsPage` + 每页"可搜索行标题"生成子页条目；
   保留极少数"动作型/原地切换"特例（如 Check updates、Prefer SDR、Accent）。
   匹配规则对齐参考：查询词全部命中行标题；路径为 `组 [· 页]`；结果右侧显示当前值。
5. **Quick 解析**：`quickRows` 改为"钉选键 → 别名 → 目录行"；未命中即丢弃；
   文件头说明与参考一致（TV 默认 6 键 → 手机只认 2 键）。
6. **Help 表**：`SettingsHelpTable` 的 key 不变；仅当行标题/说明迁移时同步文案。
   组名、分区名全部复用 core 字符串，避免新增 24 语言变体。

### 阶段 4：交互对齐与清理（1 天）

1. 开关：确认所有纯开关行原地切换（当前 `quick_*`、auto start、update startup 已满足）。
2. 选择项：`PickerDialog` 增加"行说明 + 推荐项"展示，选中立即写入并关闭；
   多问题弹窗（Catch-up：时区 + 播放器）保持现有复合面板。
3. 数字：步进边界禁用；`Saved` 类行按参考显示"N saved · Reset / None saved"，
   重置动作保留在行内或长按菜单。
4. 页面行：统一 `Opens(value)` 形态，值缺失时留空而不是显示 `Off`（参考的 `Opens("")`）。
5. 长按：根行与视频行统一 Pin/Unpin；Quick 内支持上下移动；移动只跨已绘制行。
6. 清理：删除无入口的 `SettingsTab.BACKUP/LOCAL_SYNC` 分支（若确认只剩 More 入口）、
   删除重复的 `update_startup` 与 `quick_check_update` 语义重复（保留一个入口，键不变）、
   删除旧 12 组卡片与相关注释。

### 改动文件清单（预估）

| 模块 | 文件 |
|---|---|
| 分类模型 | `features/shell/components/SettingsGroup.kt`、`features/settings/SettingsPage.kt`（新） |
| 根入口 | `features/more/MorePages.kt`、`features/shell/OwnTVShell.kt`（`SettingsStart`） |
| 组页 | `features/shell/components/SettingsScreen.kt`（拆分，建议把目录/搜索移出以避开 JVM 方法上限） |
| 行目录 | `features/settings/SettingsCatalogue.kt`（新）、`SettingsHelpTable.kt`、`VideoPlayerSettingsScreen.kt` |
| 子页 | `ProfileSettingsRows.kt`、`OpenSubtitlesAccountScreen.kt`、`MetadataSettingsScreen.kt`、`GlassEffectPage.kt` 等（只改入口归属，页内不动） |
| 测试 | `SettingsSearchCoverageTest`、`SettingsSearchRowsTest`、`VideoQuickRowCatalogueTest`、`LockedSettingsExitTest` + 新目录测试 |

---

## 五、兼容性考虑与风险点

| # | 风险 | 影响 | 应对 |
|---|---|---|---|
| R1 | **Quick 钉选键变化** | 用户钉选丢失、跨端恢复失败 | 全流程禁止改 key；只改分组归属。新增测试：改造前后所有 `VIDEO_QUICK_ROWS.key` / root key 集合完全一致 |
| R2 | **组下标漂移** | `SettingsStart.group: Int`、`rememberSaveable selectedGroup` 在升级/进程恢复后指向错误组 | `SettingsStart` 与 `selectedGroup` 改为组标识（枚举名或 route），不再用 ordinal；读取旧态时按名称回退并 clamp |
| R3 | **More 卡片计数不一致** | 卡片写死 8/9/10，合并后失真 | 计数改为目录派生；加测试断言"卡片计数 = 组行数" |
| R4 | **搜索覆盖率测试大量失败** | CI 红灯 | 阶段 1 同步改测试期望；阶段 3 将测试从"手写条目"改为"目录全覆盖"断言 |
| R5 | **Profile 能力被误删** | 多档案、Kids、PIN、下载权限丢失 | Profile 页整体保留，只把入口移到 App 首行；`profileCount()` 与删除确认逻辑不动 |
| R6 | **OpenSubtitles / Metadata 入口搬家后深链失效** | 搜索结果、Quick、返回焦点指错组 | 所有跳转统一走 `SettingsPage.route`；`groupOfPage()` 由注册表反查，删除手写映射 |
| R7 | **视频组复合渲染顺序** | Picture 行出现在 Player 之后，或计数漏算 | 用 `VideoGroup.PLAYBACK.parts` 固化顺序；计数从实际绘制行求和；加截图/顺序测试 |
| R8 | **字符串资源在 core AAR** | 新分区标题若无 key 需发 core 版本 | 只复用既有 key（已核对全部存在）；不新增字符串 |
| R9 | **JVM 方法上限** | `SettingsScreen.kt` 已接近上限，搬迁后可能编译失败 | 阶段 3 主动拆分：目录、搜索、页面分发各自成文件 |
| R10 | **外观实时预览条件** | `panelTop` 依赖 `group == APPEARANCE`；Layout 并入后行更多，预览挤压列表 | 保留预览，但仅在组页顶部且搜索时隐藏；长列表用 LazyColumn |
| R11 | **备份/恢复** | 旧备份含 12 组相关 UI 状态？ | 核对备份只序列化偏好键与数据库；若有组下标，做版本兼容读取 |
| R12 | **TV 与 mini 共用设置页** | 手机横屏下 8 张卡密度变化 | mini 布局复用同一 8 组；卡片列数按宽度自适应（TV 4×2 / 手机 2×4） |
| R13 | **应用锁语义** | 设置 PIN 入口移动后用户找不到 | 保留 Customize ⋯ 原入口，新增 App 行仅作为并列入口；`SettingsPinGate` 不动 |
| R14 | **行为回归：原地切换 vs 弹窗** | 部分行从"点开弹窗"改为"直接切换"可能误触 | 只对纯布尔项原地切换；有副作用的（录音中、切换引擎、AFR 警告）保留确认/弹窗 |

**回滚策略**：阶段 1 单独一个提交/PR，只引入 8 组与新映射，页面实现不动；
若出现严重问题，回滚该提交即可恢复 12 组，不影响偏好数据（因为键未变）。

---

## 六、验证与测试建议

### 6.1 自动化测试

| 测试 | 断言 |
|---|---|
| 新 `SettingsCatalogueTest` | 8 组均非空；行 key 全局唯一；每个 `SettingsPage` 的 group 存在于 8 组；`VideoGroup` 全部映射到某组 |
| 新 `SettingsCountTest` | More 卡片计数 == `groupSections(group).sumOf { items.size }`；Quick 计数 == 已解析钉选数 |
| `VideoQuickRowCatalogueTest`（改） | 改造前后 `VIDEO_QUICK_ROWS` 键集合一致；每个键都能在目录中解析到行 |
| `SettingsSearchCoverageTest`（改） | 每个组、每个页、每个可搜索行都能被标题命中；路径为 `组 [· 页]` |
| `SettingsSearchRowsTest`（改） | 各子页 `*_SEARCH_ROWS` 与页面实际行一致（若阶段 3 改为派生，则删表并改断言） |
| `LockedSettingsExitTest`、`GuideWidthsTest`、`PanelWidthsTest` | 不受分组收敛影响，作为回归基线 |
| Quick 兼容测试（新） | 手机写入的 `quick_hdr` / `quick_check_update` / `detailed_diagnostics` 能在 TV 解析或按规则丢弃；未知键被 GC |

### 6.2 手工功能矩阵（Android TV 模拟器 + 手机横屏）

| # | 场景 | 期望 |
|---|---|---|
| 1 | More › Settings | 8 张卡、顺序正确、计数与组页行数一致 |
| 2 | Sources | Sources / Guide & catch-up / Content & metadata 三分区；Playlists、EPG、Customize、Metadata、Kids 均可达 |
| 3 | Player | Picture → Frame rate → Player → Streaming 顺序；saved zoom/reset 正常 |
| 4 | Sound | Sound 行完整；Subtitle appearance、OpenSubtitles 从本组进入 |
| 5 | Live | 条件行（preview audio / rewind length / resume）随开关出现/消失 |
| 6 | Watching | 录音 4 行 + 多画面/迷你播放器；定时不精确说明只在需要时出现 |
| 7 | Appearance | 主题/外观 8 行 + Layout 分区；实时预览仍在顶部；Panel/Guide Width 可达 |
| 8 | App | Profiles 首行可进多档案；语言/图标/启动/更新/网络/诊断可达 |
| 9 | Quick | 钉选、取消、上下移动、跨端键恢复；空态提示 |
| 10 | 搜索 | 每组按名称命中；结果跳对组/页；面包屑正确；无结果文案 |
| 11 | 返回 | 从子页返回组页并落在原组；从组页返回 More 并落在原卡 |
| 12 | 设置锁 | 开启 PIN 后进入 Settings 需解锁；锁定期间不能从设置内绕过 |

### 6.3 回归检查

- 备份导出/导入后，所有偏好值、Quick 钉选、档案 PIN 保持一致。
- 手机端写入的 Quick 钉选在 TV 端的解析符合别名规则。
- 外观（Glass/背景/字体/缩放）实际渲染无变化；分组只影响入口。
- 多档案切换后 Kids mode、App lock、下载权限显示正确。
- Mini（手机横屏）布局下 8 张卡不溢出、焦点可达。

### 6.4 完成定义（DoD）

1. 8 组结构、分区顺序、子项归属与 3.1 完全一致。
2. 所有既有偏好键与备份格式零变化。
3. 计数、Quick、搜索均由行目录派生，不再有手写清单与写死计数。
4. 自动化测试全绿，手工矩阵 12 项通过。
5. 文档同步：更新 `docs/features-zh.md` 9.2（12 组 → 8 组），
   并在 `settings-simplification-zh.md` 第六节标注"已被本方案取代"。

---

## 附录 A：12 → 8 组完整映射

| 旧组 | 旧行/子页 | 新归属 |
|---|---|---|
| QUICK | 6 个 `quick_*` + 用户钉选 | QUICK |
| PROFILE | 档案列表 / 新增 / 编辑 / 删除 / Kids / PIN / 下载权限 | APP › Profiles（一行一页）；Kids 另在 SOURCES › Content & metadata |
| SOURCES | Playlists、EPG、偏移、提醒、提醒提前量、Guide days、Catch-up、按播放列表时区 | SOURCES |
| APPEARANCE | Theme、Accent、Focus、Glass、Fonts、Popup、Zoom、Animations | APPEARANCE（无标题分区） |
| LAYOUT | Navigation、Menu labels、Live layout、Live opens in、VOD layout、Panel/Guide width、Browsing、Home、Content menus、Ch paging | APPEARANCE › Layout |
| CONTENT | Customize、Metadata、OpenSubtitles | Customize/Metadata → SOURCES › Content & metadata；OpenSubtitles → SOUND |
| PLAYER | Engine、per-playlist、reset、External、Streaming | PLAYER › Player / Streaming |
| PICTURE | 解码、SDR、画质、Tunnel、Zoom、Frame rate | PLAYER › Picture / Frame rate（置前） |
| SOUND | 音量、环绕、直通、音画同步、语言、字幕外观 | SOUND › Sound / Languages & subtitles |
| LIVE | 频道号、预览、时移、左右键、调优 | LIVE |
| WATCHING | 快进/快退、续播、自动下一集、多画面、迷你、录制 | WATCHING |
| APP | 语言、图标、启动、自启、更新、Sports、Proxy、DNS、Diagnostics | APP（Profiles 置首） |

## 附录 B：键稳定性清单（改造中禁止修改）

- Quick：`quick_live_preview`、`quick_preview_sound`、`quick_channel_numbers`、`quick_hdr`、
  `quick_autoplay`、`quick_check_update`
- 视频：全部 `vp_*`（含 `vp_reset_*`、`vp_*_sources`、条件行 `vp_tunneled`、`vp_pip`、
  `vp_timeshift_window`、`vp_timeshift_resume`、`vp_multiview_tiles`）
- 录制：`rec_reserve`、`rec_watching`、`rec_pre_roll`、`rec_post_roll`（TV 无 `rec_mobile_data`）
- 根行/子页：`tab_*`、`epg_offset`、`reminders`、`reminder_lead`、`guide_days`、`catchup`、
  `catchup_sources`、`theme`、`accent`、`focus_highlight`、`fonts`、`popup_size`、`ui_zoom`、
  `animations`、`navigation`、`live_layout`、`live_opens_in`、`vod_layout`、`browsing_lists`、
  `app_icon`、`app_startup`、`auto_start_boot`、`auto_start_wake`、`check_updates`、
  `update_startup`
  > 参考项目的 `downloads_allowed` / `download_folder` / `downloads_wifi_only` 只存在于手机侧，
  > TV 不引入，也不参与本次键稳定性约束。
- 仅改归属、不改键：`tab_CUSTOMIZE`、`tab_METADATA`、`tab_OPEN_SUBTITLES`、`tab_HOME`、
  `tab_CONTENT_MENUS`、`tab_PANEL_WIDTH`、`tab_GUIDE_WIDTH`、`tab_CH_NAV`
