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
| `onboarding/` | the hands-on gesture walkthrough |
| `update/` | GitHub release checks and installs (Updater, Updates), What's New after an update (WhatsNew) |
| `scan/` | scanning text from a photo (the camera app) or a picture (Files) with ML Kit's bundled, on-device recognizer (Scan, three passes with mi. Budget's ImageEnhance); the mark before each line read from the picture (ScanMarks, pure) and the lines read back into lists, checklists and table rows (ScanLayout, pure) |

Shell packages: `data/` (Settings, JsonStore/Saver, Backup), `logic/` (accent colours), `ui/` (App,
OverlaySheet, Colors, NoteMenu, Dialogs, `ui/settings/` one file per section, `ui/common/` widgets).

## 2. The note format is the contract
- A note body is `RichText(text, spans)`: plain text plus ranges, each with one `SpanType`. Paragraph kinds (`PARA`,
  `ALIGN`) cover whole paragraphs; `BLOCK` (quote, code, callout) is a paragraph kind too; a divider is one `\uFFFC` on its own line with a `DIVIDER` span. `IMAGE` spans
  (a file in `images/`) still load from older notes; new pictures can't be added.
- In the editor a paragraph format's span covers the paragraph *and its line break* (`PARA_FLAGS`, `paraSpanEnd`), so
  it's never empty: an empty span on an empty line gets carried off it by the on-screen keyboard. It's saved without
  the line break (`RichCodec.toRich`), so the saved format is unchanged.
- The editor's text always carries one extra trailing line break that isn't saved (`RichCodec`): Android draws no
  list marker on an empty last line. `RichCodec.load` adds it, `toRich` strips it, `keepTrailingLine` restores it,
  and the cursor is kept off it. Never save `editor.text` without going through `toRich`.
- Change it only by adding: a new `SpanType` or a new field with a default. Older apps ignore unknown keys, and an
  unknown span type reads as `UNKNOWN` and is skipped (`Span.type` defaults to it, and Json has `coerceInputValues`),
  so backups stay readable both ways (`FormatCompatTest`).
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
The only network use is `update/` (GitHub releases and release notes); see PRIVACY.md, and keep it true. ML Kit
(`scan/`) runs on the phone; its usage reporting (the `datatransport` services and `ACCESS_NETWORK_STATE`) is removed
in the manifest: keep it removed, and check the merged manifest when ML Kit is updated. Notes leave
the phone only when the user shares or exports them.

## Project rules
- The in-app logo is drawn in the text colour; the app's yellow (`#FFCC00`) is the launcher icon's and the default
  accent.
- Versioning: MAJOR massive, MINOR significant features, PATCH fixes/QoL.
- Releases are one APK, `mi-notes-<version>-release.apk`, for 64-bit phones only (`arm64-v8a`): ML Kit's native code is
  per CPU type, and 32-bit phones are old budget models. Its native code is compressed in the APK (`useLegacyPackaging`).
- Test on the `mi_notes_test` emulator only when needed, and shut it down afterwards; never the user's phone.
- Commit or push only when asked. Never commit `keystore.properties` or `*.jks`.
