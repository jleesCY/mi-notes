// What a note is, as saved: plain JSON (notes.json, and the same inside a backup), readable by anything. A note's body
// is its text plus a list of spans, each a range of that text with one kind of formatting, so nothing about the
// format depends on Android: Markdown export, import and future apps read it the same way.
package com.jlees.minotes.notes

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The character that stands in the text for an image (Unicode's object replacement character). */
const val IMAGE_CHAR = '￼'

/** A note's colour (its card and its page). NONE follows the theme. */
@Serializable enum class NoteColor(val label: String, val argb: Long) {
    @SerialName("none") NONE("Default", 0),
    @SerialName("red") RED("Red", 0xFFFF6B6B),
    @SerialName("orange") ORANGE("Orange", 0xFFFFA94D),
    @SerialName("yellow") YELLOW("Yellow", 0xFFFFD43B),
    @SerialName("green") GREEN("Green", 0xFF69DB7C),
    @SerialName("teal") TEAL("Teal", 0xFF38D9A9),
    @SerialName("blue") BLUE("Blue", 0xFF4DABF7),
    @SerialName("purple") PURPLE("Purple", 0xFFB197FC),
    @SerialName("pink") PINK("Pink", 0xFFF783AC),
    @SerialName("gray") GRAY("Gray", 0xFFADB5BD),
}

/** The kinds of formatting a span can carry. Paragraph kinds ([PARA], [ALIGN]) always cover whole paragraphs. */
@Serializable enum class SpanType {
    /** Font weight, 100–1000 in [Span.value] (400 is regular, 700 bold). */
    @SerialName("weight") WEIGHT,
    @SerialName("italic") ITALIC,
    @SerialName("underline") UNDERLINE,
    @SerialName("strike") STRIKE,
    /** Text colour, "#RRGGBB". */
    @SerialName("color") COLOR,
    /** Highlight (background) colour, "#RRGGBB". */
    @SerialName("highlight") HIGHLIGHT,
    /** Text size as a multiple of the body size ("1.5"). */
    @SerialName("size") SIZE,
    /** Typeface: one of [TextFont]'s ids. */
    @SerialName("font") FONT,
    /** A paragraph's list kind ([ListKind] id in value), its indent [Span.level] and, for a checklist, [Span.checked]. */
    @SerialName("para") PARA,
    /** A paragraph's alignment: "center" or "end". */
    @SerialName("align") ALIGN,
    /** An image: its file name in the note's images (value), on one [IMAGE_CHAR]. */
    @SerialName("image") IMAGE,
}

@Serializable enum class ListKind { @SerialName("none") NONE, @SerialName("bullet") BULLET, @SerialName("number") NUMBER, @SerialName("check") CHECK }

@Serializable enum class TextFont(val label: String) {
    @SerialName("sans") SANS("Sans"), @SerialName("rounded") ROUNDED("Rounded"), @SerialName("serif") SERIF("Serif"), @SerialName("mono") MONO("Mono")
}

@Serializable
data class Span(
    val start: Int,
    val end: Int,
    val type: SpanType,
    val value: String = "",
    val level: Int = 0,
    val checked: Boolean = false,
)

@Serializable
data class RichText(val text: String = "", val spans: List<Span> = emptyList()) {
    /** The text without image placeholders (search, previews, sharing as text). */
    val plain: String get() = text.replace(IMAGE_CHAR.toString(), "")
    val images: List<String> get() = spans.filter { it.type == SpanType.IMAGE }.map { it.value }
}

@Serializable
data class Note(
    val id: String,
    val title: String = "",
    val body: RichText = RichText(),
    /** The folder it's in (a [Folder.id]); null: in no folder (it shows under All Notes only). */
    val folder: String? = null,
    val pinned: Boolean = false,
    val color: NoteColor = NoteColor.NONE,
    val created: Long = 0,
    val edited: Long = 0,
    /** When it went to Recently Deleted; null while it's a live note. */
    val deletedAt: Long? = null,
) {
    val isEmpty: Boolean get() = title.isBlank() && body.text.isBlank()
}

@Serializable
data class Folder(val id: String, val name: String, val color: NoteColor = NoteColor.NONE)
