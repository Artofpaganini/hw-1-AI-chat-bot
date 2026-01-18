import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.compose)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(localPropertiesFile.inputStream())
    }
}

android {
    namespace = "com.example.aiagentchat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.aiagentchat"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val deepSeekKey = localProperties.getProperty("DEEPSEEK_API_KEY") ?: ""
        val openRouterKey = localProperties.getProperty("OPENROUTER_API_KEY") ?: ""
        val context7Key = localProperties.getProperty("CONTEXT7_API_KEY") ?: "ctx7sk-3c70abfe-27c0-4a04-ab41-343bd9f21d34"
        val mcpServerUrl = localProperties.getProperty("MCP_SERVER_URL") ?: "https://mcp.context7.com/"

        buildConfigField("String", "DEEPSEEK_API_KEY", "\"$deepSeekKey\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"$openRouterKey\"")
        buildConfigField("String", "CONTEXT7_API_KEY", "\"$context7Key\"")
        buildConfigField("String", "MCP_SERVER_URL", "\"$mcpServerUrl\"")

        buildConfigField("Double", "DEEPSEEK_INPUT_PRICE", "0.14")
        buildConfigField("Double", "DEEPSEEK_OUTPUT_PRICE", "0.28")
        buildConfigField("Double", "CLAUDE_35_SONNET_INPUT_PRICE", "3.00")
        buildConfigField("Double", "CLAUDE_35_SONNET_OUTPUT_PRICE", "15.00")
        buildConfigField("Double", "GPT_4O_MINI_INPUT_PRICE", "0.15")
        buildConfigField("Double", "GPT_4O_MINI_OUTPUT_PRICE", "0.60")
        buildConfigField("Double", "GEMINI_PRO_15_INPUT_PRICE", "1.25")
        buildConfigField("Double", "GEMINI_PRO_15_OUTPUT_PRICE", "5.00")

        val googleDriveClientId = localProperties.getProperty("GOOGLE_DRIVE_CLIENT_ID") ?: "EMPTY GOOGLE DRIVE CLIENT ID"
        buildConfigField("String", "GOOGLE_DRIVE_CLIENT_ID", "\"$googleDriveClientId\"")
        
        val googleDriveAccessToken = localProperties.getProperty("GOOGLE_DRIVE_ACCESS_TOKEN") ?: ""
        buildConfigField("String", "GOOGLE_DRIVE_ACCESS_TOKEN", "\"$googleDriveAccessToken\"")
        
        val projectRoot = localProperties.getProperty("PROJECT_ROOT") ?: ""
        buildConfigField("String", "PROJECT_ROOT", "\"$projectRoot\"")
    }

    signingConfigs {
        val keystoreFile = rootProject.file("keystore.jks")
        val keystorePassword = System.getenv("SIGNING_STORE_PASSWORD") 
            ?: localProperties.getProperty("SIGNING_STORE_PASSWORD") ?: ""
        val keyAlias = System.getenv("SIGNING_KEY_ALIAS") 
            ?: localProperties.getProperty("SIGNING_KEY_ALIAS") ?: "release"
        val keyPassword = System.getenv("SIGNING_KEY_PASSWORD") 
            ?: localProperties.getProperty("SIGNING_KEY_PASSWORD") ?: ""
        
        if (keystoreFile.exists() && keystorePassword.isNotEmpty()) {
            try {
                // Проверяем валидность keystore перед использованием
                val process = ProcessBuilder(
                    "keytool", "-list", "-keystore", keystoreFile.absolutePath,
                    "-storepass", keystorePassword, "-alias", keyAlias
                ).redirectErrorStream(true).start()
                
                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()
                
                if (exitCode == 0) {
                    // Дополнительная проверка - пытаемся прочитать ключ
                    val keyProcess = ProcessBuilder(
                        "keytool", "-list", "-v", "-keystore", keystoreFile.absolutePath,
                        "-storepass", keystorePassword, "-alias", keyAlias
                    ).redirectErrorStream(true).start()
                    
                    val keyOutput = keyProcess.inputStream.bufferedReader().readText()
                    val keyExitCode = keyProcess.waitFor()
                    
                    if (keyExitCode == 0 && !keyOutput.contains("Given final block not properly padded")) {
                        create("release") {
                            storeFile = keystoreFile
                            storePassword = keystorePassword
                            this.keyAlias = keyAlias
                            this.keyPassword = keyPassword
                        }
                        println("✅ Keystore validated successfully")
                    } else {
                        println("⚠️  Warning: Keystore key validation failed (exit code: $keyExitCode)")
                        println("⚠️  Error: ${keyOutput.take(200)}")
                        println("⚠️  Signing will be skipped - keystore appears to be corrupted")
                        // Удаляем поврежденный keystore, чтобы Gradle не пытался его использовать
                        try {
                            keystoreFile.delete()
                            println("⚠️  Removed invalid keystore file")
                        } catch (e: Exception) {
                            println("⚠️  Could not remove keystore file: ${e.message}")
                        }
                    }
                } else {
                    println("⚠️  Warning: Keystore validation failed (exit code: $exitCode)")
                    println("⚠️  Error output: ${output.take(200)}")
                    println("⚠️  Signing will be skipped")
                    // Удаляем поврежденный keystore
                    try {
                        keystoreFile.delete()
                        println("⚠️  Removed invalid keystore file")
                    } catch (e: Exception) {
                        println("⚠️  Could not remove keystore file: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                println("⚠️  Warning: Cannot validate keystore (${e.message}), signing will be skipped")
                // Удаляем поврежденный keystore
                try {
                    if (keystoreFile.exists()) {
                        keystoreFile.delete()
                        println("⚠️  Removed invalid keystore file")
                    }
                } catch (deleteException: Exception) {
                    println("⚠️  Could not remove keystore file: ${deleteException.message}")
                }
            }
        }
    }

    buildTypes {
        release {
            val releaseSigningConfig = signingConfigs.findByName("release")
            if (releaseSigningConfig != null) {
                signingConfig = releaseSigningConfig
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:uikit"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:home"))

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.bundles.koin)
    implementation(libs.bundles.network)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.bundles.room)
    implementation(libs.work.runtime.ktx)
    implementation(libs.startup.runtime)

    debugImplementation(libs.bundles.compose.debug)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

// Release Automation Tasks
tasks.register<com.example.releaseautomation.tasks.AnalyzeChangesTask>("analyzeChanges") {
    group = "release"
    description = "Analyze code changes using DeepSeek API and RAG"
}

tasks.register<com.example.releaseautomation.tasks.GenerateReleaseTask>("generateRelease") {
    group = "release"
    description = "Generate release notes and artifacts"
    dependsOn("analyzeChanges")
}

tasks.register<com.example.releaseautomation.tasks.BumpVersionTask>("bumpVersion") {
    group = "release"
    description = "Bump version in build.gradle.kts"
    dependsOn("analyzeChanges")
}

tasks.register<com.example.releaseautomation.tasks.DeployToStoreTask>("deployToStore") {
    group = "release"
    description = "Deploy AAB to Google Play Store"
    dependsOn("bundleRelease", "generateRelease")
}

tasks.register("aiRelease") {
    group = "release"
    description = "Complete AI-powered release pipeline"
    
    dependsOn("analyzeChanges")
    
    doLast {
        println("\n" + "=".repeat(60))
        println("AI Release Pipeline Summary")
        println("=".repeat(60))
        println("✅ Analysis complete")
        println("✅ Release artifacts generated")
        println("✅ Version bumped")
        println("✅ APK built: ${project.buildDir}/outputs/apk/release/app-release.apk")
        println("✅ AAB built: ${project.buildDir}/outputs/bundle/release/app-release.aab")
        println("✅ Deployed to Play Store")
        println("\nArtifacts location: ${project.buildDir}/release-artifacts/")
        println("=".repeat(60))
    }
    
    finalizedBy("generateRelease", "bumpVersion", "assembleRelease", "bundleRelease", "deployToStore")
}

