// The gesture walkthrough: shown once to every new user (and again from Settings › About), one card per gesture.
package com.jlees.minotes.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.ui.common.*

private class Step(val icon: String, val title: String, val text: String)

private val STEPS = listOf(
    Step("add", "Start a Note", "Tap + on the Pill for a new note. ⋮ beside it has New Checklist, sorting and Recently Deleted."),
    Step("folder_open", "Swipe Between Folders", "Swipe the Pill sideways to move to the next or previous folder."),
    Step("swap_vert", "Hold to Rearrange", "Hold a note and drag it somewhere else. Let go without moving for its menu: pin, colour, share, move."),
    Step("text_format", "Format Anything", "In a note, Aa opens size, weight, colour, highlight, fonts and more. Tap a checkbox to tick it."),
    Step("arrow_back", "Swipe to Go Back", "Swipe the Pill in a note, or in Settings, to go back."),
)

@Composable
fun Walkthrough(vm: NotesViewModel) {
    if (vm.settings.walkthroughSeen) return
    val ui = LocalUi.current
    var i by remember { mutableIntStateOf(0) }
    val done = { vm.updateSettings { copy(walkthroughSeen = true) } }
    BackHandler { if (i > 0) i-- else done() }
    Box(Modifier.fillMaxSize().zIndex(1000f).background(ui.cs.scrim.copy(alpha = 0.55f)).blockTouches(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(24.dp).widthIn(max = 420.dp).fillMaxWidth().clip(RoundedCornerShape(minOf(ui.radius, 28).dp)).background(ui.cs.surfaceContainerHigh).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(i, transitionSpec = {
                val fwd = targetState > initialState
                (slideInHorizontally(Motion.enter(250)) { if (fwd) it / 3 else -it / 3 } + fadeIn(Motion.enter(200))) togetherWith (slideOutHorizontally(Motion.exit(150)) { if (fwd) -it / 4 else it / 4 } + fadeOut(Motion.exit(100)))
            }, label = "step") { k ->
                val st = STEPS[k]
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(72.dp).clip(CircleShape).background(ui.cs.primaryContainer), contentAlignment = Alignment.Center) { Sym(st.icon, 36f, ui.cs.onPrimaryContainer) }
                    Txt(st.title, 22f, ui.cs.onSurface, ui.bold, Modifier.padding(top = 18.dp))
                    Txt(st.text, 15f, ui.cs.onSurfaceVariant, ui.regular, Modifier.padding(top = 8.dp), align = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 22f)
                }
            }
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                STEPS.indices.forEach { k -> Box(Modifier.size(if (k == i) 18.dp else 6.dp, 6.dp).clip(CircleShape).background(if (k == i) ui.cs.primary else ui.cs.outlineVariant)) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (i < STEPS.lastIndex) TextButton(onClick = done) { Txt("Skip", 14f, ui.cs.primary, ui.bold) }
                Spacer(Modifier.weight(1f))
                Button(onClick = { if (i < STEPS.lastIndex) i++ else done() }) { Txt(if (i < STEPS.lastIndex) "Next" else "Got It", 14f, ui.cs.onPrimary, ui.bold) }
            }
        }
    }
}
