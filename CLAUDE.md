# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Xiaohe IME (ideavim-shuangpin)** — an IntelliJ Platform plugin (plugin ID `org.lymansix.ideavim-shuangpin`) implementing a Xiaohe Shuangpin (小鹤双拼) Chinese input method. Instead of registering as a traditional IME, the plugin intercepts typed characters at the raw-handler level, uses IDEA's native `LookupManager` for the candidate popup, and commits candidates by directly editing the Document. This makes it work in every text input context: editors, Git Commit, Search Everywhere, Find Dialog, Terminal, Run Configuration, Settings search, etc.

- **Target IDE**: IntelliJ IDEA 2025.3.5
- **Kotlin**: 2.3.20 (JVM target)
- **Build**: Gradle 9.x with Kotlin DSL, using the IntelliJ Platform Gradle Plugin v2.18.1
- **Plugin ID**: `org.lymansix.ideavim-shuangpin`
- **Package base**: `io.github.lymansix.ime`
- **Toolchain**: Auto-provisioned via `foojay-resolver-convention` — no pre-installed JDK required

## Common Commands

```bash
# Run the plugin in a sandboxed IDE instance (hot-reload on code changes)
./gradlew runIde

# Run all tests (none currently exist — src/test/ has not been created yet)
./gradlew check

# Run a single test class or method (once tests exist)
./gradlew test --tests "io.github.lymansix.SomeTest"
./gradlew test --tests "io.github.lymansix.SomeTest.someMethod"

# Verify plugin compatibility against target IDEs
./gradlew verifyPlugin

# Build the distributable plugin zip (outputs to build/distributions/)
./gradlew buildPlugin

# Publish to JetBrains Marketplace
./gradlew publishPlugin
```

Three preconfigured run/debug configurations live in `.run/` and appear in the IDE's run menu: `Run IDE with Plugin`, `Run Tests`, `Run Verifications`.

## Architecture

### Core design principle

> The plugin intercepts typed characters **before** they reach the Document via a wrapped `TypedAction` raw handler. Letters `a`–`z` are consumed (never written to the document) while composing; candidates are shown through `LookupManager.showLookup()`, and committing a candidate writes the Chinese word directly via `WriteCommandAction`.

This is fundamentally different from using `TypedHandlerDelegate` (which fires **after** insertion) or `CompletionContributor` (which hooks IDEA's completion popup). The plugin drives its own popup and its own insertion.

### Active package layout (`src/main/kotlin/io/github/lymansix/ime/`)

```
action/
  HandlerInstaller.kt        - AppLifecycleListener; at appFrameCreated() it wires everything:
                                 · TypedAction.setupRawHandler(ImeTypeHandler(originalRawHandler))
                                 · EditorActionManager.setActionHandler(BACKSPACE, ImeBackspaceHandler(...))
                                 · EditorActionManager.setActionHandler(ESCAPE,   ImeEscHandler(...))
  ImeTypedHandler.kt         - Wrapped TypedActionHandler. The core dispatch:
                                 · a-z (when state.enabled): state.start(editor) captures startOffset,
                                   appends char to state.composing, calls ImeLookup.show(); does NOT
                                   chain to the original handler (letter never reaches the Document).
                                 · 1-9 (while composing): commit(editor, state, index-1) and return.
                                 · Space / Enter (while composing): commit(editor, state, 0) and return.
                                 · Anything else while composing: commit first, then chain.
                                 · Not enabled (English mode) or viewer/non-writable: pass through.
  ImeBackspaceHandler.kt     - Wrapped EditorActionHandler. When composing AND buffer non-empty:
                                 deletes last char of composing buffer; if buffer now empty → reset+hide;
                                 else refresh lookup. Does NOT chain to default handler.
                                 When not composing: passes through to original handler.
  ImeEscHandler.kt           - Wrapped EditorActionHandler. Always resets state, hides lookup,
                                 then chains to original ESC (ESC always propagates).

state/
  ImeState.kt                  - Per-editor composing state; stored via Editor.putUserData (Key "IME_STATE",
                                 auto-cleaned on editor disposal — no leak). Fields:
                                 · composing: StringBuilder — the pinyin letters typed so far
                                 · startOffset: Int — caret offset when composing began (-1 when idle)
                                 Companion: ImeState.get(editor) — get-or-create via UserData.
                                 Note: Chinese/English mode gating is NOT here; handlers read
                                 ImeSettings.isChineseMode directly at the top of execute()/doExecute().

dict/
  FlyPyDict.kt                 - Loads dict from `/dict/fly.txt` (~48k lines, ~49k entries after
                                 expansion) at first access (lazy). One line per code, whitespace-
                                 separated: `<code>   <word1> [<word2>]`. The code column is space-
                                 padded to fixed width; up to 2 words per line share the same code
                                 (e.g. "anqi    按期 暗器" yields two candidates for code `anqi`).
                                 Codes are 1-4 lowercase letters (Xiaohe Yin-xing 小鹤音形 encoding).
                                 Groups entries by first letter of code for fast lookup.
                                 getCandidates() prefix-matches against entries sharing the first
                                 letter, returns up to MAX_CANDIDATES = 50. Weight heuristic: exact
                                 code match → 1000, shorter codes rank higher.
  Candidate.kt                 - data class Candidate(word, code, weight: Int = 0)  [weight is var]

lookup/
  ImeLookup.kt                 - `object` that drives the candidate popup directly.
                                 show(editor, state): calls FlyPyDict.getCandidates(state.composing),
                                   takes the first 9, wraps each in LookupElementBuilder.create(word)
                                   .withPresentableText("${i+1}. $word"), then via invokeLater:
                                   hides any active lookup and calls LookupManager.getInstance(project)
                                   .showLookup(editor, *items).
                                   NOTE: this does NOT use CompletionContributor / CompletionResult /
                                   LookupElement.handleInsert — IDEA's default selection behavior is
                                   used (number keys 1-9 select, Enter selects first, arrows navigate).
                                 hide(editor): hides the active lookup via LookupManager.

status/
  ImeStatusWidget.kt           - Contains THREE classes:
                                 · ImeStatusWidget (StatusBarWidget + TextPresentation)
                                   - getText() returns "中" / "英" based on ImeSettings.isChineseMode
                                   - getClickConsumer() toggles isChineseMode on click and refreshes
                                     the widget via statusBar.updateWidget(WIDGET_ID)
                                 · ImeStatusWidgetFactory (StatusBarWidgetFactory)
                                 · ToggleImeModeAction (AnAction, Ctrl+Alt+\ shortcut)
                                   - actionPerformed: flips settings.isChineseMode, refreshes widget
                                   - update: sets text/description based on current mode (BGT thread)

settings/
  ImeSettings.kt               - Persistent app-level settings (PersistentStateComponent, @Service(APP));
                                 stored in fly-ime-settings.xml. Fields:
                                 isChineseMode, enableSmartSwitch, showPinyinHint.
                                 Companion: ImeSettings.getInstance().

ImeBundle.kt                   - DynamicBundle wrapper for messages/ImeBundle.properties
                                 (localized strings for widget, actions, etc.)
```

### Input flow

```
User types 'a'-'z' (Chinese mode — ImeSettings.isChineseMode == true):
  → IDEA's TypedAction routes to ImeTypeHandler.execute()  (wrapped raw handler)
  → state.start(editor) captures startOffset = caretModel.offset
  → state.composing.append(c)
  → ImeLookup.show(editor, state):
      · FlyPyDict.getCandidates(code) returns up to 50 matches
      · take(9), wrap in LookupElementBuilder with "1. 啊" / "2. 爱" presentation
      · invokeLater { hide active lookup; LookupManager.showLookup(editor, *items) }
  → Return WITHOUT calling original handler — the letter NEVER lands in the Document

User types 'a'-'z' (English mode — ImeSettings.isChineseMode == false):
  → If state.composing is non-empty (user toggled to English mid-composition):
       state.reset() to abandon the stale buffer and its stale startOffset.
  → Pass through to original handler — letter lands in Document normally.

User types 1-9 while composing:
  → commit(editor, state, index-1):
      · word = FlyPyDict.getCandidates(code)[index]
      · ImeLookup.hide(editor)
      · WriteCommandAction.runWriteCommandAction:
          document.insertString(startOffset, word)
          caretModel.moveToOffset(startOffset + word.length)
      · state.reset()

User types Space / Enter while composing:
  → commit(editor, state, 0)  (selects first candidate)

User types other char (e.g. punctuation) while composing:
  → commit(editor, state, 0) first, THEN chain to original.execute(editor, char, dataContext)
     so the non-letter char is inserted into the Document normally

User presses Backspace while composing:
  → ImeBackspaceHandler.doExecute() called
  → state.composing.deleteLast()
  → if buffer empty: state.reset() + ImeLookup.hide(); does NOT call original
  → else: ImeLookup.show() refreshes candidates; does NOT call original
  → When not composing: chains to original (default backspace)

User presses ESC (always):
  → ImeEscHandler.doExecute() called
  → state.reset(); ImeLookup.hide()
  → chains to original (ESC always propagates — default handler restores focus etc.)

Toggle Chinese/English mode:
  → EITHER: Ctrl+Alt+\ triggers ToggleImeModeAction.actionPerformed()
  → OR: click status bar widget (ImeStatusWidget.getClickConsumer)
  → flips settings.isChineseMode
  → statusBar.updateWidget(ImeStatusWidget.WIDGET_ID)
  Note: the handlers read ImeSettings.isChineseMode on every keystroke. If the user
        toggled to English mid-composition, the next keystroke abandons the in-flight
        composing buffer (via state.reset()) before passing through.
```

### Plugin manifest (`plugin.xml`)

Extensions registered:
- `statusBarWidgetFactory` — `ImeStatusWidgetFactory` (id=`ImeStatusWidget`)
- `applicationService` — `ImeSettings`

Actions:
- `ToggleImeModeAction` (id `io.github.lymansix.ime.ToggleImeMode`) bound to `Ctrl+Alt+\` (`ctrl alt BACK_SLASH`)

Application listeners:
- `HandlerInstaller` registered as `AppLifecycleListener` — installs all three handler wrappers (typed, backspace, escape) at `appFrameCreated()`

Resource bundle: `messages.ImeBundle`

### Critical constraints

1. **`ImeTypeHandler` intercepts BEFORE the character reaches the Document.** For a-z while composing, it consumes the character (does not call `original.execute`). Do not refactor this to use `TypedHandlerDelegate` (which fires AFTER insertion) — the whole composing model depends on keeping pinyin letters out of the Document until commit.
2. **ImeState is per-editor**, stored via `Editor.putUserData`. When the editor is disposed, its UserData is auto-cleaned — no leak. Do not introduce a static/ConcurrentHashMap cache.
3. **Candidate popup is driven directly via `LookupManager.showLookup()`**, not via `CompletionContributor` or `ActionManager.tryToExecute(ACTION_CODE_COMPLETION)`. The popup is populated with `LookupElementBuilder` items whose `lookupString` IS the Chinese word — number keys 1–9 select automatically because of the `"${i+1}. $word"` presentable text.
4. **Toggle is driven by `ImeSettings.isChineseMode`**, read directly by `ImeTypeHandler` and `ImeBackspaceHandler` on every keystroke. Two toggle methods: status bar widget click and `Ctrl+Alt+\`. `ImeState` has no `enabled` field — it is purely composing state (`composing`, `startOffset`). If the user toggles to English mid-composition, the next keystroke resets the in-flight buffer before passing through (avoids orphaned `startOffset`).
5. **Commit happens via `WriteCommandAction.runWriteCommandAction`** in `ImeTypeHandler.commit()`. It inserts the word at `state.startOffset` and advances the caret. There is no duplicate-insertion guard because the pinyin letters were never in the Document to begin with.
6. **Backspace handler does NOT chain to default handler while composing.** The composing letters aren't in the Document, so there's nothing for default backspace to delete. Only when not composing does it pass through.
7. **ESC handler ALWAYS chains to the original**, even when not composing. This preserves default ESC semantics (close popups, cancel actions) regardless of IME state.
8. **`ImeLookup.show()` hides any existing lookup BEFORE showing a new one** (`LookupManager.getActiveLookup(editor)?.hideLookup(true)`). Without this, re-triggering during composing stacks popups.
9. **Dictionary file is `/dict/fly.txt`** (not `xiaohe.txt`), and settings storage is `fly-ime-settings.xml` (not `xiaohe-ime-settings.xml`). The naming reflects an earlier project name; the scheme is still Xiaohe (小鹤). Don't rename without updating both `FlyPyDict`'s resource path and `ImeSettings`'s `@State` annotation.

### Notes for development

- Configuration cache is enabled (`org.gradle.configuration-cache=true`). Avoid accessing `Project` from task execution time in ways that break it.
- The `kotlin.stdlib.default.dependency` is set to `false` — the IntelliJ Platform bundles its own Kotlin stdlib; do not re-add it.
- Tests use JUnit 4 (not 5) with the IntelliJ Platform test framework. Test classes go under `src/test/kotlin/io/github/lymansix/`. **No tests exist yet** — `src/test/` has not been created.
- The dictionary is loaded from `src/main/resources/dict/fly.txt` (~48k lines) at first access. To swap dictionaries, replace this file with another using the same format: one line per code, whitespace-separated, `<code>   <word1> [<word2>]`. Codes are 1-4 lowercase letters. The plugin does prefix matching on codes and returns up to 50 candidates per lookup.
- `ImeStatusWidget.kt` contains three classes (widget, factory, action). If you add more status-bar-related behavior, put it in this file rather than creating a new one.
- Localized strings live in `src/main/resources/messages/ImeBundle.properties` and are accessed via `ImeBundle.message("key")`. Add new keys there rather than hard-coding UI text.
- `ImeSettings.enableSmartSwitch` and `showPinyinHint` are persisted but currently unused by the runtime code — they're placeholders for future features.
- The `plugin.xml` `<description>` block still mentions `Ctrl+Alt+L`; this is stale. The actual bound shortcut is `Ctrl+Alt+\` (see the `<actions>` block).
