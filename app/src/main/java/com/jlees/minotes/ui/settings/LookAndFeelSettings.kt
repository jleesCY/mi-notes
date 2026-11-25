// Settings › Look & Feel: colours, text, layout and haptics.
package com.jlees.minotes.ui.settings

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.data.PanArea
import com.jlees.minotes.data.Settings
import com.jlees.minotes.data.ThemeMode
import com.jlees.minotes.logic.ACCENTS
import com.jlees.minotes.logic.DYNAMIC_ACCENT
import com.jlees.minotes.logic.hexColor
import com.jlees.minotes.ui.common.*

fun lookAndFeelSection(vm: NotesViewModel): SettingsSection {
    val s = vm.settings
    fun set(f: Settings.() -> Settings) = vm.updateSettings(f)
    return SettingsSection("look", "Look & Feel", "palette", "Colors, UI and haptics", listOfNotNull(
        SettingsHeading("Colors"),
        SettingsRow("Theme", "light dark system mode") {
            ChoiceItem("routine", "Theme", null, s.themeMode) { set { copy(themeMode = it) } }
        },
        // only matters in dark: hidden in Light
        if (s.themeMode == ThemeMode.LIGHT) null else SettingsRow("Pure Black", "oled amoled black dark battery") {
            SwitchItem("contrast", "Pure Black", "True black backgrounds in dark mode", s.pureBlack) { set { copy(pureBlack = it) } }
        },
        SettingsRow("Accent Color", "colour wallpaper material you dynamic") { AccentItem(vm) },
        SettingsHeading("Text"),
        SettingsRow("Font Size", "text scale app") {
            SliderItem("format_size", "Font Size", "UI font size throughout mi. Notes", s.fontScale, 0.8f, 1.2f) { set { copy(fontScale = it) } }
        },
        SettingsRow("Font", "typeface rounded system font") { ChoiceItem("font_download", "Font", null, s.font) { set { copy(font = it) } } },
        SettingsHeading("Overflowed Text"),
        SettingsRow("Pan Overflowed Text", "pan long text scroll marquee overflow cut off ellipsis slide names") {
            SwitchItem("arrow_forward", "Pan Overflowed Text", "Text that doesn't fit slides to show the rest", s.panLongText) { set { copy(panLongText = it) } }
        },
    ) + (if (!s.panLongText) emptyList() else PanArea.entries.map { area ->
        // where it pans: only shown with Pan Overflowed Text on
        SettingsRow(area.label, "pan overflowed long text scroll marquee ${area.label.lowercase()}") {
            SwitchItem(area.icon, area.label, null, area in s.panAreas) { on -> set { copy(panAreas = if (on) panAreas + area else panAreas - area) } }
        }
    }) + listOf(
        SettingsHeading("Layout"),
        SettingsRow("Corners", "radius shape round square") {
            ChoiceItem("rounded_corner", "Corners", null, listOf("0" to "Square", "12" to "Soft", "28" to "Round"), s.cornerRadius.toString()) { set { copy(cornerRadius = it.toInt()) } }
        },
        SettingsRow("List Spacing", "spacing density padding compact airy") {
            ChoiceItem("density_medium", "List Spacing", "How much space between list items", s.uiPadding) { set { copy(uiPadding = it) } }
        },
        SettingsRow("List Style", "table cards rows plain flat") {
            ChoiceItem("list", "List Style", "How the note list and Recently Deleted look", s.listStyle) { set { copy(listStyle = it) } }
        },
        SettingsRow("Status Bar", "clock battery immersive") { SwitchItem("battery_charging_full", "Status Bar", null, s.showStatusBar) { set { copy(showStatusBar = it) } } },
        SettingsRow("Rotate Screen", "landscape portrait orientation sideways auto rotate lock") {
            SwitchItem("screen_rotation", "Rotate Screen", "Turn mi. Notes sideways with your phone", s.rotateScreen) { set { copy(rotateScreen = it) } }
        },
        SettingsHeading("Feedback"),
        SettingsRow("Gesture Haptics", "haptic feedback vibration vibrate haptics touch feel") {
            SwitchItem("vibration", "Gesture Haptics", "Haptic feedback when gestures are performed", s.hapticFeedback) { set { copy(hapticFeedback = it) } }
        },
    ))
}

/** Accent Color: the wallpaper's colours (Android 12+) or one of mi.'s, as a row of swatches. */
@Composable
private fun AccentItem(vm: NotesViewModel) {
    val ui = LocalUi.current
    val dynamicOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val list = (if (dynamicOk) listOf(DYNAMIC_ACCENT) else emptyList()) + ACCENTS
    Item("palette", "Accent Color", if (dynamicOk) "Based on your wallpaper, or choose your own" else null, below = {
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list) { hex ->
                val selected = vm.settings.accentColor == hex
                val scale by animateFloatAsState(if (selected) 0.86f else 1f, Motion.fastSpatial(), label = "swatch")
                val isDyn = hex == DYNAMIC_ACCENT
                Box(
                    Modifier.size(48.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                        .clip(if (selected) RoundedCornerShape(30) else CircleShape)
                        .background(if (isDyn) ui.cs.tertiaryContainer else Color(hexColor(hex)))
                        .touchable { vm.updateSettings { copy(accentColor = hex) } },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isDyn) Sym("wallpaper", 22f, ui.cs.onTertiaryContainer, label = "Wallpaper colors")
                    if (selected && !isDyn) Sym("check", 22f, Color.White)
                }
            }
        }
    })
}
