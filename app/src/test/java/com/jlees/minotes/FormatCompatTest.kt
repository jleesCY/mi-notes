package com.jlees.minotes

import com.jlees.minotes.data.json
import com.jlees.minotes.notes.RichText
import org.junit.Assert.assertEquals
import org.junit.Test

/** The saved format reads both ways: a note from a newer app, with a kind of span this one doesn't know, still opens. */
class FormatCompatTest {
    @Test fun anUnknownKindOfSpanIsSkippedNotFatal() {
        val rt = json.decodeFromString(RichText.serializer(), """{"text":"ab","spans":[{"start":0,"end":1,"type":"from-the-future"},{"start":1,"end":2,"type":"italic"}]}""")
        assertEquals("ab", rt.text)
        assertEquals(listOf("italic"), rt.spans.filter { it.type != com.jlees.minotes.notes.SpanType.UNKNOWN }.map { it.type.name.lowercase() })
    }
}

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class OrphanPlaceholderTest {
    @Test fun aPlaceholderWithNothingToDrawGoesWithItsLine() {
        // "table" is a kind this version doesn't make: it reads as UNKNOWN, and its placeholder shouldn't show as "OBJ"
        val rt = json.decodeFromString(RichText.serializer(), """{"text":"a\n￼\nb","spans":[{"start":2,"end":3,"type":"table","value":"[]"}]}""")
        val t = android.text.SpannableStringBuilder()
        com.jlees.minotes.editor.RichCodec.load(t, rt, { _, _ -> null })
        assertEquals("a\nb", com.jlees.minotes.editor.RichCodec.toRich(t).text)
    }
}
