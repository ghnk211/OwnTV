# 点播页搜索框并入全局搜索 · 技术方案

> 范围：Movies / Series 页面工具行上的分类内搜索框，与 `features/search` 的全局搜索页。
> 目标读者：负责本次改造的开发与评审人。
> 本文只描述方案，不包含已落地的代码改动。
>
> 核对基准：`OwnTV@9a05e570`、`OwnTV_Core@1.0.69`（本机 `../OwnTV_Core` 有 core 全量源码）。
> 文中行号均按该基线实测。v2 相对 v1 的修订见 §0.2；章节号自 §6 起前移一位。

---

## 0. 评审结论

**方案成立，建议按 A 实施。** 两块关键机制都已经存在，不需要新造导航：
`StageSearchField.onActivate` 本就是「OK 交给别人、不在此处编辑」的标准口子
（`ui/stage/StageControls.kt:74-79`），Settings 全页搜索就是用它把搜索交给 Settings；
`SearchViewModel` 在 shell 与 screen 中通过 `koinViewModel()` 取到同一实例
（`OwnTVShell.kt:257`、`SearchScreen.kt:164`），外壳可以直接驱动它。

但 v1 有 4 处会直接导致功能不可用，另有 6 处与代码不符，下面先列出来。

### 0.1 会让功能坏掉的 4 处

| # | v1 位置 | 问题 | 后果 |
|---|---|---|---|
| 1 | §5.4 / §6.2 | 说移除 `searchLeft`，「◀ 走现有打开分类逻辑」 | `searchLeft` **本身就是**那段逻辑（`MoviesScreen.kt:551` 的 `onDpadLeft`）。删掉后 ◀ 不再有任何行为 |
| 2 | §5.7 / §6.1 | 用 `restoreFocus = true` 让焦点回到搜索框 | 点播页的 `restoreFocus` 含义是「从播放器回来，聚焦刚看的条目」（`MoviesScreen.kt:298-311`），且 `movies.itemCount == 0` 时直接 `return`、`onRestored` 根本不触发。焦点不会落在搜索框 |
| 3 | §6.3 | `val autoEdit = remember { scope !is SearchScope.All }` | `SearchScreen` 每次切 section 都会离开组合。从搜索结果 Back 回来时会再弹一次键盘，正好盖住结果列表 |
| 4 | §5.7 | BackHandler 的 `enabled` 依赖 `scope !is All` | 第一次 Back 清掉范围后 handler 变 disabled，落到外壳的 `focusRail()`，用户再也回不到点播页。同时 `searchReturn` 未与新状态互斥，会形成「搜索 ↔ 点播页」来回跳 |

### 0.2 事实性修正

| # | v1 说法 | 实测 |
|---|---|---|
| 5 | 新增文案写进 `app/src/main/res/values*/strings.xml` | 本项目文案在 **core 仓库**：`OwnTV_Core/core/src/main/res/values/strings_features.xml`（+ `values-zh-rCN`、`values-zh-rTW`）。`tools/i18n/README.md` 开头就写明「strings 不在本仓库」。改文案要改 core 并 bump `owntvCore`（`gradle/libs.versions.toml:82`，现为 `1.0.69`） |
| 6 | "core 在外部 AAR，本仓库无源码，需与 core 侧对齐版本后再用" | core 源码就在同机 `../OwnTV_Core`；`~/.gradle/gradle.properties` 里的 `owntv.corePath` 可直接用源码替换 AAR（`libs.versions.toml:80-81` 有说明）。P2 不必等外部排期 |
| 7 | "`SearchResults` 是否支持 `copy` 需核对 core" | 是 `data class` 且有默认参数（`SearchReader.kt:29-53`），`copy` 与具名构造都可用，§5.6 的过渡实现没有障碍 |
| 8 | 搜索分支含 `searchCustom` | 没有这个方法。`LiveKey.Custom` 走 `customCategoryDao.searchMovies(...)`（`MovieViewModel.kt:1049`） |
| 9 | 建议把 `headerLabel` 下沉到 ViewModel 的 `StateFlow<String>` | 做不到。`headerLabel` 来自 `LiveRailItem.displayLabel()` + `stringResource`（`LiveRailText.kt:10-18`），是 `@Composable`，VM 没有 Context。应由屏幕把 label 放进 `SearchScope`，见 §5.2 |
| 10 | "改动量集中在 6 个文件 + 2 段字符串资源" | 与 v1 §7 的 12 项自相矛盾。P0+P1 实际是 7 个 Kotlin 文件 + core 1 个文案文件；P3 再加 2 个 |

另有若干行号偏差：四处搜索框在 **585 / 698 / 658 / 768**（v1 各少 1 行），`searchLeft` 在 **551**（v1 写 553）。

### 0.3 需要产品先拍板的两件事

1. **点播页是否接受「不再原地过滤」？** 这是 A 方案唯一的手感变化，不接受就走 C（§4.3）。
2. **「收藏 / 历史 / 自定义分类」算不算范围？** 本期建议不算：只对真实分类（`LiveKey.Folder`）建范围，其余入口按普通全局搜索进入（§5.4）。

---

## 1. 目标

把点播页（电影 / 剧集）的搜索框从「当前分类内的内联过滤」改成「全局搜索的统一入口」，并让全局搜索携带点播页的上下文（当前分类、内容类型），做到：

1. 应用内只有一套搜索输入与结果界面，不再有两套语义（分类内 FTS 过滤 vs 跨区 FTS 搜索）。
2. 点播页搜索框保留原有位置与视觉，OK 后交给全局搜索，并自动弹出键盘。
3. 全局搜索页能显示「范围」并支持一键清除，清除后退回全库搜索。
4. 返回路径明确：搜索 → 点播页原分类，焦点回到原搜索框；从结果跳到条目后仍可先回搜索。

非目标：

- 不改全局搜索引擎（core `SearchReader`）的匹配规则、隐藏项、Kids 过滤、Customize 改名等既有语义。
- 不改 Live TV / Guide 的分类面板内搜索（`LiveCategories.categoryQuery`）。
- **不改 `MovieViewModel.categoryQuery` / `SeriesViewModel.categoryQuery`**（分类面板内的搜索，`MoviesScreen.kt:671`、`:829`）。它和本次要移除的 `searchQuery` 名字相近，但是另一条链路，删错就废掉分类面板的搜索。
- 不在本期实现「收藏 / 历史 / 自定义分类」作为精确范围（见 §11）。

---

## 2. 结论摘要

**推荐方案：入口交接 + 搜索范围（Scope）。**

- 点播页的 `StageSearchField` 用已有的 `onActivate`，把 OK 交给外壳；字段本身不再进入编辑态。
- 外壳调用 `SearchViewModel.openFromVod(scope)`（一次原子写，避免多个 setter 的顺序耦合），再切到 `MainSection.SEARCH`。
- `SearchViewModel` 新增 `scope`，结果流合并 scope 做收敛；`SearchScreen` 新增一次性 `autoEdit`、ScopeBar、`onReturnToOrigin`。
- P3 再把 `Movie/SeriesViewModel` 的 `_search` 链路移除。

改动集中在 7 个 Kotlin 文件 + core 1 个文案文件，不动 core 的检索逻辑。

---

## 3. 现状分析

### 3.1 全局搜索

| 关注点 | 现状 | 位置 |
|---|---|---|
| 入口 | Stage 导航栏首项（永不可隐藏），也支持遥控器快捷键 `OPEN_SEARCH` | `features/shell/components/StageRail.kt:101-104`、`OwnTVShell.kt:818` |
| 页面 | `SearchScreen`：标题带、搜索框 + 语音、筛选按钮（LIVE / TV_SHOWS / MOVIES / SERIES）、左侧结果列、右侧预览 / 海报面板 | `features/search/SearchScreen.kt:154` |
| 输入 | `SearchFieldRow` → `StageSearchField`，查询词 ≥2 字符才检索 | `SearchFieldRow.kt:35`、`SearchScreen.kt:191` |
| 检索 | 300 ms 防抖后调 `SearchReader.search(profileId, ActiveProfileSources, query)`，用 `SearchResults.only(kinds)` 做类型过滤 | `SearchViewModel.kt:68-79`、`:115` |
| 结果跳转 | OK 只「去往」条目所在的分类 / 页面，不直接播放；`searchReturn` 让返回时先回搜索页 | `SearchScreen.kt:263`、`OwnTVShell.kt:919-932` |
| 附带能力 | 语音搜索、最近搜索、空态「跳转 / 继续观看 / 频道」、频道复用直播预览窗 | `SearchFieldRow.kt:57`、`SearchViewModel.kt:90-96` |

关键点：`SearchScreen` 每次切 section 都会离开组合（外壳是 `selectedSection == X -> ...` 的平铺分支），所以它内部所有 `remember` 在离开时丢失；需要跨进出保留的状态（scope、一次性 autoEdit）必须放在 ViewModel 或外壳。

### 3.2 点播页搜索框

电影页与剧集页各有两处（Cinematic 与 List 布局）：

| 页面 | 位置 |
|---|---|
| Movies · Cinematic | `features/movies/MoviesScreen.kt:585` |
| Movies · List / Separate | `features/movies/MoviesScreen.kt:698` |
| Series · Cinematic | `features/series/SeriesScreen.kt:658` |
| Series · List / Separate | `features/series/SeriesScreen.kt:768` |

形态一致（以 Movies 为例，`MoviesScreen.kt:584-589`）：

```kotlin
StageSearchField(
    query = searchQuery, onQueryChange = vm::setSearchQuery,
    placeholder = stringResource(R.string.content_search_in, headerLabel),
    modifier = Modifier.width(440.mpx).focusRequester(listSearchFocus).then(searchLeft),
    onEditingChange = { searchEditing = it },
)
```

它的语义是**分类内过滤**：

- `searchQuery` 来自 `MovieViewModel._search`（`MovieViewModel.kt:293-294`；Series 在 `SeriesViewModel.kt:298-299`），进入 `movies` 的 combine、300 ms 防抖后重建 Paging 参数（`MovieViewModel.kt:441-444`）。
- 查询词落到 `pagingSource` 的搜索分支：`searchAll` / `searchFavorites` / `searchHistory` / `customCategoryDao.searchMovies` / `searchInCategory`（`MovieViewModel.kt:1043-1053`）。
- 因此只能看到**当前 rail 分类内**的结果，不能跨分类，也没有语音、最近搜索、预览面板。
- 空态文案也依赖它：`emptyText` 在 `MoviesScreen.kt:555` / `SeriesScreen.kt:628` 用 `searchQuery.isNotBlank()` 选 `content_no_movies_found`，P3 删链路时要一并简化。
- `◀` 在未编辑时打开分类面板，由 `searchLeft`（`MoviesScreen.kt:551`）实现，predicate 是 `!searchEditing && lockedKey == null`。

### 3.3 已有的「交接」机制

`StageSearchField` 已经支持把搜索交给另一个页面：

```kotlin
/** Open the keyboard as soon as the field appears (Settings search reached from a full page). */
autoEdit: Boolean = false,
/** Set = OK calls this instead of editing here (a full page's search hands over to Settings search). */
onActivate: (() -> Unit)? = null,
```

——`ui/stage/StageControls.kt:74-79`；OK 的分派在 `:117`。

Settings 全页正是这样做的：`LocalSettingsSearch` 提供回调（`SettingsStage.kt:848`），`StageFullPage` 把它接到 `onSearchActivate`（`:887`），字段在 `:292` 透传。注意 `:291` 同时传了 `autoEdit = searchAutoEdit` —— 「有 onActivate 的字段」和「autoEdit」是可以并存的，这正是本方案要的组合。

同理，Home 的 Now Trending 已有「带查询进入搜索页」的先例（`OwnTVShell.kt:1273-1278`）：

```kotlin
onOpenTrendingSearch = { query ->
    searchVm.setQuery(query)
    trendingSearchActive = true
    ...
    onSelectSection(MainSection.SEARCH)
}
```

**本方案就是把这两个既有模式组合起来**，不引入新的导航机制。

### 3.4 能力差异

| 能力 | 点播页搜索框 | 全局搜索 |
|---|---|---|
| 范围 | 当前分类（Movies / Series 之一） | 全库四类 |
| 跨分类 | 否 | 是 |
| 语音 | 无 | 有 |
| 最近搜索 | 无 | 有 |
| 结果预览 | 无 | 直播预览窗 / 海报详情面板 |
| 结果跳转 | 就在当前列表内 | 跳到条目所在分类 / 页面 |
| 筛选 | 无 | 四个类型开关 |
| 返回语义 | 清空查询 | 先清查询，再离开 |

结论：全局搜索是点播搜索的超集，唯一差异是「范围」与「就地过滤」的手感。

---

## 4. 方案选型

### 4.1 候选

**A. 入口交接 + 范围（推荐）**：点播页搜索框按 OK → 直接进全局搜索页，键盘自动弹出，结果锁定在「当前类型 + 当前分类」。

**B. 点播页内嵌全局结果**：在工具行下展开覆盖层，复用 `SearchReader` 展示跨区结果，但不切 section。

**C. 保留内联过滤 + 新增「全局搜索」按钮**：搜索框仍过滤当前列表，右侧加一个放大镜 / 箭头按钮，点击后带 `query + 当前分类` 进全局搜索。

### 4.2 对比

| 维度 | A | B | C |
|---|---|---|---|
| 语义统一 | 彻底（只剩一套搜索） | 中等（两套 UI，共用引擎） | 差（两套结果同时存在） |
| 改动量 | 小 | 大（新覆盖层、焦点 / 返回、预览复用） | 小 |
| 破坏现有手感 | 是（不再原地过滤） | 否 | 否 |
| 复用语音 / 最近 / 预览 | 全量复用 | 部分复用 | 全量复用 |
| 返回 / 焦点复杂度 | 低（section 级） | 高（同页覆盖层） | 低 |
| 风险 | 行为变更需产品确认 | 中高 | 低 |

### 4.3 推荐

**首选 A**。理由是点播页的分类内搜索本来就有明显短板（不能跨分类、无语音、无预览），而全局搜索已经具备这些能力，且项目里已有两处「搜索交接」先例。

如果产品上必须保留「原地过滤」，退到 **C**：A 的 ViewModel / scope 改动全部复用，只是入口从「搜索框 OK」换成「搜索框右侧按钮」，实现代价几乎相同。

B 不建议：它会让点播页同时承载列表与全局结果两套焦点模型，和 `focusTitles` / `listPin` / 分类面板的既有焦点逻辑冲突（`MoviesScreen.kt:392-412`、`:546-552`）。

---

## 5. 详细设计（方案 A）

### 5.1 交互流程

**进入**

1. 用户在 Movies 页分类「Disney+ Movies」的工具行聚焦搜索框。
2. 按 OK：不弹出本页键盘，直接切到全局搜索页。
3. 搜索框自动获得焦点并打开键盘（一次性 `autoEdit`）。
4. 搜索框下方出现范围条：`电影 · Disney+ Movies`。
5. 已有草稿不作为初始查询带入（A 方案下字段不可编辑，草稿概念不存在；C 方案才需要）。

**搜索**

6. 输入 ≥2 字符 → 只检索该范围；结果单类型平铺（`singleKind`，`SearchScreen.kt:195`）。
7. 语音按钮可用，识别结果同样受范围约束。

**离开**

| 操作 | 行为 |
|---|---|
| Back（有查询词） | 清空查询与类型筛选，**保留范围**，留在搜索页，焦点回搜索框（沿用 `BackHandler`，`SearchScreen.kt:229`） |
| Back（无查询词，来自点播页） | 回 Movies / Series 页原分类，焦点回到该页搜索框，并丢弃范围 |
| Back（无查询词，来自导航栏） | 与现状一致：焦点回导航栏 |
| 范围条 OK | 只清除范围，保留查询词并立即重查（变为全库） |
| OK 打开结果 | 走现有 `searchReturn`，进入条目所在页面；在该页 Back 先回搜索页（范围仍在） |

> 为什么 Back 不一次清掉范围：范围是用户进来的上下文，清掉查询后仍应留在同一范围里继续输入。要扩大范围有显式的范围条，不需要靠 Back。

### 5.2 数据契约

新增 `features/search/SearchScope.kt`：

```kotlin
package tv.own.owntv.features.search

enum class VodScopeType { MOVIE, SERIES }

/**
 * The range a global search was opened from. [All] is the ordinary, unrestricted search.
 *
 * [categoryId] is the provider category DB id (LiveKey.Folder.id); null means "every category of
 * this type" — the case for the All rail entry. [label] is already display-ready (provider prefixes
 * stripped by the browse screen); it is a String, not a resource id, because the label comes from
 * stringResource and a ViewModel has no Context.
 */
sealed interface SearchScope {
    data object All : SearchScope

    data class Vod(
        val type: VodScopeType,
        val categoryId: Long?,
        val label: String,
    ) : SearchScope
}
```

两点与原稿不同：

- **去掉 `railKey`**。点播页的 `selectedKey` 保存在 activity 作用域的 `MovieViewModel` / `SeriesViewModel`（`MovieViewModel.kt:269`、`SeriesViewModel.kt:275`），切 section 不会丢；返回时无需任何恢复参数。`LiveKey.serialize()` 在这里没有消费者。
- **`label` 由屏幕给，不由 VM 算**。见 §0.2 第 9 条。

> 为什么不直接用 `LiveKey` 当范围 key：`LiveKey` 还包含 `Favorites` / `History` / `Catchup`，`Custom(id)` 的 id 是 `String`（`core/live/LiveKey.kt:12-13`），都不是 `categoryId: Long`。本期只给 `Folder` 建范围，其余按 §5.4 的规则处理。

### 5.3 状态归属与生命周期

放在 `SearchViewModel`（activity 作用域，shell 与 screen 共享）：

```kotlin
private val _scope = MutableStateFlow<SearchScope>(SearchScope.All)
val scope: StateFlow<SearchScope> = _scope.asStateFlow()

/** 进入搜索页后自动弹键盘：只消费一次。放在 VM 里，因为 SearchScreen 每次切 section 都会重建。 */
private val _pendingAutoEdit = MutableStateFlow(false)
val pendingAutoEdit: StateFlow<Boolean> = _pendingAutoEdit.asStateFlow()

/** 从点播页进入：一次原子写，避免 setQuery/setScope/clearKinds 的先后顺序出错。 */
fun openFromVod(scope: SearchScope.Vod) {
    setQuery("")
    setIntent(null)
    clearKinds()
    lastFocusKey = null          // 否则会去聚焦上一轮的某一行，而不是输入框
    _scope.value = scope
    _pendingAutoEdit.value = true
}

fun clearScope() { _scope.value = SearchScope.All }
fun consumeAutoEdit() { _pendingAutoEdit.value = false }

/** 任何"非交接"地离开搜索页时调用：导航栏、快捷键、Home Trending。 */
fun resetScope() { _scope.value = SearchScope.All; _pendingAutoEdit.value = false }
```

生命周期（与现有 `setQuery("")` 的处置位置对齐）：

| 入口 / 出口 | 位置 | 要做的事 |
|---|---|---|
| 快捷键 / `openShortcutSection` | `OwnTVShell.kt:796-800` | 追加 `searchVm.resetScope()` |
| 导航栏切换 | `OwnTVShell.kt:1089-1090` | 追加 `searchVm.resetScope()` |
| Home Now Trending | `OwnTVShell.kt:1273-1278` | 先 `resetScope()` 再 `setQuery(query)` |
| 点播页进入搜索 | 新增 `openSearchFromVod` | `openFromVod(scope)` |
| 从搜索回点播页 | 新增 `leaveSearchToOrigin` | `resetScope()` + 清 `searchOrigin` / `searchReturn` |
| 从搜索结果去条目再返回 | `searchReturn` 机制 | **不重置范围**，保持上下文 |

不做持久化：范围是会话态，与 `query`、`kinds` 一致。

### 5.4 点播页改造

**新增参数**

```kotlin
fun MoviesScreen(
    ...,
    onOpenSearch: (SearchScope.Vod) -> Unit,
    /** 从搜索页返回时一次性把焦点放到工具行的搜索框；与 restoreFocus 互斥，见 §5.7。 */
    focusSearchField: Boolean = false,
    onSearchFieldFocused: () -> Unit = {},
)
```

由 `OwnTVShell.kt:1362` / `:1371` 传入。Series 同理。

**四处搜索框改为入口**

```kotlin
StageSearchField(
    query = "",
    onQueryChange = {},
    placeholder = stringResource(R.string.content_search_in, headerLabel),
    modifier = Modifier.width(440.mpx).focusRequester(listSearchFocus).then(searchLeft),
    onActivate = {
        onOpenSearch(
            SearchScope.Vod(
                type = VodScopeType.MOVIE,                       // Series 页为 SERIES
                categoryId = (selectedKey as? LiveKey.Folder)?.id,
                label = headerLabel,
            )
        )
    },
)
```

- `headerLabel` 已经是清洗过的显示名（`MoviesScreen.kt:432`：`ProviderTags.parse(selectedLabel).name`），直接放进 scope。
- `selectedKey` 在屏上是 `by vm.selectedKey.collectAsStateWithLifecycle()`，可直接取。
- **不要删 `searchLeft`**，只简化 predicate —— 字段不再进入编辑态，`searchEditing` 可以去掉：

```kotlin
// 字段不编辑了，◀ 始终交给分类面板
val searchLeft = Modifier.onDpadLeft({ lockedKey == null }) { openCategories() }
```

- `searchEditing`（`MoviesScreen.kt:549`、`SeriesScreen.kt:622`）随之删除；`listSearchFocus` 保留（返回时要聚焦它）。
- P0 起 `vm.searchQuery` 只剩 `emptyText` 一个消费者（`MoviesScreen.kt:555` / `SeriesScreen.kt:628`），先留着不删，P3 与 `_search` 链路一起清。
- P3 才动 `emptyText`：`MoviesScreen.kt:555` / `SeriesScreen.kt:628` 的 `searchQuery.isNotBlank()` 分支与 `content_no_*_found` 一起失效。

**范围的产生规则（P1）**

| 当前 rail 分类 | `categoryId` | `label` | 效果 |
|---|---|---|---|
| `LiveKey.Folder(id)` | `id` | `headerLabel` | 该供应商分类 |
| `LiveKey.All` | `null` | `headerLabel` | 该类型全部（范围条只显示「电影」/「剧集」） |
| `LiveKey.Favorites` / `History` / `Custom` | — | — | **不建范围**，调 `onOpenSearch` 时走普通进入（`scope = All`），行为与导航栏进搜索一致 |

原稿给收藏 / 历史 / 自定义造了 `categoryId = null` 的「伪范围」，标签写着「收藏」但结果并不限于收藏 —— 这是自造的误导。P1 直接不给它们建范围，等 P2 有精确过滤能力时再加。

### 5.5 全局搜索页改造

**(1) 一次性自动编辑**

`SearchFieldRow` 增加 `autoEdit` 与 `onEditingChange` 并透传给 `StageSearchField`（`SearchFieldRow.kt:43-51`）：

```kotlin
internal fun SearchFieldRow(
    query: String,
    onQueryChange: (String) -> Unit,
    fieldFocus: FocusRequester,
    focusDown: FocusRequester,
    modifier: Modifier = Modifier,
    autoEdit: Boolean = false,
    onEditingChange: (Boolean) -> Unit = {},
)
```

`SearchScreen`：

```kotlin
val scope by vm.scope.collectAsStateWithLifecycle()
val autoEdit by vm.pendingAutoEdit.collectAsStateWithLifecycle()

SearchFieldRow(
    ...,
    autoEdit = autoEdit,
    // 进入编辑态即消费掉标记。不要在 autoEdit 变 true 的同一帧清掉它：
    // StageSearchField 内部是 delay(250) 后才 editing = true，提前翻转会把这个 effect 取消。
    onEditingChange = { if (it) vm.consumeAutoEdit() },
)
```

`StageSearchField` 的 `autoEdit` 是延迟 250 ms 后进入编辑并请求焦点（`StageControls.kt:85`），可避开页面自身的焦点恢复。

> 不要用 `remember { scope !is SearchScope.All }`：从结果 Back 回搜索页时 `SearchScreen` 重新组合，`remember` 会重新求值为 `true`，键盘再次弹出。Settings 的 `searchAutoEdit`（`SettingsScreen.kt:258-259`）能这么写，是因为它在同一 section 内切换、组合不销毁；这里不行。

**(2) 范围条**

`scopeFocus` 要和 `fieldFocus` / `filterFocus` 一起声明在函数开头（`SearchScreen.kt:184-189` 一带），因为 `filterEntry`（`:198`）在代码顺序上先用到它：

```kotlin
// SearchScreen.kt:184-189 一带
val fieldFocus = remember { FocusRequester() }
val filterFocus = remember { SearchKind.entries.associateWith { FocusRequester() } }
val scopeFocus = remember { FocusRequester() }          // 新增
```

替换筛选按钮行的位置（`SearchScreen.kt:324-339`），按状态二选一：

```kotlin
val vod = scope as? SearchScope.Vod
if (vod != null) {
    val sep = dotSeparator()
    val type = stringResource(
        when (vod.type) {
            VodScopeType.MOVIE -> R.string.common_nav_movies
            VodScopeType.SERIES -> R.string.common_nav_series
        }
    )
    StageTool(
        text = if (vod.categoryId == null) type else listOf(type, vod.label).joinToString(sep),
        icon = OwnTVIcon.CLOSE,
        boxed = true,
        onClick = { vm.clearScope() },
        modifier = Modifier
            .padding(start = fx(84) - 12.mpx, top = 192.mpx)
            .focusRequester(scopeFocus),
    )
} else if (searching) {
    // 现有四个 SearchKind 筛选按钮
}
```

> `scope` 是 `by collectAsStateWithLifecycle()` 的委托属性，Kotlin 不对它做智能转换，所以用 `as?` 取一次 `vod`，不要写成 `if (scope is Vod) { scope.label }`。

- **不新增「电影 · %1$s」这类带分隔符的文案**：分隔符由 `dotSeparator()` 提供（`settings/SettingsStage.kt:1091`，随语言变化），类型名复用 `common_nav_movies` / `common_nav_series`。i18n 只需 1 个新 key（✕ 的语义），见 §8。
- 一个可聚焦元素即可，不要做成「标签 + 独立 ✕ 按钮」两个焦点：遥控器上多一个焦点目标只增加按键成本。

**(3) 焦点改道（容易漏）**

范围态下四个 `SearchKind` 按钮不再组合，`filterFocus` 里的 requester 全部失效。`filterEntry`（`SearchScreen.kt:198`）必须改指 `scopeFocus`，否则：

- 搜索框按 ▼ 落到一个不存在的节点 → 无反应；
- 结果列表按 ▲ 的 `onExit`（`SearchScreen.kt:351-358`）请求一个已消失的 requester → 焦点卡在列表顶部。

```kotlin
val filterEntry = if (scope is SearchScope.Vod) scopeFocus
                  else filterFocus.getValue(kinds.minOrNull() ?: SearchKind.entries.first())
```

底部按键提示同样要加分支：范围态显示 `OK·清除范围`，而不是 `OK·选择`。

**(4) 类型筛选与范围解耦**

范围态下不要动 `kinds`：

- 进入时不调 `showOnly(...)` / `clearKinds()`；
- `shown = results.only(kinds)` 保持原样（`kinds` 为空即不过滤，scope 已在数据流里收敛好）；
- `singleKind` 改为：

```kotlin
val singleKind = searching && (kinds.size == 1 || scope is SearchScope.Vod)
```

原稿在进入时 `showOnly(MOVIES)`，清除范围后又保留它 —— 而范围态下四个 tab 是隐藏的，用户从未主动选过 MOVIES，于是清除范围后会莫名只剩电影。这是隐藏过滤器，必须避免。

**(5) 空态**

- `scope is All` 且无查询：现有 `Launcher` + `LauncherCard`（`SearchScreen.kt:306-315`），不变。
- `scope is Vod` 且无查询：**不画 launcher**（「继续观看 / 未看 / 频道」在范围语义下不适用），只画范围条。字段已聚焦并弹键盘，大片留白在 TV 版式里可接受；若产品觉得空，P2 再加一句提示文案，本期不加。
- 有范围且结果为空：沿用 `search_nothing_found`（`SearchScreen.kt:316-322`）。范围条就在上方，用户能看出是范围限制。

### 5.6 结果范围收敛

**过渡（P1，app 层）**

```kotlin
val results: StateFlow<SearchResults> = combine(
    _query.map { it.trim() }.debounce(300).distinctUntilChanged(),
    _scope,
) { q, s -> q to s }
    .flatMapLatest { (q, s) ->
        if (q.length < 2) flowOf(SearchResults()) else flowOf(search(q).restrictTo(s))
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())
```

```kotlin
/** Scope 收敛：具名构造比 copy 短，也不依赖 core 的可变性。 */
internal fun SearchResults.restrictTo(scope: SearchScope): SearchResults = when (scope) {
    SearchScope.All -> this
    is SearchScope.Vod -> SearchResults(
        movies = if (scope.type == VodScopeType.MOVIE) {
            movies.filter { scope.categoryId == null || it.categoryId == scope.categoryId }
        } else emptyList(),
        series = if (scope.type == VodScopeType.SERIES) {
            series.filter { scope.categoryId == null || it.categoryId == scope.categoryId }
        } else emptyList(),
    )
}
```

`scope` 变化不经过 300 ms 防抖，清除范围后立即重查（`combine` 由任一源触发）。

**必须知道的截断事实**

`SearchReader` 的 `limit` 是**先截断、后过滤**：

```kotlin
// SearchReader.kt:268
const val MATCH_LIMIT = 500
// MovieDao.kt:267-272
"SELECT * FROM movies WHERE sourceId IN (:sourceIds)
 AND id IN (SELECT rowid FROM movies_fts WHERE movies_fts MATCH :ftsQuery) ORDER BY name ASC LIMIT :limit"
```

排序是 `name ASC`，不是相关性。所以两字母查询命中几百条时，目标分类的条目完全可能落在第 500 条之外 —— 表现为「这个分类里明明有，搜索却是空的」。P1 要在范围条旁给出结果计数（已有 `pluralStringResource(search_result_count, ...)`），让用户能看出是范围问题；彻底解决靠 P2。

**P2（core 原生过滤）**

给 `search(...)` 增加 `categoryIds: Set<Long>? = null`（带默认值，两个 app 都不破坏），movie / series 各加两条 DAO 查询（FTS 与 LIKE 各一条，带 `categoryId IN (:categoryIds)`），过滤下推到 SQL，`limit` 不再误伤。

core 的改动路径已经打通：本机 `../OwnTV_Core` 有源码，`~/.gradle/gradle.properties` 配 `owntv.corePath` 即可用源码替换 AAR 调试；发版后 bump `libs.versions.toml:82` 的 `owntvCore`（现 `1.0.69`）。不是"等外部排期"。

### 5.7 返回与焦点

外壳需要区分**三个**互不相同的来源概念：

| 状态 | 含义 | 现有实现 |
|---|---|---|
| `searchReturn` | 从搜索结果「去往」了某个 section，Back 先回搜索页 | `OwnTVShell.kt:175-176`、`:919-948` |
| `searchOrigin`（新增） | 从某个 section 进入搜索页；搜索页无内容可清时 Back 回该 section | 无 |
| `returnToHomeOnBack` | 从 Home 的 Now Trending 进入，Back 回 Home | `SearchScreen.kt:165`、`:229` |

**写入与清除**

```kotlin
// OwnTVShell.kt
var searchOrigin by remember { mutableStateOf<MainSection?>(null) }
/** 一次性：回到点播页后把焦点放到工具行的搜索框。与 restoreFocus 二选一。 */
var focusVodSearchField by remember { mutableStateOf(false) }
var restoreFocus by remember { mutableStateOf(false) }   // 已有

val openSearchFromVod: (SearchScope.Vod) -> Unit = { scope ->
    if (playerMode == PlayerMode.FULLSCREEN) dockPlayer()
    searchVm.openFromVod(scope)
    searchReturn = null          // 新的进入不是"从结果回来"
    searchOrigin = selectedSection.takeIf { it == MainSection.MOVIES || it == MainSection.SERIES }
    restoreFocus = false         // 两个一次性标记互斥
    onSelectSection(MainSection.SEARCH)
}

val leaveSearchToOrigin: () -> Unit = {
    val origin = searchOrigin
    searchOrigin = null
    searchReturn = null          // 否则外壳的 Back 会立刻把用户送回搜索，形成来回跳
    searchVm.resetScope()
    if (origin != null) {
        restoreFocus = false
        focusVodSearchField = true
        onSelectSection(origin)
    }
}

// 传参（MoviesScreen / SeriesScreen 两处）
focusSearchField = focusVodSearchField,
onSearchFieldFocused = { focusVodSearchField = false },

// 非交接地离开搜索页（导航栏 / 快捷键），丢弃来源
LaunchedEffect(selectedSection) {
    if (selectedSection != MainSection.SEARCH) searchOrigin = null
}
```

`searchReturn = null` 这一行不能省：`LaunchedEffect(selectedSection)`（`OwnTVShell.kt:926-928`）只在 `selectedSection != searchReturn` 时清，而这里两者恰好相等（都等于 origin），不手动清就会留下「回到 Movies → 按 Back → 又回搜索」的死循环。

`focusVodSearchField` 必须由屏幕回调清掉（上面的 `onSearchFieldFocused`），不能留在 true：否则下次从导航栏进 Movies 也会抢焦点到搜索框。

**SearchScreen 的 Back 分支**

```kotlin
BackHandler(
    enabled = returnToHomeOnBack || onReturnToOrigin != null ||
        query.isNotBlank() || intent != null,
) {
    when {
        query.isNotBlank() || intent != null -> {
            vm.setQuery(""); vm.setIntent(null); vm.clearKinds()
            runCatching { fieldFocus.requestFocus() }
        }
        returnToHomeOnBack -> onReturnToHome()
        else -> onReturnToOrigin?.invoke()
    }
}
```

`enabled` 里**不要**放 `scope !is All`：范围不需要 Back 来清，清了反而让 handler 失效、掉进 `focusRail()`。

**焦点回到搜索框（新增一次性标记，不复用 `restoreFocus`）**

```kotlin
// MoviesScreen.kt / SeriesScreen.kt
LaunchedEffect(focusSearchField) {
    if (!focusSearchField) return@LaunchedEffect
    repeat(20) {
        if (runCatching { listSearchFocus.requestFocus() }.getOrDefault(false)) break
        withFrameNanos { }
    }
    // 无论是否成功都要消费标记：成功时提前 return 会让它永远留在 true，
    // 下次从导航栏进 Movies 也会抢焦点。
    onSearchFieldFocused()
}
```

- 不复用 `restoreFocus`：它聚焦的是「刚播放的条目」，且列表为空时 `LaunchedEffect` 直接 return、`onRestored` 不触发（`MoviesScreen.kt:298-311`），`restoreFocus` 会永远留在 true。
- 两个标记由外壳保证互斥：从播放器回来传 `restoreFocus`，从搜索回来传 `focusSearchField`，不要同时为 true。
- `paneModifier.onEnter`（`MoviesScreen.kt:524-529`）只在 D-pad **进入**焦点组时抢焦点，程序化 `requestFocus()` 不受影响；搜索框本来就是 `paneModifier` 焦点组的第一项，无需改结构。

### 5.8 键盘、语音、最近搜索

- 键盘：交接后由全局搜索页的 `autoEdit` 打开；点播页不再弹键盘。
- 语音：自然获得（`SearchFieldRow.kt:57`），识别结果走 `onQueryChange`，因此同样受范围约束。
- 最近搜索：用户从结果打开条目时才写入（`SearchViewModel.kt:96`），与现状一致。
- 空态：带范围进入时不展示 launcher（§5.5(5)）。

---

## 6. 改动清单

P0 + P1：

| # | 文件 | 变更 |
|---|---|---|
| 1 | `features/search/SearchScope.kt` | 新增：`SearchScope`、`VodScopeType` |
| 2 | `features/search/SearchViewModel.kt` | `scope` / `pendingAutoEdit`、`openFromVod` / `clearScope` / `consumeAutoEdit` / `resetScope`；`results` 合并 scope；`restrictTo` |
| 3 | `features/search/SearchScreen.kt` | `autoEdit` + 消费、`ScopeBar`、`onReturnToOrigin`、Back 分支、`filterEntry` 改道、`singleKind`、空态分支 |
| 4 | `features/search/SearchFieldRow.kt` | `autoEdit`、`onEditingChange` 透传 |
| 5 | `features/movies/MoviesScreen.kt` | 两处搜索框改 `onActivate`；`searchLeft` 简化；新增 `onOpenSearch` / `focusSearchField` / `onSearchFieldFocused` |
| 6 | `features/series/SeriesScreen.kt` | 同上 |
| 7 | `features/shell/OwnTVShell.kt` | `searchOrigin`、`focusVodSearchField`、`openSearchFromVod`、`leaveSearchToOrigin`、传参、三处 `resetScope()` |
| 8 | `OwnTV_Core/.../res/values/strings_features.xml`（+ `values-zh-rCN`、`values-zh-rTW`） | 新增 1 个 key（§8），bump `owntvCore` |

P3（清理，单独一版）：

| # | 文件 | 变更 |
|---|---|---|
| 9 | `features/movies/MovieViewModel.kt` | 删 `_search` / `searchQuery` / `setSearchQuery`（`:293-294`、`:563`、`:573`）；`movies` combine 去掉 `_search`（`:441-444`）；`pagingSource` 去掉 `query` 参数与搜索分支（`:1015`、`:1043-1053`），同步改 `revealMovie` 的调用（`:582`） |
| 10 | `features/series/SeriesViewModel.kt` | 同上（`:298-299`、`:543`、`:733`、`:1382-1389`） |
| 11 | `MoviesScreen.kt` / `SeriesScreen.kt` | `emptyText` 去掉 `searchQuery` 分支（`:555` / `:628`） |

注意 #9：`pagingSource` 的 `query` 参数被 `revealMovie` 以 `""` 调用（`:582`），删参数时要一起改；`MovieDao.search*` 这些 `PagingSource` 方法届时可能整体失去调用方，但**不要顺手删 DAO** —— 先确认没有别的入口。

---

## 7. 分期实施

| 阶段 | 内容 | 产出 | 风险 |
|---|---|---|---|
| P0 | `onActivate` 交接 + `searchOrigin` 返回 + `focusSearchField`，范围恒为 `All` | 点播页搜索框进全局搜索，Back 回原页、焦点回搜索框 | 低，可单独回滚 |
| P1 | `SearchScope` + `restrictTo` + ScopeBar + 一次性 `autoEdit` + 空态 | 带分类范围的搜索 | 中，受 core `LIMIT` 截断影响 |
| P2 | core `SearchReader` 增加 `categoryIds`；Favorites / History / Custom 精确范围 | 过滤正确、条数不受限 | 中，需发 core 版本 |
| P3 | 移除点播页内联过滤及其 ViewModel 链路 | 单一搜索语义 | 低，属清理 |

P0 与 P1 可以合并发布；P3 建议在 P1 上线观察一版后再做，避免「两套都不可用」的回退窗口。

---

## 8. i18n

**只有一个新 key**，加在 core（不是 app）：

| key | 英文 | 中文（zh-rCN / zh-rTW） |
|---|---|---|
| `search_scope_clear` | `Clear search range` | `清除搜索范围` |

- 位置：`OwnTV_Core/core/src/main/res/values/strings_features.xml`，以及 `values-zh-rCN`、`values-zh-rTW` 的同名文件。
- 若 key 带占位符，一律写 `%1$s` 并用 `<xliff:g>` 包住；非显然的 key 前加 `<!-- Translators: ... -->`（`docs/i18n.md:91`、`:108`）。本 key 无占位符，只需注意大小写风格与相邻 `search_*` 一致。
- 范围条上的「电影 / 剧集」直接复用 `common_nav_movies` / `common_nav_series`，分隔符用 `dotSeparator()`，不新增带 `%1$s` 的拼接字符串。
- 其余语言走 core 的回退机制（缺 key 显示英文），无需在本仓库补。
- 改完 core 后 bump `gradle/libs.versions.toml:82` 的 `owntvCore`，再跑本仓库的守卫：

```bash
python tools/i18n/check_hardcoded_strings.py generate
python tools/i18n/check_hardcoded_strings.py verify --base <merge-base基线>
```

不新增 Kotlin 侧技术字面量，`safe_literals.txt` 不用动。

---

## 9. 测试与验收

### 9.1 单元测试

新增 `app/src/test/java/tv/own/owntv/features/search/SearchScopeTest.kt`：

1. `SearchScope.All` 不改变 `SearchResults`。
2. `Vod(MOVIE, categoryId = 7)` 只保留 `categoryId == 7` 的电影，channels / programmes / series 为空。
3. `Vod(SERIES, categoryId = null)` 保留全部剧集，其它类型为空。
4. `Vod(MOVIE, categoryId = 7)` 过滤掉 `categoryId == null` 的条目（不能被 `null` 分支放行）。

`LiveKey` → scope 的映射发生在屏幕层且依赖 `stringResource`，不放进单测；用 §9.2 的手动矩阵覆盖。

```bash
./gradlew test --tests "tv.own.owntv.features.search.SearchScopeTest"
```

### 9.2 手动验收矩阵

| 场景 | 期望 |
|---|---|
| Movies · Cinematic · 分类 X 按 OK | 进全局搜索，键盘弹出，范围条「电影 · X」 |
| Movies · List 布局 | 同上 |
| Series · 两种布局 | 范围条「剧集 · Y」 |
| Movies · All 分类按 OK | 范围条只显示「电影」，结果是全部电影 |
| 从收藏 / 历史 / 自定义分类按 OK | 普通全局搜索，无范围条，与导航栏进入一致 |
| 输入 ≥2 字符 | 结果只含该分类的电影 / 剧集，单类型平铺、无分组标题 |
| 范围条按 OK | 变全库结果，查询词保留 |
| 有查询词按 Back | 清查询与类型筛选，**范围保留**，焦点回搜索框 |
| 无查询词按 Back（来自点播页） | 回原分类，焦点在搜索框 |
| 无查询词按 Back（来自导航栏） | 焦点回导航栏，不跳页 |
| 从结果按 OK 进条目，再 Back | 先回搜索页，范围仍在，**键盘不再弹出** |
| 上述状态下再 Back | 回点播页，且不会再被送回搜索页 |
| 范围态按 ▼ / 列表按 ▲ | 焦点落在范围条上，不丢焦点 |
| 空分类（`itemCount == 0`）从搜索返回 | 仍能落焦点，不卡在 `restoreFocus` |
| 语音按钮（有识别器设备） | 可用，结果受当前范围约束 |
| 迷你播放器开启时进入搜索 | 预览窗按既有规则降级，范围条不受影响 |
| 儿童档案 / 分类 PIN | 结果与点播页一致，不出现越权条目 |

### 9.3 编译校验

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
```

---

## 10. 风险与对策

| 风险 | 影响 | 对策 |
|---|---|---|
| 点播页失去「原地过滤」手感 | 用户习惯变化 | 先做 P0 观察；必要时退 C（保留过滤 + 新增按钮），后端改动全部复用 |
| core `LIMIT 500` 先截断后过滤 | 分类内有结果却搜不到 | P1 在范围条旁显示结果数；P2 下推到 SQL |
| 范围态下 `filterFocus` 已失效 | ▼ / ▲ 焦点丢失 | §5.5(3) 的 `filterEntry` 改道 + 按键提示分支 |
| `autoEdit` 在从结果返回时重放 | 键盘盖住结果 | 一次性标记放 VM，`onEditingChange` 消费 |
| `searchOrigin` 与 `searchReturn` 同时指向同一 section | Back 来回跳 | `leaveSearchToOrigin` 里显式 `searchReturn = null` |
| 四处搜索框漏改 | 行为不一致 | 四处都在同一 `onActivate` 形态上收敛；可选加 grep 守卫 |
| 内联过滤链路删除影响 `revealMovie` | 「去往条目」定位失败 | `revealMovie` 传的是空查询（`MovieViewModel.kt:582`），删参数时同步改；跑一次定位回归 |
| 误删 `categoryQuery` | 分类面板搜索失效 | 只动 `searchQuery` / `_search`；`categoryQuery`（`:114-116`）保持原样 |

回滚：P0/P1 的改动集中在外壳与搜索包。回滚只需去掉点播页的 `onActivate`、`SearchScreen` 的 scope 分支与 `searchOrigin`；`SearchScope` 留着不接线也没有行为变化。

---

## 11. 待确认问题

1. **是否接受点播页不再原地过滤？** 不接受走 C：A 的全部改动复用，只把入口从「搜索框 OK」换成「右侧按钮」。
2. **P1 是否接受「收藏 / 历史 / 自定义分类不建范围」？** 当前建议接受（改为普通全局搜索）；精确过滤放 P2，需要 `customCategoryDao` / 收藏表的额外查询。
3. **core 的 `categoryIds` 参数是否本期就做？** 决定 P1 的截断问题是否只靠提示糊过去。
4. **带范围且查询为空时，页面留白是否可接受？** 若不可接受，P2 加一句提示文案，不进 P1。
5. **导航栏搜索入口是否要显示「上次范围」？** 建议不显示，范围只属于当次交接。
