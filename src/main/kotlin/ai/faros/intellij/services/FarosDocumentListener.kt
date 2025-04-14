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
        SPACE,
        AUTO_CLOSE_BRACKET,
        UNKNOWN
    }

    // Singleton instance
    companion object {
        @JvmStatic
        fun getInstance(): FarosDocumentListener {
            return Holder.INSTANCE
        }
    }
    private object Holder {
        val INSTANCE = FarosDocumentListener()
    }

    
    private val LOG = Logger.getInstance(FarosDocumentListener::class.java)
    private val stateService = FarosStateService.getInstance()
    private val statsService = FarosStatsService.getInstance()
    private val timer: Timer
    private val lastTextRef = AtomicReference<String>("")
    
    // Track last event time to detect rapid completions
    private var lastEventTime = System.currentTimeMillis()
    
    // Auto-bracketing detection
    private val autoBracketPairs = setOf("()", "[]", "{}", "\"\"", "''", "``")
    
    init {
        // Schedule task to send events periodically
        timer = Timer(true)
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                checkAndLogEvents()
            }
        }, 0, FarosSettingsService.getInstance().batchInterval.toLong())
        
        LOG.info(">>> FarosDocumentListener initialized - waiting for document changes")
        try {
            LOG.info(">>> FarosDocumentListener settings: batchInterval=${FarosSettingsService.getInstance().batchInterval}ms")
            LOG.info(">>> Git4Idea available: ${GitUtil.isGit4IdeaAvailable()}")
        } catch (e: Exception) {
            LOG.error(">>> Error during FarosDocumentListener initialization", e)
        }
    }
    
    fun dispose() {
        timer.cancel()
        LOG.info(">>> FarosDocumentListener disposed")
    }


    /**
     * Check and log events periodically
     */
    
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
        // Log that we received a beforeDocumentChange event
        val document = event.document
        val file = FileDocumentManager.getInstance().getFile(document)
        LOG.info(">>> beforeDocumentChange received for file: ${file?.name ?: "unknown"}, offset: ${event.offset}, length: ${event.oldLength}")
    }
    
    override fun documentChanged(event: DocumentEvent) {
        val document = event.document
        val file = FileDocumentManager.getInstance().getFile(document)
        
        LOG.info(">>> documentChanged received for file: ${file?.name ?: "unknown"}, offset: ${event.offset}, newText: '${event.newFragment}', oldText: '${event.oldFragment}'")
        
        if (file == null) {
            LOG.warn(">>> Cannot process document change: file is null")
            return
        }
        
        ApplicationManager.getApplication().invokeLater {
            try {
                val currentText = document.text
                val previousText = lastTextRef.getAndSet(currentText)
                
                // Skip empty documents
                if (previousText.isEmpty() && currentText.length <= 1) {
                    LOG.info(">>> Skipping empty document")
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
                
                // Enhanced logging for diagnosing issues
                LOG.info(">>> Document change in $filename: type=$changeType, newText='${event.newFragment}', " +
                        "oldText='${event.oldFragment}', offset=${event.offset}, old length=${event.oldLength}, " +
                        "new length=${event.newFragment.length}, time since last=${timeSinceLastEvent}ms")
                
                when (changeType) {
                    TextChangeType.AUTO_COMPLETION -> {
                        // Calculate non-whitespace character count like in the VS Code extension
                        val newTextNoWhitespace = event.newFragment.toString().replace("\\s".toRegex(), "")
                        val charCountChange = newTextNoWhitespace.length
                        
                        if (charCountChange > 0) {
                            LOG.info(">>> DETECTED AUTO-COMPLETION: $charCountChange non-whitespace chars, content='${event.newFragment}'")
                            
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
                            
                            // Display explicit debug info about the state update
                            LOG.info(">>> Stats updated - Added auto-completion event: charCount=$charCountChange, " +
                                    "totalEvents=${stateService.getAutoCompletionEventQueue().size}, " +
                                    "totalChars=${stateService.getCharCount()}")
                            
                            // Force UI refresh immediately after auto-completion
                            ApplicationManager.getApplication().invokeLater {
                                try {
                                    // This will trigger a UI update in the tool window
                                    LOG.info(">>> Updating UI after auto-completion: $charCountChange chars added")
                                    statsService.notifyStatsChanged()
                                } catch (e: Exception) {
                                    LOG.error(">>> Error refreshing UI after auto-completion: ${e.message}", e)
                                }
                            }
                        } else {
                            LOG.warn(">>> Auto-completion detected but no non-whitespace characters found: '${event.newFragment}'")
                        }
                    }
                    TextChangeType.HAND_WRITTEN_CHAR -> {
                        LOG.info(">>> DETECTED HAND-WRITTEN CHAR: '${event.newFragment}'")
                        
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
                        
                        LOG.info(">>> Hand-written event added, total=${stateService.getHandWrittenEventQueue().size}")
                    }
                    else -> {
                        LOG.info(">>> Change type $changeType not tracked")
                    }
                }
            } catch (e: Exception) {
                LOG.error(">>> Error processing document change: ${e.message}", e)
                e.printStackTrace()
            }
        }
    }
    
    private fun calculateCharDifference(oldText: String, newText: String): Int {
        if (oldText.length >= newText.length) return 0
        return newText.length - oldText.length
    }
    
    private fun classifyTextChange(event: DocumentEvent, previousText: String, currentText: String): TextChangeType {
        val newText = event.newFragment.toString()
        val oldText = event.oldFragment.toString()
        
        // Debug logging for diagnosing issues
        LOG.debug(">>> Change classification - oldText: '$oldText', newText: '$newText', " +
                  "offset: ${event.offset}, oldLength: ${event.oldLength}, " +
                  "prevTextLen: ${previousText.length}, currTextLen: ${currentText.length}")
        
        // Match VSCode extension's change classification logic
        
        // Check for deletion (content removed)
        if (oldText.isNotEmpty() && newText.isEmpty()) {
            return TextChangeType.DELETION
        }
        
        // Check for single character typed
        if (newText.length == 1 && oldText.isEmpty()) {
            return TextChangeType.HAND_WRITTEN_CHAR
        }
        
        // Check for whitespace only
        if (newText.isNotEmpty() && newText.trim().isEmpty()) {
            return TextChangeType.SPACE
        }
        
        // Check for auto-closing brackets
        if (newText.length == 2 && oldText.isEmpty() && autoBracketPairs.contains(newText)) {
            return TextChangeType.AUTO_CLOSE_BRACKET
        }
        
        // Most importantly: Check for Copilot auto-completion
        // This is the key part that aligns with VSCode extension logic
        if (newText.isNotEmpty()) {
            // Get non-whitespace content
            val nonWhitespaceContent = newText.replace("\\s".toRegex(), "")
            
            // If there's actual content (not just whitespace) and the document grew larger
            if (nonWhitespaceContent.isNotEmpty() && currentText.length > previousText.length) {
                // Check for common code patterns that suggest Copilot completions
                if (newText.length > 3 || 
                    newText.contains("\n") || 
                    nonWhitespaceContent.contains("{") || 
                    nonWhitespaceContent.contains("}") || 
                    nonWhitespaceContent.contains("(") || 
                    nonWhitespaceContent.contains(")") ||
                    nonWhitespaceContent.contains(";") ||
                    newText.contains("=>") ||
                    newText.contains("->")
                ) {
                    LOG.debug(">>> Detected code-like structure in completion: '$newText'")
                    return TextChangeType.AUTO_COMPLETION
                }
                
                // Check for multi-word completions (likely Copilot)
                if (newText.contains(" ") && newText.length > 5) {
                    LOG.debug(">>> Detected multi-word completion: '$newText'")
                    return TextChangeType.AUTO_COMPLETION
                }
                
                // Check for substantial completion (just based on size)
                if (nonWhitespaceContent.length > 2) {
                    LOG.debug(">>> Detected substantial completion: '$newText'")
                    return TextChangeType.AUTO_COMPLETION
                }
            }
        }
        
        // Default case
        return TextChangeType.UNKNOWN
    }
} 