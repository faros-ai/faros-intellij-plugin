package ai.faros.intellij.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.xmlb.XmlSerializerUtil
import java.security.MessageDigest
import java.util.*

@State(
    name = "ai.faros.intellij.services.FarosSettingsService",
    storages = [Storage("FarosAISettings.xml")]
)
class FarosSettingsService : PersistentStateComponent<FarosSettingsService> {
    private val LOG = Logger.getInstance(FarosSettingsService::class.java)
    
    var apiKey: String = ""
    var vcsUid: String = ""
    var vcsEmail: String = ""
    var vcsName: String = ""
    var url: String = "https://prod.api.faros.ai"
    var webhook: String = ""
    var graph: String = "default"
    var origin: String = "faros-jetbrains-plugin"
    var batchSize: Int = 500
    var batchInterval: Int = 60000
    var autoCompletionCategory: String = "AutoCompletion"
    var handWrittenCategory: String = "HandWritten"
    var userSource: String = "jetbrains-plugin"

    override fun getState(): FarosSettingsService = this

    override fun loadState(state: FarosSettingsService) {
        XmlSerializerUtil.copyBean(state, this)
        
        // Auto-generate values if not set (matching VS Code's updateConfig function)
        updateConfigIfNeeded()
    }
    
    /**
     * Update configuration values if needed, matching VS Code's updateConfig function
     */
    private fun updateConfigIfNeeded() {
        // Only generate vcsUid if it's empty (matching VS Code behavior)
        if (vcsUid.isEmpty()) {
            try {
                val md = MessageDigest.getInstance("SHA-256")
                // Use name or email if available, otherwise use random UUID
                val input = if (vcsName.isNotEmpty() || vcsEmail.isNotEmpty()) {
                    (vcsName + vcsEmail).toByteArray()
                } else {
                    UUID.randomUUID().toString().toByteArray()
                }
                val hash = md.digest(input)
                val hexString = hash.joinToString("") { String.format("%02x", it) }
                // Use first 8 chars just like VS Code does
                vcsUid = hexString.substring(0, 8)
                LOG.info("Generated VCS UID: $vcsUid")
            } catch (e: Exception) {
                LOG.error("Failed to generate VCS UID, using random UUID", e)
                vcsUid = UUID.randomUUID().toString().substring(0, 8)
            }
        }
    }

    companion object {
        fun getInstance(): FarosSettingsService =
            ApplicationManager.getApplication().getService(FarosSettingsService::class.java)
    }
} 