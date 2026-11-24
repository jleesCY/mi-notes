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

@SuppressLint("AppCompatCustomView", "ViewConstructor")
class NoteEditText(ctx: Context) : EditText(ctx) {
    /** The note changed (typed, formatted, undone): its new body. Called at most every 300ms while typing. */
    var onChange: (RichText) -> Unit = {}
    /** The cursor or selection moved, or a style changed: the format controls re-read what's under it. */
    var onState: () -> Unit = {}
    /** The text was touched (not a checkbox): the format panel gives way to the keyboard. */
    var onTouchText: () -> Unit = {}
    /** Ticked items move below the rest of their checklist (Settings › Notes › Checked Items to Bottom). */
    var checkedToBottom = false
    /** The colour of ticked items' text. */
    var fadedInk = 0x80FFFFFF.toInt()
    /** Bumped on every change, so Compose can re-read the format state. */
    var version = 0; private set

    /** Styles picked with nothing selected: they apply to the next text typed at the cursor. */
    private val pending = HashMap<Inline, Any>()
    private var busy = false // the editor is changing its own text: don't treat it as typing
    private var typingNow = false // between a change's before and after: the cursor moving with the text isn't a jump
    private var newlineAt = -1
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

    init {
        background = null
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        isVerticalScrollBarEnabled = false
        includeFontPadding = false
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) { typingNow = true }
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (busy) return
                newlineAt = if (count == 1 && before == 0 && s[start] == '\n') start else -1
                insertAt = if (count > 0) start else -1; insertLen = count
            }
            override fun afterTextChanged(e: Editable) {
                if (busy) { typingNow = false; return }
                busy = true
                try {
                    if (newlineAt >= 0 && endListOnEmptyEnter(e, newlineAt)) setSelection(newlineAt.coerceAtMost(e.length))
                    else if (insertAt >= 0 && pending.isNotEmpty()) pending.forEach { (k, v) -> applyInline(e, insertAt, (insertAt + insertLen).coerceAtMost(e.length), k, v) }
                    normalizeParagraphs(e)
                    refreshDone(e, fadedInk)
                } finally { busy = false; newlineAt = -1; insertAt = -1; typingNow = false }
                changed(typing = true)
            }
        })
        ready = true
    }

    /** Shows [rt] (a note opened): history starts fresh from it. */
    fun load(rt: RichText) {
        busy = true
        RichCodec.load(text, rt, ::loadImage)
        refreshDone(text, fadedInk)
        busy = false
        history.clear(); at = -1; snapshot()
        version++
    }

    // ---- formatting, called by the format controls

    fun inline(k: Inline): Any? = pending[k] ?: inlineValue(text, selectionStart.coerceAtLeast(0), selectionEnd.coerceAtLeast(0), k)

    /** Sets [k] on the selection; with nothing selected, on what's typed next. Toggles take [v] = !current. */
    fun setInline(k: Inline, v: Any) {
        val s = selectionStart; val e = selectionEnd
        if (s < 0) return
        if (s == e) { pending[k] = v; version++; onState(); return }
        edit { applyInline(it, s, e, k, v) }
    }

    fun style(st: TextStyle) {
        val s = selectionStart.coerceAtLeast(0); val e = selectionEnd.coerceAtLeast(0)
        if (paragraphStart(text, s) == paragraphEnd(text, e)) { pending[Inline.SIZE] = st.size; pending[Inline.WEIGHT] = st.weight; version++; onState(); return }
        edit { applyStyle(it, s, e, st) }
    }

    /** Clears every inline style from the selection (the paragraph's list and alignment stay). */
    fun clearFormatting() {
        val s = selectionStart; val e = selectionEnd
        pending.clear()
        if (s < 0 || s == e) { version++; onState(); return }
        edit { t -> Inline.entries.forEach { applyInline(t, s, e, it, defaultOf(it)) } }
    }

    fun list(kind: ListKind) = edit { toggleList(it, selectionStart.coerceAtLeast(0), selectionEnd.coerceAtLeast(0), kind) }
    fun indentBy(delta: Int) = edit { indent(it, selectionStart.coerceAtLeast(0), selectionEnd.coerceAtLeast(0), delta) }
    fun align(a: android.text.Layout.Alignment) = edit { setAlign(it, selectionStart.coerceAtLeast(0), selectionEnd.coerceAtLeast(0), a) }
    fun listAt(): ParaSpan? = paraAt(text, paragraphStart(text, selectionStart.coerceAtLeast(0)))
    fun alignment() = alignAt(text, selectionStart.coerceAtLeast(0))

    /** Puts a picture on its own line at the cursor. */
    fun insertImage(name: String) {
        val d = loadImage(name) ?: return
        val s = selectionStart.coerceAtLeast(0); val e = selectionEnd.coerceAtLeast(0)
        edit { t ->
            t.delete(s, e)
            val before = if (s > 0 && t[s - 1] != '\n') "\n" else ""
            val after = if (s >= t.length || t[s] != '\n') "\n" else ""
            t.insert(s, "$before$IMAGE_CHAR$after")
            val at = s + before.length
            t.setSpan(NoteImageSpan(name, d), at, at + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSelection((at + 1 + after.length).coerceAtMost(t.length))
        }
    }

    /** Runs a formatting change as one step: one undo, saved at once. */
    private fun edit(f: (Editable) -> Unit) {
        val selS = selectionStart; val selE = selectionEnd
        busy = true
        try { f(text); normalizeParagraphs(text); refreshDone(text, fadedInk) } finally { busy = false }
        if (selS >= 0 && selectionStart < 0) setSelection(selS.coerceAtMost(length()), selE.coerceAtMost(length()))
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
        if (history.size > 200) history.removeAt(0) // ponytail: 200 snapshots of the whole note; diff-based if notes get huge
        at = history.size - 1
    }

    fun undo() {
        if (commitPending) { main.removeCallbacks(commit); commitPending = false; snapshot() }
        if (at > 0) restore(history[--at])
    }

    fun redo() { if (at < history.size - 1) restore(history[++at]) }

    private fun restore(s: Snapshot) {
        busy = true
        RichCodec.load(text, s.text, ::loadImage)
        refreshDone(text, fadedInk)
        busy = false
        setSelection(s.selStart.coerceIn(0, length()), s.selEnd.coerceIn(0, length()))
        version++
        onChange(s.text); onState()
    }

    // ---- typing details

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (!ready) return
        if (!busy && !typingNow) pending.clear() // a style picked for the cursor applies only where it was picked
        version++; onState()
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downOnCheck = checkAt(event.x, event.y); if (downOnCheck >= 0) return true; onTouchText() }
            MotionEvent.ACTION_UP -> if (downOnCheck >= 0) {
                val p = downOnCheck; downOnCheck = -1
                if (checkAt(event.x, event.y) == p) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                    edit { toggleCheck(it, p, checkedToBottom) }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> if (downOnCheck >= 0) return true
            MotionEvent.ACTION_CANCEL -> downOnCheck = -1
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

    private fun loadImage(name: String): Drawable? {
        val f = NoteImages.file(context, name)
        if (!f.exists()) return null
        val maxW = ((width - totalPaddingLeft - totalPaddingRight).takeIf { it > 0 } ?: (resources.displayMetrics.widthPixels - 40 * resources.displayMetrics.density).toInt())
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxW) sample *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val w = minOf(maxW, bmp.width * 2).coerceAtLeast(1)
        val h = (bmp.height * w.toFloat() / bmp.width).toInt()
        return BitmapDrawable(resources, bmp).apply { setBounds(0, 0, w, h) }
    }

    override fun onDetachedFromWindow() {
        // leaving: anything typed in the last moment is saved
        if (commitPending) { main.removeCallbacks(commit); commit.run() }
        super.onDetachedFromWindow()
    }
}
