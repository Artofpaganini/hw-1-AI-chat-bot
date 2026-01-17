package com.example.releaseautomation.tasks

import com.example.releaseautomation.api.ReleaseAnalysis
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

abstract class GenerateReleaseTask : DefaultTask() {
    @get:InputFile
    var analysisFile: File = project.buildDir.resolve("release-analysis.json")
    
    @get:OutputDirectory
    var outputDir: File = project.buildDir.resolve("release-artifacts")
    
    @TaskAction
    fun generate() {
        if (!analysisFile.exists()) {
            throw IllegalStateException("Analysis file not found: ${analysisFile.absolutePath}")
        }
        
        val json = Json { ignoreUnknownKeys = true }
        val analysis = json.decodeFromString<ReleaseAnalysis>(analysisFile.readText())
        
        outputDir.mkdirs()
        
        // Генерируем RELEASE_NOTES.md
        val releaseNotes = generateReleaseNotes(analysis)
        val releaseNotesFile = outputDir.resolve("RELEASE_NOTES.md")
        releaseNotesFile.writeText(releaseNotes)
        println("Generated: ${releaseNotesFile.absolutePath}")
        
        // Генерируем Play Store metadata
        val playStoreRu = outputDir.resolve("play-store-ru.txt")
        playStoreRu.writeText(analysis.whatsNewRu.take(500))
        println("Generated: ${playStoreRu.absolutePath}")
        
        val playStoreEn = outputDir.resolve("play-store-en.txt")
        playStoreEn.writeText(analysis.whatsNewEn.take(500))
        println("Generated: ${playStoreEn.absolutePath}")
        
        // Обновляем CHANGELOG.md
        val changelogFile = project.rootDir.resolve("CHANGELOG.md")
        updateChangelog(changelogFile, analysis)
        println("Updated: ${changelogFile.absolutePath}")
        
        println("\nRelease artifacts generated successfully!")
        println("  Version bump: ${analysis.versionBump}")
        println("  User impact: ${analysis.userImpact}")
    }
    
    private fun generateReleaseNotes(analysis: ReleaseAnalysis): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val version = calculateNewVersion(analysis.versionBump)
        
        return buildString {
            appendLine("# Release Notes v$version")
            appendLine("**Дата:** $date")
            appendLine()
            appendLine("## Что нового")
            appendLine()
            appendLine(analysis.releaseNotesRu)
            appendLine()
            appendLine("## What's New")
            appendLine()
            appendLine(analysis.releaseNotesEn)
            appendLine()
            
            if (!analysis.breakingChanges.isNullOrEmpty()) {
                appendLine("## ⚠️ Breaking Changes")
                appendLine()
                analysis.breakingChanges.forEach { change ->
                    appendLine("- $change")
                }
                appendLine()
            }
            
            appendLine("## Changelog")
            appendLine()
            appendLine(analysis.changelog)
            appendLine()
            appendLine("## User Impact: ${analysis.userImpact.uppercase()}")
            appendLine()
            appendLine(analysis.summary)
        }
    }
    
    private fun updateChangelog(changelogFile: File, analysis: ReleaseAnalysis) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val version = calculateNewVersion(analysis.versionBump)
        
        val newEntry = buildString {
            appendLine("## [$version] - $date")
            appendLine()
            appendLine("### Added")
            appendLine(analysis.releaseNotesRu)
            appendLine()
            if (!analysis.breakingChanges.isNullOrEmpty()) {
                appendLine("### Breaking Changes")
                analysis.breakingChanges.forEach { change ->
                    appendLine("- $change")
                }
                appendLine()
            }
            appendLine("---")
            appendLine()
        }
        
        val existingContent = if (changelogFile.exists()) {
            changelogFile.readText()
        } else {
            "# Changelog\n\nAll notable changes to this project will be documented in this file.\n\n"
        }
        
        val updatedContent = existingContent.replace(
            "# Changelog",
            "# Changelog\n\n$newEntry"
        )
        
        changelogFile.writeText(updatedContent)
    }
    
    private fun calculateNewVersion(bump: String): String {
        val buildGradleFile = project.rootDir.resolve("app/build.gradle.kts")
        if (!buildGradleFile.exists()) {
            return "1.0.0"
        }
        
        val content = buildGradleFile.readText()
        val versionNameRegex = Regex("versionName\\s*=\\s*\"([\\d.]+)\"")
        val match = versionNameRegex.find(content)
        
        if (match == null) {
            return "1.0.0"
        }
        
        val currentVersion = match.groupValues[1]
        val parts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
        
        val newVersion = when (bump.lowercase()) {
            "major" -> "${parts[0] + 1}.0.0"
            "minor" -> "${parts[0]}.${parts[1] + 1}.0"
            "patch" -> "${parts[0]}.${parts[1]}.${parts.getOrElse(2) { 0 } + 1}"
            else -> currentVersion
        }
        
        return newVersion
    }
}
