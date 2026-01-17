package com.example.releaseautomation.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.util.io.NullOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.logging.Logger

class GitAnalyzer(private val repositoryPath: File) {
    private val logger = Logger.getLogger(GitAnalyzer::class.java.name)
    
    fun getCommitsBetweenTags(
        previousTag: String?,
        currentTag: String = "HEAD"
    ): List<String> {
        val repository = openRepository()
        val git = Git(repository)
        
        try {
            val revWalk = RevWalk(repository)
            
            val fromRef = if (previousTag != null && previousTag.isNotBlank()) {
                try {
                    repository.resolve(previousTag)
                } catch (e: Exception) {
                    logger.warning("Could not resolve tag $previousTag, using HEAD~10")
                    repository.resolve("HEAD~10")
                }
            } else {
                // Если нет предыдущего тега, берем последние 10 коммитов
                repository.resolve("HEAD~10")
            }
            
            val toRef = repository.resolve(currentTag)
            
            val fromCommit = revWalk.parseCommit(fromRef)
            val toCommit = revWalk.parseCommit(toRef)
            
            revWalk.markStart(toCommit)
            revWalk.markUninteresting(fromCommit)
            
            val commits = mutableListOf<String>()
            revWalk.forEach { commit ->
                val shortMessage = commit.shortMessage
                val author = commit.authorIdent.name
                val hash = commit.name.take(7)
                commits.add("$shortMessage ($author) [$hash]")
            }
            
            revWalk.close()
            return commits.reversed() // Старые коммиты первыми
        } catch (e: Exception) {
            logger.severe("Error getting commits: ${e.message}")
            throw RuntimeException("Failed to get commits between tags", e)
        } finally {
            repository.close()
        }
    }
    
    fun getDiffBetweenTags(
        previousTag: String?,
        currentTag: String = "HEAD"
    ): String {
        val repository = openRepository()
        val git = Git(repository)
        
        try {
            val revWalk = RevWalk(repository)
            
            val fromRef = if (previousTag != null && previousTag.isNotBlank()) {
                try {
                    repository.resolve(previousTag)
                } catch (e: Exception) {
                    logger.warning("Could not resolve tag $previousTag, using HEAD~10")
                    repository.resolve("HEAD~10")
                }
            } else {
                repository.resolve("HEAD~10")
            }
            
            val toRef = repository.resolve(currentTag)
            
            val fromCommit = revWalk.parseCommit(fromRef)
            val toCommit = revWalk.parseCommit(toRef)
            
            val fromTree = revWalk.parseTree(fromCommit.tree)
            val toTree = revWalk.parseTree(toCommit.tree)
            
            val diffFormatter = DiffFormatter(NullOutputStream.INSTANCE)
            diffFormatter.setRepository(repository)
            diffFormatter.setDetectRenames(true)
            
            val diffEntries = diffFormatter.scan(fromTree, toTree)
            
            val outputStream = ByteArrayOutputStream()
            val outputFormatter = DiffFormatter(outputStream)
            outputFormatter.setRepository(repository)
            outputFormatter.setDetectRenames(true)
            
            diffEntries.forEach { entry ->
                outputFormatter.format(entry)
            }
            
            revWalk.close()
            return outputStream.toString(Charsets.UTF_8)
        } catch (e: Exception) {
            logger.severe("Error getting diff: ${e.message}")
            throw RuntimeException("Failed to get diff between tags", e)
        } finally {
            repository.close()
        }
    }
    
    fun getCurrentTag(): String? {
        val repository = openRepository()
        try {
            val git = Git(repository)
            val tags = git.tagList().call()
            if (tags.isEmpty()) return null
            
            // Получаем последний тег
            val head = repository.resolve("HEAD")
            val refs = repository.refDatabase.getRefsByPrefix("refs/tags/")
            
            return refs.maxByOrNull { it.objectId }?.name?.removePrefix("refs/tags/")
        } catch (e: Exception) {
            logger.warning("Could not get current tag: ${e.message}")
            return null
        } finally {
            repository.close()
        }
    }
    
    private fun openRepository(): Repository {
        val builder = FileRepositoryBuilder()
        builder.setGitDir(File(repositoryPath, ".git"))
        builder.setMustExist(true)
        builder.findGitDir()
        
        return builder.build()
    }
}
