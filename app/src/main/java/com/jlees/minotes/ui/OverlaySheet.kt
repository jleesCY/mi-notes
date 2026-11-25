// The full-screen sheet that Library and Settings open in.
package com.jlees.minotes.ui


import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.jlees.minotes.ui.common.*
import kotlin.math.abs
import androidx.compose.ui.graphics.graphicsLayer

// ---------------------------------------------------------------- overlay sheet
/**
 * Full-screen sheet (Library, Settings, History). It slides in from the side of the button that opened it, or up from
 * the side ([slide]: its offset in sheet widths). No handle, no partial height.
 *
 * Swiping a view's search Pill goes back, following the finger: toward the side the sheet came from (right for
 * the right with [backTowardEnd], else the left). [onBackDrag] reports how far (0..1 of the width) and
 * [onBackRelease] whether to finish (close the sheet) or spring back, unless a view inside has levels of its own and
 * has taken the swipe ([SheetBack.inner]).
 */
@Composable
fun OverlaySheet(
    slide: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    topInsetDp: Float, imeDp: () -> Float, onClose: () -> Unit,
    backTowardEnd: Boolean, onBackDrag: (Float) -> Unit, onBackRelease: (finish: Boolean, speed: Float) -> Unit,
    morphFrom: () -> androidx.compose.ui.geometry.Rect? = { null }, morphContent: @Composable () -> Unit = {},
    header: (@Composable () -> Unit)? = null, content: @Composable () -> Unit,
) {
    val ui = LocalUi.current
    var sizePx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val back = remember(backTowardEnd) {
        SheetBack(
            backTowardEnd, BackSwipe(onBackDrag, onBackRelease), { sizePx.width.toFloat() }, { slide.value }, morphFrom, morphContent,
            offset = { slide.value * sizePx.width },
        )
    }
    Box(Modifier.fillMaxSize().zIndex(100f).onSizeChanged { sizePx = it }) {
        // dims the page while the sheet slides; also keeps taps off the page during the slide
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - abs(slide.value).coerceIn(0f, 1f) }.background(ui.cs.scrim.copy(alpha = 0.32f)).blockTouches())
        Column(
            Modifier.fillMaxSize().graphicsLayer { translationX = slide.value * size.width }
                .background(ui.cs.surfaceContainerLow).blockTouches().padding(top = topInsetDp.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { Column(Modifier.widthIn(max = COMFORTABLE_WIDTH.dp).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (header != null) Box(Modifier.weight(1f).padding(end = 12.dp)) { header() }
                else Txt("Settings", 28f, ui.cs.onSurface, ui.extrabold, Modifier.weight(1f))
                FilledTonalIconButton(onClick = onClose) { Sym("close", 24f, ui.cs.onSecondaryContainer, label = "Close") }
            }
            // a tap on nothing in particular (an empty page, the space between rows) puts the keyboard away
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            Box(Modifier.fillMaxWidth().weight(1f).paddingDp(bottom = imeDp).pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }) {
                CompositionLocalProvider(LocalSheetBack provides back) { content() }
            }
        } }
    }
}

