// The note's body editor: Android's EditText with mi.'s formatting spans, plus what a notes editor needs on top: lists
// that carry on with Enter and end on an empty item, checkboxes you tap, backspace that takes a marker off first,
// styles chosen with nothing selected applying to what's typed next, and undo/redo.
package com.jlees.minotes.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.Spanned
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import com.jlees.minotes.notes.IMAGE_CHAR
import com.jlees.minotes.notes.ListKind
import com.jlees.minotes.notes.NoteImages
import com.jlees.minotes.notes.RichText
import com.jlees.minotes.notes.SpanType

@SuppressLint("AppCompatCustomView", "ViewConstructor")
class NoteEditText(ctx: Context) : EditText(ctx) {
    /** The note changed (typed, formatted, undone): its new body. Called at most every 300ms while typing. */
    var onChange: (RichText) -> Unit = {}
    /** The cursor or selection moved, or a style changed: the format controls re-read what's under it. */
    var onState: () -> Unit = {}
    /** mi.'s haptics (they follow Settings › Gesture Haptics); set by the editor screen. */
    var haptics: com.jlees.minotes.ui.common.Haptics? = null
    /** The text was touched (not a checkbox): the format panel gives way to the keyboard. */
    var onTouchText: () -> Unit = {}
    /** Ticked items move below the rest of their checklist (Settings › Notes › Checked Items to Bottom). */
    var checkedToBottom = false
    /** The colour of ticked items' text. */
    var fadedInk = 0x80FFFFFF.toInt()
    /** Bumped on every change, so Compose can re-read the format state. */
    var version = 0; private set

    /**
     * The selection the format controls act on: the last one made while the text had focus. Tapping a control can take
     * focus away from the text, and Android then collapses the selection; the format must still apply to what was chosen.
     */
    private var selS = 0; private var selE = 0

    /**
     * While the format panel is open the selection is held: Android collapses a selection when its Cut/Copy toolbar
     * closes (which a tap anywhere else does), so a collapse not made by the editor itself is put straight back, and
     * the text stays highlighted for one control after another. Tapping the text lets go ([onTouchText] closes the panel).
     */
    var holdSelection = false
        set(v) {
            field = v
            if (!v) return
            val a = minOf(selectionStart, selectionEnd).coerceAtLeast(0); val b = maxOf(selectionStart, selectionEnd).coerceAtLeast(0)
            // the tap that opened the panel may itself have just collapsed the selection: take back the range it had
            val lost = lostRange
            if (a == b && lost != null && android.os.SystemClock.uptimeMillis() - lost.third < 1000) { selS = lost.first; selE = lost.second }
            else { selS = a; selE = b }
        }
    /** A range collapsed by something other than typing or a touch in the text: (start, end, when). */
    private var lostRange: Triple<Int, Int, Long>? = null
    private var touchedText = false
    /** Styles picked with nothing selected: they apply to the next text typed at the cursor. */
    private val pending = HashMap<Inline, Any>()
    private var busy = false // the editor is changing its own text: don't treat it as typing
    private var typingNow = false // between a change's before and after: the cursor moving with the text isn't a jump
    private var newlineAt = -1
    private var clearedAt = -1 // a deletion over a line break (or of a selection) started here: the line it leaves empty goes plain
    private var insertAt = -1; private var insertLen = 0

    private class Snapshot(val text: RichText, val selStart: Int, val selEnd: Int)
    private val history = ArrayList<Snapshot>()
    private var at = -1
    private val main = Handler(Looper.getMainLooper())
    private var commitPending = false
    private val commit = Runnable { commitPending = false; snapshot(); onChange(RichCodec.toRich(text)) }
    /** Fields are set up after EditText's constructor, which already calls onSelectionChanged: until then, nothing runs. */
    private var ready = false

    val canUndo get() = at > 0 || commitPending
    val canRedo get() = at < history.size - 1

    /** The Cut/Copy toolbar while it shows, so a panel opening over the selection can put it away. */
    private var selectionMenu: android.view.ActionMode? = null
    fun hideSelectionMenu() { selectionMenu?.finish() }

    init {
        customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean { selectionMenu = mode; return true }
            override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu) = false
            override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem) = false
            override fun onDestroyActionMode(mode: android.view.ActionMode) { if (selectionMenu === mode) selectionMenu = null }
        }
        background = null
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        isVerticalScrollBarEnabled = false
        includeFontPadding = false
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
                typingNow = true
                if (busy) return
                // deleting lines (or a selection) that leaves its line empty takes the list or block with them: select all and
                // delete, and no lone bullet is left behind. One backspace emptying an item keeps its marker, as in any editor
                clearedAt = if (after == 0 && count > 0 && (count > 1 || s[start] == '\n')) start else -1
            }
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (busy) return
                newlineAt = if (count == 1 && before == 0 && s[start] == '\n') start else -1
                insertAt = if (count > 0) start else -1; insertLen = count
            }
            override fun afterTextChanged(e: Editable) {
                if (busy) { typingNow = false; return }
                busy = true
                try {
                    // the editor's own last line break back first: Select all and delete takes it too, and the empty line
                    // must exist for its list or block to be found and taken off below
                    keepTrailingLine()
                    if (clearedAt in 0 until e.length && paragraphStart(e, clearedAt) == clearedAt && paragraphEnd(e, clearedAt) == clearedAt) {
                        setPara(e, clearedAt, ListKind.NONE, 0); setBlock(e, clearedAt, clearedAt, null)
                    }
                    if (newlineAt >= 0 && endListOnEmptyEnter(e, newlineAt)) setSelection(newlineAt.coerceAtMost(e.length))
                    else if (insertAt >= 0 && pending.isNotEmpty()) pending.forEach { (k, v) -> applyInline(e, insertAt, (insertAt + insertLen).coerceAtMost(e.length), k, v) }
                    if (insertAt >= 0 && insertLen > 0) {
                        val n = e.length
                        val end = separateFromObjects(e, insertAt, (insertAt + insertLen).coerceAtMost(n))
                        if (e.length != n) setSelection(end.coerceAtMost(e.length))
                    }
                    keepTrailingLine() // again, after the edits above; the last paragraph's format covers that line break
                    normalizeParagraphs(e)
                    refreshDone(e, fadedInk)
                } finally { busy = false; newlineAt = -1; clearedAt = -1; insertAt = -1; typingNow = false }
                changed(typing = true)
                keepCursorInView()
            }
        })
        ready = true
    }

    /** Shows [rt] (a note opened): history starts fresh from it. */
    fun load(rt: RichText) {
        busy = true
        RichCodec.load(text, rt, ::loadImage, ::pageWidth)
        refreshDone(text, fadedInk)
        busy = false
        setSelection(0)
        history.clear(); at = -1; snapshot()
        version++
    }

    // ---- formatting, called by the format controls

    fun inline(k: Inline): Any? = pending[k] ?: inlineValue(text, selS.coerceAtMost(length()), selE.coerceAtMost(length()), k)

    /** The remembered selection, kept within the text. */
    /**
     * The selection the controls act on, never on the editor's own last line (RichCodec): a cursor that lands there for
     * a moment (a tap into an empty note) once put a list on that hidden line, drawn beside the text but out of reach.
     */
    private fun sel(): Pair<Int, Int> { val last = (length() - 1).coerceAtLeast(0); return selS.coerceIn(0, last) to selE.coerceIn(0, last) }

    /** Sets [k] on the selection; with nothing selected, on what's typed next. Toggles take [v] = !current. */
    fun setInline(k: Inline, v: Any) {
        val (s, e) = sel()
        if (s == e) { pending[k] = v; version++; onState(); return }
        edit { applyInline(it, s, e, k, v) }
    }

    fun style(st: TextStyle) {
        val (s, e) = sel()
        if (paragraphStart(text, s) == paragraphEnd(text, e)) { pending[Inline.SIZE] = st.size; pending[Inline.WEIGHT] = st.weight; version++; onState(); return }
        edit { applyStyle(it, s, e, st) }
    }

    /** Clears every inline style from the selection (the paragraph's list and alignment stay). */
    fun clearFormatting() {
        val (s, e) = sel()
        pending.clear()
        if (s == e) { version++; onState(); return }
        edit { t -> Inline.entries.forEach { applyInline(t, s, e, it, defaultOf(it)) } }
    }

    fun list(kind: ListKind) { val (s, e) = sel(); edit { toggleList(it, s, e, kind) } }
    fun indentBy(delta: Int) { val (s, e) = sel(); edit { indent(it, s, e, delta) } }
    fun align(a: android.text.Layout.Alignment) { val (s, e) = sel(); edit { setAlign(it, s, e, a) } }
    fun listAt(): ParaSpan? = paraAt(text, paragraphStart(text, sel().first))
    fun alignment() = alignAt(text, sel().first)
    fun block(kind: BlockKind) { val (s, e) = sel(); edit { toggleBlock(it, s, e, kind) } }
    fun blockAt(): BlockKind? = blockAt(text, sel().first)

    /** A dividing line on a line of its own at the cursor, with a line after it to carry on writing. */
    fun insertDivider() {
        val (s, e) = sel()
        edit { t ->
            t.delete(s, e)
            val before = if (s > 0 && t[s - 1] != '\n') "\n" else ""
            // always a line after it to carry on writing on, never beside the object
            t.insert(s, "$before$IMAGE_CHAR\n")
            val at = s + before.length
            t.setSpan(DividerSpan(::pageWidth), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            selS = (at + 2).coerceAtMost(t.length - 1); selE = selS
            setSelection(selS)
        }
    }

    /**
     * [rt] at the cursor (in place of a selection) on lines of its own, its lists with it, as one undo step, the cursor
     * at its end: scanned text. Mid-line, the line is broken around it rather than the scan running into the words
     * beside it; its lines are plain or list items as scanned, whatever list the cursor was in.
     */
    fun insertLines(rt: RichText) {
        val (a, b) = sel()
        edit { t ->
            val before = if (a > 0 && t[a - 1] != '\n') "\n" else ""
            val after = if (b < t.length - 1 && t[b] != '\n') "\n" else "" // (the last character is the hidden line break)
            t.replace(a, b, before + rt.text + after)
            normalizeParagraphs(t) // the list item it went into covers the new lines now: one span per paragraph first
            val at = a + before.length
            var p = 0
            for (line in rt.text.split('\n')) {
                val item = rt.spans.firstOrNull { it.type == SpanType.PARA && it.start == p }
                val kind = ListKind.of(item?.value)
                setPara(t, at + p, kind, item?.level ?: 0, item?.checked == true)
                p += line.length + 1
            }
            selS = (at + rt.text.length).coerceAtMost(t.length - 1); selE = selS; setSelection(selS)
        }
    }

    /** The trailing line break (see RichCodec) back after an edit, without moving the cursor onto it. */
    private fun keepTrailingLine() {
        val a = selectionStart; val b = selectionEnd
        if (RichCodec.keepTrailingLine(text) && a >= 0) setSelection(a.coerceAtMost(length() - 1), b.coerceAtMost(length() - 1))
    }

    /** Runs a formatting change as one step: one undo, saved at once. */
    private fun edit(f: (Editable) -> Unit) {
        busy = true
        try { f(text); keepTrailingLine(); normalizeParagraphs(text); refreshDone(text, fadedInk) } finally { busy = false }
        // the selection stays chosen for the next control (while busy, moves from the edit itself were kept in selS/selE)
        val (s, e) = sel(); selS = s; selE = e
        if (!isFocused || selectionStart != s || selectionEnd != e) setSelection(s, e)
        changed(typing = false)
    }

    private fun changed(typing: Boolean) {
        version++
        main.removeCallbacks(commit)
        // typing is saved (and becomes an undo step) once it pauses; a formatting change at once
        if (typing) { commitPending = true; main.postDelayed(commit, 300) } else commit.run()
        onState()
    }

    // ---- undo / redo: whole snapshots of the note, small enough for any note people write by hand

    private fun snapshot() {
        val rt = RichCodec.toRich(text)
        if (at >= 0 && history[at].text == rt) return
        while (history.size > at + 1) history.removeAt(history.size - 1)
        history += Snapshot(rt, selectionStart, selectionEnd)
        // at most 200 steps, and at most ~2 million characters of snapshots all told: each is the whole note, so a huge
        // note keeps fewer steps rather than running the phone out of memory (ponytail: diff-based if that ever matters)
        while (history.size > 200 || (history.size > 1 && history.sumOf { it.text.text.length } > MAX_HISTORY_CHARS)) history.removeAt(0)
        at = history.size - 1
    }

    fun undo() {
        if (commitPending) { main.removeCallbacks(commit); commitPending = false; snapshot() }
        if (at > 0) restore(history[--at])
    }

    fun redo() { if (at < history.size - 1) restore(history[++at]) }

    private fun restore(s: Snapshot) {
        val was = text.toString()
        busy = true
        RichCodec.load(text, s.text, ::loadImage, ::pageWidth)
        refreshDone(text, fadedInk)
        busy = false
        val now = text.toString()
        if (now == was) { selS = s.selStart.coerceIn(0, length()); selE = s.selEnd.coerceIn(0, length()) }
        else { selS = changedUpTo(was, now).coerceAtMost(length() - 1); selE = selS } // where the undone change was
        setSelection(selS, selE)
        version++
        onChange(s.text); onState()
    }

    // ---- typing details

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!ready) return
        if (isFocused) keepCursorInView() // typing moves the cursor: it stays where it can be seen
        // the editor's own last line break (RichCodec) isn't the note's: the cursor never goes past it
        val last = length() - 1
        if (!busy && last >= 0 && (selStart > last || selEnd > last)) { main.post { if (length() > 0) setSelection(minOf(selectionStart, length() - 1).coerceAtLeast(0), minOf(selectionEnd, length() - 1).coerceAtLeast(0)) }; return }
        if (holdSelection && !busy && !typingNow) {
            // a collapse from outside (the toolbar closing): put the held selection back once Android is done with it
            if (minOf(selStart, selEnd) != selS || maxOf(selStart, selEnd) != selE) main.post { if (holdSelection) sel().let { (a, b) -> setSelection(a, b) } }
            return
        }
        if (!busy && !typingNow && !touchedText && selS != selE && selStart == selEnd) lostRange = Triple(selS, selE, android.os.SystemClock.uptimeMillis())
        touchedText = false
        if (isFocused || busy || typingNow) { selS = minOf(selStart, selEnd).coerceAtLeast(0); selE = maxOf(selStart, selEnd).coerceAtLeast(0) }
        else return // focus going elsewhere collapsed it: keep the one the controls act on
        if (!busy && !typingNow) pending.clear() // a style picked for the cursor applies only where it was picked
        version++; onState()
        main.post { matchKeysToLine() } // after the edit or tap that moved it has finished
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        return object : InputConnectionWrapper(ic, true) {
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean =
                if (beforeLength == 1 && afterLength == 0 && takeMarkerOff()) true else super.deleteSurroundingText(beforeLength, afterLength)
            override fun sendKeyEvent(event: KeyEvent): Boolean =
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL && takeMarkerOff()) true else super.sendKeyEvent(event)
        }
    }

    private fun inCode() = selectionStart >= 0 && blockAt(text, selectionStart) == BlockKind.CODE

    /**
     * In a code line the keyboard neither capitalises nor corrects ("val" stays "val"): the input type changes as the
     * cursor goes in or out of one, and the keyboard is told to read it again.
     */
    private var codeKeys = false
    private fun matchKeysToLine() {
        val code = inCode()
        if (code == codeKeys) return
        codeKeys = code
        setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            if (code) InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS else InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        if (isFocused) context.getSystemService(android.view.inputmethod.InputMethodManager::class.java).restartInput(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        if (keyCode == KeyEvent.KEYCODE_DEL && takeMarkerOff()) true else super.onKeyDown(keyCode, event)

    private fun takeMarkerOff(): Boolean {
        val s = selectionStart
        if (s < 0 || s != selectionEnd) return false
        var done = false
        edit { done = backspaceAtParagraphStart(it, s) }
        return done
    }

    // a tap on a checkbox ticks it (and doesn't move the cursor)
    private var downOnCheck = -1
    private var downX = 0f; private var downY = 0f; private var downAt = 0L
    private var scrolled = false // this touch moved before a long press: it's a scroll, never a tap that opens the keyboard
    private val slop get() = android.view.ViewConfiguration.get(context).scaledTouchSlop
    /** Room the Pill takes at the bottom: the cursor is kept above it when the text scrolls to it. */
    var bottomGap = 0

    /** The page's width (dividers span it). */
    private fun pageWidth() = (width - totalPaddingLeft - totalPaddingRight).takeIf { it > 0 } ?: (resources.displayMetrics.widthPixels - (40 * resources.displayMetrics.density).toInt())

    private var cursorCheck: android.view.ViewTreeObserver.OnPreDrawListener? = null

    /**
     * Scrolls the note so the cursor's line shows above the keyboard and the Pill. Done just before the next frame is
     * drawn, once the text has been laid out again: asked straight away (as Android does), a line typed at the bottom
     * isn't part of the page's height yet, the scroll stops short, and the cursor stays hidden behind the keyboard.
     */
    fun keepCursorInView() {
        if (cursorCheck != null) return
        val check = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                viewTreeObserver.removeOnPreDrawListener(this); cursorCheck = null
                val sv = parent as? android.widget.ScrollView ?: return true
                val l = layout ?: return true
                val line = l.getLineForOffset(selectionEnd.coerceAtLeast(0))
                val top = extendedPaddingTop + l.getLineTop(line)
                val bottom = extendedPaddingTop + l.getLineBottom(line)
                val shown = sv.height - bottomGap // the part of the page not under the Pill
                when {
                    bottom > sv.scrollY + shown -> { sv.scrollTo(0, bottom - shown); return false } // drawn again, scrolled
                    top < sv.scrollY -> { sv.scrollTo(0, top); return false }
                }
                return true
            }
        }
        cursorCheck = check
        viewTreeObserver.addOnPreDrawListener(check)
        invalidate()
    }

    override fun requestRectangleOnScreen(rectangle: android.graphics.Rect, immediate: Boolean): Boolean =
        super.requestRectangleOnScreen(android.graphics.Rect(rectangle).apply { bottom += bottomGap }, immediate) // above the Pill

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; downAt = event.eventTime; scrolled = false
                downOnCheck = checkAt(event.x, event.y); if (downOnCheck >= 0) return true
                holdSelection = false; touchedText = true; lostRange = null; onTouchText()
            }
            MotionEvent.ACTION_MOVE -> {
                // moved before the long-press time: a scroll (or the start of one), never a tap
                if (!scrolled && kotlin.math.hypot(event.x - downX, event.y - downY) > slop &&
                    event.eventTime - downAt < android.view.ViewConfiguration.getLongPressTimeout()) { scrolled = true; downOnCheck = -1 }
                if (downOnCheck >= 0) return true
            }
            MotionEvent.ACTION_UP -> {
                if (downOnCheck >= 0) {
                    val p = downOnCheck; downOnCheck = -1
                    if (checkAt(event.x, event.y) == p) edit { toggleCheck(it, p, checkedToBottom) }
                    return true
                }
                // a scroll that ended here: let the text forget the touch, so no cursor is placed and no keyboard opens
                if (scrolled) { val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }; super.onTouchEvent(cancel); cancel.recycle(); return true }
            }
            MotionEvent.ACTION_CANCEL -> { downOnCheck = -1 }
        }
        return super.onTouchEvent(event)
    }

    /** The start of the checklist item whose box is at ([x], [y]), or -1. */
    private fun checkAt(x: Float, y: Float): Int {
        val l = layout ?: return -1
        val line = l.getLineForVertical((y - totalPaddingTop + scrollY).toInt())
        val start = l.getLineStart(line)
        if (paragraphStart(text, start) != start) return -1
        val p = paraAt(text, start)?.takeIf { it.kind == ListKind.CHECK } ?: return -1
        val lx = x - totalPaddingLeft + scrollX
        val rtl = l.getParagraphDirection(line) < 0
        val w = p.hitWidth() + 8 * resources.displayMetrics.density
        return if ((!rtl && lx <= w && lx >= p.level * ParaSpan.Look.indent - 8 * resources.displayMetrics.density) || (rtl && lx >= l.width - w)) start else -1
    }

    // ---- pictures: scaled to the editor's width, rounded off by the note's corners

    /**
     * Picture [name] scaled to [widthPct] percent of the editor's width (0: all of it). Its size comes from the file's
     * header at once (cheap), so the text lays out right away; the picture itself is decoded off the main thread and
     * drawn in when it's ready, so opening a note with photos doesn't stall.
     */
    private fun loadImage(name: String, widthPct: Int): Drawable? {
        val f = NoteImages.file(context, name)
        if (!f.exists()) return null
        val maxW = ((width - totalPaddingLeft - totalPaddingRight).takeIf { it > 0 } ?: (resources.displayMetrics.widthPixels - 40 * resources.displayMetrics.density).toInt())
        val header = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, header)
        if (header.outWidth <= 0 || header.outHeight <= 0) return null
        val w = (if (widthPct in 1..99) maxW * widthPct / 100 else maxW).coerceAtLeast(1)
        var sample = 1
        while (header.outWidth / (sample * 2) >= w) sample *= 2
        val late = LateDrawable().apply { setBounds(0, 0, w, (header.outHeight * w.toFloat() / header.outWidth).toInt()) }
        decoder.execute {
            val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return@execute
            main.post { late.inner = BitmapDrawable(resources, bmp).apply { bounds = late.bounds }; invalidate() }
        }
        return late
    }

    // ---- screen readers can't aim at a box: ticking the checklist item at the cursor is an action on the editor

    /** The checklist item the cursor is in (where it starts, and whether it's ticked), or null. */
    private fun checkItemAtCursor(): Pair<Int, Boolean>? {
        val p = paragraphStart(text, sel().first)
        return paraAt(text, p)?.takeIf { it.kind == ListKind.CHECK }?.let { p to it.checked }
    }

    /** Ticks or unticks the checklist item the cursor is in, as tapping its box does. False when it isn't in one. */
    fun toggleCheckAtCursor(): Boolean {
        val (p, wasTicked) = checkItemAtCursor() ?: return false
        edit { toggleCheck(it, p, checkedToBottom) }
        @Suppress("DEPRECATION") announceForAccessibility(if (wasTicked) "Unticked" else "Ticked")
        return true
    }

    override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        checkItemAtCursor()?.let { (_, ticked) ->
            info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(com.jlees.minotes.R.id.tick_item, if (ticked) "Untick item" else "Tick item"))
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean =
        if (action == com.jlees.minotes.R.id.tick_item) toggleCheckAtCursor() else super.performAccessibilityAction(action, arguments)

    /** Hands over anything typed in the last moment (the 300 ms before a pause commits it): the app or the note is closing. */
    fun flush() { if (commitPending) { main.removeCallbacks(commit); commit.run() } }

    override fun onDetachedFromWindow() { flush(); super.onDetachedFromWindow() }
}

/** The end, in [now], of the part that differs from [was]: where the cursor goes after an undo or redo. */
internal fun changedUpTo(was: String, now: String): Int {
    val pre = was.commonPrefixWith(now).length
    val suf = minOf(was.length, now.length) - pre
    var k = 0
    while (k < suf && was[was.length - 1 - k] == now[now.length - 1 - k]) k++
    return now.length - k
}

/** A picture's place, sized at once, that draws the picture once it's been decoded ([inner]). */
private class LateDrawable : Drawable() {
    var inner: Drawable? = null
    override fun draw(canvas: android.graphics.Canvas) { inner?.draw(canvas) }
    override fun setAlpha(alpha: Int) { inner?.alpha = alpha }
    override fun setColorFilter(filter: android.graphics.ColorFilter?) { inner?.colorFilter = filter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

/** The undo history's size cap, in characters across all its snapshots. */
private const val MAX_HISTORY_CHARS = 2_000_000

/** Pictures in older notes are decoded here, one at a time, off the main thread. */
private val decoder = java.util.concurrent.Executors.newSingleThreadExecutor()
