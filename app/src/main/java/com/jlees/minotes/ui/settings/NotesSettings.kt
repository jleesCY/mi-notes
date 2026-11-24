// Settings › Notes: how notes are laid out and ordered on the home page, and how the editor behaves.
package com.jlees.minotes.ui.settings

import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.data.Settings

fun notesSection(vm: NotesViewModel): SettingsSection {
    val s = vm.settings
    fun set(f: Settings.() -> Settings) = vm.updateSettings(f)
    return SettingsSection("notes", "Notes", "sticky_note_2", "How notes appear and behave", listOf(
        SettingsHeading("Home"),
        SettingsRow("Layout", "grid list cards rows view") { ChoiceItem("grid_view", "Layout", null, s.layout) { set { copy(layout = it) } } },
        SettingsRow("Sort By", "order sort manual edited created title alphabetical") { ChoiceItem("sort", "Sort By", "Pinned notes always come first", s.sort) { set { copy(sort = it) } } },
        SettingsRow("Preview Lines", "preview card lines length") {
            ChoiceItem("notes", "Preview Lines", "How much of each note its card shows", listOf("3" to "3", "6" to "6", "9" to "9", "12" to "12"), s.previewLines.toString()) { set { copy(previewLines = it.toInt()) } }
        },
        SettingsRow("Show Dates", "date time edited") { SwitchItem("calendar_today", "Show Dates", "When each note was last edited", s.showDates) { set { copy(showDates = it) } } },
        SettingsHeading("Editor"),
        SettingsRow("Text Size", "editor note text size zoom bigger smaller") {
            SliderItem("text_fields", "Text Size", "The size of text in your notes", s.editorTextScale, 0.8f, 1.6f) { set { copy(editorTextScale = it) } }
        },
        SettingsRow("Checked Items to Bottom", "checklist checkbox done ticked move bottom") {
            SwitchItem("checklist", "Checked Items to Bottom", "Ticked items move below the rest of their checklist", s.checkedToBottom) { set { copy(checkedToBottom = it) } }
        },
    ))
}
