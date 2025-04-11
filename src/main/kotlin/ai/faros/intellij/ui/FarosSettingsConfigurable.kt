package ai.faros.intellij.ui

import ai.faros.intellij.services.FarosSettingsService
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import org.jetbrains.annotations.Nls
import javax.swing.*
import java.awt.*

class FarosSettingsConfigurable : Configurable {
    private lateinit var mainPanel: JPanel
    private lateinit var apiKeyField: JTextField
    private lateinit var vcsUidField: JTextField
    private lateinit var vcsEmailField: JTextField
    private lateinit var vcsNameField: JTextField
    private lateinit var urlField: JTextField
    private lateinit var webhookField: JTextField
    private lateinit var graphField: JTextField
    private lateinit var batchSizeField: JTextField
    private lateinit var batchIntervalField: JTextField

    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String {
        return "Faros AI Settings"
    }

    override fun createComponent(): JComponent {
        mainPanel = JPanel()
        mainPanel.layout = GridBagLayout()
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.insets = Insets(5, 5, 5, 5)

        addField(mainPanel, gbc, "API Key:", JTextField().also { apiKeyField = it })
        addField(mainPanel, gbc, "VCS User ID:", JTextField().also { vcsUidField = it })
        addField(mainPanel, gbc, "VCS Email:", JTextField().also { vcsEmailField = it })
        addField(mainPanel, gbc, "VCS Name:", JTextField().also { vcsNameField = it })
        addField(mainPanel, gbc, "Faros AI URL:", JTextField().also { urlField = it })
        addField(mainPanel, gbc, "Webhook URL:", JTextField().also { webhookField = it })
        addField(mainPanel, gbc, "Graph:", JTextField().also { graphField = it })
        addField(mainPanel, gbc, "Batch Size:", JTextField().also { batchSizeField = it })
        addField(mainPanel, gbc, "Batch Interval (ms):", JTextField().also { batchIntervalField = it })

        // Add space at the bottom
        gbc.gridy++
        gbc.weighty = 1.0
        mainPanel.add(JPanel(), gbc)

        // Load settings
        reset()

        return mainPanel
    }

    private fun addField(panel: JPanel, gbc: GridBagConstraints, label: String, field: JTextField) {
        val jLabel = JLabel(label)
        gbc.gridx = 0
        gbc.weightx = 0.2
        panel.add(jLabel, gbc)

        gbc.gridx = 1
        gbc.weightx = 0.8
        panel.add(field, gbc)
        gbc.gridy++
    }

    override fun isModified(): Boolean {
        val settings = FarosSettingsService.getInstance()
        return apiKeyField.text != settings.apiKey ||
                vcsUidField.text != settings.vcsUid ||
                vcsEmailField.text != settings.vcsEmail ||
                vcsNameField.text != settings.vcsName ||
                urlField.text != settings.url ||
                webhookField.text != settings.webhook ||
                graphField.text != settings.graph ||
                batchSizeField.text != settings.batchSize.toString() ||
                batchIntervalField.text != settings.batchInterval.toString()
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        val settings = FarosSettingsService.getInstance()
        settings.apiKey = apiKeyField.text
        settings.vcsUid = vcsUidField.text
        settings.vcsEmail = vcsEmailField.text
        settings.vcsName = vcsNameField.text
        settings.url = urlField.text
        settings.webhook = webhookField.text
        settings.graph = graphField.text
        try {
            settings.batchSize = batchSizeField.text.toInt()
            settings.batchInterval = batchIntervalField.text.toInt()
        } catch (e: NumberFormatException) {
            throw ConfigurationException("Batch size and interval must be valid integers")
        }
    }

    override fun reset() {
        val settings = FarosSettingsService.getInstance()
        apiKeyField.text = settings.apiKey
        vcsUidField.text = settings.vcsUid
        vcsEmailField.text = settings.vcsEmail
        vcsNameField.text = settings.vcsName
        urlField.text = settings.url
        webhookField.text = settings.webhook
        graphField.text = settings.graph
        batchSizeField.text = settings.batchSize.toString()
        batchIntervalField.text = settings.batchInterval.toString()
    }
} 