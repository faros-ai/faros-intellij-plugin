package ai.faros.intellij.ui

import ai.faros.intellij.services.FarosStateService
import ai.faros.intellij.services.FarosStatsService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import org.jetbrains.annotations.NotNull
import java.awt.*
import javax.swing.*
import java.util.Timer
import java.util.TimerTask
import java.awt.geom.Rectangle2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.border.EmptyBorder
import java.awt.image.BufferedImage
import java.text.DecimalFormat

class FarosToolWindowFactory : ToolWindowFactory {
    private val LOG = Logger.getInstance(FarosToolWindowFactory::class.java)
    private lateinit var refreshTimer: Timer
    private var toolWindowContent: FarosToolWindowContent? = null

    override fun createToolWindowContent(@NotNull project: Project, @NotNull toolWindow: ToolWindow) {
        try {
            LOG.info("Creating Faros AI tool window content")
            toolWindowContent = FarosToolWindowContent()
            val content = ContentFactory.getInstance().createContent(
                    toolWindowContent!!.getContentPanel(), "", false)
            toolWindow.contentManager.addContent(content)

            // Set up UI refresh timer - refresh more frequently to show updates quickly
            refreshTimer = Timer(true)
            refreshTimer.scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    try {
                        SwingUtilities.invokeLater { toolWindowContent?.refreshStats() }
                    } catch (e: Exception) {
                        LOG.error("Error refreshing stats: ${e.message}", e)
                    }
                }
            }, 0, 2000) // Refresh every 2 seconds
            
            LOG.info("Faros AI tool window created successfully")
        } catch (e: Exception) {
            LOG.error("Error creating tool window: ${e.message}", e)
        }
    }

    private inner class FarosToolWindowContent {
        private val contentPanel: JPanel
        private val statsService = FarosStatsService.getInstance()
        private val stackedBarChart = StackedBarChart()
        private val overviewPanel = JPanel(GridLayout(3, 2, 10, 5))
        private val detailsPanel = JPanel(GridLayout(3, 4, 10, 5))
        private val detailsContainer = JPanel(BorderLayout())
        private val topReposPanel = JPanel(GridLayout(0, 2, 10, 5))
        private val topLangsPanel = JPanel(GridLayout(0, 2, 10, 5))
        private var showDetails = false

        // Labels for overview section
        private val totalCompletionsLabel = JBLabel("0")
        private val timeSavedLabel = JBLabel("0m")
        private val completionRatioLabel = JBLabel("N/A")

        // Labels for detailed breakdown
        private val todayCompletionsLabel = JBLabel("0")
        private val todayTimeSavedLabel = JBLabel("0m")
        private val todayRatioLabel = JBLabel("N/A")
        private val weekCompletionsLabel = JBLabel("0")
        private val weekTimeSavedLabel = JBLabel("0m")
        private val weekRatioLabel = JBLabel("N/A")
        private val monthCompletionsLabel = JBLabel("0")
        private val monthTimeSavedLabel = JBLabel("0m")
        private val monthRatioLabel = JBLabel("N/A")

        // Details toggle
        private val detailsToggleButton = JButton("Detailed Breakdown")
        private val detailsToggleIcon = JLabel()

        init {
            contentPanel = JPanel()
            contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)

            // Configure the main panel
            val mainScrollPane = JBScrollPane(
                JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(createStatsPanel())
                    add(createTopReposPanel())
                    add(createTopLangsPanel())
                }
            )
            contentPanel.add(mainScrollPane)

            // Initial refresh
            refreshStats()
        }

        private fun createStatsPanel(): JPanel {
            val panel = JPanel(BorderLayout())
            panel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                EmptyBorder(10, 10, 10, 10)
            )

            // Create title section
            val titlePanel = JPanel(BorderLayout())
            val title = JLabel("My Stats")
            title.font = title.font.deriveFont(Font.BOLD, 16f)
            titlePanel.add(title, BorderLayout.NORTH)
            
            val subtitle = JLabel("Overview of my auto-completion usage")
            subtitle.font = subtitle.font.deriveFont(Font.PLAIN, 12f)
            subtitle.foreground = Color.GRAY
            titlePanel.add(subtitle, BorderLayout.CENTER)
            
            panel.add(titlePanel, BorderLayout.NORTH)

            // Add chart panel
            panel.add(stackedBarChart, BorderLayout.CENTER)

            // Configure overview grid
            overviewPanel.border = EmptyBorder(10, 0, 10, 0)
            
            // Add icons and labels to overview grid
            overviewPanel.add(FarosUIUtil.createLabelWithIcon(FarosUIUtil.EVENT_COUNT_ICON, "Total Auto-completions"))
            overviewPanel.add(totalCompletionsLabel)
            
            overviewPanel.add(FarosUIUtil.createLabelWithIcon(FarosUIUtil.TIME_SAVED_ICON, "Time saved"))
            overviewPanel.add(timeSavedLabel)
            
            overviewPanel.add(FarosUIUtil.createLabelWithIcon(FarosUIUtil.PERCENTAGE_ICON, "Auto-completed ratio"))
            overviewPanel.add(completionRatioLabel)
            
            panel.add(overviewPanel, BorderLayout.SOUTH)

            // Add details toggle button
            val detailsTogglePanel = JPanel(FlowLayout(FlowLayout.LEFT))
            
            detailsToggleIcon.icon = FarosUIUtil.CHEVRON_RIGHT_ICON
            detailsTogglePanel.add(detailsToggleIcon)
            
            detailsToggleButton.setBorderPainted(false)
            detailsToggleButton.setContentAreaFilled(false)
            detailsToggleButton.foreground = FarosUIUtil.LINK_COLOR
            detailsToggleButton.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            detailsToggleButton.addActionListener {
                showDetails = !showDetails
                detailsToggleIcon.icon = if (showDetails) FarosUIUtil.CHEVRON_DOWN_ICON else FarosUIUtil.CHEVRON_RIGHT_ICON
                detailsContainer.isVisible = showDetails
                panel.revalidate()
                panel.repaint()
            }
            detailsTogglePanel.add(detailsToggleButton)
            
            // Configure details panel
            detailsPanel.border = EmptyBorder(5, 0, 5, 0)
            
            // Day row
            detailsPanel.add(FarosUIUtil.createLabelWithIcon(FarosUIUtil.CALENDAR_DAY_ICON, ""))
            detailsPanel.add(todayCompletionsLabel)
            detailsPanel.add(todayTimeSavedLabel)
            detailsPanel.add(todayRatioLabel)
            
            // Week row
            detailsPanel.add(FarosUIUtil.createLabelWithIcon(FarosUIUtil.CALENDAR_WEEK_ICON, ""))
            detailsPanel.add(weekCompletionsLabel)
            detailsPanel.add(weekTimeSavedLabel)
            detailsPanel.add(weekRatioLabel)
            
            // Month row
            detailsPanel.add(FarosUIUtil.createLabelWithIcon(FarosUIUtil.CALENDAR_MONTH_ICON, ""))
            detailsPanel.add(monthCompletionsLabel)
            detailsPanel.add(monthTimeSavedLabel)
            detailsPanel.add(monthRatioLabel)
            
            detailsContainer.add(detailsPanel, BorderLayout.CENTER)
            detailsContainer.isVisible = false
            
            val bottomPanel = JPanel(BorderLayout())
            bottomPanel.add(detailsTogglePanel, BorderLayout.NORTH)
            bottomPanel.add(detailsContainer, BorderLayout.SOUTH)
            
            panel.add(bottomPanel, BorderLayout.PAGE_END)
            
            return panel
        }
        
        private fun createTopReposPanel(): JPanel {
            val panel = JPanel(BorderLayout())
            panel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                EmptyBorder(10, 10, 10, 10)
            )
            
            // Title section
            val titlePanel = JPanel(BorderLayout())
            val title = JLabel("Top Repositories")
            title.font = title.font.deriveFont(Font.BOLD, 16f)
            titlePanel.add(title, BorderLayout.NORTH)
            
            val subtitle = JLabel("Repositories with the highest auto-completion")
            subtitle.font = subtitle.font.deriveFont(Font.PLAIN, 12f)
            subtitle.foreground = Color.GRAY
            titlePanel.add(subtitle, BorderLayout.CENTER)
            
            panel.add(titlePanel, BorderLayout.NORTH)
            
            // Configure repos grid
            topReposPanel.border = EmptyBorder(10, 0, 10, 0)
            
            // Add a placeholder when empty
            val emptyReposLabel = JLabel("N/A")
            topReposPanel.add(emptyReposLabel)
            
            panel.add(topReposPanel, BorderLayout.CENTER)
            
            return panel
        }
        
        private fun createTopLangsPanel(): JPanel {
            val panel = JPanel(BorderLayout())
            panel.border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY),
                EmptyBorder(10, 10, 10, 10)
            )
            
            // Title section
            val titlePanel = JPanel(BorderLayout())
            val title = JLabel("Top Languages")
            title.font = title.font.deriveFont(Font.BOLD, 16f)
            titlePanel.add(title, BorderLayout.NORTH)
            
            val subtitle = JLabel("Languages with the highest auto-completion")
            subtitle.font = subtitle.font.deriveFont(Font.PLAIN, 12f)
            subtitle.foreground = Color.GRAY
            titlePanel.add(subtitle, BorderLayout.CENTER)
            
            panel.add(titlePanel, BorderLayout.NORTH)
            
            // Configure languages grid
            topLangsPanel.border = EmptyBorder(10, 0, 10, 0)
            
            // Add a placeholder when empty
            val emptyLangsLabel = JLabel("N/A")
            topLangsPanel.add(emptyLangsLabel)
            
            panel.add(topLangsPanel, BorderLayout.CENTER)
            
            return panel
        }
        
        private fun createLabelWithIcon(icon: JLabel, text: String): JPanel {
            val panel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
            panel.add(icon)
            
            val label = JLabel(text)
            label.font = label.font.deriveFont(Font.PLAIN, 12f)
            panel.add(label)
            
            return panel
        }
        
        fun getContentPanel(): JPanel {
            return contentPanel
        }
        
        fun refreshStats() {
            try {
                // Get statistics from the service
                val stats = statsService.getCompletionStats()
                val ratios = statsService.getCompletionRatios()
                val topRepos = statsService.getTopRepositories()
                val topLangs = statsService.getTopLanguages()
                val chartData = statsService.getHourlyChartData()
                
                // Update chart data
                stackedBarChart.updateData(chartData)
                
                // Update overview section
                val totalStats = stats["total"] as Map<*, *>
                totalCompletionsLabel.text = (totalStats["count"] as Int).toString()
                timeSavedLabel.text = FarosUIUtil.formatTimeSaved(totalStats["timeSaved"] as Double)
                completionRatioLabel.text = FarosUIUtil.formatPercentage(ratios["total"] as Double)
                
                // Update detailed breakdown section
                val todayStats = stats["today"] as Map<*, *>
                todayCompletionsLabel.text = (todayStats["count"] as Int).toString()
                todayTimeSavedLabel.text = FarosUIUtil.formatTimeSaved(todayStats["timeSaved"] as Double)
                todayRatioLabel.text = FarosUIUtil.formatPercentage(ratios["today"] as Double)
                
                val weekStats = stats["thisWeek"] as Map<*, *>
                weekCompletionsLabel.text = (weekStats["count"] as Int).toString()
                weekTimeSavedLabel.text = FarosUIUtil.formatTimeSaved(weekStats["timeSaved"] as Double)
                weekRatioLabel.text = FarosUIUtil.formatPercentage(ratios["thisWeek"] as Double)
                
                val monthStats = stats["thisMonth"] as Map<*, *>
                monthCompletionsLabel.text = (monthStats["count"] as Int).toString()
                monthTimeSavedLabel.text = FarosUIUtil.formatTimeSaved(monthStats["timeSaved"] as Double)
                monthRatioLabel.text = FarosUIUtil.formatPercentage(ratios["thisMonth"] as Double)
                
                // Update top repositories
                topReposPanel.removeAll()
                if (topRepos.isEmpty()) {
                    topReposPanel.add(JLabel("N/A"))
                } else {
                    for (i in topRepos.indices) {
                        val repo = topRepos[i]
                        val repoName = repo["repository"] as String
                        val repoCount = repo["count"] as Int
                        
                        // Use repository icon with different index for variation
                        topReposPanel.add(FarosUIUtil.createLabelWithIcon(FarosUIUtil.REPOSITORY_ICON, repoName))
                        topReposPanel.add(JLabel(repoCount.toString()))
                    }
                }
                
                // Update top languages
                topLangsPanel.removeAll()
                if (topLangs.isEmpty()) {
                    topLangsPanel.add(JLabel("N/A"))
                } else {
                    for (i in topLangs.indices) {
                        val lang = topLangs[i]
                        val langName = lang["language"] as String
                        val langCount = lang["count"] as Int
                        
                        // Use language icon with different index for variation
                        topLangsPanel.add(FarosUIUtil.createLabelWithIcon(FarosUIUtil.LANGUAGE_ICON, langName))
                        topLangsPanel.add(JLabel(langCount.toString()))
                    }
                }
                
                // Force UI to repaint
                contentPanel.revalidate()
                contentPanel.repaint()
            } catch (e: Exception) {
                LOG.error("Error refreshing stats: ${e.message}", e)
            }
        }
    }
    
    /**
     * Custom component for rendering a stacked bar chart
     */
    private inner class StackedBarChart : JPanel() {
        private var data: List<Map<String, Any>> = emptyList()
        private val barColor = FarosUIUtil.SECONDARY_COLOR // Gray for hand-written
        private val accentColor = FarosUIUtil.ACCENT_COLOR // Orange for auto-completions
        private val tooltipPanel = JPanel()
        private var tooltipWindow: JWindow? = null
        
        init {
            preferredSize = Dimension(500, 50)
            tooltipPanel.layout = BoxLayout(tooltipPanel, BoxLayout.Y_AXIS)
            tooltipPanel.background = Color(50, 50, 50)
            tooltipPanel.border = BorderFactory.createLineBorder(Color.DARK_GRAY)
            
            addMouseListener(object : MouseAdapter() {
                override fun mouseExited(e: MouseEvent) {
                    hideTooltip()
                }
            })
            
            addMouseMotionListener(object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    val barWidth = (width / (data.size.coerceAtLeast(1))).toFloat()
                    val barIndex = (e.x / barWidth).toInt()
                    
                    if (barIndex >= 0 && barIndex < data.size) {
                        showTooltip(barIndex, e.locationOnScreen)
                    } else {
                        hideTooltip()
                    }
                }
            })
        }
        
        fun updateData(newData: List<Map<String, Any>>) {
            data = newData
            repaint()
        }
        
        private fun showTooltip(index: Int, location: Point) {
            if (data.isEmpty() || index >= data.size) return
            
            if (tooltipWindow == null) {
                tooltipWindow = JWindow()
                tooltipWindow?.contentPane?.add(tooltipPanel)
            }
            
            val item = data[index]
            val label = item["label"] as String
            val values = item["values"] as List<*>
            val autoCompleted = values[0] as Int
            val handwritten = values[1] as Int
            val total = autoCompleted + handwritten
            
            tooltipPanel.removeAll()
            
            // Title
            val titleLabel = JLabel(label)
            titleLabel.foreground = Color.WHITE
            titleLabel.border = EmptyBorder(5, 5, 5, 5)
            titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
            tooltipPanel.add(titleLabel)
            
            // Handwritten section
            val handwrittenPct = if (total > 0) (handwritten.toDouble() / total * 100).toInt() else 0
            val handwrittenLabel = JLabel("Handwritten:")
            handwrittenLabel.foreground = Color.WHITE
            handwrittenLabel.border = EmptyBorder(0, 5, 0, 5)
            tooltipPanel.add(handwrittenLabel)
            
            val handwrittenValueLabel = JLabel("  • $handwritten chars ($handwrittenPct%)")
            handwrittenValueLabel.foreground = Color.WHITE
            handwrittenValueLabel.border = EmptyBorder(0, 5, 5, 5)
            tooltipPanel.add(handwrittenValueLabel)
            
            // Auto-completed section
            val autoCompletedPct = if (total > 0) (autoCompleted.toDouble() / total * 100).toInt() else 0
            val autoCompletedLabel = JLabel("Auto-completed:")
            autoCompletedLabel.foreground = Color.WHITE
            autoCompletedLabel.border = EmptyBorder(0, 5, 0, 5)
            tooltipPanel.add(autoCompletedLabel)
            
            val autoCompletedValueLabel = JLabel("  • $autoCompleted chars ($autoCompletedPct%)")
            autoCompletedValueLabel.foreground = Color.WHITE
            autoCompletedValueLabel.border = EmptyBorder(0, 5, 5, 5)
            tooltipPanel.add(autoCompletedValueLabel)
            
            tooltipWindow?.pack()
            
            // Position tooltip near cursor
            val x = location.x + 15
            val y = location.y - tooltipWindow!!.height - 5
            tooltipWindow?.location = Point(x, y)
            tooltipWindow?.isVisible = true
        }
        
        private fun hideTooltip() {
            tooltipWindow?.isVisible = false
        }
        
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            
            val g2d = g as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            
            if (data.isEmpty()) return
            
            val barWidth = (width / data.size).toFloat()
            val barPadding = barWidth * 0.3f
            val adjustedBarWidth = barWidth - barPadding
            
            // Find the maximum value to scale the bars
            var maxValue = 0
            for (item in data) {
                val values = item["values"] as List<*>
                val sum = (values[0] as Int) + (values[1] as Int)
                if (sum > maxValue) maxValue = sum
            }
            
            // Draw each bar
            for (i in data.indices) {
                val item = data[i]
                val values = item["values"] as List<*>
                val handwritten = values[1] as Int
                val autoCompleted = values[0] as Int
                val total = handwritten + autoCompleted
                
                val x = i * barWidth + barPadding / 2
                
                if (total > 0) {
                    val scale = height.toFloat() / maxValue
                    
                    // Draw handwritten portion (bottom)
                    val handwrittenHeight = (handwritten * scale).toInt()
                    g2d.color = barColor
                    g2d.fillRect(
                        x.toInt(),
                        height - handwrittenHeight,
                        adjustedBarWidth.toInt(),
                        handwrittenHeight
                    )
                    
                    // Draw auto-completed portion (top)
                    val autoCompletedHeight = (autoCompleted * scale).toInt()
                    g2d.color = accentColor
                    g2d.fillRect(
                        x.toInt(),
                        height - handwrittenHeight - autoCompletedHeight,
                        adjustedBarWidth.toInt(),
                        autoCompletedHeight
                    )
                }
                
                // Draw hour label if there's room
                if (barWidth > 20) {
                    val label = item["label"] as String
                    val metrics = g2d.fontMetrics
                    val labelWidth = metrics.stringWidth(label)
                    
                    if (labelWidth < barWidth) {
                        g2d.color = Color.DARK_GRAY
                        g2d.drawString(
                            label,
                            x.toInt() + (adjustedBarWidth.toInt() - labelWidth) / 2,
                            height - 2
                        )
                    }
                }
            }
        }
    }
} 