// Settings › About: mi. Notes itself (its version and updates), then help (learning it, then reaching out), then the
// fine print (privacy, source, credits).
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
import com.jlees.minotes.update.REPO

private const val SOURCE_URL = "https://github.com/$REPO"
private const val GUIDE_URL = "https://mi-app-hub.github.io/mi-notes/user"
private const val NEW_ISSUE_URL = "$SOURCE_URL/issues/new"
private const val PRIVACY_URL = "https://mi-app-hub.github.io/mi-notes/privacy"

fun aboutSection(vm: NotesViewModel) = SettingsSection("about", "About", "info", "Updates, help, feedback, credits", listOf(
    SettingsHeading("mi. Notes"),
    SettingsRow("Check for Updates", "version update") {
        Item("restart_alt", "Check for Updates", "mi. Notes ${BuildConfig.VERSION_NAME}", onClick = { vm.updater.checkNow() })
    },
    SettingsHeading("Help & Feedback"),
    SettingsRow("Gesture Walkthrough", "help gestures intro tutorial tour guide") {
        Item("toolbar", "Gesture Walkthrough", "Try every swipe, step by step", onClick = { vm.sheet = Sheet.NONE; vm.updateSettings { copy(walkthroughSeen = false) } })
    },
    SettingsRow("User Guide", "help how to") { LinkItem("help", "User Guide", "Every gesture, tool and setting", GUIDE_URL) },
    SettingsRow("Report a Bug or Suggest a Feature", "feedback issue bug report feature request idea github problem") {
        LinkItem("bug_report", "Report a Bug or Suggest a Feature", "Open a new issue on GitHub", NEW_ISSUE_URL)
    },
    SettingsHeading("Legal & Credits"),
    SettingsRow("Privacy Policy", "privacy data collection policy network") { LinkItem("shield", "Privacy Policy", "mi. Notes collects no personal data", PRIVACY_URL) },
    SettingsRow("Source Code", "github open source") { LinkItem("code", "Source Code", "github.com/$REPO", SOURCE_URL) },
    SettingsRow("Credits", "licenses open source attribution") { CreditsItem() },
))

/** A row that opens a page in the phone's browser. */
@Composable
private fun LinkItem(icon: String, title: String, subtitle: String, url: String) {
    val ctx = LocalContext.current
    Item(icon, title, subtitle, onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
        trailing = { Sym("open_in_new", 20f, LocalUi.current.cs.onSurfaceVariant) })
}

@Composable
private fun CreditsItem() {
    val ui = LocalUi.current
    ExpandItem("info", "Credits", "Open-source projects mi. Notes is built with", null) { _ ->
        listOf(
            "Font" to "Google Sans Flex, SIL Open Font License",
            "Icons" to "Material Symbols, Apache License 2.0",
            "UI" to "Jetpack Compose & Material 3, Apache License 2.0",
            "Colors" to "MaterialKolor, MIT License",
            "Text Recognition" to "Google ML Kit, ML Kit Terms of Service",
        ).forEach { (what, who) ->
            Column(Modifier.fillMaxWidth().padding(start = 72.dp, end = 20.dp, top = 6.dp, bottom = 6.dp)) {
                Txt(what, 14f, ui.cs.onSurface, ui.semibold)
                Txt(who, 13f, ui.cs.onSurfaceVariant, ui.regular)
            }
        }
    }
}
