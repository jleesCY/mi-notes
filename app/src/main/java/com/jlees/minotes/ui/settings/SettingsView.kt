// Settings: a main page (the sections, under headings) where the search Pill covers every setting, and one page per
// section (back arrow, no search). Each section is built in its own file (LookAndFeelSettings.kt, NotesSettings.kt…);
// this file is the screen: the list, the headings over it, search and paging.
package com.jlees.minotes.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.jlees.minotes.BuildConfig
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.ui.common.*
import kotlinx.coroutines.launch

@Composable
fun SettingsView(vm: NotesViewModel) {
    val ui = LocalUi.current
    val scope = rememberCoroutineScope()
    val search = vm.settingsSearch
    val list = rememberLazyListState()
    LaunchedEffect(search) { list.scrollToItem(0) }

    val groups = listOf(lookAndFeelSection(vm), notesSection(vm), gesturesSection(vm), backupSection(vm), aboutSection(vm))
    // the main page's headings, each over its own block of sections (by id): a new section is added to one here
    val headings = listOf(
        "Appearance" to listOf("look"),
        "Notes" to listOf("notes", "gestures"),
        "General" to listOf("data", "about"),
    ).map { (title, ids) -> title to ids.mapNotNull { id -> groups.firstOrNull { it.id == id } } }.filter { it.second.isNotEmpty() }

    val q = search.trim().lowercase()
    val visible = if (q.isEmpty()) emptyList() else groups.mapNotNull { g ->
        val placed = placeRows(g.rows).filter { !it.row.header }
        val hits = if (g.title.lowercase().contains(q)) placed
            else placed.filter { it.row.label.lowercase().contains(q) || it.row.keywords.contains(q) || it.heading?.lowercase()?.contains(q) == true }
        hits.takeIf { it.isNotEmpty() }?.let { g to it }
    }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<Pair<String?, String>?>(null) } // a search result opened: (heading, label)
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    BackHandler(enabled = openId != null) { openId = null }
    val sheetBg = ui.cs.surfaceContainerLow

    @Composable
    fun Page(pageId: String?) {
        val page = groups.firstOrNull { it.id == pageId }
        if (page == null) TopBarOver(sheetBg, bar = {}) { top ->
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top, bottom = SheetPillSpace)) {
                if (q.isEmpty()) {
                    headings.forEach { (title, sections) ->
                        item(key = "g:$title") { SectionHeader(null, title) }
                        itemsIndexed(sections, key = { _, g -> "s:${g.id}" }) { i, g ->
                            Box(Modifier.segment(i, sections.size)) { Item(g.icon, g.title, g.summary, onClick = { openId = g.id }, trailing = { Sym("chevron_right", 22f, ui.cs.onSurfaceVariant) }) }
                        }
                    }
                } else {
                    if (visible.isEmpty()) item(key = "empty") { EmptyState("search", "No Matches", top = 60.dp) }
                    visible.forEach { (g, hits) ->
                        item(key = "h:${g.id}") { SectionHeader(g.icon, g.title) }
                        itemsIndexed(hits, key = { _, p -> "${g.id}:${p.heading}:${p.row.label}" }) { i, p ->
                            Box(Modifier.animateItem().segment(i, hits.size)) {
                                Item(g.icon, p.row.label, p.heading, onClick = { focusManager.clearFocus(); found = p.heading to p.row.label; openId = g.id }, trailing = { Sym("chevron_right", 22f, ui.cs.onSurfaceVariant) })
                            }
                        }
                    }
                }
                item(key = "footer") {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        Txt("mi. Notes v${BuildConfig.VERSION_NAME}", 12f, ui.cs.onSurfaceVariant, ui.regular, Modifier.graphicsLayer { alpha = 0.5f })
                    }
                }
            }
        } else TopBarOver(sheetBg, bar = {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { openId = null }) { Sym("arrow_back", 24f, ui.cs.onSurface, label = "Back") }
                Sym(page.icon, 22f, ui.cs.primary, Modifier.padding(start = 4.dp, end = 10.dp))
                Txt(page.title, 22f, ui.cs.onSurface, ui.bold, maxLines = 1)
            }
        }) { top ->
            val pageList = rememberLazyListState()
            // opened from a search result: scroll to the setting and light it up for a moment
            val flash = remember { androidx.compose.animation.core.Animatable(0f) }
            val placed = placeRows(page.rows)
            val target = found?.let { (heading, label) -> placed.indexOfFirst { !it.row.header && it.heading == heading && it.row.label == label } }?.takeIf { it >= 0 }
            LaunchedEffect(page.id, target) {
                if (target == null) return@LaunchedEffect
                pageList.animateScrollToItem(target)
                flash.animateTo(1f, tween(250)); flash.animateTo(0f, tween(900, delayMillis = 400))
                found = null
            }
            LazyColumn(Modifier.fillMaxSize(), state = pageList, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = top, bottom = SheetPillSpace)) {
                itemsIndexed(placed, key = { _, p -> "${page.id}:${p.heading}:${p.row.label}" }) { i, p ->
                    if (p.row.header) SectionHeader(null, p.row.label)
                    else Box(Modifier.segment(p.index, p.count).then(
                        if (i == target) Modifier.drawWithContent { drawContent(); drawRect(ui.cs.primary.copy(alpha = 0.18f * flash.value)) } else Modifier,
                    )) { p.row.content() }
                }
            }
        }
    }

    // swiping the search Pill right inside a section goes up a level, following the finger
    val back = remember { androidx.compose.animation.core.Animatable(0f) }
    var backing by remember { mutableStateOf(false) }
    var instant by remember { mutableStateOf(false) }
    val sheetBack = LocalSheetBack.current
    DisposableEffect(openId) {
        sheetBack?.inner = if (openId == null) null else BackSwipe(
            drag = { p -> backing = true; scope.launch { back.snapTo(p) } },
            release = { up, speed ->
                scope.launch {
                    if (up) { back.animateTo(1f, Motion.switcher, initialVelocity = speed); instant = true; openId = null }
                    else back.animateTo(0f, tween(200))
                    backing = false; back.snapTo(0f)
                }
            },
        )
        onDispose { sheetBack?.inner = null }
    }

    Box(Modifier.fillMaxSize()) {
        if (backing) Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = -size.width * 0.25f * (1f - back.value) }) { Page(null) }
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = size.width * back.value; shadowElevation = 8.dp.toPx() }.background(sheetBg)) { Page(openId) }
        } else AnimatedContent(
            openId, Modifier.fillMaxSize(), label = "settings page",
            transitionSpec = {
                if (instant) { instant = false; return@AnimatedContent EnterTransition.None togetherWith ExitTransition.None }
                val forward = targetState != null
                (slideInHorizontally(tween(220)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(160))) togetherWith
                    (slideOutHorizontally(tween(220)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(120)))
            },
        ) { Page(it) }
        SheetSearchPill(search, { vm.settingsSearch = it; if (it.isNotEmpty()) openId = null })
    }
}
