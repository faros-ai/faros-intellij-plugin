package ai.faros.intellij.services

import ai.faros.intellij.model.CodingEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.*

@State(
    name = "ai.faros.intellij.services.FarosMetricsPersistenceService",
    storages = [Storage("FarosMetrics.xml")]
)
class FarosMetricsPersistenceService : PersistentStateComponent<FarosMetricsPersistenceService> {
    var autoCompletionEvents: ArrayList<CodingEvent> = ArrayList()
    var handWrittenEvents: ArrayList<CodingEvent> = ArrayList()
    var repositoryStats: MutableMap<String, Int> = mutableMapOf()
    var languageStats: MutableMap<String, Int> = mutableMapOf()
    var hourlyAutoCompletionData: MutableMap<String, Int> = mutableMapOf()
    var hourlyHandWrittenData: MutableMap<String, Int> = mutableMapOf()

    override fun getState(): FarosMetricsPersistenceService = this

    override fun loadState(state: FarosMetricsPersistenceService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): FarosMetricsPersistenceService =
            ApplicationManager.getApplication().getService(FarosMetricsPersistenceService::class.java)
    }
} 