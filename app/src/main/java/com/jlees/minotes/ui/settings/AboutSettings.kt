// Settings › About: the app, learning it again, and the fine print.
package com.jlees.minotes.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jlees.minotes.BuildConfig
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.Sheet
import com.jlees.minotes.ui.common.*

private const val SOURCE_URL = "https://github.com/jleesCY/mi-notes"

fun aboutSection(vm: NotesViewModel) = SettingsSection("about", "About", "info", "Help, privacy, credits", listOf(
    SettingsHeading("mi. Notes"),
    SettingsRow("Version", "version update") { Item("sticky_note_2", "mi. Notes", "Version ${BuildConfig.VERSION_NAME}") },
    SettingsRow("Gesture Walkthrough", "help gestures intro tutorial tour guide") {
        Item("toolbar", "Gesture Walkthrough", "See every gesture again", onClick = { vm.sheet = Sheet.NONE; vm.updateSettings { copy(walkthroughSeen = false) } })
    },
    SettingsHeading("Privacy & Credits"),
    SettingsRow("Privacy", "privacy data collection network") {
        Item("shield", "Privacy", "Your notes never leave your phone unless you share or export them. No accounts, no tracking.")
    },
    SettingsRow("Source Code", "github open source report bug feature") { SourceItem() },
    SettingsRow("Credits", "licenses open source attribution") { CreditsItem() },
))

@Composable
private fun SourceItem() {
    val ctx = LocalContext.current
    Item("code", "Source Code", "github.com/jleesCY/mi-notes", onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL))) } },
        trailing = { Sym("open_in_new", 20f, LocalUi.current.cs.onSurfaceVariant) })
}

@Composable
private fun CreditsItem() {
    val ui = LocalUi.current
    ExpandItem("info", "Credits", "Open-source projects mi. is built with", null) { _ ->
        listOf(
            "Font" to "Google Sans Flex, SIL Open Font License",
            "Icons" to "Material Symbols, Apache License 2.0",
            "UI" to "Jetpack Compose & Material 3, Apache License 2.0",
            "Colors" to "MaterialKolor, MIT License",
        ).forEach { (what, who) ->
            Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 20.dp, top = 6.dp, bottom = 6.dp)) {
                Txt(what, 14f, ui.cs.onSurface, ui.semibold)
                Txt(who, 13f, ui.cs.onSurfaceVariant, ui.regular)
            }
        }
    }
}
