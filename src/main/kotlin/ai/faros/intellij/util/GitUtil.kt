package ai.faros.intellij.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitUtil as GitUtilIJ
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

object GitUtil {

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

        val repository = getGitRepository(file)
        if (repository == null) {
            return "unknown"
        }

        val root = repository.root
        return root.name
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

        val repository = getGitRepository(file)
        if (repository == null || repository.currentBranch == null) {
            return "unknown"
        }

        return repository.currentBranch!!.name
    }

    private fun getGitRepository(file: VirtualFile): GitRepository? {
        val projects = ProjectManager.getInstance().openProjects
        if (projects.isEmpty()) {
            return null
        }

        // Try to find in all open projects
        for (project in projects) {
            val manager = GitUtilIJ.getRepositoryManager(project) ?: continue
            
            val repository = manager.getRepositoryForFile(file)
            if (repository != null) {
                return repository
            }
        }

        return null
    }
} 