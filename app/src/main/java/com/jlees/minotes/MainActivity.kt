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
        vm.start(if (savedInstanceState == null) sharedText(intent) else null)
        // one Haptics for the whole UI, following the Gesture Haptics setting
        val haptics = Haptics(window.decorView) { vm.settings.hapticFeedback }
        setContent { if (vm.ready) CompositionLocalProvider(LocalHaptics provides haptics) { App(vm, this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        sharedText(intent)?.let { vm.receiveShared(it) }
    }

    /** Text shared to mi. Notes from another app: it becomes a new note. */
    private fun sharedText(i: Intent?) = i?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }

    override fun onStop() { vm.saveNow(); super.onStop() }
}
