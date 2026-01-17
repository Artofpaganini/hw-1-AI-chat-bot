package com.example.releaseautomation.tasks

import com.example.releaseautomation.api.DeepSeekClient
import com.example.releaseautomation.api.ReleaseAnalysis
import com.example.releaseautomation.git.GitAnalyzer
import com.example.releaseautomation.rag.RagIndexer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.logging.Logger

abstract class AnalyzeChangesTask : DefaultTask() {
    @get:Input
    var deepseekApiKey: String = System.getenv("DEEPSEEK_API_KEY") ?: ""
    
    @get:Input
    @get:Optional
    var previousTag: String? = project.findProperty("previousTag") as? String
    
    @get:Input
    var currentTag: String = project.findProperty("currentTag") as? String ?: "HEAD"
    
    @get:OutputFile
    var outputFile: File = project.buildDir.resolve("release-analysis.json")
    
    private val logger = Logger.getLogger(AnalyzeChangesTask::class.java.name)
    
    @TaskAction
    fun analyze() {
        // Пытаемся получить API ключ из env, если не установлен в task
        var apiKey = if (deepseekApiKey.isBlank()) {
            System.getenv("DEEPSEEK_API_KEY") ?: ""
        } else {
            deepseekApiKey
        }
        
        // Очищаем API ключ от пробелов и переносов строк
        apiKey = apiKey
            .trim()
            .replace("\n", "")
            .replace("\r", "")
            .replace("\t", "")
            .replace(" ", "")
        
        if (apiKey.isBlank()) {
            logger.warning("DEEPSEEK_API_KEY is not set or is blank after sanitization. Analysis will use fallback mode.")
        } else {
            logger.info("API key length: ${apiKey.length}, starts with: ${apiKey.take(3)}")
        }
        
        val projectRoot = project.rootDir
        val gitAnalyzer = GitAnalyzer(projectRoot)
        
        logger.info("Getting commits between tags: $previousTag -> $currentTag")
        val commits = gitAnalyzer.getCommitsBetweenTags(previousTag, currentTag)
        logger.info("Found ${commits.size} commits")
        
        logger.info("Getting git diff...")
        var gitDiff = gitAnalyzer.getDiffBetweenTags(previousTag, currentTag)
        logger.info("Diff size: ${gitDiff.length} characters")
        
        // Ограничиваем размер diff (максимум 20000 символов)
        if (gitDiff.length > 20000) {
            logger.warning("Diff is too large (${gitDiff.length} chars), truncating to 20000 chars")
            gitDiff = gitDiff.take(20000) + "\n\n... (truncated, showing first 20000 chars)"
        }
        
        logger.info("Indexing codebase for RAG...")
        val indexDbPath = project.buildDir.resolve("rag-index.db")
        val ragIndexer = RagIndexer(projectRoot, indexDbPath)
        val chunksIndexed = ragIndexer.indexCodebase()
        logger.info("Indexed $chunksIndexed chunks")
        
        logger.info("Searching RAG context...")
        val ragContext = ragIndexer.searchContext("Android AI chat application Kotlin", topK = 3)
            .joinToString("\n\n") { chunk ->
                "${chunk.filePath} (lines ${chunk.startLine}-${chunk.endLine}):\n${chunk.chunkText.take(200)}"
            }
        logger.info("RAG context size: ${ragContext.length} characters")
        
        // Ограничиваем размер RAG контекста (максимум 5000 символов)
        val limitedRagContext = if (ragContext.length > 5000) {
            logger.warning("RAG context is too large (${ragContext.length} chars), truncating to 5000 chars")
            ragContext.take(5000) + "\n\n... (truncated)"
        } else {
            ragContext
        }
        
        // Ограничиваем количество коммитов (максимум 20)
        val limitedCommits = if (commits.size > 20) {
            logger.warning("Too many commits (${commits.size}), using first 20")
            commits.take(20)
        } else {
            commits
        }
        
        logger.info("Analyzing with DeepSeek API...")
        val analysis = runBlocking {
            if (apiKey.isNotBlank()) {
                val client = DeepSeekClient(apiKey)
                try {
                    client.analyzeCommitsForRelease(limitedCommits, gitDiff, limitedRagContext)
                } finally {
                    client.close()
                }
            } else {
                // Fallback анализ
                createFallbackAnalysis(commits, gitDiff)
            }
        }
        
        logger.info("Analysis complete:")
        logger.info("  Version bump: ${analysis.versionBump}")
        logger.info("  User impact: ${analysis.userImpact}")
        logger.info("  Breaking changes: ${analysis.breakingChanges?.size ?: 0}")
        
        // Сохраняем результат
        outputFile.parentFile?.mkdirs()
        val json = Json { prettyPrint = true }
        outputFile.writeText(json.encodeToString(ReleaseAnalysis.serializer(), analysis))
        
        logger.info("Analysis saved to: ${outputFile.absolutePath}")
    }
    
    private fun createFallbackAnalysis(commits: List<String>, gitDiff: String): ReleaseAnalysis {
        val hasBreakingChanges = gitDiff.contains("BREAKING", ignoreCase = true) ||
                commits.any { it.contains("BREAKING", ignoreCase = true) }
        
        val versionBump = when {
            hasBreakingChanges -> "major"
            gitDiff.contains("feat", ignoreCase = true) -> "minor"
            else -> "patch"
        }
        
        return ReleaseAnalysis(
            versionBump = versionBump,
            releaseNotesRu = "Обновление приложения с улучшениями и исправлениями.",
            releaseNotesEn = "App update with improvements and bug fixes.",
            whatsNewRu = "Обновление приложения с улучшениями и исправлениями.",
            whatsNewEn = "App update with improvements and bug fixes.",
            breakingChanges = if (hasBreakingChanges) listOf("Обнаружены breaking changes") else null,
            changelog = commits.joinToString("\n") { "- $it" },
            userImpact = "medium",
            summary = "Обновление включает ${commits.size} коммитов"
        )
    }
}
