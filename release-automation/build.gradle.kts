plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    // Ktor Client
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    
    // Kotlin Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Kotlin Coroutines
    implementation(libs.kotlinx.coroutines.core)
    
    // JGit
    implementation(libs.jgit)
    
    // Google Play Publisher API
    implementation(libs.google.api.services.androidpublisher)
    implementation(libs.google.auth.library.oauth2.http)
    implementation(libs.google.api.client)
    implementation(libs.google.api.client.gson)
    
    // SQLite для RAG индекса
    implementation("org.xerial:sqlite-jdbc:3.44.1.0")
}
