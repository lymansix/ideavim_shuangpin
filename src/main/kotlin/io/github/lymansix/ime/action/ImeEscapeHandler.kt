package io.github.lymansix.ime.action
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import io.github.lymansix.ime.lookup.ImeLookup
import io.github.lymansix.ime.state.ImeState

class ImeEscHandler(
    private val original: EditorActionHandler
) : EditorActionHandler() {

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        val state = ImeState.get(editor)
        state.reset()
        ImeLookup.hide(editor)
        original.execute(editor, caret, dataContext)
    }
}