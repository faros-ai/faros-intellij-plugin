package ai.faros.intellij.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "ai.faros.intellij.services.FarosSettingsService",
    storages = [Storage("FarosAISettings.xml")]
)
class FarosSettingsService : PersistentStateComponent<FarosSettingsService> {
    var apiKey: String = ""
    var vcsUid: String = ""
    var vcsEmail: String = ""
    var vcsName: String = ""
    var url: String = "https://prod.api.faros.ai"
    var webhook: String = ""
    var graph: String = "default"
    var origin: String = "faros-intellij-plugin"
    var batchSize: Int = 500
    var batchInterval: Int = 60000
    var autoCompletionCategory: String = "AutoCompletion"
    var handWrittenCategory: String = "HandWritten"
    var userSource: String = "intellij-plugin"

    override fun getState(): FarosSettingsService = this

    override fun loadState(state: FarosSettingsService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): FarosSettingsService =
            ApplicationManager.getApplication().getService(FarosSettingsService::class.java)
    }
} 