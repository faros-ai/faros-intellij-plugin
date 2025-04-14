package ai.faros.intellij.services

import ai.faros.intellij.model.CodingEvent
import ai.faros.intellij.ui.FarosUIUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.round

@Service
class FarosStatsService {
    private val LOG = Logger.getInstance(FarosStatsService::class.java)
    private val autoCompletionEvents = CopyOnWriteArrayList<CodingEvent>()
    private val handWrittenEvents = CopyOnWriteArrayList<CodingEvent>()
    
    // Top repositories and languages data
    private val repositoryStats = mutableMapOf<String, Int>()
    private val languageStats = mutableMapOf<String, Int>()
    
    // Hourly aggregated data for charts
    private val hourlyAutoCompletionData = mutableMapOf<String, Int>()
    private val hourlyHandWrittenData = mutableMapOf<String, Int>()
    
    // Constants
    companion object {
        const val CHARS_PER_MINUTE = 240.0 // Average typing speed
        
        fun getInstance(): FarosStatsService {
            return ApplicationManager.getApplication().getService(FarosStatsService::class.java)
        }
        
        // Format an hour key for storage: yyyy-MM-dd-HH
        private fun formatHourKey(date: Date): String {
            val calendar = Calendar.getInstance()
            calendar.time = date
            return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH)}-${calendar.get(Calendar.DAY_OF_MONTH)}-${calendar.get(Calendar.HOUR_OF_DAY)}"
        }
        
        // Get start of day
        private fun getStartOfDay(date: Date = Date()): Date {
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.time
        }
        
        // Get start of week
        private fun getStartOfWeek(date: Date = Date()): Date {
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.time
        }
        
        // Get start of month
        private fun getStartOfMonth(date: Date = Date()): Date {
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.time
        }
    }
    
    fun addAutoCompletionEvent(event: CodingEvent) {
        autoCompletionEvents.add(event)
        
        // Update hourly data
        val hourKey = formatHourKey(event.timestamp)
        hourlyAutoCompletionData[hourKey] = (hourlyAutoCompletionData[hourKey] ?: 0) + event.charCountChange
        
        // Update repository stats
        if (event.repository.isNotEmpty() && event.repository != "unknown") {
            repositoryStats[event.repository] = (repositoryStats[event.repository] ?: 0) + 1
        }
        
        // Update language stats
        if (event.language.isNotEmpty() && event.language != "unknown") {
            languageStats[event.language] = (languageStats[event.language] ?: 0) + 1
        }
        
        LOG.debug("Added auto-completion event: ${event.charCountChange} chars, repo: ${event.repository}, lang: ${event.language}")
    }
    
    fun addHandWrittenEvent(event: CodingEvent) {
        handWrittenEvents.add(event)
        
        // Update hourly data
        val hourKey = formatHourKey(event.timestamp)
        hourlyHandWrittenData[hourKey] = (hourlyHandWrittenData[hourKey] ?: 0) + event.charCountChange
        
        LOG.debug("Added hand-written event")
    }
    
    fun getAutoCompletionEvents(): List<CodingEvent> {
        return ArrayList(autoCompletionEvents)
    }
    
    fun getHandWrittenEvents(): List<CodingEvent> {
        return ArrayList(handWrittenEvents)
    }
    
    fun clearAutoCompletionEvents() {
        autoCompletionEvents.clear()
    }
    
    fun clearHandWrittenEvents() {
        handWrittenEvents.clear()
    }
    
    // Get total completion stats
    fun getCompletionStats(): Map<String, Any> {
        val now = Date()
        val startOfDay = getStartOfDay(now)
        val startOfWeek = getStartOfWeek(now)
        val startOfMonth = getStartOfMonth(now)
        
        // Calculate total stats
        val totalAutoCompletionCount = autoCompletionEvents.size
        val totalAutoCompletionChars = autoCompletionEvents.sumOf { it.charCountChange }
        val totalTimeSaved = totalAutoCompletionChars / CHARS_PER_MINUTE
        
        // Calculate today's stats
        val todayEvents = autoCompletionEvents.filter { it.timestamp >= startOfDay }
        val todayCount = todayEvents.size
        val todayChars = todayEvents.sumOf { it.charCountChange }
        val todayTimeSaved = todayChars / CHARS_PER_MINUTE
        
        // Calculate this week's stats
        val weekEvents = autoCompletionEvents.filter { it.timestamp >= startOfWeek }
        val weekCount = weekEvents.size
        val weekChars = weekEvents.sumOf { it.charCountChange }
        val weekTimeSaved = weekChars / CHARS_PER_MINUTE
        
        // Calculate this month's stats
        val monthEvents = autoCompletionEvents.filter { it.timestamp >= startOfMonth }
        val monthCount = monthEvents.size
        val monthChars = monthEvents.sumOf { it.charCountChange }
        val monthTimeSaved = monthChars / CHARS_PER_MINUTE
        
        return mapOf(
            "total" to mapOf("count" to totalAutoCompletionCount, "timeSaved" to totalTimeSaved, "chars" to totalAutoCompletionChars),
            "today" to mapOf("count" to todayCount, "timeSaved" to todayTimeSaved, "chars" to todayChars),
            "thisWeek" to mapOf("count" to weekCount, "timeSaved" to weekTimeSaved, "chars" to weekChars),
            "thisMonth" to mapOf("count" to monthCount, "timeSaved" to monthTimeSaved, "chars" to monthChars)
        )
    }
    
    // Calculate completion ratios (auto-completed vs hand-written)
    fun getCompletionRatios(): Map<String, Double> {
        val now = Date()
        val startOfDay = getStartOfDay(now)
        val startOfWeek = getStartOfWeek(now)
        val startOfMonth = getStartOfMonth(now)
        
        // Calculate total ratio
        val totalAutoCompletionChars = autoCompletionEvents.sumOf { it.charCountChange }
        val totalHandWrittenChars = handWrittenEvents.sumOf { it.charCountChange }
        val totalChars = totalAutoCompletionChars + totalHandWrittenChars
        val totalRatio = if (totalChars > 0) totalAutoCompletionChars.toDouble() / totalChars else 0.0
        
        // Calculate today's ratio
        val todayAutoCompletionChars = autoCompletionEvents.filter { it.timestamp >= startOfDay }.sumOf { it.charCountChange }
        val todayHandWrittenChars = handWrittenEvents.filter { it.timestamp >= startOfDay }.sumOf { it.charCountChange }
        val todayChars = todayAutoCompletionChars + todayHandWrittenChars
        val todayRatio = if (todayChars > 0) todayAutoCompletionChars.toDouble() / todayChars else 0.0
        
        // Calculate this week's ratio
        val weekAutoCompletionChars = autoCompletionEvents.filter { it.timestamp >= startOfWeek }.sumOf { it.charCountChange }
        val weekHandWrittenChars = handWrittenEvents.filter { it.timestamp >= startOfWeek }.sumOf { it.charCountChange }
        val weekChars = weekAutoCompletionChars + weekHandWrittenChars
        val weekRatio = if (weekChars > 0) weekAutoCompletionChars.toDouble() / weekChars else 0.0
        
        // Calculate this month's ratio
        val monthAutoCompletionChars = autoCompletionEvents.filter { it.timestamp >= startOfMonth }.sumOf { it.charCountChange }
        val monthHandWrittenChars = handWrittenEvents.filter { it.timestamp >= startOfMonth }.sumOf { it.charCountChange }
        val monthChars = monthAutoCompletionChars + monthHandWrittenChars
        val monthRatio = if (monthChars > 0) monthAutoCompletionChars.toDouble() / monthChars else 0.0
        
        return mapOf(
            "total" to totalRatio,
            "today" to todayRatio,
            "thisWeek" to weekRatio,
            "thisMonth" to monthRatio
        )
    }
    
    // Get the top repositories by auto-completion count
    fun getTopRepositories(limit: Int = 5): List<Map<String, Any>> {
        return repositoryStats.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { mapOf("repository" to it.key, "count" to it.value) }
    }
    
    // Get the top languages by auto-completion count
    fun getTopLanguages(limit: Int = 5): List<Map<String, Any>> {
        return languageStats.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { mapOf("language" to it.key, "count" to it.value) }
    }
    
    // Get hourly data for chart
    fun getHourlyChartData(hours: Int = 24): List<Map<String, Any>> {
        val result = mutableListOf<Map<String, Any>>()
        val calendar = Calendar.getInstance()
        val now = calendar.time
        
        // Create a list of hour keys for the last N hours
        val hourKeys = mutableListOf<String>()
        for (i in hours - 1 downTo 0) {
            calendar.time = now
            calendar.add(Calendar.HOUR_OF_DAY, -i)
            hourKeys.add(formatHourKey(calendar.time))
        }
        
        // For each hour, get the auto-completion and hand-written data
        for (hourKey in hourKeys) {
            val autoCompletion = hourlyAutoCompletionData[hourKey] ?: 0
            val handWritten = hourlyHandWrittenData[hourKey] ?: 0
            
            // Format hour label (e.g., "10:00")
            val parts = hourKey.split("-")
            val hour = if (parts.size >= 4) parts[3].toIntOrNull() ?: 0 else 0
            val hourLabel = "$hour:00"
            
            result.add(mapOf(
                "label" to hourLabel,
                "values" to listOf(autoCompletion, handWritten)
            ))
        }
        
        return result
    }
    
    // Format time saved string (e.g., "2h 30m" or "45m")
    fun formatTimeSaved(minutesSaved: Double): String {
        return FarosUIUtil.formatTimeSaved(minutesSaved)
    }
    
    // Format percentage string (e.g., "95%")
    fun formatPercentage(ratio: Double): String {
        return FarosUIUtil.formatPercentage(ratio)
    }
    
    // Notify that stats have changed (for UI updates)
    fun notifyStatsChanged() {
        // This method will be called when stats are updated,
        // triggering UI refreshes in components that observe this service
        LOG.info("Stats updated notification sent")
        ApplicationManager.getApplication().messageBus.syncPublisher(FarosStatsListener.TOPIC)
            .statsChanged()
    }
    
    // Reset all statistics
    fun resetStats() {
        autoCompletionEvents.clear()
        handWrittenEvents.clear()
        repositoryStats.clear()
        languageStats.clear()
        hourlyAutoCompletionData.clear()
        hourlyHandWrittenData.clear()
    }
}
