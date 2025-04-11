package ai.faros.intellij.services

import ai.faros.intellij.model.CodingEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service

import java.util.concurrent.CopyOnWriteArrayList

@Service
class FarosStateService {
    private val autoCompletionEventQueue = CopyOnWriteArrayList<CodingEvent>()
    private val handWrittenEventQueue = CopyOnWriteArrayList<CodingEvent>()
    private var suggestionsCount = 0
    private var charCount = 0

    // Methods for auto-completion events
    fun addAutoCompletionEvent(event: CodingEvent) {
        autoCompletionEventQueue.add(event)
        suggestionsCount++
        charCount += event.charCountChange
    }

    fun getAutoCompletionEventQueue(): List<CodingEvent> {
        return ArrayList(autoCompletionEventQueue)
    }

    fun clearAutoCompletionEventQueue() {
        autoCompletionEventQueue.clear()
    }

    // Methods for hand-written events
    fun addHandWrittenEvent(event: CodingEvent) {
        handWrittenEventQueue.add(event)
    }

    fun getHandWrittenEventQueue(): List<CodingEvent> {
        return ArrayList(handWrittenEventQueue)
    }

    fun clearHandWrittenEventQueue() {
        handWrittenEventQueue.clear()
    }

    // Getters for statistics
    fun getSuggestionsCount(): Int {
        return suggestionsCount
    }

    fun getCharCount(): Int {
        return charCount
    }
    
    // Add method to get completion ratio
    fun getCompletionRatio(): Double {
        val totalHandWrittenChars = handWrittenEventQueue.sumOf { it.charCountChange }
        val totalChars = charCount + totalHandWrittenChars
        return if (totalChars > 0) charCount.toDouble() / totalChars else 0.0
    }
    
    // Add method to get time saved in minutes (assuming 240 chars per minute typing speed)
    fun getTimeSavedMinutes(): Double {
        return charCount / 240.0
    }
    
    // Add method to get unique repositories
    fun getUniqueRepositories(): List<String> {
        return autoCompletionEventQueue
            .map { it.repository }
            .filter { it.isNotEmpty() && it != "unknown" }
            .distinct()
    }
    
    // Add method to get unique languages
    fun getUniqueLanguages(): List<String> {
        return autoCompletionEventQueue
            .map { it.language }
            .filter { it.isNotEmpty() && it != "unknown" }
            .distinct()
    }

    // Reset statistics
    fun resetStats() {
        suggestionsCount = 0
        charCount = 0
    }

    companion object {
        fun getInstance(): FarosStateService {
            return ApplicationManager.getApplication().getService(FarosStateService::class.java)
        }
    }
} 