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
        // Only reset/hide when we're actually composing — otherwise these calls are
        // pure no-ops (state already empty, no active lookup). Guarding keeps the
        // non-composing ESC path as cheap as possible.
        if (state.composing.isNotEmpty()) {
            state.reset()
            ImeLookup.hide(editor)
        }
        // ESC always propagates: even when not composing, the user expects default
        // ESC semantics (close popups, exit IdeaVim insert mode, cancel actions).
        original.execute(editor, caret, dataContext)
    }
}