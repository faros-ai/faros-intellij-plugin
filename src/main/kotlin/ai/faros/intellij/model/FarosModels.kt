package ai.faros.intellij.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a reference to another entity in the Faros graph
 */
data class FarosRef(
    val ref: Map<String, Any>
) {
    companion object {
        fun create(modelName: String, model: Any): FarosRef {
            return FarosRef(mapOf(modelName to model))
        }
    }
}

/**
 * Represents a vcs_User entity
 */
data class VcsUser(
    val uid: String,
    val name: String,
    val email: String,
    val source: String
)

/**
 * Represents a vcs_UserTool entity
 */
data class VcsUserTool(
    val tool: Map<String, String>,
    val user: FarosRef
)

/**
 * Represents a vcs_Repository entity
 */
data class VcsRepository(
    val name: String
)

/**
 * Represents a vcs_Branch entity
 */
data class VcsBranch(
    val name: String,
    val repository: FarosRef
)

/**
 * Represents a vcs_File entity
 */
data class VcsFile(
    val path: String,
    val extension: String,
    val uid: String
)

/**
 * Represents a vcs_UserToolUsage entity
 */
data class VcsUserToolUsage(
    val usedAt: String,
    val userTool: FarosRef,
    val charactersAdded: Int,
    val repository: FarosRef? = null,
    val branch: FarosRef? = null,
    val file: FarosRef? = null
)

/**
 * Represents a GraphQL mutation operation
 */
data class FarosMutation(
    val type: String,
    val model: String,
    val data: Map<String, Any>
) {
    companion object {
        fun insert(
            model: String,
            objects: List<Map<String, Any>>,
            onConflict: Map<String, Any>
        ): FarosMutation {
            return FarosMutation(
                type = "insert",
                model = model,
                data = mapOf(
                    "objects" to objects,
                    "on_conflict" to onConflict
                )
            )
        }
    }
}

/**
 * Represents a GraphQL mutation request
 */
data class GraphQLMutation(
    val query: String,
    val variables: Map<String, Any>? = null
)

/**
 * Represents a batch mutation request
 */
data class BatchMutation(
    val mutations: List<FarosMutation>
)
