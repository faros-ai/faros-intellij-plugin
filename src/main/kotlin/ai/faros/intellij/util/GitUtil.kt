package ai.faros.intellij.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.file.Paths
import java.io.BufferedReader
import java.io.InputStreamReader

object GitUtil {
    private val LOG = Logger.getInstance(GitUtil::class.java)
    private val GIT4IDEA_AVAILABLE = isGit4IdeaAvailable()
    
    /**
     * Check if Git4Idea is available
     */
    @JvmStatic
    fun isGit4IdeaAvailable(): Boolean {
        return try {
            Class.forName("git4idea.GitUtil")
            true
        } catch (e: ClassNotFoundException) {
            LOG.info("Git4Idea plugin not available, using fallback Git implementation")
            false
        }
    }

    /**
     * Gets the git repository name for a file
     *
     * @param file The virtual file to get the repository for
     * @return The repository name or "unknown" if not in a git repository
     */
    @JvmStatic
    fun getGitRepoName(file: VirtualFile?): String {
        if (file == null) {
            return "unknown"
        }
        
        return try {
            if (GIT4IDEA_AVAILABLE) {
                getGitRepoNameWithGit4Idea(file)
            } else {
                getGitRepoNameFallback(file)
            }
        } catch (e: Exception) {
            LOG.warn("Error getting Git repository name", e)
            "unknown"
        }
    }

    /**
     * Gets the current git branch for a file
     *
     * @param file The virtual file to get the branch for
     * @return The branch name or "unknown" if not in a git repository
     */
    @JvmStatic
    fun getGitBranch(file: VirtualFile?): String {
        if (file == null) {
            return "unknown"
        }
        
        return try {
            if (GIT4IDEA_AVAILABLE) {
                getGitBranchWithGit4Idea(file)
            } else {
                getGitBranchFallback(file)
            }
        } catch (e: Exception) {
            LOG.warn("Error getting Git branch", e)
            "unknown"
        }
    }

    /**
     * Uses Git4Idea to get the repository name (when available)
     */
    private fun getGitRepoNameWithGit4Idea(file: VirtualFile): String {
        // We need to use reflection to avoid direct dependencies
        val repository = getGitRepositoryWithGit4Idea(file)
        if (repository == null) {
            return "unknown"
        }

        // Access repository.root.name via reflection
        val root = repository.javaClass.getMethod("getRoot").invoke(repository) as VirtualFile
        return root.name
    }

    /**
     * Uses Git4Idea to get the branch name (when available)
     */
    private fun getGitBranchWithGit4Idea(file: VirtualFile): String {
        // We need to use reflection to avoid direct dependencies
        val repository = getGitRepositoryWithGit4Idea(file)
        if (repository == null) {
            return "unknown"
        }

        // Access repository.currentBranch?.name via reflection
        val currentBranch = repository.javaClass.getMethod("getCurrentBranch").invoke(repository)
        if (currentBranch == null) {
            return "unknown"
        }
        
        return currentBranch.javaClass.getMethod("getName").invoke(currentBranch) as String
    }

    /**
     * Uses Git4Idea to get the repository (when available)
     */
    private fun getGitRepositoryWithGit4Idea(file: VirtualFile): Any? {
        val projects = ProjectManager.getInstance().openProjects
        if (projects.isEmpty()) {
            return null
        }

        // Using reflection to access Git4Idea
        val gitUtilClass = Class.forName("git4idea.GitUtil")
        val getRepositoryManagerMethod = gitUtilClass.getMethod("getRepositoryManager", Project::class.java)

        // Try to find in all open projects
        for (project in projects) {
            val manager = getRepositoryManagerMethod.invoke(null, project) ?: continue
            
            // Get the repository for file
            val getRepositoryForFileMethod = manager.javaClass.getMethod("getRepositoryForFile", VirtualFile::class.java)
            val repository = getRepositoryForFileMethod.invoke(manager, file)
            if (repository != null) {
                return repository
            }
        }

        return null
    }

    /**
     * Fallback implementation to get repository name
     */
    private fun getGitRepoNameFallback(file: VirtualFile): String {
        val gitDir = findGitDirectory(File(file.path))
        if (gitDir == null) {
            return "unknown"
        }
        
        // Git directory is .git, parent is the repo root
        val repoRoot = gitDir.parentFile
        return repoRoot.name
    }

    /**
     * Fallback implementation to get branch name
     */
    private fun getGitBranchFallback(file: VirtualFile): String {
        val gitDir = findGitDirectory(File(file.path))
        if (gitDir == null) {
            return "unknown"
        }
        
        // Read HEAD file to get current branch
        val headFile = File(gitDir, "HEAD")
        if (!headFile.exists() || !headFile.isFile) {
            return "unknown"
        }
        
        // Parse HEAD file content (format: ref: refs/heads/branch-name)
        val headContent = headFile.readText().trim()
        if (headContent.startsWith("ref: refs/heads/")) {
            return headContent.removePrefix("ref: refs/heads/")
        }
        
        // If we get here, we're probably in detached HEAD state
        return "HEAD"
    }

    /**
     * Finds the .git directory for a file path
     */
    private fun findGitDirectory(directory: File?): File? {
        var current = directory
        while (current != null) {
            val gitDir = File(current, ".git")
            if (gitDir.exists() && gitDir.isDirectory) {
                return gitDir
            }
            current = current.parentFile
        }
        return null
    }
} 