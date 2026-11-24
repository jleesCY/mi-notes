// Settings › Gestures: what the Pill's and the list's swipes do.
package com.jlees.minotes.ui.settings

import com.jlees.minotes.NotesViewModel

fun gesturesSection(vm: NotesViewModel) = SettingsSection("gestures", "Gestures", "vibration", "How gestures work", listOf(
    SettingsHeading("Pill"),
    SettingsRow("Swipe Between Folders", "pill swipe sideways folder next previous") {
        SwitchItem("folder_open", "Swipe Between Folders", "Swipe the Pill sideways for the next or previous folder", vm.settings.swipeFolders) { vm.updateSettings { copy(swipeFolders = it) } }
    },
    SettingsHeading("Notes"),
    SettingsRow("Swipe Rows", "swipe row delete pin list") {
        SwitchItem("swap_vert", "Swipe Rows", "In the list, swipe a note left to delete it or right to pin it", vm.settings.swipeRows) { vm.updateSettings { copy(swipeRows = it) } }
    },
))
