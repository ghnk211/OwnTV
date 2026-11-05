# OwnTV 功能整理（Android TV IPTV 播放器）

> 整理日期：2026-11-05 ｜ 基于仓库当前分支 `mini`（与上游 `main` 同步，0 ahead / 0 behind）
> 依据：`app/src/main` 实际代码结构、`AndroidManifest.xml`、`app/build.gradle.kts`，并交叉核对
> `extras/OwnTV_Master_Product_Brief.md`、`extras/USER_GUIDE.md`、`CHANGELOG.md`
> 代码规模：`app/src` 下 326 个 Kotlin 源文件（含测试与 debug 工具），`features/` 22 个功能包

---

## 1. 一句话定位

**纯播放器**形态的原生 Android TV IPTV 客户端：不内置任何频道、订阅或片源，用户自带
**Xtream 账号**、**M3U 播放列表**（URL 或本地文件）或 **Stalker / Ministra 门户**（Portal URL + MAC）。

三条不可动摇的设计前提：

| 原则 | 含义 |
|---|---|
| Player only | 无内容、无订阅、无付费墙，不提供也不引导任何未授权源 |
| Remote-first | 全部交互围绕 D-pad 设计，焦点/返回/长按语义统一 |
| Language-first | 首次启动先选语言，再进向导；多语言（README 标注 26 种，本仓库 `res` 下为 24 个语言变体 + 默认英文），RTL 感知 |

---

## 2. 形态与技术栈

| 项 | 值 |
|---|---|
| 平台 | Android TV / Fire TV（`leanback` 非必需、`touchscreen` 非必需，可装到手机/盒子；手机、平板打开即以横屏显示） |
| 语言 / UI | Kotlin 2.4.20 + Jetpack Compose for TV（`androidx.tv:tv-material` 1.1.0，Compose BOM 2026.08.00） |
| 构建 | AGP 9.4.0 / Gradle 9.8.2 / KSP2 / JDK 25；**无 `kotlin-android` 插件**（由 Compose 编译器插件带起 Kotlin 版本） |
| SDK | `minSdk 26`、`targetSdk 36`、`compileSdk 37`、`applicationId tv.own.owntv` |
| 播放引擎 | libmpv（FFmpeg，OwnTV 自建 `tv.own.owntv:libmpv`）+ ExoPlayer / Media3 1.11.1 |
| 数据 | Room 2.8.5 + Paging 3.5.1 + FTS4（WAL）；DataStore 存偏好 |
| 网络 / 图片 / DI | OkHttp 5.5.0 · Coil 3.6.0 · Koin 4.2.2 |
| 其它 | WorkManager（后台同步/下载）、ZXing（二维码）、YouTube IFrame（预告片）、ProfileInstaller（基线配置） |

**拆分方式**：本仓库只有 TV 界面层。数据库、同步、解析器、EPG、备份、设置存储、播放引擎与全部
翻译字符串都在独立仓库 `OwnTV_Core`，以 `tv.own.owntv:core` / `:player-core` 发布；手机端
`OwnTV_Mobile` 复用同一套 core。

---

## 3. 功能地图

| 一级入口 | 核心能力 | 代码位置 |
|---|---|---|
| 首页 Home | Now Trending、继续观看、收藏频道/电影/剧集、最近频道、体育行 | `features/home` |
| 直播 Live TV | 频道列表 + 预览窗、分类、换台、追看、频道菜单 | `features/live` |
| 电视指南 Guide | XMLTV 时间×频道网格、提醒、录制、追看回看、自动匹配 | `features/epg` |
| 电影 Movies | 分类/网格/详情/播放、排序、标记已看 | `features/movies` |
| 剧集 Series | 季/集、续播、下一集、整季下载、集序 | `features/series` |
| 搜索 Search | 直播/剧/电影/剧集联合搜索、语音搜索、节目搜索 | `features/search` |
| 体育 Sports | 比分、赛程日历、联赛榜单、球队页、找频道 | `features/sports` |
| 下载 Downloads | 电影/剧集离线下载 + 录制文件管理 | `features/downloads`、`features/recordings` |
| 更多 More | 设置、收藏、历史、备份、本地同步、错误日志、关于 | `features/more` |
| 首次启动 Setup | 语言 → 显示 → 免责 → 建档 → 加源 → 完成 | `features/setup` |
| 外壳 Shell | 导航栏、顶栏胶囊、离线横幅、播放器托管 | `features/shell` |
| 播放器 HUD | 控制条、进度、音轨/字幕/倍速、信息浮层、迷你播放器 | `player/` |

---

## 4. 播放能力

### 4.1 双引擎与降级

- **分工**：mpv 负责电影/剧集与最大兼容性（全编码/容器/音轨/字幕、4K HDR 直出）；ExoPlayer 负责
  直播（HLS 秒开，预览可直接提升为全屏，无需重新加载）。
- **引擎偏好按板块设置**：直播、影视各四种选择——任一引擎优先+自动交接，或只用某一个（关闭交接，
  但仍会尝试 `.m3u8` 与 `.ts`）。默认刻意不同：直播 ExoPlayer 优先，点播 mpv 优先。可按播放列表覆盖。
- **单项钉选**：播放器内 ⇄ MPV/EXO 按钮把某频道/影片钉到另一引擎并记住，钉选优先级高于全局设置。
- **四级降级阶梯**：`ExoPlayer+HLS → ExoPlayer+TS → mpv+HLS → mpv+TS`（或以 mpv 起头的同序列表），
  每级试一次；「放弃该频道的时间」默认 30 s，可设 15/30/60 s 或永不放弃。
- **点播降级不记忆**：每部影片都从你选的引擎开始。
- **DRM**：Widevine / ClearKey，走 DASH 与 HLS，从播放列表 `#KODIPROP` 读取许可证或内联密钥；
  受保护条目强制钉在 ExoPlayer，且**拒绝录制**（CDM 只能解密到安全解码器即时显示）。FairPlay
  无 Android CDM，直接给出明确提示。
- **容器判定靠证据**：先看声明的 `manifest_type`，再看响应实际格式，最后参考同源历史——
  这样无扩展名的 DASH 频道、Stalker/Xtream 的裸流地址才能播。

### 4.2 渲染与画面

- 解码器零拷贝直出，4K HDR 交给电视面板处理；硬件解码异常时可回落软解；高码率下丢晚帧保 A/V 同步。
- **自动帧率匹配**（可选）：向显示端请求片源原生刷新率；直播未声明帧率时实测，两次采样一致才采用；
  尊重系统的「匹配内容帧率」。影片侧可选「切换时暂停」「匹配分辨率」。
- **直播缓冲可控**：Live latency 决定真实缓冲大小，Pre-buffer（关/2/5/10 s）决定起播门槛，均可按播放列表覆盖。
- 画面模式：Fit / Fill / Stretch / Original / Force 16:9 / Force 4:3（单击切换下一模式，长按出列表）；
  默认缩放可按条目记忆。

### 4.3 音频

- 全音轨按语言标签列出，可实时切换；音轨语言与字幕语言按频道/影片/剧集记忆（分档案）。
- **环绕声**三档：Auto / 仅立体声 / 环绕；输出看门狗（静音、sink 错误、连续 underrun）会把会话锁到立体声保底。
- 音量增强至 150%（带软限幅）；A/V 同步微调 25 ms 步进（可按条目记忆）；上一频道；
  夜间模式、音量均衡、Dolby/DTS 直通；最高画质与单项 Quality 按钮；实验性 tunneled playback。
- **纯音频内容有标识**（Audio only 铭牌），与用户主动开的 **Audio Mode（关画面听声音）** 是两件事。

### 4.4 字幕

| 类型 | 处理方式 |
|---|---|
| 文本 SRT/ASS | 应用在直出通路上自行绘制 |
| 图形 PGS/VOBSUB/DVB | 交给第二层 ExoPlayer 渲染，视频仍在零拷贝/HDR 通路 |
| 闭路字幕 CEA-608/708 | 从视频流解码为可选轨道 |

- 字体、字号、颜色、位置、背景可独立设置；mpv 与 ExoPlayer 的字号分别存储（同一倍率视觉大小不同）。
- 首选音频/字幕语言按档案配置，含「原语言」（取 TMDB 原语言，回退流内主轨）。
- 外部字幕：OpenSubtitles 搜索（自有账号，可扫码+PIN 从手机登录）或本地文件；带 0.1 s 步进的时间轴微调。

### 4.5 播放器 HUD 与操作

- 可拖拽进度条、上一集/下一集、播放暂停、音轨/字幕/倍速、缩放与画面比例、音量与静音、收藏、
  流信息浮层（编码、分辨率、帧率、位深、HDR 类型、隔行、码率、解码器、音频、缓冲、丢帧、脱敏 URL）、时钟。
- 返回键先收起控制条，再退出。
- **换台**：CH+/CH− 在整个直播/追看会话内始终有效（含控制条弹出、起播中），两端循环；上下键与
  ⏮/⏭ 同理。左键出「当前上下文频道列表」，再左出直播分类面板（收藏、最近观看、追看、全部、各分组）；
  右键出最近 30 个频道。数字键直接输入频道号。
- **Sources**：同一频道的其他清晰度/分类/播放列表版本，一键切换。
- **续播**：电影/剧集 Always / Ask / Never；自动下一集跨季续播；追看可连播下一节目；重开剧集回到上次观看集。
- **迷你播放器**：把影片/剧集/频道停靠到角落，全应用内继续播放；尺寸与位置在设置里调。
- **PiP**：离开应用时进系统画中画（默认关，仅支持的电视可见）。

### 4.6 健壮性

内存预算随设备伸缩；4K/8K 软解死循环有解码看门狗拦截并明确报错；退到后台立即释放流；
播放器命令全部离开 UI 线程；直播断流自动退避重连；坏文件销毁重建 mpv 实例避免污染会话；
屏保恢复能按原位恢复暂停的影片、把直播重新拉到最新边缘。

---

## 5. 浏览与内容组织

### 5.1 首页

- **Now Trending**：TMDB 榜单 4–10 条，只展示当前源**确实能播**的匹配结果；榜单按源 5–8 天随机刷新，
  匹配每次同步都重跑。Full-bleed 布局下是一个标题+背景溶解+详情+Play/Trailer/全部版本/收藏，底部翻页器；
  Posters only 布局则变成首行海报墙。
- **继续观看**：电影/剧集/频道 16:9 静帧（TMDB backdrop → 源 backdrop → 海报 → 频道台标），带进度与剩余时间；
  停留 3 s 在卡内静音预览。
- 收藏频道（卡片或 On-now 小指南）、收藏电影、收藏剧集海报行、最近频道行（可选）。
- 向系统 **Watch Next**（Android TV 启动器）投递内容。

### 5.2 直播

- 头部显示分类与频道数，工具行（搜索、排序、指南视图）；84dp 频道行含号码、台标、名称、当前节目与剩余时间、
  进度、源标签、追看、收藏、播放列表标记。
- 右侧预览窗播放该频道：LIVE 角标、真实清晰度/帧率/音轨、节目进度与简介、下一档/稍后；
  ▶ 进入排期，OK 提供「提醒我 / 录制 / 看频道」。◀ 以玻璃面板带出分类（Stage 布局）或常驻列（Separate 布局）。
- 长按 OK / Menu 出分组菜单：观看 / 频道 / 指南数据 / 整理。
- 分类内可隐藏「全部频道」「追看」，可把整类隐藏/移动/恢复默认。
- 记住上次所在分类与频道；「直播从列表还是指南打开」可配置。

### 5.3 电影与剧集

- 两种布局：**Separate**（分类列 + 网格/列表 + 玻璃详情卡，海报列可 0% 隐藏）与
  **Cinematic**（焦点影片背景铺满整屏，上方只读 hero + 下方横向海报网格，分类走 ◀ 玻璃面板）。
- 排序工具、标题菜单（观看 / 资料库 / 整理 / 详情）；日期新增、A–Z、源顺序等。
- 剧集页：背景、续播/播放、收藏、**整季下载**（先告知集数；关闭下载的档案不显示）、季标签与集数
  （Specials 置末且不计入）、Grid | List、集序菜单（旧→新/新→旧、隐藏已看）。
- 集标题自动清理 `Show – S01E03 –` 前缀；页面标题去掉 `|MULTI|` 之类的源前缀（海报保留源写法）。
- 预告片在应用内播放器播放（OK 暂停、◀▶ 跳 10 s），被墙则换下一条，最后给「在 YouTube 打开」。

### 5.4 搜索

- 筛选按钮：直播 / 剧集节目 / 电影 / 剧集，带数量，OK 开关（全关=全选）。
- 频道结果复用直播的预览窗，影视结果复用海报详情面板。
- 语义是「只找不播」：OK 跳到条目所在分类/页面，返回保留查询词。
- 空状态给「跳转、最近搜索、继续观看」；**TV Shows** 组列出已存指南中标题匹配、正在播或 12 小时内播出的节目，OK 跳频道。
- 支持语音搜索（设备有语音识别器时；通过系统 `RECOGNIZE_SPEECH`，无需麦克风权限）。

### 5.5 体育

- 数据来自 ESPN：实时比分、今日/明日赛事、赛事页（比分、场地、时间线、统计、阵容、积分榜、近况、转播台）。
- 关注维度：可按运动项目与联赛多选，长按 OK 自排顺序；首页有带 LIVE 标记的体育行。
- **三个标签页**共用一套项目/联赛按钮：Scores、Calendar（月历 + 当日赛事，按月缓存）、
  Leagues（联赛卡片→积分榜/赛程/赛果；F1 车手/车队/分站；网球排名）。
- 球队独立页：本赛季赛程赛果、下一场、近五场、积分榜位次。
- **找频道**：在用户自己的指南里排序正在播该赛事的频道（开赛前 30 分钟起），可按语言/国家/画质/播放列表过滤；
  匹配全在本地设备完成，播放列表数据不出设备。
- 设置里有独立分组：联赛选择、自动重建、实时刷新、播放列表与严格频道过滤、日历刷新。

### 5.6 多播放列表与 Stalker

- 可把所有播放列表合并浏览，也可把整个应用收窄到某一个；右上播放列表胶囊切换（显示短标记与频道数），
  纯展示、不删不重导。多源同时活跃时，分类与条目带源标签。
- **Test / Info**：报告账号状态、到期时间、是否试用、已用连接数，不写任何数据。
- **Stalker / Ministra**：Portal URL + MAC，可选用户名密码，可选 Serial Number / Device ID / Device ID2 / Signature；
  MAG 握手、内存态 token、UA 预设；播放链接每次现签、过期静默重解析，长会话/断点续传下载/跨引擎降级都不受 token 重置影响。
  与 M3U、Xtream 功能对齐（下载、外部播放器、TMDB、备份、自动刷新、切换器）。

---

## 6. EPG / 电视指南

- **完整时间×频道网格**（XMLTV）：打开时当前时间位于 3/8 处，已预载最近两小时；焦点频道在顶部用直播的
  同一个预览播放器播放（不额外占解码器），旁边是光标所在节目详情。
- 控制行：今天、Now、分类、排序、搜索、自动匹配 EPG；带 now 线的刻度尺。
- 两段式焦点：先选行，OK/右键进入节目，返回退出；格子显示录制/提醒/追看图标；空档是可聚焦的
  「该时段无节目」单元，左右以 30 分钟跨越。
- 节目操作（提醒我 / 从头看、录制、看频道……）集中在长按 OK 菜单。
- **追看频道**会向 core 请求供应商归档中的过去天数（一次一个请求，最多回溯一周），指南能追到与录像同样远。
- 排序：源顺序、A–Z、追看优先、收藏优先；分类过滤复用直播的分类列表。
- **节目提醒**：提前量（默认 5 分钟）到点后在任意界面（含播放器）询问切换 / 直接切换 / 只通知。
- **追看 TV**：回放已播节目，最多 7 天，可拖动；**直播回看（rewind）** 在支持归档的频道上擦洗直播流；
  **Go back to…** 用日/时/分选择器直接跳到某时刻，且**无指南也能用**；直播页有独立 Catch-up 分类。
- **本地时移（可选）**：无追看能力的频道在全屏观看时于本机留存 15–60 min（始终保留 ≥1 GB 空间），
  从该副本播放，因此可暂停/回看；离开 5 分钟内可 Resume / Go live，切到别的频道 2 分钟后删除，每次启动清空。
- **自动匹配 EPG**：缺 `tvg-id` 或错误时按名称匹配（400 频道约 3 秒），高置信自动应用，其余进复核列表；
  匹配按档案保存、重同步不丢。**时区偏移**可按全局或单频道校正。
- **多 XMLTV 源**：EPG Sources 的顺序即优先级（同一频道由高优先级绘制，低优先级只补空档）；
  每个源可有自己的台标与自动刷新间隔；导入播放列表**不会**自动下载指南（需显式开启）。

---

## 7. 录制与多画面

### 7.1 录制

- 三个入口：指南（按节目时间，支持前后 padding）、频道列表、播放器内；可对某节目在频道上设「每场都录」。
- 追看节目可从供应商归档另存。
- 录制文件进 **下载 → 录制** 标签，显示时长、大小、完整路径；录制约一个播放列表连接数，开始前会说明。
- 跨重启的录制预约在下次启动时修复；加密频道录制会立即拒绝并说明原因。

### 7.2 多画面（Multiview）

- 最多 4 路直播同屏，一路出声；可对某格「只留声音」保留解说放弃画面。
- 从播放器进入，或在频道上「加入 Multiview」组队；某格起不来会说明原因而不是留白。
- 画面用 **TextureView** 而非 SurfaceView——设备的硬件视频层很少，部分只有一层，第二个 SurfaceView 会只剩声音没画面。
- 离开网格即全部停止。

### 7.3 连接数测量

- 首次同步时（任何频道行出现前、没有播放时）**实测**该账号允许的并发流数量，随播放列表存储、
  随备份迁移（描述账号而非设备），用于在供应商拒绝前预警。坏频道不会污染结果（存不下就不存）。
- Settings → Playlists → Info 可查看，**Re-test** 在警告后重新实测；**忽略连接限制**（按播放列表）
  让 Multiview 与录制尝试超出声明的路数。

---

## 8. 档案、下载与存储

- **多档案**：各自独立的收藏、历史、续播点与布局；可选 PIN 锁（加盐哈希）与「谁在看？」启动门。
- **儿童模式**：在直播、电影、剧集、首页、搜索、指南、追看、下载、启动器推荐、直接播放等所有面向上
  过滤成人分类与条目，并排除成人向 TMDB 结果；指南本身也隐藏。识别用多语言分类标记 + Adult Swim 例外。
- 源可在档案间共享；切换档案不必退出应用。
- **分类级 PIN 锁**：单个分类可挂 PIN（与 Customize 的 PIN 共用错误等待），设置本身也可要求 PIN。
- **下载**：仅电影与剧集（不含直播），可单集或整季；支持暂停、续传、重试、删除、外部播放器播放；
  多连接（最多 8，受播放列表限制）分工写入同一文件，保证 U 盘跟得上；失败说明原因，文件丢失显示 File missing；
  单流播放列表会暂停下载并询问「继续看 / 继续下」。
- 下载目录可选（含 USB 卷），按类型固定子目录；可**按档案**关闭下载。

---

## 9. 外观、布局与设置体系

### 9.1 界面骨架

- 全应用一套 **Stage** 设计：整宽页面、Plus Jakarta Sans、单一线性图标集、统一焦点光晕与弹窗套件。
- **导航栏**：搜索、首页、直播、体育、指南、电影、剧集、下载、更多；顶部字标、底部档案头像；
  有停靠播放或 Audio Mode 时出现「正在播放」。可设 Floating（胶囊，◀ 唤出，2/4/8 s 后隐藏）或
  Docked（常驻，内容重排）；尺寸 Compact / Normal / Wide（带计数与档案行）/ Extra wide；
  长度适应条目或满高；菜单项 Dynamic（跟随播放列表）或 Static（可勾选隐藏，搜索与更多永不可藏）。
- **右上角簇**：Continue 胶囊（续播 / 播放 / 下一集 / 上一频道）、播放列表胶囊、大时钟+日期+天气（颜色可调）。
- **面板宽度**按各页面布局分别命名：Separate 三份额（第三份可为 0%）、直播 Stage 的列表+预览与分类面板分开、
  Cinematic 的面板与详情高度、指南两列独立分配。
- **主题**：System / Dark / Light。浅色会重绘整站（页面、玻璃、焦点环、播放器 HUD 及列表、TMDB 白色标题字改深墨），视频画面不变。

### 9.2 设置（12 组）

| 分组 | 包含 |
|---|---|
| Quick | 用户长按 OK 钉住的常用行 |
| Profile | 档案、新增档案、儿童模式、PIN |
| Sources & guide | 播放列表、EPG 源、时区偏移、节目提醒与提前量、指南保留天数、追看时区（+按播放列表） |
| Appearance | 主题、强调色、焦点高亮、玻璃与背景、Ambient Glow、字体与字号、弹窗尺寸、UI 缩放、动画、日期时间天气（带首页实时预览） |
| Layout | 导航栏、直播布局、直播默认视图、影视布局、面板宽度、指南列宽、浏览与列表、首页、长按菜单、遥控器快捷键 |
| Content & metadata | 自定义分类与条目、TMDB 元数据、OpenSubtitles |
| Player | 直播/影视引擎（+按播放列表）、已保存选择重置、外部播放器、影片缓冲、网络超时、重连次数 |
| Picture | 硬件解码、优先 SDR、最高画质、Tunneled、默认缩放与已存缩放、自动帧率、切换暂停、匹配分辨率 |
| Sound & subtitles | 默认音量、环绕、Dolby/DTS 直通、夜间模式、音量均衡、音频同步（+已存）、首选音频/字幕语言、字幕外观 |
| Live TV | 频道号、预览开关与预览声音、暂停回看与时长、Resume 策略、左右键回看步长、延迟/预缓冲/放弃时间（+按播放列表） |
| Watching & recording | 快进步长、回看步长、续播策略、自动下一集、Multiview 与最大格数、迷你播放器、PiP、录制 padding 与保留一路空闲 |
| App | 语言、应用图标、强调色图标、启动页、开机/唤醒自启、检查更新、代理、DNS、实测流统计、详细播放日志 |

- 组内是「左行 + 右玻璃上下文面板」：面板说明该行、推荐选项与按键提示；开关/选项/步进器/分段控件直接落在行上，
  只有进入子页才出现箭头。**搜索全部设置**匹配名称并显示路径、标题、当前值。
- 子页返回时焦点精确回到原来那一行。

### 9.3 个性化要点

- **强调色**：预设、十六进制、可分离聚焦的饱和度方块与色相条，实时重染，取消可还原；焦点高亮可另设一色。
- **应用图标**：8 种配色的翻卡图标 + Pixel 点阵电视，共 9 个 launcher activity（清单里逐一注册、只启用一个），
  切换会同时改变启动器横幅、启动屏与全应用 logo；`MainActivity`（Eggshell）保持默认名字以兼容旧快捷方式。
- **背景**：Stage（柔和强调光）/ Picture（本地或手机推送的图片，Sharp/Soft/Dark + Darken/Blur）/ Plain；
  **Glass** 按不透明度磨砂导航、面板、菜单、弹窗。
- **遥控器快捷键**：备用彩色/数字/频道/媒体键的长短按映射到 26 个动作，关键键受保护；
  **播放器按键**可定义 OK 与长按 OK 的行为、交换 CH+/−、关闭数字键。

---

## 10. 语言与首次启动

- 全新安装先出**语言选择器**（在 Get Started 之前），英文 + 24 个语言资源目录（`values-ar/bn/cs/da/de/es/es-rUS/fr/hi/hu/it/ja/ko/ml/nb/nl/pl/pt/pt-rPT/ru/sv/tr/zh-rCN/zh-rTW`）；
  字符串主源在 `OwnTV_Core`，新增语言需两侧同步。
- 首次启动链路：欢迎（含语言）→ 显示（UI 缩放、字号、图标、样例行）→ 免责声明 → **Set up OwnTV** →
  档案 → **添加播放列表** → 导入 → **All set**（此时可加电视指南）。
- **三条建档路线**：新建档案 / 恢复备份文件 / **从另一台 OwnTV 设备整机复制**（局域网，首次即可用，不必先走完向导）。
- **加源四张卡**：Remote（手机扫码+PIN 填写，避免用遥控器输密码）、Type it here、Import（本地文件）、Existing。
  同一套 Remote / 手填页面在 Settings → Playlists → Add 也能进，不是一次性机会。
- 应用语言与档案无关，也与 TMDB 元数据语言相互独立，重启与备份恢复后保持；复数、日期时间数字、RTL 导航、
  字体回退、英文兜底覆盖界面/通知/启动器文案/播放器消息/诊断。

---

## 11. 备份、本地同步与更新

- **备份 & 恢复**：单文件 `.own` 容器（数据 + 背景图 + 已下载字幕）；可选密码整包加密（AES-256-GCM + PBKDF2）；
  不设密码则明文且**省略所有机密**（源与代理密码、Stalker 身份、TMDB/OpenSubtitles 凭据、PIN 哈希）。
  恢复接受 `.own` 与旧版 `.json`，按内容而非扩展名识别；合并恢复会重映射文件内所有 id；
  源按类型+URL+用户名（Stalker 按类型+URL+MAC）匹配。
- **Android 自动备份被刻意关闭**：原始存储里有明文凭据，这个页面是唯一显式、可加密的离机通道。
- **本地同步**：与 OwnTV 手机端在局域网互换收藏、历史与续播点，无账号无云端；双方需主动进入同步模式，
  到达的容器先预览再应用；删除会以删除传播，不被合并复活；首次启动也能用（空白设备只收不发）。
- **应用内更新**：从 GitHub Releases 检查与安装，可选启动时检查，手动检查展示完整 changelog；
  安装交给系统安装器（FileProvider）。
- **错误日志**（更多）：最后一次崩溃 + 播放失败/降级/上报的可读历史，可选详细追踪；崩溃即时落盘；
  导出到 `Download/owntv-playback-report.txt`。

---

## 12. 平台集成与权限

- **自动启动**（设备级，默认关）：开机（`BOOT_COMPLETED`）、应用更新（`MY_PACKAGE_REPLACED`）、
  屏幕唤醒（`WakeListenerService`，`specialUse` 前台服务）时打开应用；需要「在其他应用上层显示」权限；
  受 PIN 保护的档案每次唤醒都会重新上锁。
  - 两者跑在独立进程 `:autostart`，该进程里 `OwnTVApp` 不初始化任何东西，避免为等一个广播把整个应用驻留内存。
- **进程与生命周期**：launcher activity 声明 `configChanges` 覆盖遥控器唤醒、HDR/帧率切换、密度变化，
  避免观影中重建 Activity；`locale|layoutDirection` 故意不声明，脚本语系切换仍需一次重建。
- **屏幕方向**：9 个入口 Activity 统一声明 `screenOrientation="sensorLandscape"`——手机、平板打开即横屏，
  因为 Stage 界面只有一套 16:9 布局，没有竖屏版本。TV 本来就是横屏，行为不变；Android 16 会在最小宽度
  ≥600dp 的大屏上忽略该声明，正好限定为手机场景。方向变化已被 `configChanges` 接住，不会重建 Activity。
- **PiP**：`supportsPictureInPicture`，窗口尺寸变化靠 `screenSize|smallestScreenSize|screenLayout` 保活。
- **Deep link**：`owntv://play`、`owntv://open`（每个图标 activity 都带全部 intent-filter，禁用的 activity 解析不到，链接总能落到启用的那个）。
- **Watch Next**：`WRITE_EPG_DATA` 写入 Android TV 启动器推荐位。
- **存储权限**：`MANAGE_EXTERNAL_STORAGE` + `READ_EXTERNAL_STORAGE`（≤API 32，故意保留以适配 TCL Android 12
  「权限盾」）+ `WRITE_EXTERNAL_STORAGE`（≤API 29）；`requestLegacyExternalStorage=true`。
- 其它：`REQUEST_INSTALL_PACKAGES`（应用内更新）、`SYSTEM_ALERT_WINDOW`（自启）、
  `FOREGROUND_SERVICE_SPECIAL_USE`（唤醒监听）；`usesCleartextTraffic=true`（IPTV 源常为 HTTP）；
  `allowBackup=false`。
- 网络/前台服务/通知权限由 `:core` 清单合并进来；`MODIFY_AUDIO_SETTINGS` 由 `:player-core` 提供。
- `queries` 声明 `RECOGNIZE_SPEECH`，否则 Android 11+ 上搜索页看不到麦克风。

---

## 13. 架构与工程结构

### 13.1 分层

```
tv.own.owntv
├── MainActivity / MainActivityColours   多图标入口，共用同一 Compose 树
├── OwnTVApp                             Application：Koin 启动、WorkManager 配置
├── core/i18n                            本地化内容、RTL 键、字体回退
├── di/                                  AppModule、PlayerModule
├── ui/                                  主题与可复用组件
├── player/                              HUD、迷你播放器、字幕/信息浮层、帧率控制、缩放、录制角标
├── features/                            22 个功能包（见下表）
└── BlurredBackdropLoader                搜索页背景模糊（一次性）
```

| 包 | 文件数 | 职责 |
|---|---|---|
| `shell` | 31 | 导航壳、焦点/返回语义、顶栏胶囊、离线横幅、播放器托管、待处理请求 |
| `settings` | 58 | 12 组设置、EPG 源、播放列表管理、备份/本地同步、DNS/代理、字体/语言/面板宽度、PIN 门 |
| `sports` | 15 | 比分/日历/联赛/球队/找频道/体育设置 |
| `live` | 11 | 直播列表与预览、追看对话框、换台延迟、隐藏项校正 |
| `setup` | 8 | 向导、加源、Remote 配对、显示尺寸 |
| `autostart` | 7 | 开机/唤醒自启、策略与档案门、独立进程组件 |
| `customize` | 7 | 分类与条目隐藏/改名/排序、批量重命名、移动到分类 |
| `profiles` | 7 | 档案列表、PIN 锁屏、头像选择、启动门 |
| `categorylock` | 6 | 分类级 PIN 与到期提醒 |
| `home` | 6 | 首页各区块、Hero、分页器 |
| `pip` | 6 | 画中画策略、Shell/窗口实现、应用交接 |
| `epg` | 4 | 指南网格、Guide 核心与阶段、ViewModel |
| `search` | 5 | 搜索页、语音、定位揭示 |
| `downloads` / `recordings` | 3 / 2 | 下载列表与状态、录制列表 |
| `movies` / `series` | 2 / 2 | 各自列表与详情 |
| `multiview` | 3 | 多画面状态、磁贴菜单 |
| `subtitles` | 3 | 字幕搜索与删除 |
| `more` | 3 | 更多中心与计数 |
| `recovery` | 1 | 数据库损坏恢复页 |
| `update` | 3 | 更新对话框、失败文案、状态提示 |

### 13.2 关键设计

- **双引擎同一个 `PlaybackEngine` 接口**，降级阶梯与看门狗都在 `:player-core`，与手机端共享；
  直播把正在跑的预览直接提为全屏；外壳在全屏与迷你播放器之间「吊装」同一个 surface；状态以 `StateFlow` 喂给 Compose HUD。
- **解析与同步**：M3U 行式流式解析，Xtream `player_api` JSON 用 `android.util.JsonReader`，超大响应不整体入内存；
  重新同步按内容哈希 diff，保留供应商顺序且不改写未变行；写入分块事务、可取消、带进度，
  下载/解析与写库通过 rendezvous channel 重叠；整表被截断（HTTP 512）时退化为按分类抓取。
- **存储**：Room 覆盖档案与源（交叉引用支持共享）、内容（分类/频道/电影/剧集/季/集）、
  按档案的收藏/历史/进度/下载、EPG 频道与节目、录制与录制规则、外挂字幕缓存与关联、FTS4 搜索表、
  按源的 Trending 快照；Paging 3 限制 `maxSize` 让 5 万条列表内存保持平稳；同步后与启动时跑 `ANALYZE`。
- **元数据**：`MetadataRepository` 是共享的懒加载 TMDB 缓存；Trending 复用已确认的 id，不做二次模糊搜索；
  默认网关是 Cloudflare Worker（缓存键规范化、Trending 15 分钟新/24 小时旧、普通元数据 30/90 天、
  瞬时失败重试一次、不暴露 TMDB key）；每次安装独立配额（40/分、150/时、400/日），单点计量。
- **DI**：Koin 模块 `appModule`、`databaseModule`、`dataModule`、`playerModule`。

---

## 14. 构建与变体

- **ABI 双 flavor**：`standard`（`arm64-v8a` + `armeabi-v7a`，真机/arm 模拟器）与 `x86_64`（模拟器/Intel 盒子）；
  两包分别发布以减小下载体积（约 49 MB vs 合并 104 MB）。**必须选对 ABI，原生播放器才能加载**。
- buildTypes：`debug` / `release`（R8）/ `benchmark` / `non-minified`。
- 命令：`./gradlew assembleDebug`，产物在 `app/build/outputs/apk/`；发布构建 `:app:assembleStandardRelease`。
- `versionCode`/`versionName` 由环境变量注入，本地默认 `99999 / 99.99.99`。
- **签名/密钥不从仓库读**：`owntv-signing.properties`（仓库外）+ 环境变量。
- `owntv.devTools=true` 才编译开发者页面；`owntv.edgeKey` 是默认元数据 Worker 的共享密钥。
- **本地联调 core**：在 `~/.gradle/gradle.properties` 设 `owntv.corePath=<OwnTV_Core 路径>`，
  Gradle 用 `includeBuild` 替换已发布 artifact，改 core 无需先发布。
- **基线配置**：`:baselineprofile` 是纯测试模块，`./gradlew :app:generateBaselineProfile` 在 API 33+ 设备录制冷启动，
  `mergeIntoMain` 合并进主源集；侧载应用靠 `profileinstaller` 在首启安装 ART profile。
- Gradle 侧已调优：4 GB Gradle heap、8 GB Kotlin daemon、`workers.max=4`、
  禁用 Kotlin daemon 回退（避免 OOM 后静默在 Gradle daemon 里重编）、开启配置缓存与构建缓存。

---

## 15. 边界、依赖与注意事项

1. **不含内容**：仓库不带任何频道、播放列表、订阅或流；用户对所用源负全部责任。GPLv3。
2. **一半代码不在本仓库**：`core` / `player-core` 从 OwnTV 公共 Maven 拉取（`ahxn00.github.io/OwnTV_Core/maven`），
   无需账号；因此数据库 schema、同步逻辑、引擎实现、字符串需到 `OwnTV_Core` 查看。
3. **第三方服务**：TMDB（元数据/预告片）、OpenSubtitles（字幕，需用户自有账号与配额）、ESPN（体育数据）、
   YouTube IFrame（预告片）。均有各自的可用性与条款约束。
4. **平台限制**：PiP 仅部分电视支持；自动帧率在 Android 12 以下风险较高（会被主动关闭并提示）；
   FairPlay 无法播放；DRM 内容不可录制；Multiview 上限受硬件视频层与账号连接数双重约束。
5. **文档口径差异**：README 写「26 种语言」，本仓库 `res` 下有 24 个语言变体目录（+ 默认英文）；
   差异来自 core 的字符串包，改语言相关代码时两侧要对齐。

---

## 附：与现有文档的关系

| 文件 | 用途 |
|---|---|
| `README.md` | 对外介绍、安装、构建入口（英文） |
| `extras/USER_GUIDE.md` | 面向用户的逐功能操作说明（英文，1078 行） |
| `extras/OwnTV_Master_Product_Brief.md` | 已建成功能与架构的完整参考（英文，622 行） |
| `CHANGELOG.md` | 版本级新增/修复清单，含 Unreleased |
| `docs/internationalization.md` | 国际化体系（1900 行）与各阶段 i18n 文档 |
| **本文** | 中文功能整理 + 代码位置映射，供开发/交接/评审快速定位 |
