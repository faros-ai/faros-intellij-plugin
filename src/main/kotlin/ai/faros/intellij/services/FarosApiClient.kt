package ai.faros.intellij.services

import ai.faros.intellij.model.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.diagnostic.Logger
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

object FarosApiClient {
    private val LOG = Logger.getInstance(FarosApiClient::class.java)
    private val GSON: Gson = GsonBuilder().create()
    private val ISO_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    
    init {
        ISO_DATE_FORMAT.timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Sends events to the Faros API using GraphQL mutations
     *
     * @param events The list of events to send
     * @param category The category of events
     * @return true if successful, false otherwise
     */
    @JvmStatic
    fun sendEvents(events: List<CodingEvent>, category: String): Boolean {
        if (events.isEmpty()) {
            return true
        }

        val settings = FarosSettingsService.getInstance()
        val apiKey = settings.apiKey
        if (apiKey.isNullOrEmpty()) {
            LOG.warn("No API key configured for Faros API")
            return false
        }
        
        val graph = settings.graph
        
        try {
            // Create mutations similar to VS code extension
            val mutations = createMutations(events, category, settings)
            
            // Send batches of mutations
            val batchSize = settings.batchSize
            val batches = mutations.chunked(batchSize)
            
            LOG.info("Sending ${mutations.size} mutations in ${batches.size} batches")
            
            for ((index, batch) in batches.withIndex()) {
                LOG.info("Sending batch ${index + 1} of ${batches.size}")
                val success = sendMutationBatch(batch, graph, apiKey, settings.url)
                if (!success) {
                    LOG.error("Failed to send batch ${index + 1}")
                    return false
                }
            }
            
            return true
        } catch (e: Exception) {
            LOG.error("Error creating or sending mutations: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Create Faros mutations from coding events, matching VS Code approach
     */
    private fun createMutations(events: List<CodingEvent>, category: String, settings: FarosSettingsService): List<FarosMutation> {
        val mutations = mutableListOf<FarosMutation>()
        val origin = settings.origin

        fun ref(model: Map<String, Any>): Map<String, Any> = mapOf("ref" to model)

        // Insert vcs_User
        val vcsUser = mapOf(
            "uid" to settings.vcsUid,
            "name" to settings.vcsName,
            "email" to settings.vcsEmail,
            "source" to settings.userSource
        )
        mutations.add(
            FarosMutation.insert(
                model = "vcs_User",
                objects = listOf(vcsUser),
                onConflict = mapOf(
                    "constraint" to "vcs_User_pkey",
                    "update_columns" to listOf("refreshedAt")
                )
            )
        )

        // Insert vcs_UserTool
        val vcsUserTool = mapOf(
            "tool" to mapOf("category" to category),
            "user" to ref(mapOf("vcs_User" to vcsUser))
        )
        mutations.add(
            FarosMutation.insert(
                model = "vcs_UserTool",
                objects = listOf(vcsUserTool),
                onConflict = mapOf(
                    "constraint" to "vcs_UserTool_pkey",
                    "update_columns" to listOf("refreshedAt")
                )
            )
        )

        for (event in events) {
            var vcsRepository: Map<String, Any>? = null
            var vcsBranch: Map<String, Any>? = null
            var vcsFile: Map<String, Any>? = null

            if (event.repository.isNotEmpty() && event.repository != "unknown") {
                vcsRepository = mapOf("name" to event.repository)
                mutations.add(
                    FarosMutation.insert(
                        model = "vcs_Repository",
                        objects = listOf(vcsRepository),
                        onConflict = mapOf(
                            "constraint" to "vcs_Repository_pkey",
                            "update_columns" to listOf("refreshedAt")
                        )
                    )
                )

                if (event.branch.isNotEmpty() && event.branch != "unknown") {
                    vcsBranch = mapOf(
                        "name" to event.branch,
                        "repository" to ref(mapOf("vcs_Repository" to vcsRepository))
                    )
                    mutations.add(
                        FarosMutation.insert(
                            model = "vcs_Branch",
                            objects = listOf(vcsBranch),
                            onConflict = mapOf(
                                "constraint" to "vcs_Branch_pkey",
                                "update_columns" to listOf("refreshedAt")
                            )
                        )
                    )
                }
            }

            if (event.filename.isNotEmpty()) {
                vcsFile = mapOf(
                    "path" to event.filename,
                    "extension" to (if (event.extension.isNotEmpty()) event.extension else event.language),
                    "uid" to event.filename
                )
                mutations.add(
                    FarosMutation.insert(
                        model = "vcs_File",
                        objects = listOf(vcsFile),
                        onConflict = mapOf(
                            "constraint" to "vcs_File_pkey",
                            "update_columns" to listOf("refreshedAt")
                        )
                    )
                )
            }

            val vcsUserToolUsage = mutableMapOf<String, Any>(
                "usedAt" to ISO_DATE_FORMAT.format(event.timestamp),
                "userTool" to ref(mapOf("vcs_UserTool" to vcsUserTool)),
                "charactersAdded" to event.charCountChange
            )

            vcsRepository?.let {
                vcsUserToolUsage["repository"] = ref(mapOf("vcs_Repository" to it))
            }

            vcsBranch?.let {
                vcsUserToolUsage["branch"] = ref(mapOf("vcs_Branch" to it))
            }

            vcsFile?.let {
                vcsUserToolUsage["file"] = ref(mapOf("vcs_File" to it))
            }

            mutations.add(
                FarosMutation.insert(
                    model = "vcs_UserToolUsage",
                    objects = listOf(vcsUserToolUsage),
                    onConflict = mapOf(
                        "constraint" to "vcs_UserToolUsage_pkey",
                        "update_columns" to listOf("refreshedAt")
                    )
                )
            )
        }

        return mutations
    }
    
    /**
     * Send a batch of mutations to the Faros API (matching VS Code's sendToFaros function)
     */
    private fun sendMutationBatch(mutations: List<FarosMutation>, graph: String, apiKey: String, baseUrl: String): Boolean {
        val graphqlEndpoint = "$baseUrl/graphs/$graph/graphql"
        LOG.info("Sending ${mutations.size} mutations to Faros API...")
        
        try {
            val url = URL(graphqlEndpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", apiKey)
            conn.doOutput = true
            
            // Create the batch mutation query
            val query = buildBatchMutationQuery(mutations)


            val graphQLRequest = GraphQLMutation(query)
            val jsonPayload = GSON.toJson(graphQLRequest)

            LOG.info("GraphQL Query: $query")
            LOG.info("Sending to: $graphqlEndpoint")
            LOG.info("Headers: ${conn.requestProperties}")
            LOG.info("Payload: $jsonPayload")
            
            conn.outputStream.use { os ->
                val input = jsonPayload.toByteArray(StandardCharsets.UTF_8)
                os.write(input, 0, input.size)
            }
            
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                return true
            } else {
                val errorResponse = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                LOG.warn("Failed to send mutations to Faros API.")
                LOG.warn("HTTP Response Code: $responseCode")
                LOG.warn("Response Body: $errorResponse")
                LOG.warn("Payload: $jsonPayload")
                return false
            }
        } catch (e: IOException) {
            LOG.error("IOException occurred while sending mutations: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Build a GraphQL mutation query from a list of mutations (equivalent to VS Code's batchMutation function)
     */
    private fun buildBatchMutationQuery(mutations: List<FarosMutation>): String {
        val mutationStrings = mutations.mapIndexed { index, mutation ->
            val modelJsonStr = GSON.toJson(mutation.data)
            "m$index: ${mutation.type}_${mutation.model}($modelJsonStr) { affected_rows }"
        }
        
        return "mutation { ${mutationStrings.joinToString(" ")} }"
    }

    /**
     * Get the current date in ISO 8601 format
     */
    @JvmStatic
    fun getCurrentDate(): String {
        return ISO_DATE_FORMAT.format(Date())
    }
    
}