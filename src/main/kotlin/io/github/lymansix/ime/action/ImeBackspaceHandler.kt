package io.github.lymansix.ime.action
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import io.github.lymansix.ime.lookup.ImeLookup
import io.github.lymansix.ime.settings.ImeSettings
import io.github.lymansix.ime.state.ImeState
import java.awt.event.KeyEvent

class ImeBackspaceHandler(
    private val original: EditorActionHandler
) : EditorActionHandler() {

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        val state = ImeState.get(editor)

        if (ImeSettings.getInstance().isChineseMode && state.composing.isNotEmpty()) {
            state.composing.deleteAt(state.composing.length - 1)
            if (state.composing.isEmpty()) {
                state.reset()
                ImeLookup.hide(editor)
            } else {
                ImeLookup.show(editor, state)
            }
            // Consume the AWT KeyEvent so plugins listening below EditorActionHandler
            // (e.g. IdeaVim) don't see the backspace as a Vim command.
            (IdeEventQueue.getInstance().trueCurrentEvent as? KeyEvent)?.consume()
            return
        }

        original.execute(editor, caret, dataContext)
    }
}