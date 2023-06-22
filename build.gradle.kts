plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.7.20"
    id("org.jetbrains.intellij") version "1.14.1"
}

group = "com.github.manu156"
version = "0.6-223"
//version = "1.0-SNAPSHOT"

val sinceBuildPluginXml: String by project
val untilBuildPluginXml: String by project
val ideaVersion: String by project


repositories {
    mavenCentral()
}

dependencies {
    // https://mvnrepository.com/artifact/org.eclipse.persistence/org.eclipse.persistence.jpa.jpql
    implementation("org.eclipse.persistence:org.eclipse.persistence.jpa.jpql:4.0.1")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set(ideaVersion)
    updateSinceUntilBuild.set(false)
    type.set("IC") // Target IDE Platform

    plugins.set(listOf("com.intellij.java"))
}

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
        sinceBuild.set(sinceBuildPluginXml)
        untilBuild.set(untilBuildPluginXml)
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
