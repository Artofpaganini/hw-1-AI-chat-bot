package com.example.releaseautomation.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.logging.Logger

abstract class BumpVersionTask : DefaultTask() {
    @get:Input
    var versionBump: String = project.findProperty("versionBump") as? String ?: "patch"
    
    @get:InputFile
    var analysisFile: File = project.buildDir.resolve("release-analysis.json")
    
    private val logger = Logger.getLogger(BumpVersionTask::class.java.name)
    
    @TaskAction
    fun bumpVersion() {
        val buildGradleFile = project.rootDir.resolve("app/build.gradle.kts")
        if (!buildGradleFile.exists()) {
            logger.warning("app/build.gradle.kts not found, skipping version bump")
            return
        }
        
        var content = buildGradleFile.readText()
        
        // Читаем versionBump из analysis файла если есть
        val actualBump = if (analysisFile.exists()) {
            try {
                val analysisJson = analysisFile.readText()
                val versionBumpRegex = Regex("\"versionBump\"\\s*:\\s*\"(\\w+)\"")
                versionBumpRegex.find(analysisJson)?.groupValues?.get(1) ?: versionBump
            } catch (e: Exception) {
                logger.warning("Could not read versionBump from analysis: ${e.message}")
                versionBump
            }
        } else {
            versionBump
        }
        
        // Обновляем versionName
        val versionNameRegex = Regex("versionName\\s*=\\s*\"([\\d.]+)\"")
        val versionNameMatch = versionNameRegex.find(content)
        
        if (versionNameMatch != null) {
            val currentVersion = versionNameMatch.groupValues[1]
            val parts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
            
            val newVersion = when (actualBump.lowercase()) {
                "major" -> "${parts[0] + 1}.0.0"
                "minor" -> "${parts[0]}.${parts[1] + 1}.0"
                "patch" -> "${parts[0]}.${parts[1]}.${parts.getOrElse(2) { 0 } + 1}"
                else -> currentVersion
            }
            
            content = content.replace(
                versionNameRegex,
                "versionName = \"$newVersion\""
            )
            
            logger.info("Bumped version: $currentVersion -> $newVersion ($actualBump)")
        }
        
        // Обновляем versionCode
        val versionCodeRegex = Regex("versionCode\\s*=\\s*(\\d+)")
        val versionCodeMatch = versionCodeRegex.find(content)
        
        if (versionCodeMatch != null) {
            val currentCode = versionCodeMatch.groupValues[1].toInt()
            val newCode = currentCode + 1
            
            content = content.replace(
                versionCodeRegex,
                "versionCode = $newCode"
            )
            
            logger.info("Bumped version code: $currentCode -> $newCode")
        }
        
        buildGradleFile.writeText(content)
        logger.info("Version updated in ${buildGradleFile.absolutePath}")
    }
}
