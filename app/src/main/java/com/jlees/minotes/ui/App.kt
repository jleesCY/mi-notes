// The one screen: the home page, the note open over it (growing out of its card), and the sheets, menus and dialogs.
package com.jlees.minotes.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jlees.minotes.NotesViewModel
import com.jlees.minotes.Sheet
import com.jlees.minotes.data.ThemeMode
import com.jlees.minotes.editor.EditorScreen
import com.jlees.minotes.home.HomeScreen
import com.jlees.minotes.logic.DYNAMIC_ACCENT
import com.jlees.minotes.logic.GRAY_ACCENT
import com.jlees.minotes.logic.hexColor
import com.jlees.minotes.onboarding.Walkthrough
import com.jlees.minotes.trash.TrashView
import com.jlees.minotes.ui.common.*
import com.jlees.minotes.ui.settings.SettingsView
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun App(vm: NotesViewModel, activity: Activity) {
    val s = vm.settings
    val dark = when (s.themeMode) { ThemeMode.LIGHT -> false; ThemeMode.DARK -> true; ThemeMode.SYSTEM -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val baseScheme = if (s.accentColor == DYNAMIC_ACCENT && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else rememberDynamicColorScheme(
        seedColor = Color(hexColor(s.accentColor)), isDark = dark,
        style = if (s.accentColor == GRAY_ACCENT) PaletteStyle.Monochrome else PaletteStyle.Vibrant,
    )
    val scheme = if (dark && s.pureBlack) remember(baseScheme) { pureBlack(baseScheme) } else baseScheme
    val ui = UiConfig(scheme, dark, s.cornerRadius, s.fontScale, s.uiPadding, appFont(s.font), pureBlack = dark && s.pureBlack, listStyle = s.listStyle)
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(), typography = appTypography(s.font)) {
    CompositionLocalProvider(LocalUi provides ui, LocalPan provides s.panLongText, LocalListPan provides s.panLongText) {
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val d = density.density
        val imeInsets = WindowInsets.ime
        val imeDp = { imeInsets.getBottom(density) / d }
        val statusDp = maxOf(WindowInsets.statusBars.getTop(density), WindowInsets.displayCutout.getTop(density)) / d
        val navDp = WindowInsets.navigationBars.getBottom(density) / d

        LaunchedEffect(s.showStatusBar, ui.isDark, s.rotateScreen) {
            val ctl = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            ctl.isAppearanceLightStatusBars = !ui.isDark
            ctl.isAppearanceLightNavigationBars = !ui.isDark
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (s.showStatusBar) ctl.show(WindowInsetsCompat.Type.statusBars()) else ctl.hide(WindowInsetsCompat.Type.statusBars())
            activity.requestedOrientation = if (s.rotateScreen) ActivityInfo.SCREEN_ORIENTATION_FULL_USER else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        // ---- the open note: it grows out of its card (or the + button), and shrinks back into it when closed
        val grow = remember { Animatable(0f) }
        var shownId by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(vm.openId) {
            val id = vm.openId
            if (id != null) { shownId = id; grow.snapTo(0f); grow.animateTo(1f, Motion.switcher) }
            else if (shownId != null) { grow.animateTo(0f, Motion.exit(220)); shownId = null }
        }

        // ---- sheets (Settings, Recently Deleted) slide in from the right
        val sheetX = remember { Animatable(1f) }
        var sheetShown by remember { mutableStateOf(Sheet.NONE) }
        LaunchedEffect(vm.sheet) {
            if (vm.sheet != Sheet.NONE) { sheetShown = vm.sheet; sheetX.snapTo(1f); sheetX.animateTo(0f, Motion.switcher) }
        }
        fun closeSheet(speed: Float? = null) = scope.launch {
            if (speed == null) sheetX.animateTo(1f, Motion.exit(200)) else sheetX.animateTo(1f, Motion.switcher, initialVelocity = speed)
            vm.sheet = Sheet.NONE; sheetShown = Sheet.NONE; vm.settingsSearch = ""
        }

        // Back closes the top layer first: the menu, a dialog (their own handlers), a sheet, then the note
        BackHandler(vm.sheet != Sheet.NONE || vm.openId != null) {
            when {
                vm.sheet != Sheet.NONE -> closeSheet()
                vm.openId != null -> vm.closeNote()
            }
        }

        Box(Modifier.fillMaxSize()) {
            Box(Modifier.size(1.dp).focusable()) // focus sink: keeps Compose from handing focus to the search field
            HomeScreen(vm, statusDp, navDp, imeDp)

            shownId?.let { id ->
                val origin = vm.cardBounds[id] ?: vm.openFrom
                val radius = with(density) { ui.radius.coerceAtMost(24).dp.toPx() }
                Box(Modifier.fillMaxSize().zIndex(50f).graphicsLayer {
                    val p = grow.value
                    val w = size.width; val h = size.height
                    val o = origin ?: Rect(w * 0.3f, h * 0.75f, w * 0.7f, h * 0.85f)
                    val s0 = (o.width / w).coerceIn(0.05f, 1f)
                    val sc = lerp(s0, 1f, p)
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = sc; scaleY = sc
                    translationX = lerp(o.left, 0f, p); translationY = lerp(o.top, 0f, p)
                    val clipH = lerp(o.height / s0, h, p)
                    val r = lerp(radius / s0, 0f, p)
                    shape = object : Shape {
                        override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density) =
                            Outline.Generic(Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, clipH, CornerRadius(r))) })
                    }
                    clip = true
                    alpha = (p * 2.5f).coerceAtMost(1f)
                }) {
                    EditorScreen(vm, id, statusDp, navDp, imeDp, onClose = { vm.closeNote() })
                }
            }

            if (sheetShown != Sheet.NONE) Box(Modifier.fillMaxSize().zIndex(160f)) {
                OverlaySheet(
                    sheetX, statusDp, imeDp, { closeSheet() },
                    backTowardEnd = true,
                    onBackDrag = { p -> scope.launch { sheetX.snapTo(p) } },
                    onBackRelease = { close, speed -> if (close) closeSheet(speed) else scope.launch { sheetX.animateTo(0f, Motion.switcher) } },
                    header = { Txt(if (sheetShown == Sheet.TRASH) "Recently Deleted" else "Settings", 28f, ui.cs.onSurface, ui.extrabold, maxLines = 1) },
                ) {
                    when (sheetShown) {
                        Sheet.SETTINGS -> SettingsView(vm)
                        Sheet.TRASH -> TrashView(vm)
                        Sheet.NONE -> {}
                    }
                }
            }

            NoteMenu(vm, fromTop = vm.openId != null, statusDp, navDp)
            Dialogs(vm)
            UndoBar(Notices.current, onTimeout = { Notices.current = null }, Modifier.align(Alignment.BottomCenter).zIndex(400f).padding(bottom = (maxOf(navDp, 0f) + 92).dp).imePadding())
            Walkthrough(vm)
        }
    }
    }
}

/**
 * The OLED variant of a dark scheme: everything that fills the screen is true black; cards, menus, dialogs and the
 * Pill stay a step above it, with a hint of the accent, so they still read as surfaces.
 */
private fun pureBlack(cs: androidx.compose.material3.ColorScheme): androidx.compose.material3.ColorScheme {
    fun step(v: Int) = androidx.compose.ui.graphics.lerp(Color(0xFF000000.toInt() or (v shl 16) or (v shl 8) or v), cs.primary, 0.04f)
    return cs.copy(
        background = Color.Black, surface = Color.Black, surfaceDim = Color.Black, surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color.Black, surfaceContainer = step(0x12), surfaceContainerHigh = step(0x1C),
        surfaceContainerHighest = step(0x26), surfaceBright = step(0x2E), inverseOnSurface = Color.Black,
    )
}
