# mi. Notes: code standard

Kotlin, Jetpack Compose (Material 3 Expressive). Code is in `app/src/main/java/com/jlees/minotes/`. It follows the
mi. brand standard (the `mi-brand` skill) and mi. Browser's code standard; the shared pieces in `ui/common/`,
`ui/OverlaySheet.kt`, `ui/settings/SettingsKit.kt` and `data/Store.kt` are copied from mi-browser until they move to a
shared `mi-design` library. Fix them in both places.

## 1. A feature is one package

| Package | Owns |
|---|---|
| `notes/` | the saved format (`Note.kt`: Note, Folder, RichText, Span), NotesStore, NotesRules (filtering, sorting, previews, trash), Markdown export/import, NoteImages, ShareNote |
| `editor/` | the rich text editor: Spans (one span class per saved format kind), RichCodec (editor text ⇄ RichText), Formatting (pure-ish Editable operations), NoteEditText (lists, checkboxes, backspace, pending styles, undo/redo), EditorScreen, FormatPill (the Pill and the Aa panel) |
| `home/` | HomeScreen (header, folder chips, grid/list, drag to rearrange), NoteCard (cards, rows, the tap/hold/drag gesture), HomePill |
| `trash/` | Recently Deleted |
| `onboarding/` | the gesture walkthrough |

Shell packages: `data/` (Settings, JsonStore/Saver, Backup), `logic/` (accent colours, list helpers), `ui/` (App,
OverlaySheet, Colors, NoteMenu, Dialogs, `ui/settings/` one file per section, `ui/common/` widgets).

## 2. The note format is the contract
- A note body is `RichText(text, spans)`: plain text plus ranges, each with one `SpanType`. Paragraph kinds (`PARA`,
  `ALIGN`) cover whole paragraphs; an image is one `￼` with an `IMAGE` span naming its file in `images/`.
- Change it only by adding: a new `SpanType` or a new field with a default. Older apps ignore unknown keys and
  values (Json has `ignoreUnknownKeys` and `coerceInputValues`), so backups stay readable both ways.
- A new formatting kind needs: a `SpanType`, a span class in `Spans.kt`, both directions in `RichCodec`, the
  `Inline` enum in `Formatting.kt` if inline, Markdown export/import, and a codec round-trip test.

## 3. State lives in a store; decisions are pure functions
- `NotesStore` owns notes and folders (Compose state, `private set`, named methods, debounced `savers`).
- `NotesViewModel` holds stores and app-wide UI state (open note, sheet, dialog, menu) and coordinates (Undo).
- Rules are plain functions (`NotesRules.kt`, `Markdown.kt`) with JVM tests; editor operations on `Editable` are
  tested with Robolectric (`FormattingTest`). A test for a bug must fail with the bug put back.
- Run `./gradlew :app:testDebugUnitTest` before calling anything done.

## 4. Settings
One file per section in `ui/settings/`, each `fun xSection(vm): SettingsSection`, listed in `SettingsView` and under a
heading there. New fields go in `data/Settings.kt` with a default; defaults change only through
`Settings.migrated()`, with a test.

## 5. Privacy
No INTERNET permission. Notes leave the phone only when the user shares or exports them.

## Project rules
- The in-app logo is drawn in the text colour; the app's yellow (`#FFCC00`) is the launcher icon's and the default
  accent.
- Versioning: MAJOR massive, MINOR significant features, PATCH fixes/QoL.
- Test on the `mi_notes_test` emulator only when needed, and shut it down afterwards; never the user's phone.
- Commit or push only when asked. Never commit `keystore.properties` or `*.jks`.
