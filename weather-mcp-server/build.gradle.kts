plugins {
    alias(libs.plugins.kotlin.jvm)
    kotlin("plugin.serialization") version "2.0.21"
    application
}

group = "com.example"
version = "1.0.0"

dependencies {
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-cio:2.3.12")
    implementation("io.ktor:ktor-server-cors:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.0")
}

application {
    mainClass.set("com.example.weathermcpserver.WeatherMcpServerSimpleKt")
}

tasks.jar {
    archiveBaseName.set("weather-mcp-server")
    archiveVersion.set("1.0.0")
    
    manifest {
        attributes["Main-Class"] = "com.example.weathermcpserver.WeatherMcpServerSimpleKt"
    }
    
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    doLast {
        println("JAR created at: ${archiveFile.get().asFile.absolutePath}")
    }
}

tasks.register<Jar>("fatJar") {
    archiveBaseName.set("weather-mcp-server")
    archiveVersion.set("1.0.0")
    archiveClassifier.set("all")
    
    manifest {
        attributes["Main-Class"] = "com.example.weathermcpserver.WeatherMcpServerSimpleKt"
    }
    
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get() as CopySpec)
    
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    doLast {
        println("Fat JAR created at: ${archiveFile.get().asFile.absolutePath}")
    }
}

kotlin {
    jvmToolchain(17)
}

