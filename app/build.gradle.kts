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
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val deepSeekKey = localProperties.getProperty("DEEPSEEK_API_KEY") ?: ""
        val openRouterKey = localProperties.getProperty("OPENROUTER_API_KEY") ?: ""

        buildConfigField("String", "DEEPSEEK_API_KEY", "\"$deepSeekKey\"")
        buildConfigField("String", "OPENROUTER_API_KEY", "\"$openRouterKey\"")

        buildConfigField("Double", "DEEPSEEK_INPUT_PRICE", "0.14")
        buildConfigField("Double", "DEEPSEEK_OUTPUT_PRICE", "0.28")
        buildConfigField("Double", "CLAUDE_35_SONNET_INPUT_PRICE", "3.00")
        buildConfigField("Double", "CLAUDE_35_SONNET_OUTPUT_PRICE", "15.00")
        buildConfigField("Double", "GPT_4O_MINI_INPUT_PRICE", "0.15")
        buildConfigField("Double", "GPT_4O_MINI_OUTPUT_PRICE", "0.60")
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
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:uikit"))
    implementation(project(":feature:chat"))
    implementation(project(":feature:home"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:patients"))
    implementation(project(":feature:appointments"))

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

    debugImplementation(libs.bundles.compose.debug)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.mockk)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
}

