// App updates from GitHub releases: a quiet check at launch (at most every 12 hours), a manual check from
// Settings › About, and the offer to install a new version. An update replaces the app only: Android keeps its data
// (notes, folders, pictures, settings), since it's the same app signed with the same key.
package com.jlees.minotes.update

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.jlees.minotes.Ask
import com.jlees.minotes.BuildConfig
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.ui.common.Notices
import kotlinx.coroutines.launch

class Updater(private val vm: NotesViewModel) {
    /** At launch: ask GitHub for a newer release and offer to install it. */
    fun checkQuietly() = vm.viewModelScope.launch {
        if (BuildConfig.DEBUG) return@launch // dev builds are a separate app; releases can't update them
        val now = System.currentTimeMillis()
        if (!Updates.dueForCheck(now, vm.settings.lastUpdateCheck)) return@launch
        val result = Updates.check(BuildConfig.VERSION_NAME)
        val release = result.getOrNull()
        if (release == null) {
            if (result.isSuccess) vm.updateSettings { copy(lastUpdateCheck = now) }
            return@launch
        }
        offer(release)
    }

    /** Settings › About: check now, and say so when there's nothing new. */
    fun checkNow() = vm.viewModelScope.launch {
        val current = BuildConfig.VERSION_NAME
        if (BuildConfig.DEBUG) {
            vm.ask = Ask.Alert("Development Build", "mi. Notes dev ($current) is a separate app for development and doesn't update from GitHub releases.")
            return@launch
        }
        Updates.check(current)
            .onSuccess { release -> if (release != null) offer(release) else vm.ask = Ask.Alert("Up to Date", "mi. Notes $current is the latest version.") }
            .onFailure { vm.ask = Ask.Alert("Couldn't Check for Updates", "GitHub couldn't be reached. Check your connection and try again.") }
    }

    private fun offer(release: Release) {
        vm.ask = Ask.Alert(
            "Update Available",
            "mi. Notes ${release.version} is available (you have ${BuildConfig.VERSION_NAME}). Your notes, folders and settings stay as they are.",
            confirm = "Install",
        ) { install(release) }
    }

    private fun install(release: Release) = vm.viewModelScope.launch {
        Notices.show("Downloading update…")
        runCatching { Updates.downloadAndInstall(vm.getApplication<Application>(), release) }
            .onFailure { vm.ask = Ask.Alert("Couldn't Update", "The update couldn't be downloaded. Check your connection and try again.") }
    }
}
