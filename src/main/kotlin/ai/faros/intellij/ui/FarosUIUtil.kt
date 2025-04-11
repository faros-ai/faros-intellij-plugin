package ai.faros.intellij.ui

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.IconLoader
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon
import javax.swing.JLabel

/**
 * Utility class for Faros UI components including icons and common UI elements
 */
object FarosUIUtil {
    private val LOG = Logger.getInstance(FarosUIUtil::class.java)
    
    // Colors
    val ACCENT_COLOR = Color(227, 121, 51) // Orange for Faros
    val SECONDARY_COLOR = Color(160, 160, 160) // Gray for secondary elements
    val LINK_COLOR = Color(53, 120, 229) // Blue for links
    
    // Icons
    val EVENT_COUNT_ICON = createTextIcon("●", ACCENT_COLOR)
    val TIME_SAVED_ICON = createTextIcon("⏱", ACCENT_COLOR)
    val PERCENTAGE_ICON = createTextIcon("%", ACCENT_COLOR)
    val REPOSITORY_ICON = createTextIcon("◆", ACCENT_COLOR)
    val LANGUAGE_ICON = createTextIcon("⌘", ACCENT_COLOR)
    val CALENDAR_DAY_ICON = createTextIcon("1d", SECONDARY_COLOR)
    val CALENDAR_WEEK_ICON = createTextIcon("1w", SECONDARY_COLOR)
    val CALENDAR_MONTH_ICON = createTextIcon("1m", SECONDARY_COLOR)
    val CHEVRON_RIGHT_ICON = createTextIcon("▶", LINK_COLOR)
    val CHEVRON_DOWN_ICON = createTextIcon("▼", LINK_COLOR)
    
    /**
     * Create an icon from text with a custom color
     */
    private fun createTextIcon(text: String, color: Color): Icon {
        return object : Icon {
            override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
                val g2d = g as Graphics2D
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g2d.color = color
                
                val fm = when (c) {
                    is JLabel -> c.getFontMetrics(c.font)
                    else -> g2d.getFontMetrics()
                }
                val textY = y + fm.ascent - 1
                
                g2d.drawString(text, x, textY)
            }
            
            override fun getIconWidth(): Int {
                return 16
            }
            
            override fun getIconHeight(): Int {
                return 16
            }
        }
    }
    
    /**
     * Create a label with an icon
     */
    fun createLabelWithIcon(icon: Icon, text: String): JLabel {
        val label = JLabel(text)
        label.icon = icon
        label.iconTextGap = 5
        return label
    }
    
    /**
     * Format a time saved value to a readable string (e.g., "2h 30m" or "45m")
     */
    fun formatTimeSaved(minutes: Double): String {
        val hours = (minutes / 60).toInt()
        val remainingMinutes = Math.round(minutes % 60).toInt()
        
        return if (hours > 0) {
            "${hours}h ${remainingMinutes}m"
        } else {
            "${remainingMinutes}m"
        }
    }
    
    /**
     * Format a percentage value to a readable string (e.g., "95%")
     */
    fun formatPercentage(ratio: Double): String {
        return if (ratio > 0) {
            "${(ratio * 100).toInt()}%"
        } else {
            "N/A"
        }
    }
} 