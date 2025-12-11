import java.util.Properties

plugins {
    id("com.android.application") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}

// Load local.properties
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
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // API Keys - разработчик добавляет ключи в local.properties:
        // DEEPSEEK_API_KEY=your_key_here
        // OPENROUTER_API_KEY=your_key_here
        val deepSeekKey = localProperties.getProperty("DEEPSEEK_API_KEY") ?: ""
        val openRouterKey = localProperties.getProperty("OPENROUTER_API_KEY") ?: ""
        
        buildConfigField("String", "DEEPSEEK_API_KEY", "\"$deepSeekKey\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"$openRouterKey\"")
        
        // Pricing per 1M tokens (в USD)
        // DeepSeek
        buildConfigField("Double", "DEEPSEEK_INPUT_PRICE", "0.14")
        buildConfigField("Double", "DEEPSEEK_OUTPUT_PRICE", "0.28")
        // Claude 3.5 Sonnet (через OpenRouter)
        buildConfigField("Double", "CLAUDE_35_SONNET_INPUT_PRICE", "3.00")
        buildConfigField("Double", "CLAUDE_35_SONNET_OUTPUT_PRICE", "15.00")
        // GPT-4o Mini (через OpenRouter)
        buildConfigField("Double", "GPT_4O_MINI_INPUT_PRICE", "0.15")
        buildConfigField("Double", "GPT_4O_MINI_OUTPUT_PRICE", "0.60")
        // Gemini Pro 1.5 (через OpenRouter)
        buildConfigField("Double", "GEMINI_PRO_15_INPUT_PRICE", "1.25")
        buildConfigField("Double", "GEMINI_PRO_15_OUTPUT_PRICE", "5.00")
    }

    buildTypes {
        release {
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
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Lifecycle + ViewModel
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Koin DI
    implementation("io.insert-koin:koin-android:4.0.0")
    implementation("io.insert-koin:koin-androidx-compose:4.0.0")

    // Network
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.13")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.11.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
