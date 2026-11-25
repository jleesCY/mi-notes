package com.jlees.minotes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.jlees.minotes.ui.App
import com.jlees.minotes.ui.common.Haptics
import com.jlees.minotes.ui.common.LocalHaptics

class MainActivity : ComponentActivity() {
    private val vm: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { !vm.ready }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        vm.start(if (savedInstanceState == null) request(intent) else null)
        addNewNoteShortcut()
        // one Haptics for the whole UI, following the Gesture Haptics setting
        val haptics = Haptics(window.decorView) { vm.settings.hapticFeedback }
        setContent { if (vm.ready) CompositionLocalProvider(LocalHaptics provides haptics) { App(vm, this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        request(intent)?.let(vm::receive)
    }

    /** What [i] asks of mi. Notes: shared text, the New Note shortcut, or a minotes:// link (NotesRules.incoming). */
    private fun request(i: Intent?) = i?.let { com.jlees.minotes.notes.incoming(it.action, it.getStringExtra(Intent.EXTRA_TEXT), it.dataString) }

    /** "New Note" on the app icon (long-press it, or drag it to the home screen as an icon of its own). */
    private fun addNewNoteShortcut() {
        val sm = getSystemService(android.content.pm.ShortcutManager::class.java) ?: return
        if (sm.dynamicShortcuts.any { it.id == "new_note" }) return
        sm.dynamicShortcuts = listOf(
            android.content.pm.ShortcutInfo.Builder(this, "new_note").setShortLabel("New Note")
                .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.shortcut_new_note))
                .setIntent(Intent(com.jlees.minotes.notes.NEW_NOTE_ACTION, null, this, MainActivity::class.java)).build(),
        )
    }

    override fun onStop() { vm.saveNow(); super.onStop() }
}
