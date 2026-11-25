// Settings › Gestures: what the Pill's and the list's swipes do.
package com.jlees.minotes.ui.settings

import com.jlees.minotes.NotesViewModel

fun gesturesSection(vm: NotesViewModel) = SettingsSection("gestures", "Gestures", "vibration", "How gestures work throughout mi. Notes", listOf(
    SettingsHeading("Home Pill"),
    SettingsRow("Long Press to Search", "pill hold long press search find") {
        SwitchItem("search", "Long Press to Search", "Hold the Pill to search your notes", vm.settings.longPressSearch) { vm.updateSettings { copy(longPressSearch = it) } }
    },
    SettingsHeading("Note List"),
    SettingsRow("Swipe Rows", "swipe row delete pin list") {
        SwitchItem("swap_vert", "Swipe Rows", "In the list, swipe a note left to delete it or right to pin it", vm.settings.swipeRows) { vm.updateSettings { copy(swipeRows = it) } }
    },
))
