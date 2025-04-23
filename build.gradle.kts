plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.8.0"
    id("org.jetbrains.intellij") version "1.13.3"
}

group = "ai.faros"
version = "0.1.0"

repositories {
    mavenCentral()
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2022.3.3")  // Use a slightly older version that's more stable
    type.set("IU") // Target IntelliJ IDEA Ultimate which includes modules for all IDEs

    // Include Git4Idea for development, but our code has fallbacks for IDEs that don't have it
    plugins.set(listOf(
        "Git4Idea"
    ))
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin")
        }
        resources {
            srcDirs("src/main/resources")
        }
    }
    test {
        kotlin {
            srcDirs("src/test/kotlin")
        }
    }
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    // Don't explicitly include kotlin stdlib, it's provided by the platform
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
}

// Configure Gradle IntelliJ Plugin - read more: https://github.com/JetBrains/gradle-intellij-plugin
tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("223")
        untilBuild.set("261.*") // or "999.*" if you want no upper limit (not recommended for public plugins)
    }

    buildSearchableOptions {
        enabled = false  // Disable to save memory
    }

    processResources {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }

    runIde {
        // Increase memory
        jvmArgs = listOf("-Xmx2g")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
} 