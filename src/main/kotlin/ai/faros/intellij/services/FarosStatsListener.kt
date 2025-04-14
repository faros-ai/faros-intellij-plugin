package ai.faros.intellij.services

import com.intellij.util.messages.Topic

/**
 * Listener interface for Faros statistics change events
 */
interface FarosStatsListener {
    companion object {
        val TOPIC = Topic.create("Faros statistics changed", FarosStatsListener::class.java)
    }
    
    /**
     * Called when statistics have been updated
     */
    fun statsChanged()
    /**
     * Called when statistics have been sent
     */
    fun statsSent()
    /**
     * Called when statistics have been cleared
     */
    fun statsCleared()
    /**
     * Called when statistics have been reset
     */
    fun statsReset()
    /**
     * Called when statistics have been sent to the server
     */
    fun statsSentToServer()
} 