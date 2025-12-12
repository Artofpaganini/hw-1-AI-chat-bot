plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.plugin.compose)
}

android {
    namespace = "com.example.aiagentchat.feature.chat"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
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
    api(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":core:uikit"))

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.bundles.lifecycle)
    implementation(libs.bundles.koin)
    implementation(libs.bundles.network)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.navigation.compose)
    implementation(libs.gson)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}

