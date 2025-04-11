package ai.faros.intellij.services

import ai.faros.intellij.model.CodingEvent
import ai.faros.intellij.util.GitUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicReference

class FarosDocumentListener : DocumentListener {
    
    private enum class TextChangeType {
        HAND_WRITTEN_CHAR,
        AUTO_COMPLETION,
        DELETION,
        NO_CHANGE,
        UNKNOWN
    }
    
    private val LOG = Logger.getInstance(FarosDocumentListener::class.java)
    private val stateService = FarosStateService.getInstance()
    private val statsService = FarosStatsService.getInstance()
    private val timer: Timer
    private val lastTextRef = AtomicReference<String>("")
    
    // Track last event time to detect rapid completions
    private var lastEventTime = System.currentTimeMillis()
    private var consecutiveCompletionsCount = 0
    
    // For more accurate Copilot detection
    private var pendingCompletion = false
    private var lastChangeOffset = -1
    private var lastChangeLength = 0
    
    init {
        // Schedule task to send events periodically
        timer = Timer(true)
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                checkAndLogEvents()
            }
        }, 0, FarosSettingsService.getInstance().batchInterval.toLong())
        
        LOG.info("FarosDocumentListener initialized")
    }
    
    private fun checkAndLogEvents() {
        try {
            val autoCompletionEvents = stateService.getAutoCompletionEventQueue()
            if (autoCompletionEvents.isNotEmpty()) {
                LOG.info("Sending ${autoCompletionEvents.size} auto-completion events")
                if (FarosApiClient.sendEvents(autoCompletionEvents, 
                        FarosSettingsService.getInstance().autoCompletionCategory)) {
                    stateService.clearAutoCompletionEventQueue()
                }
            }
            
            val handWrittenEvents = stateService.getHandWrittenEventQueue()
            if (handWrittenEvents.isNotEmpty()) {
                LOG.info("Sending ${handWrittenEvents.size} hand-written events")
                if (FarosApiClient.sendEvents(handWrittenEvents, 
                        FarosSettingsService.getInstance().handWrittenCategory)) {
                    stateService.clearHandWrittenEventQueue()
                }
            }
        } catch (e: Exception) {
            LOG.error("Error sending events: ${e.message}", e)
        }
    }
    
    override fun beforeDocumentChange(event: DocumentEvent) {
        // Track where the change is happening for better completion detection
        lastChangeOffset = event.offset
        lastChangeLength = event.oldLength
    }
    
    override fun documentChanged(event: DocumentEvent) {
        val document = event.document
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        
        ApplicationManager.getApplication().invokeLater {
            try {
                val currentText = document.text
                val previousText = lastTextRef.getAndSet(currentText)
                
                // Skip empty documents
                if (previousText.isEmpty() && currentText.length <= 1) {
                    return@invokeLater
                }
                
                val changeType = classifyTextChange(event, previousText, currentText)
                val filename = file.path
                val extension = file.extension ?: ""
                val language = file.fileType.name
                val repository = GitUtil.getGitRepoName(file)
                val branch = GitUtil.getGitBranch(file)
                
                val currentTime = System.currentTimeMillis()
                val timeSinceLastEvent = currentTime - lastEventTime
                lastEventTime = currentTime
                
                // Log details about the change
                LOG.debug("Document change: type=$changeType, offset=${event.offset}, " +
                          "old length=${event.oldLength}, new length=${event.newFragment.length}, " +
                          "time since last=${timeSinceLastEvent}ms")
                
                when (changeType) {
                    TextChangeType.AUTO_COMPLETION -> {
                        // Count characters in new fragment, but also consider multiline insertions
                        val charCountChange = calculateCharDifference(previousText, currentText)
                        
                        if (charCountChange > 0) {
                            // Check if this is part of a sequence of rapid auto-completions
                            if (timeSinceLastEvent < 500) { // 500ms threshold for considering related completions
                                consecutiveCompletionsCount++
                            } else {
                                consecutiveCompletionsCount = 0
                            }
                            
                            LOG.info("Detected AUTO_COMPLETION: $charCountChange chars at offset ${event.offset}")
                            
                            val codingEvent = CodingEvent(
                                Date(), 
                                charCountChange, 
                                "AutoCompletion",
                                filename,
                                extension,
                                language,
                                repository,
                                branch
                            )
                            stateService.addAutoCompletionEvent(codingEvent)
                            statsService.addAutoCompletionEvent(codingEvent) // Add to stats service for UI
                            
                            // Force UI refresh immediately after auto-completion
                            ApplicationManager.getApplication().invokeLater {
                                try {
                                    // This will trigger a UI update in the tool window
                                    statsService.notifyStatsChanged()
                                } catch (e: Exception) {
                                    LOG.error("Error refreshing UI after auto-completion: ${e.message}", e)
                                }
                            }
                        }
                    }
                    TextChangeType.HAND_WRITTEN_CHAR -> {
                        val codingEvent = CodingEvent(
                            Date(),
                            1,
                            "HandWritten",
                            filename,
                            extension,
                            language,
                            repository,
                            branch
                        )
                        stateService.addHandWrittenEvent(codingEvent)
                        statsService.addHandWrittenEvent(codingEvent) // Add to stats service for UI
                    }
                    else -> {
                        // Reset consecutive completions counter for other event types
                        consecutiveCompletionsCount = 0
                    }
                }
            } catch (e: Exception) {
                LOG.error("Error processing document change: ${e.message}", e)
            }
        }
    }
    
    private fun calculateCharDifference(oldText: String, newText: String): Int {
        if (oldText.length >= newText.length) return 0
        
        return newText.length - oldText.length
    }
    
    private fun classifyTextChange(event: DocumentEvent, previousText: String, currentText: String): TextChangeType {
        val newText = event.newFragment
        val oldText = event.oldFragment
        
        // Instant detection for standard patterns
        if (oldText.length > 0 && newText.length == 0) {
            return TextChangeType.DELETION
        } else if (newText.length == 1 && oldText.isEmpty()) {
            return TextChangeType.HAND_WRITTEN_CHAR
        }
        
        // GitHub Copilot often inserts with Tab key
        // If offset matches cursor position (lastChangeOffset) and the new text is substantial
        if (event.offset == lastChangeOffset && newText.length > 2) {
            LOG.debug("Likely Copilot tab completion at cursor position")
            return TextChangeType.AUTO_COMPLETION
        }
        
        // Check for multi-line insertion (common in Copilot)
        if (newText.contains('\n') && previousText.length < currentText.length) {
            LOG.debug("Detected multi-line insertion - likely Copilot completion")
            return TextChangeType.AUTO_COMPLETION
        }
        
        // Check for significant insertion (more than 3 chars at once)
        if (newText.length > 3 && !newText.toString().trim().isEmpty()) {
            LOG.debug("Detected significant insertion (${newText.length} chars) - likely AI completion")
            return TextChangeType.AUTO_COMPLETION
        }
        
        // Check if the change looks like a tab completion from copilot
        // Copilot often completes with indentation and special patterns
        if (previousText.length < currentText.length) {
            val addedText = if (event.offset + oldText.length < currentText.length) {
                currentText.substring(event.offset + oldText.length)
            } else {
                newText.toString()
            }
            
            // Check for code-like patterns (brackets, semicolons, etc.)
            if (addedText.matches(Regex(".*[{}();=].*")) || 
                addedText.contains("=>") || 
                addedText.contains("->")) {
                LOG.debug("Detected code pattern in insertion - likely AI completion")
                return TextChangeType.AUTO_COMPLETION
            }
        }
        
        // Fallback: if it's a substantial addition but not a single char, consider it auto-completion
        return if (newText.length > 1) {
            TextChangeType.AUTO_COMPLETION
        } else {
            TextChangeType.UNKNOWN
        }
    }
} 