// Text fields shared by dialogs and sheets.
package com.jlees.minotes.ui.common

import androidx.compose.ui.draw.clip

import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jlees.minotes.ui.common.*

/** A text field with a clear (×) button. */
@Composable
fun ClearableField(
    value: String, onChange: (String) -> Unit, fr: FocusRequester = remember { FocusRequester() }, modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    val ui = LocalUi.current
    Row(
        modifier.fillMaxWidth().height(56.dp).background(ui.cs.surfaceContainerHighest, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value, onChange, Modifier.weight(1f).focusRequester(fr), singleLine = true,
            textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = FontWeight.W600, fontSize = 16.sp),
            cursorBrush = SolidColor(ui.cs.primary),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder != null) Txt(placeholder, 16f, ui.cs.onSurfaceVariant, ui.regular)
                    inner()
                }
            },
        )
        ClearButton(value.isNotEmpty()) { onChange(""); fr.requestFocus() }
    }
}

/** A field's clear (×): 48dp to tap, it pops in when there's text and out when there isn't. */
@Composable
fun ClearButton(visible: Boolean, onClear: () -> Unit) {
    val ui = LocalUi.current
    androidx.compose.animation.AnimatedVisibility(
        visible,
        enter = androidx.compose.animation.fadeIn(Motion.enter(150)) + androidx.compose.animation.scaleIn(Motion.enter(200), initialScale = 0.6f),
        exit = androidx.compose.animation.fadeOut(Motion.exit(100)) + androidx.compose.animation.scaleOut(Motion.exit(100), targetScale = 0.6f),
    ) {
        Box(Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).touchable(onClick = onClear), contentAlignment = Alignment.Center) {
            Sym("cancel", 20f, ui.cs.onSurfaceVariant, label = "Clear")
        }
    }
}

/** The search field inside a sheet view's search Pill (see SheetSearchPill): just "Search", so it never overflows. */
@Composable
fun SheetSearchField(
    value: String, onChange: (String) -> Unit,
    trailing: (@Composable () -> Unit)? = null, onFocus: (Boolean) -> Unit = {}, autoFocus: Boolean = false, placeholder: String = "Search",
) {
    val ui = LocalUi.current
    val fr = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { fr.requestFocus() } // opened to type in: the keyboard comes straight up
    Row(
        // the main Pill's field: a lighter tone inside the Pill's bar, the same height and corners
        Modifier.fillMaxWidth().height(49.dp)
            .background(ui.pillField, RoundedCornerShape((ui.radius * 1.5f).dp))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Sym("search", 24f, ui.cs.onSurfaceVariant, Modifier.padding(end = 12.dp))
        BasicTextField(
            value, onChange, Modifier.weight(1f).focusRequester(fr).onFocusChanged { onFocus(it.isFocused) }, singleLine = true,
            textStyle = TextStyle(color = ui.cs.onSurface, fontFamily = ui.font, fontWeight = ui.semibold, fontSize = 16.sp),
            cursorBrush = SolidColor(ui.cs.primary),
            decorationBox = { inner -> Box { if (value.isEmpty()) Txt(placeholder, 16f, ui.cs.onSurfaceVariant, ui.semibold, scaled = false); inner() } },
        )
        // the clear button pops in while there's text; the page's ⋮ shows otherwise
        ClearButton(value.isNotEmpty()) { onChange(""); fr.requestFocus() }
        if (value.isEmpty()) trailing?.invoke()
    }
}
