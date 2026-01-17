package com.example.releaseautomation.tasks

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.api.services.androidpublisher.model.*
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileInputStream
import java.util.logging.Logger

abstract class DeployToStoreTask : DefaultTask() {
    @get:Input
    var packageName: String = project.findProperty("packageName") as? String
        ?: "com.example.aiagentchat"
    
    @get:Input
    var track: String = project.findProperty("track") as? String ?: "internal"
    
    @get:InputFile
    var aabFile: File = project.buildDir.resolve("outputs/bundle/release/app-release.aab")
    
    @get:InputFile
    @get:Optional
    var serviceAccountJson: File? = project.findProperty("serviceAccountJson")?.let { File(it.toString()) }
        ?: project.rootDir.resolve("play-store-key.json").takeIf { it.exists() }
    
    @get:InputFile
    var releaseNotesRu: File = project.buildDir.resolve("release-artifacts/play-store-ru.txt")
    
    @get:InputFile
    var releaseNotesEn: File = project.buildDir.resolve("release-artifacts/play-store-en.txt")
    
    private val logger = Logger.getLogger(DeployToStoreTask::class.java.name)
    
    @TaskAction
    fun deploy() {
        val dryRun = System.getenv("DRY_RUN") == "true"
        val serviceAccountFile = serviceAccountJson
        
        if (dryRun || serviceAccountFile == null || !serviceAccountFile.exists()) {
            if (dryRun) {
                logger.info("DRY RUN MODE: Skipping actual deployment")
                logger.info("Would deploy:")
                logger.info("  Package: $packageName")
                logger.info("  Track: $track")
                logger.info("  AAB: ${aabFile.absolutePath}")
            } else {
                logger.warning("⚠️  Service Account JSON not found: ${serviceAccountFile?.absolutePath ?: "play-store-key.json"}")
                logger.warning("⚠️  Skipping Play Store deployment")
                logger.warning("")
                logger.warning("To enable Play Store deployment:")
                logger.warning("  1. Create Service Account in Google Cloud Console")
                logger.warning("  2. Download JSON key file")
                logger.warning("  3. Save as 'play-store-key.json' in project root")
                logger.warning("  4. Or set DRY_RUN=true to skip deployment")
                logger.warning("")
                logger.info("✅ Release artifacts generated successfully (without Play Store deployment)")
            }
            return
        }
        
        if (!serviceAccountFile.exists()) {
            throw IllegalStateException("Service Account JSON not found: ${serviceAccountFile.absolutePath}")
        }
        
        if (!aabFile.exists()) {
            throw IllegalStateException("AAB file not found: ${aabFile.absolutePath}")
        }
        
        logger.info("Initializing Google Play Publisher API...")
        val publisher = createAndroidPublisher(serviceAccountFile)
        
        logger.info("Creating edit session...")
        val edit = publisher.edits().insert(packageName, null).execute()
        val editId = edit.id
        
        try {
            logger.info("Uploading AAB file...")
            val bundle = publisher.edits().bundles().upload(
                packageName,
                editId,
                com.google.api.client.http.FileContent(
                    "application/octet-stream",
                    aabFile
                )
            ).execute()
            
            val versionCode = bundle.versionCode
            logger.info("AAB uploaded with version code: $versionCode")
            
            logger.info("Creating track release...")
            val release = TrackRelease().apply {
                versionCodes = listOf(versionCode.toLong())
                status = "completed"
                releaseNotes = listOf(
                    LocalizedText().apply {
                        language = "ru-RU"
                        text = releaseNotesRu.readText().take(500)
                    },
                    LocalizedText().apply {
                        language = "en-US"
                        text = releaseNotesEn.readText().take(500)
                    }
                )
            }
            
            val trackUpdate = publisher.edits().tracks().update(
                packageName,
                editId,
                track,
                Track().apply {
                    releases = listOf(release)
                }
            ).execute()
            
            logger.info("Track updated: ${trackUpdate.track}")
            
            logger.info("Committing edit...")
            val commitResult = publisher.edits().commit(packageName, editId).execute()
            logger.info("Edit committed: ${commitResult.id}")
            
            logger.info("✅ Successfully deployed to Play Store!")
            logger.info("  Package: $packageName")
            logger.info("  Track: $track")
            logger.info("  Version code: $versionCode")
            logger.info("  Play Console: https://play.google.com/console/developers")
            
        } catch (e: Exception) {
            logger.severe("Error during deployment: ${e.message}")
            try {
                logger.info("Rolling back edit...")
                publisher.edits().delete(packageName, editId).execute()
            } catch (rollbackError: Exception) {
                logger.severe("Failed to rollback: ${rollbackError.message}")
            }
            throw e
        }
    }
    
    private fun createAndroidPublisher(serviceAccountFile: File): AndroidPublisher {
        val httpTransport = GoogleNetHttpTransport.newTrustedTransport()
        val jsonFactory = GsonFactory.getDefaultInstance()
        
        val credential = GoogleCredential.fromStream(
            FileInputStream(serviceAccountFile),
            httpTransport,
            jsonFactory
        ).createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))
        
        return AndroidPublisher.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName("AI Agent Chat Release Automation")
            .build()
    }
}
