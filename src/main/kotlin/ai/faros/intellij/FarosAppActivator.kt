package ai.faros.intellij

import ai.faros.intellij.services.FarosDocumentListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

@Service
class FarosAppActivator : Disposable {
    private val LOG = Logger.getInstance(FarosAppActivator::class.java)
    private val documentListener = FarosDocumentListener()
    
    init {
        LOG.info("FarosAppActivator initializing")
        val editorFactory = EditorFactory.getInstance()
        editorFactory.eventMulticaster.addDocumentListener(documentListener, this)
        LOG.info("Document listener registered")
    }
    
    override fun dispose() {
        LOG.info("FarosAppActivator disposing")
    }
    
    companion object {
        fun getInstance(): FarosAppActivator {
            return ApplicationManager.getApplication().getService(FarosAppActivator::class.java)
        }
    }
}

/**
 * Startup activity to ensure our activator is initialized
 */
class FarosStartupActivity : StartupActivity.DumbAware {
    private val LOG = Logger.getInstance(FarosStartupActivity::class.java)
    
    override fun runActivity(project: Project) {
        LOG.info("FarosStartupActivity: initializing services")
        ApplicationManager.getApplication().getService(FarosAppActivator::class.java)
    }
}