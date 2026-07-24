package io.github.lymansix.ime.action
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import io.github.lymansix.ime.dict.FlyPyDict
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
            state.start(editor)
            state.composing.append(charTyped)
            ImeLookup.show(editor, state)
            return
        }

        // 数字选词 1–9
        if (charTyped in '1'..'9' && state.composing.isNotEmpty()) {
            commit(editor, state, charTyped - '1')
            return
        }

        // Space / Enter 提交
        if ((charTyped == ' ' || charTyped == '\n') && state.composing.isNotEmpty()) {
            commit(editor, state, 0)
            return
        }

        // 其他字符：先提交再放行
        if (state.composing.isNotEmpty()) {
            commit(editor, state, 0)
        }

        original.execute(editor, charTyped, dataContext)
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
}