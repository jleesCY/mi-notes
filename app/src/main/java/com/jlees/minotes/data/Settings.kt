// The user's settings (saved as settings.json) and the choices they offer. Each choice is an enum saved under a fixed
// text value (@SerialName), so settings load unchanged across versions; an unknown value falls back to the default
// (Store's Json has coerceInputValues).
package com.jlees.minotes.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A choice shown in Settings: its label there. */
interface Choice { val label: String }

@Serializable enum class ThemeMode(override val label: String) : Choice {
    @SerialName("light") LIGHT("Light"), @SerialName("dark") DARK("Dark"), @SerialName("system") SYSTEM("System")
}

/** Look & Feel › List Style (List layout only): each note its own rounded card, or one table of rows. */
@Serializable enum class ListStyle(override val label: String) : Choice { @SerialName("cards") CARDS("Cards"), @SerialName("table") TABLE("Table") }

@Serializable enum class Spacing(override val label: String) : Choice {
    @SerialName("compact") COMPACT("Compact"), @SerialName("normal") NORMAL("Normal"), @SerialName("airy") AIRY("Airy")
}

/** The typeface for mi's own text: Google Sans Flex, its rounded variant, or the phone's own font. */
@Serializable enum class AppFont(override val label: String) : Choice {
    @SerialName("sans_flex") STANDARD("Standard"), @SerialName("rounded") ROUNDED("Rounded"), @SerialName("system") SYSTEM("System")
}

/** How the home page lays notes out: cards as tall as their note (staggered), cards all one size (grid), or rows. */
@Serializable enum class NotesLayout(override val label: String) : Choice {
    @SerialName("grid") STAGGERED("Staggered"), @SerialName("uniform") GRID("Grid"), @SerialName("list") LIST("List")
}

/** The order of the home page's notes. Manual is the order you dragged them into. */
@Serializable enum class SortOrder(override val label: String) : Choice {
    @SerialName("manual") MANUAL("Manual"), @SerialName("edited") EDITED("Edited"), @SerialName("created") CREATED("Created"), @SerialName("title") TITLE("Title")
}

/** Where long text pans (Look & Feel › Pan Overflowed Text); everywhere else it ends in "…". */
@Serializable enum class PanArea(override val label: String, val icon: String) : Choice {
    @SerialName("settings") SETTINGS("Settings", "settings"),
    @SerialName("noteMenu") NOTE_MENU("Note Menu", "more_vert"),
    @SerialName("notes") NOTES("Notes", "sticky_note_2"),
    @SerialName("trash") TRASH("Recently Deleted", "delete"),
}

/** How long Recently Deleted keeps a note before it's gone for good. */
@Serializable enum class KeepDeleted(override val label: String, val days: Int) : Choice {
    @SerialName("week") WEEK("1 Week", 7), @SerialName("month") MONTH("30 Days", 30), @SerialName("quarter") QUARTER("90 Days", 90),
}

@Serializable
data class Settings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    /** Dark theme on true black (OLED screens): backgrounds black, cards a hair lighter. */
    val pureBlack: Boolean = false,
    /** mi. Notes' yellow by default, its icon's colour. */
    val accentColor: String = com.jlees.minotes.logic.NOTES_YELLOW,
    val hapticFeedback: Boolean = true,
    val cornerRadius: Int = 28, // 0 | 12 | 28
    val uiPadding: Spacing = Spacing.NORMAL,
    val listStyle: ListStyle = ListStyle.TABLE,
    /** Look & Feel › Pan Overflowed Text, and where: Settings and the note menu to begin with. */
    val panLongText: Boolean = false,
    val panAreas: Set<PanArea> = setOf(PanArea.SETTINGS, PanArea.NOTE_MENU),
    val fontScale: Float = 1f,
    val font: AppFont = AppFont.STANDARD,
    val showStatusBar: Boolean = true,
    val rotateScreen: Boolean = true,
    // ---- Notes
    val layout: NotesLayout = NotesLayout.GRID,
    val sort: SortOrder = SortOrder.MANUAL,
    /** How many lines of a note the home page previews (grid cards). */
    val previewLines: Int = 6,
    val showDates: Boolean = true,
    /** Ticking a checklist item moves it below the ones still to do. */
    val checkedToBottom: Boolean = false,
    /** Text size in the editor, as a multiple of 16sp (separate from the app's own Font Size). */
    val editorTextScale: Float = 1f,
    /** Settings › Recently Deleted › Keep Deleted Notes. */
    val keepDeleted: KeepDeleted = KeepDeleted.MONTH,
    // ---- Gestures
    /** Holding the home Pill opens search. */
    val longPressSearch: Boolean = true,
    /** Swiping a list row: left deletes, right pins. */
    val swipeRows: Boolean = true,
    /** The walkthrough was shown (or skipped). */
    val walkthroughSeen: Boolean = false,
    /** Not a setting you see: the folder the home page last showed (null: All Notes). */
    val lastFolder: String? = null,
    /** Not a setting you see: when the automatic update check last found nothing new (ms); see update/Updates. */
    val lastUpdateCheck: Long = 0,
    /** Not a setting you see: the version code whose What's New was last shown (0: none yet). */
    val lastSeenVersion: Int = 0,
    /** Bumped when a default changes for existing installs; see [migrated]. */
    val settingsVersion: Int = SETTINGS_VERSION,
) {
    /** Load-time repair of values no screen could set (a hand-edited or damaged backup). */
    fun sanitized() = copy(
        cornerRadius = cornerRadius.takeIf { it in listOf(0, 12, 28) } ?: 28,
        fontScale = fontScale.coerceIn(0.8f, 1.2f),
        editorTextScale = editorTextScale.coerceIn(0.8f, 1.6f),
        previewLines = previewLines.coerceIn(2, 12),
    )
}

/**
 * A backup's settings, in place of [current], except what belongs to this phone: the folder it was showing, when it
 * last checked for updates and the last What's New it showed.
 */
fun Settings.importedOver(current: Settings) =
    copy(lastFolder = current.lastFolder, lastUpdateCheck = current.lastUpdateCheck, lastSeenVersion = current.lastSeenVersion).sanitized().migrated()

/** Whether long text pans in [area] (Pan Overflowed Text on, and that place picked). */
fun Settings.pans(area: PanArea) = panLongText && area in panAreas

/** The settings' version now; saved settings below it go through [migrated]. */
const val SETTINGS_VERSION = 1

/** Defaults changed for existing installs, one step per version. */
fun Settings.migrated(): Settings {
    var s = this
    // 1: Grid (cards all one size) became the default layout; anyone still on the old default, Staggered, moves to it
    if (s.settingsVersion < 1) s = s.copy(layout = if (s.layout == NotesLayout.STAGGERED) NotesLayout.GRID else s.layout, settingsVersion = 1)
    return s
}
