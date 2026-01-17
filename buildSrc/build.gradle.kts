plugins {
    `kotlin-dsl`
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    implementation("com.android.tools.build:gradle:8.6.0")
    
    // Ktor Client для HTTP запросов
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    
    // Kotlin Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.0")
    
    // JGit для работы с Git
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.9.0.202403050737-r")
    
    // Google Play Publisher API
    implementation("com.google.apis:google-api-services-androidpublisher:v3-rev20251215-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.23.0")
    implementation("com.google.api-client:google-api-client:2.2.0")
    implementation("com.google.api-client:google-api-client-gson:2.2.0")
    
    // SQLite для RAG индекса
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
}

kotlin {
    jvmToolchain(17)
}
