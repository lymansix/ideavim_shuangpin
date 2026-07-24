package io.github.lymansix.ime.action
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.TypedAction

class HandlerInstaller : AppLifecycleListener {

    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        val typedAction = TypedAction.getInstance()
        typedAction.setupRawHandler(
            ImeTypeHandler(typedAction.rawHandler)
        )

        val manager = EditorActionManager.getInstance()

        manager.setActionHandler(
            IdeActions.ACTION_EDITOR_BACKSPACE,
            ImeBackspaceHandler(
                manager.getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE)
            )
        )

        manager.setActionHandler(
            IdeActions.ACTION_EDITOR_ESCAPE,
            ImeEscHandler(
                manager.getActionHandler(IdeActions.ACTION_EDITOR_ESCAPE)
            )
        )
    }
}