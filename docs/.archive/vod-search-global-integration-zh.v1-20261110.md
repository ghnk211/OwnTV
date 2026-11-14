# 点播页搜索框并入全局搜索 · 技术方案

> 范围：Movies / Series 页面工具行上的分类内搜索框，与 `features/search` 的全局搜索页。
> 目标读者：负责本次改造的开发与评审人。
> 本文只描述方案，不包含已落地的代码改动。

---

## 1. 目标

把点播页（电影 / 剧集）里的搜索框从「当前分类内的内联过滤」升级为「全局搜索的统一入口」，并让全局搜索在进入时携带点播页的上下文（当前分类、内容类型），从而做到：

1. 应用内只有一套搜索输入与结果界面，不再有两套语义（分类内 FTS 过滤 vs 跨区 FTS 搜索）。
2. 点播页的搜索框保留原有位置与视觉，但按 OK 后交给全局搜索，并自动弹出键盘。
3. 全局搜索页能显示「范围」并支持一键清除，清除后退回全库搜索。
4. 返回路径明确：全局搜索 → 点播页原分类，焦点回到原搜索框；从结果跳到条目后仍可先回搜索。

非目标：

- 不改动全局搜索的检索引擎（core `SearchReader`）的匹配规则、隐藏项、Kids 过滤、Customize 改名等既有语义。
- 不改动 Live TV / Guide 的分类面板内搜索（`LiveCategories` 的 `categoryQuery`，属于分类浏览器，不是内容搜索）。
- 不在本期实现「收藏 / 历史 / 自定义分类」作为搜索范围（见 §12 待确认）。

---

## 2. 结论摘要

**推荐方案：入口交接 + 搜索范围（Scope）。**

- 点播页的 `StageSearchField` 使用其已有的 `onActivate` 参数，把 OK 事件交给外壳（与 Settings 全页搜索交接给 Settings 搜索的实现同构）。
- 外壳调用全局搜索的 `SearchViewModel.setScope(...)` + `setQuery(...)`，然后 `onSelectSection(MainSection.SEARCH)`。
- `SearchViewModel` 新增 `scope` 状态，对 `SearchReader` 的结果做范围收敛；`SearchScreen` 增加自动编辑与范围条（Scope Bar）。
- 点播页的 `MovieViewModel.searchQuery` / `SeriesViewModel.searchQuery` 不再驱动列表，从点播列表链路移除。

改动量集中在 6 个文件 + 2 段字符串资源，不动 core。

---

## 3. 现状分析

### 3.1 全局搜索

| 关注点 | 现状 | 位置 |
|---|---|---|
| 入口 | Stage 导航栏首项（永不可隐藏），也支持遥控器快捷键 `OPEN_SEARCH` | `features/shell/components/StageRail.kt:102`、`features/shell/OwnTVShell.kt:818` |
| 页面 | `SearchScreen`：标题带、搜索框 + 语音、筛选按钮（LIVE / TV_SHOWS / MOVIES / SERIES）、左侧结果列、右侧预览/海报面板 | `features/search/SearchScreen.kt:153` |
| 输入 | `SearchFieldRow` → `StageSearchField`，查询词 ≥2 字符才检索 | `features/search/SearchFieldRow.kt:35`、`SearchScreen.kt:191` |
| 检索 | 300 ms 防抖后调用 core `SearchReader.search(profileId, ActiveProfileSources, query)`，结果用 `SearchResults.only(kinds)` 做类型过滤 | `features/search/SearchViewModel.kt:66`、`:115` |
| 结果跳转 | OK 只「去往」条目所在分类/页面，不直接播放；`searchReturn` 让返回时先回搜索页 | `SearchScreen.kt:263`、`OwnTVShell.kt:919` |
| 附带能力 | 语音搜索、最近搜索、空态「跳转 / 继续观看 / 频道」、频道复用直播预览窗 | `SearchFieldRow.kt:57`、`SearchViewModel.kt:90` |

关键点：`SearchViewModel` 在 `OwnTVShell` 与 `SearchScreen` 中通过 `koinViewModel()` 获取，是同一个 activity 作用域实例（`OwnTVShell.kt:257`、`SearchScreen.kt:164`），因此外壳可以直接驱动它。

### 3.2 点播页搜索框

电影页与剧集页各有两处搜索框（Cinematic 布局与 List 布局）：

| 页面 | 位置 |
|---|---|
| Movies · Cinematic | `features/movies/MoviesScreen.kt:584` |
| Movies · List/Separate | `features/movies/MoviesScreen.kt:697` |
| Series · Cinematic | `features/series/SeriesScreen.kt:657` |
| Series · List/Separate | `features/series/SeriesScreen.kt:767` |

四处的形态一致：

```kotlin
StageSearchField(
    query = searchQuery, onQueryChange = vm::setSearchQuery,
    placeholder = stringResource(R.string.content_search_in, headerLabel),
    modifier = Modifier.width(440.mpx).focusRequester(listSearchFocus).then(searchLeft),
    onEditingChange = { searchEditing = it },
)
```

它的语义是**分类内过滤**：

- 查询词进入 `MovieViewModel._search`（`MovieViewModel.kt:293`）或 `SeriesViewModel._search`（`SeriesViewModel.kt:298`），300 ms 防抖后重建 Paging 参数（`MovieViewModel.kt:442`）。
- 查询词最终落到 `pagingSource(...)` 的搜索分支：`searchAll` / `searchFavorites` / `searchHistory` / `searchInCategory` / `searchCustom`（`MovieViewModel.kt:1043-1052`）。
- 因此只能看到**当前 rail 分类内**的结果，无法跨分类，也没有语音、最近搜索、预览面板。
- `◀` 在未编辑时打开分类面板（`searchLeft`，`MoviesScreen.kt:551`）。

### 3.3 已有的「交接」机制

`StageSearchField` 本身已经支持把搜索交给另一个页面：

```kotlin
/** Set = OK calls this instead of editing here (a full page's search hands over to Settings search). */
onActivate: (() -> Unit)? = null,
```
——`ui/stage/StageControls.kt:76-77`

Settings 全页正是这样做的：`LocalSettingsSearch` 提供回调，`StageFullPage` 把 `onActivate` 接到它上面（`features/settings/SettingsStage.kt:845-848`、`:887`）。

同理，Home 的 Now Trending 已有「带查询进入搜索页」的先例：

```kotlin
onOpenTrendingSearch = { query ->
    searchVm.setQuery(query)
    trendingSearchActive = true
    ...
    onSelectSection(MainSection.SEARCH)
}
```
——`features/shell/OwnTVShell.kt:1273-1278`

**本方案就是把这两个既有模式组合起来**，不引入新的导航机制。

### 3.4 能力差异

| 能力 | 点播页搜索框 | 全局搜索 |
|---|---|---|
| 范围 | 当前分类（Movies/Series 之一） | 全库四类 |
| 跨分类 | 否 | 是 |
| 语音 | 无 | 有 |
| 最近搜索 | 无 | 有 |
| 结果预览 | 无 | 直播预览窗 / 海报详情面板 |
| 结果跳转 | 直接在当前列表内 | 跳到条目所在分类/页面 |
| 筛选 | 无 | 四个类型开关 |
| 返回语义 | 清空查询 | 先清查询，再离开 |

结论：全局搜索是点播搜索的超集，唯一的差异项是「范围」与「就在原地过滤」的手感。

---

## 4. 方案选型

### 4.1 候选

**A. 入口交接 + 范围（推荐）**

点播页搜索框按 OK → 直接进全局搜索页，键盘自动弹出，结果范围锁定为「当前类型 + 当前分类」。

**B. 点播页内嵌全局结果**

在点播页工具行下展开一个覆盖层，复用 `SearchReader` 展示跨区结果，但不切 section。

**C. 保留内联过滤，额外加一个「全局搜索」按钮**

搜索框仍过滤当前列表，右侧新增一个放大镜/箭头按钮，点击后带 `query + 当前分类` 进全局搜索。

### 4.2 对比

| 维度 | A | B | C |
|---|---|---|---|
| 语义统一 | 彻底（只剩一套搜索） | 中等（两套 UI，共用引擎） | 差（两套结果同时存在） |
| 改动量 | 小 | 大（新覆盖层、焦点/返回、预览复用） | 小 |
| 破坏现有手感 | 是（不再原地过滤） | 否 | 否 |
| 复用语音/最近/预览 | 全量复用 | 部分复用 | 全量复用 |
| 返回/焦点复杂度 | 低（section 级） | 高（同页覆盖层） | 低 |
| 风险 | 行为变更需产品确认 | 中高 | 低 |

### 4.3 推荐

**首选 A**，理由是：点播页的分类内搜索本来就有明显短板（不能跨分类、无语音、无预览），而全局搜索已经具备这些能力，且项目里已有两处「搜索交接」先例。

如果产品上必须保留「原地过滤」的手感，则退到 **C**：A 的全部后端改动仍然复用，只是入口从「搜索框 OK」换成「搜索框右侧的全局搜索按钮」，实现代价几乎相同。

B 不建议：它会让点播页同时承载列表与全局结果两套焦点模型，和 `focusTitles` / `listPin` / 分类面板的既有焦点逻辑冲突（`MoviesScreen.kt:392`、`:552`）。

---

## 5. 详细设计（方案 A）

### 5.1 交互流程

**进入**

1. 用户在 Movies 页分类「Disney+ Movies」的工具行聚焦搜索框。
2. 按 OK：不弹出本页键盘，直接切到全局搜索页。
3. 全局搜索页的搜索框自动获得焦点并自动打开键盘（`autoEdit`）。
4. 搜索框下方出现范围条：`电影 · Disney+ Movies  ✕`。
5. 若用户是在点播页已经输入过草稿（C 方案或后续增强），草稿作为初始 `query` 带入。

**搜索**

6. 输入 ≥2 字符 → 只检索该范围；结果列表为单类型平铺（沿用 `singleKind` 逻辑，`SearchScreen.kt:195`）。
7. 语音按钮可用（全局搜索已有）。

**离开**

| 操作 | 行为 |
|---|---|
| Back（有查询词） | 清空查询与范围，留在搜索页，焦点回搜索框（沿用现有 `BackHandler`，`SearchScreen.kt:229`） |
| Back（无查询词，来自点播页） | 回到 Movies/Series 页原分类，焦点回到该页搜索框 |
| 范围条 ✕ | 只清除范围，保留查询词并立即重查（变为全库） |
| OK 打开结果 | 走现有 `searchReturn` 机制，进入条目所在页面；在该页 Back 先回搜索页 |

### 5.2 数据契约

新增 `features/search/SearchScope.kt`：

```kotlin
package tv.own.owntv.features.search

enum class VodScopeType { MOVIE, SERIES }

/**
 * The range a global search was opened from. [All] is the ordinary, unrestricted search.
 * [categoryId] is the provider category DB id (LiveKey.Folder); null means "every category
 * of this type". [label] is already display-ready (provider prefixes stripped), [railKey]
 * lets the browse page restore its selected category on return.
 */
sealed interface SearchScope {
    data object All : SearchScope

    data class Vod(
        val type: VodScopeType,
        val categoryId: Long?,
        val label: String,
        val railKey: String?,
    ) : SearchScope
}
```

`label` 直接取点播页已有的 `headerLabel`（`MoviesScreen.kt:432`），它已经做过 `ProviderTags.parse(...).name` 清洗，避免把源前缀带进 UI。

> 为什么不直接用 `LiveKey` 作为范围 key：`LiveKey` 还包含 `Favorites` / `History` / `Custom(id)` / `Catchup`，这些不是「分类」，不能映射到一个 `categoryId` 上。本期只用 `Folder(id)`，其余分类退化为「仅类型范围」（`categoryId = null`），见 §5.6。

### 5.3 状态归属

放在 `SearchViewModel`（activity 作用域，shell 与 screen 共享）：

```kotlin
private val _scope = MutableStateFlow<SearchScope>(SearchScope.All)
val scope: StateFlow<SearchScope> = _scope.asStateFlow()

fun setScope(scope: SearchScope) { _scope.value = scope }
fun clearScope() { _scope.value = SearchScope.All }
```

生命周期：

- 进入搜索页前由外壳 `setScope(...)`。
- 在既有「重置搜索」的位置同步清空：`OwnTVShell.kt:796`（快捷键/`openShortcutSection`）与 `OwnTVShell.kt:1089`（导航栏切换）。这两处已经有 `searchVm.setQuery("")`，加一行 `searchVm.clearScope()` 即可。
- 不在 `SearchViewModel.onCleared` 之外做持久化（范围是会话态，与 `query` 一致）。

### 5.4 点播页改造

**新增参数**

```kotlin
fun MoviesScreen(
    ...,
    onOpenSearch: (query: String, scope: SearchScope.Vod) -> Unit,
)
```

由 `OwnTVShell.kt:1362` / `:1371` 传入。Series 同理。

**四处搜索框改为入口**

```kotlin
StageSearchField(
    query = "",                     // 见下方说明
    onQueryChange = {},
    placeholder = stringResource(R.string.content_search_in, headerLabel),
    modifier = Modifier.width(440.mpx).focusRequester(listSearchFocus).then(searchLeft),
    onActivate = { onOpenSearch("", vm.currentScope()) },
)
```

- 移除了 `onEditingChange`，`searchEditing` / `searchLeft` 不再需要（`MoviesScreen.kt:549`、`:553`；`SeriesScreen.kt:622`、`:624`）。`◀` 直接走「打开分类」的现有逻辑。
- 若采用 C 方案，则保留 `query = searchQuery` / `onQueryChange = vm::setSearchQuery` / `onEditingChange`，只把 `onActivate` 加到同一处调用上。

**`currentScope()`**

在 `MovieViewModel` / `SeriesViewModel` 中加一个纯函数，把当前 `selectedKey` 映射为范围：

```kotlin
fun currentSearchScope(): SearchScope.Vod {
    val key = selectedKey.value
    val label = /* 与 MoviesScreen.headerLabel 同源的显示名 */
    return SearchScope.Vod(
        type = VodScopeType.MOVIE,
        categoryId = (key as? LiveKey.Folder)?.id,
        label = label,
        railKey = LiveKey.serialize(key),
    )
}
```

> `headerLabel` 目前在屏上算（依赖 `selectedItem` / `selectedLabel`）。为避免逻辑重复，建议把「当前分类显示名」下沉到 ViewModel 的一个 `StateFlow<String>`，屏上直接消费；这也是方案里唯一建议做的小重构。

**清掉内联过滤**

采用 A 方案时，把以下链路移除或置空：

- `MovieViewModel._search` / `searchQuery` / `setSearchQuery`（`MovieViewModel.kt:293`、`:563`）
- `movies` 流里的 `_search` 参与（`MovieViewModel.kt:442`）
- `pagingSource` 的 `query` 分支（`MovieViewModel.kt:1043-1052`）——注意 `MovieDao.search*` 系列方法可能仍被 `revealMovie` 的定位调用（`MovieViewModel.kt:582` 里传的是 `""`），需要确认后只删「由搜索框驱动」的入参，不删 DAO 方法本身。
- Series 侧同理（`SeriesViewModel.kt:298`、`:733`）。

如果希望降低风险，P0 可以先只做「交接 + 范围」，暂不删除内联过滤（即 C 方案的形态），确认交互后再删。

### 5.5 全局搜索页改造

**(1) 自动编辑**

`SearchFieldRow` 增加 `autoEdit: Boolean = false` 并透传给 `StageSearchField`（`SearchFieldRow.kt:43`、`StageControls.kt:75`）。`SearchScreen` 在「本次是从点播页进入」时传 `autoEdit = true`。

`StageSearchField` 的 `autoEdit` 是延迟 250 ms 后进入编辑并请求焦点（`StageControls.kt:85`），可避开页面自身的焦点恢复。

**(2) 范围条**

在筛选按钮行的位置（`SearchScreen.kt:324-339`）按状态二选一渲染：

```kotlin
val scope by vm.scope.collectAsStateWithLifecycle()

if (scope !is SearchScope.All) {
    ScopeBar(
        scope = scope as SearchScope.Vod,
        onClear = { vm.clearScope() },
        modifier = Modifier.padding(start = fx(84) - 12.mpx, top = 192.mpx),
    )
} else if (searching) {
    // 现有四个 SearchKind 筛选按钮
}
```

`ScopeBar` 建议复用 `StageTab` 或 `StageSurface`，左侧图标沿用 `OwnTVIcon.SEARCH`，右侧 `OwnTVIcon.CLOSE`（`ui/components/OwnTVIcon.kt:30` 已存在），文案为 `电影 · {label}` / `剧集 · {label}`。

**(3) 类型筛选联动**

进入带范围时默认 `kinds = setOf(MOVIES)`（电影范围）或 `setOf(SERIES)`。清除范围后保持当前 `kinds` 不变，用户可继续多选。

**(4) 结果为空文案**

保持现有 `R.string.search_nothing_found`，但范围条仍在，用户能立刻理解是范围限制。可选地在正文里补一句「在 {范围} 内未找到」，走新增字符串。

### 5.6 结果范围过滤

**首选：core 原生过滤**

理想形态是 `SearchReader` 增加可选参数（例如 `categoryIds: Set<Long>?`），让 FTS SQL 在同一查询里带上 `category_id IN (...)`。理由：core 的 FTS 结果通常带 `LIMIT`，先取全库再过滤会丢结果。

`SearchReader` 在外部 core AAR 中（本仓库无源码，`di/AppModule.kt:65` 注册），需要与 core 侧对齐版本后再用。

**过渡：app 层收敛**

在 core 支持前，可在 `SearchViewModel` 内过滤：

```kotlin
val results: StateFlow<SearchResults> = combine(
    _query.map { it.trim() }.debounce(300).distinctUntilChanged(),
    _scope,
) { q, s -> q to s }
    .flatMapLatest { (q, s) ->
        if (q.length < 2) flowOf(SearchResults())
        else flowOf(search(q).restrictTo(s))
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())
```

```kotlin
internal fun SearchResults.restrictTo(scope: SearchScope): SearchResults = when (scope) {
    SearchScope.All -> this
    is SearchScope.Vod -> copy(
        channels = emptyList(),
        programmes = emptyList(),
        movies = if (scope.type == VodScopeType.MOVIE) {
            movies.filter { scope.categoryId == null || it.categoryId == scope.categoryId }
        } else emptyList(),
        series = if (scope.type == VodScopeType.SERIES) {
            series.filter { scope.categoryId == null || it.categoryId == scope.categoryId }
        } else emptyList(),
    )
}
```

注意两点：

- `SearchResults` 是 core 类型，是否支持 `copy(...)` / 直接构造需核对 core 源码；若不支持，则改为在 `SearchScreen` 的 `shown` 上过滤（`SearchScreen.kt:193`），或请求 core 暴露构造器。
- `curatedResults`（空态「继续观看 / 未看 / 频道」）在带范围进入时应视为不适用：带范围时直接跳过 launcher，进入输入态。

**范围映射降级**

| 点播页当前分类 | `categoryId` | 实际效果 |
|---|---|---|
| `LiveKey.All` | `null` | 该类型全部（等价于全局搜索里只开 MOVIES/SERIES） |
| `LiveKey.Folder(id)` | `id` | 该供应商分类 |
| `LiveKey.Favorites` | `null` | 伪范围：仅显示类型，范围条标签仍显示「收藏」（可接受，P2 再做精确过滤） |
| `LiveKey.History` | `null` | 同上 |
| `LiveKey.Custom(id)` | `null` | 同上，P2 可用 `customCategoryDao` 做成员过滤 |

标签与真实过滤不一致是过渡期风险，建议在 P1 只在 `Folder` / `All` 两种情况下显示可清除的范围条，其余分类直接以 `All` 进入（见 §12 问题 2）。

### 5.7 返回与焦点

外壳需要区分两个「来源」概念，二者语义不同，不能合并：

| 状态 | 含义 | 现有实现 |
|---|---|---|
| `searchReturn` | 从搜索结果「去往」了某个 section，Back 先回搜索页 | `OwnTVShell.kt:919-933` |
| `searchOrigin`（新增） | 从某个 section 进入搜索页，搜索页无可清内容时 Back 回该 section | 无 |

实现：

```kotlin
var searchOrigin by remember { mutableStateOf<MainSection?>(null) }

val openSearchFromVod: (MainSection, SearchScope.Vod) -> Unit = { section, scope ->
    searchVm.setScope(scope)
    searchOrigin = section
    onSelectSection(MainSection.SEARCH)
}
```

`SearchScreen` 增加：

```kotlin
onReturnToOrigin: (() -> Unit)? = null,
```

在现有 `BackHandler`（`SearchScreen.kt:229`）里追加一个分支：

```kotlin
BackHandler(enabled = returnToHomeOnBack || query.isNotBlank() || intent != null || (onReturnToOrigin != null && scope !is SearchScope.All)) {
    when {
        query.isNotBlank() || intent != null || scope !is SearchScope.All -> { /* 清查询 / 清 intent / vm.clearScope() */ }
        else -> onReturnToOrigin?.invoke()
    }
}
```

并在 `OwnTVShell` 侧：

```kotlin
onReturnToOrigin = searchOrigin?.let { origin ->
    {
        searchOrigin = null
        onSelectSection(origin)
        restoreFocus = true   // 复用既有机制，见 :1345 / :1366
    }
}
```

焦点恢复：

- 点播页的 `LaunchedEffect(Unit)` 逻辑会在回到页面时按 `restoreFocus` 决定落点（`MoviesScreen.kt:392`、`:404`）。当前实现里「列表为空」才落搜索框（`MoviesScreen.kt:409`）。
- 建议给搜索框加一个专用恢复标记（例如 `listSearchFocus` 在 `restoreFocus && 来源为搜索` 时优先请求），或在 `onRestored` 回调里显式 `listSearchFocus.requestFocus()`。
- 关键是别让 `paneModifier.onEnter` 把焦点抢到标题上（`MoviesScreen.kt:524-529`），把搜索框放在同一 focus group 的第一项即可（现状已经是）。

### 5.8 键盘、语音、最近搜索

- 键盘：交接后由全局搜索页 `autoEdit` 打开；点播页不再弹键盘。
- 语音：自然获得（`SearchFieldRow.kt:57`），点播页搜索框本身没有麦克风。
- 最近搜索：用户从结果打开条目时才会写入（`SearchViewModel.kt:96`），与现状一致。
- 空态：带范围进入时不应该展示 launcher（`SearchScreen.kt:306`），直接显示空输入 + 范围条。

---

## 6. 代码骨架

### 6.1 外壳

```kotlin
// OwnTVShell.kt
var searchOrigin by remember { mutableStateOf<MainSection?>(null) }

fun openSearchFromVod(section: MainSection, scope: SearchScope.Vod) {
    if (playerMode == PlayerMode.FULLSCREEN) dockPlayer()
    searchVm.setQuery("")
    searchVm.setScope(scope)
    searchVm.clearKinds()
    if (scope.type == VodScopeType.MOVIE) searchVm.showOnly(SearchKind.MOVIES)
    else searchVm.showOnly(SearchKind.SERIES)
    searchOrigin = section
    onSelectSection(MainSection.SEARCH)
}

// Movies / Series 调用处
MoviesScreen(
    ...,
    onOpenSearch = { q, scope -> openSearchFromVod(MainSection.MOVIES, scope) },
)
```

### 6.2 点播页

```kotlin
// MoviesScreen.kt（Cinematic 与 List 两处）
StageSearchField(
    query = "",
    onQueryChange = {},
    placeholder = stringResource(R.string.content_search_in, headerLabel),
    modifier = Modifier.width(440.mpx).focusRequester(listSearchFocus).then(searchLeft),
    onActivate = { onOpenSearch("", vm.currentSearchScope()) },
)
```

### 6.3 全局搜索

```kotlin
// SearchScreen.kt
val scope by vm.scope.collectAsStateWithLifecycle()
val autoEdit = remember { scope !is SearchScope.All }

SearchFieldRow(
    query = query,
    onQueryChange = vm::setQuery,
    fieldFocus = fieldFocus,
    focusDown = filterEntry,
    autoEdit = autoEdit,
    modifier = ...,
)
```

---

## 7. 改动清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `features/search/SearchScope.kt` | 新增：`SearchScope`、`VodScopeType` |
| 2 | `features/search/SearchViewModel.kt` | 新增 `scope` / `setScope` / `clearScope`；`results` 流合并 scope；`restrictTo` 收敛 |
| 3 | `features/search/SearchScreen.kt` | `autoEdit`、`ScopeBar`、`onReturnToOrigin`、Back 分支 |
| 4 | `features/search/SearchFieldRow.kt` | `autoEdit` 参数透传 |
| 5 | `features/movies/MoviesScreen.kt` | 两处搜索框加 `onActivate`；移除 `searchEditing`（A 方案）；新增 `onOpenSearch` 参数 |
| 6 | `features/series/SeriesScreen.kt` | 同上 |
| 7 | `features/movies/MovieViewModel.kt` | `currentSearchScope()`；A 方案下移除 `_search` 驱动链 |
| 8 | `features/series/SeriesViewModel.kt` | 同上 |
| 9 | `features/shell/OwnTVShell.kt` | `searchOrigin`、`openSearchFromVod`、传参、两处重置点补 `clearScope()` |
| 10 | `app/src/main/res/values/strings.xml` | 新增范围条与空态文案 |
| 11 | `app/src/main/res/values-zh-rCN/strings.xml`、`values-zh-rTW/strings.xml` | 同步中文 |
| 12 | `features/search/*Test.kt` | 新增 scope 过滤单测 |

---

## 8. 分期实施

| 阶段 | 内容 | 产出 | 风险 |
|---|---|---|---|
| P0 | 外壳回调 + 点播页 `onActivate` + `autoEdit`，范围恒为 `All`（只做「交接」） | 点播页搜索框直接进全局搜索 | 低，可单独回滚 |
| P1 | `SearchScope` + app 层 `restrictTo` + ScopeBar + 返回来源 | 带分类范围的搜索 | 中，结果条数受 core `LIMIT` 影响 |
| P2 | core `SearchReader` 支持分类参数；Favorites / History / Custom 精确范围 | 过滤正确、条数不受限 | 中，需 core 版本配合 |
| P3 | 移除点播页内联过滤及其 ViewModel 链路 | 单一搜索语义 | 低，属清理 |

P0 与 P1 可以合并发布；P3 建议在 P1 上线观察一版后再做，避免「两套都不可用」的回退窗口。

---

## 9. i18n

新增文案必须进 `strings.xml`，不能硬编码（CI 有硬编码字面量守卫，见 `tools/i18n/check_hardcoded_strings.py`）：

| key | 英文 | 中文（zh-rCN） |
|---|---|---|
| `search_scope_movies` | `Movies · %1$s` | `电影 · %1$s` |
| `search_scope_series` | `Series · %1$s` | `剧集 · %1$s` |
| `search_scope_clear` | `Clear search range` | `清除搜索范围` |
| `search_nothing_in_scope` | `Nothing found in %1$s` | `在 %1$s 中未找到` |

- 位置参数一律写成 `%1$s`（项目规范，`docs/i18n.md`）。
- 若引入新的技术字面量（例如 `SearchScope` 的序列化 key），先在 `tools/i18n/safe_literals.txt` 中 `classify-safe`，再 `generate` 重建基线；不新增技术字面量则无需动基线。
- 新增字符串后运行：

```bash
python tools/i18n/check_hardcoded_strings.py generate
python tools/i18n/check_hardcoded_strings.py verify --base <merge-base基线>
```

---

## 10. 测试与验收

### 10.1 单元测试

新增 `app/src/test/java/tv/own/owntv/features/search/SearchScopeTest.kt`：

1. `SearchScope.All` 不改变 `SearchResults`。
2. `Vod(MOVIE, categoryId = 7)` 只保留 `categoryId == 7` 的电影，清空 channels / programmes / series。
3. `Vod(SERIES, categoryId = null)` 保留全部剧集，清空其它类型。
4. `selectedKey = LiveKey.Favorites` → `categoryId == null` 且 `type` 正确。
5. `selectedKey = LiveKey.Folder(9)` → `categoryId == 9`。

运行：

```bash
./gradlew test --tests "tv.own.owntv.features.search.SearchScopeTest"
```

### 10.2 手动验收矩阵

| 场景 | 期望 |
|---|---|
| Movies · Cinematic · 分类 X 按 OK | 进全局搜索，键盘弹出，范围条显示「电影 · X」 |
| Movies · List 布局 | 同上 |
| Series · 两种布局 | 范围条显示「剧集 · Y」 |
| 输入 ≥2 字符 | 结果只含该分类的电影/剧集，单类型平铺 |
| 范围条 ✕ | 变全库结果，查询词保留 |
| 有查询词按 Back | 清空查询与范围，仍在本页，焦点回搜索框 |
| 无查询词按 Back | 回点播页原分类，焦点在搜索框 |
| 从结果按 OK 进条目 | 进对应页面；Back 先回搜索页，再回点播页 |
| 从导航栏进搜索 | 无范围条，行为与现状一致 |
| 语音按钮（有识别器设备） | 可用，结果受当前范围约束 |
| 儿童档案 / 分类 PIN | 结果与点播页一致，不出现越权条目 |

### 10.3 编译校验

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
```

---

## 11. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 点播页失去「原地过滤」手感 | 用户习惯变化 | 先做 P0 观察；必要时退回 C 方案（保留过滤 + 新增全局按钮） |
| core `SearchResults` 无法 `copy` | 5.6 过渡实现不可行 | 改在 `SearchScreen` 过滤，或等 P2 的 core 参数 |
| core FTS 有 `LIMIT`，后过滤丢结果 | 范围搜索条数偏少 | 优先 P2；过渡期在范围条旁提示结果数量 |
| 特殊分类（收藏 / 历史 / 自定义）标签与过滤不一致 | 用户困惑 | P1 只对 `Folder` / `All` 显示范围条，其余按全库进入 |
| 三种布局四处搜索框漏改 | 行为不一致 | 用同一 `onActivate` 调用点收敛，CI 加 grep 检查（可选） |
| 焦点被 `onEnter` 抢回标题 | 返回后无法直接输入 | 搜索框保持 focus group 首项；`onRestored` 显式请求焦点 |
| 内联过滤链路删除影响 `revealMovie` | 「去往条目」定位失败 | `revealMovie` 传的是空查询（`MovieViewModel.kt:582`），删除前跑一次定位回归 |

回滚：P0/P1 的改动集中在外壳与搜索包，回滚只需恢复点播页 `onActivate` 与 `SearchScreen` 的 scope 分支；`searchOrigin` 可保留（空值时无行为变化）。

---

## 12. 待确认问题

1. **是否接受点播页不再原地过滤？** 若不能接受，走 C 方案（保留过滤 + 全局搜索按钮），其余设计不变。
2. **范围是否要覆盖「收藏 / 历史 / 自定义分类」？** 覆盖需要 `customCategoryDao` / 收藏表的额外查询，建议 P2。
3. **core 是否愿意增加分类过滤参数？** 这决定 P1 的结果条数是否准确，也决定是否需要 core 版本升级。
4. **带范围进入且查询为空时，是否保留「继续观看 / 未看 / 频道」空态？** 建议不保留（范围语义下这些入口不适用）。
5. **是否需要在导航栏搜索入口也显示「上次范围」？** 建议不显示，范围只属于当次交接。