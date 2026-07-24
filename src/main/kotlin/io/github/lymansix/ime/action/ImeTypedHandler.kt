package io.github.lymansix.ime.action
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import io.github.lymansix.ime.dict.FlyPyDict
import io.github.lymansix.ime.dict.Punctuation
import io.github.lymansix.ime.lookup.ImeLookup
import io.github.lymansix.ime.settings.ImeSettings
import io.github.lymansix.ime.state.ImeState
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

class ImeTypeHandler(
    private val original: TypedActionHandler
) : TypedActionHandler {

    override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
        val state = ImeState.get(editor)
        val chineseMode = ImeSettings.getInstance().isChineseMode

        if (!chineseMode || editor.isViewer || !editor.document.isWritable) {
            // If we were composing and the user just toggled to English,
            // abandon the in-flight buffer so it doesn't orphan with a stale startOffset.
            if (state.composing.isNotEmpty()) state.reset()
            original.execute(editor, charTyped, dataContext)
            return
        }

        // Shift + letter = temporary English. Check the modifier on the current
        // AWT event directly (via IdeEventQueue) because charTyped may or may not
        // reflect Shift depending on platform/keyboard-layout: some configurations
        // deliver the lowercase char even when Shift is held.
        val currentEvent = IdeEventQueue.getInstance().trueCurrentEvent
        val shiftHeld = currentEvent is KeyEvent &&
                (currentEvent.modifiersEx and InputEvent.SHIFT_DOWN_MASK) != 0
        val isLetter = charTyped in 'a'..'z' || charTyped in 'A'..'Z'

        if ((shiftHeld || charTyped in 'A'..'Z') && isLetter) {
            // Shift + letter while composing: commit first candidate (consistent
            // with how any other non-a-z char is handled), then pass the letter
            // through so it lands in the document as uppercase English.
            if (state.composing.isNotEmpty()) commit(editor, state, 0)
            original.execute(editor, charTyped, dataContext)
            return
        }

        // a-z：进入 composing（不落盘）
        if (charTyped in 'a'..'z') {
            // The Xiaohe dict's codes are 1-4 chars. Once we've hit the max, any
            // further letter can't extend the current code — commit the first
            // candidate for what we have (if any), reset, and start a fresh
            // composing with the new letter. Without this, typing a 5th letter
            // would grow the buffer past the dict's max code length, getCandidates()
            // would return empty, and the popup would vanish leaving the user unable
            // to select via number keys.
            if (state.composing.length >= MAX_CODE_LENGTH) {
                commit(editor, state, 0)
                // commit() resets state on success; if there were no candidates it
                // returns early without resetting, so reset explicitly to avoid
                // carrying a stale 4-char buffer forward.
                state.reset()
            }
            state.start(editor)
            state.composing.append(charTyped)
            ImeLookup.show(editor, state)
            // Consume the underlying AWT KeyEvent so plugins that listen at a lower
            // layer than TypedAction (notably IdeaVim — whose fast-escape sequences
            // like `jk` → Esc would otherwise still see the letters and misfire)
            // don't receive this keystroke.
            consumeCurrentAwtEvent()
            return
        }

        // 数字选词 1–9
        if (charTyped in '1'..'9' && state.composing.isNotEmpty()) {
            commit(editor, state, charTyped - '1')
            consumeCurrentAwtEvent()
            return
        }

        // Space / Enter 提交
        if ((charTyped == ' ' || charTyped == '\n') && state.composing.isNotEmpty()) {
            commit(editor, state, 0)
            consumeCurrentAwtEvent()
            return
        }

        // 其他字符：先提交再放行
        if (state.composing.isNotEmpty()) {
            commit(editor, state, 0)
        }

        // We only reach here when chineseMode == true (the !chineseMode branch
        // returned early at the top of this method), so substitute ASCII
        // punctuation with its Chinese/fullwidth equivalent before passing through.
        val outputChar = Punctuation.toChinese(charTyped) ?: charTyped
        original.execute(editor, outputChar, dataContext)
    }

    /**
     * Mark the AWT event currently being dispatched as consumed.
     *
     * This is the belt to [TypedActionHandler]'s suspenders: returning without
     * calling `original.execute` keeps the character out of the Document, but
     * plugins that listen on the editor component at the AWT layer (notably
     * IdeaVim, for features like `jk` → Esc) can still observe the keystroke.
     * Calling [java.awt.AWTEvent.consume] on the in-flight event stops those
     * listeners too.
     *
     * No-op if the current event isn't a KeyEvent (defensive — shouldn't happen
     * during a typed handler callback).
     */
    private fun consumeCurrentAwtEvent() {
        (IdeEventQueue.getInstance().trueCurrentEvent as? KeyEvent)?.consume()
    }

    private fun commit(editor: Editor, state: ImeState, index: Int) {
        val code = state.composing.toString()
        val candidates = FlyPyDict.getCandidates(code)
        val candidate = candidates.getOrNull(index) ?: return

        ImeLookup.hide(editor)

        WriteCommandAction.runWriteCommandAction(editor.project) {
            editor.document.insertString(state.startOffset, candidate.word)
            editor.caretModel.moveToOffset(state.startOffset + candidate.word.length)
        }

        state.reset()
    }

    private companion object {
        // Xiaohe Yin-xing (小鹤音形) codes are 1-4 letters. Once the composing buffer
        // hits this length, the next letter can't extend the current code — we commit
        // and start fresh. Keep in sync with FlyPyDict's actual max code length.
        const val MAX_CODE_LENGTH = 4
    }
}