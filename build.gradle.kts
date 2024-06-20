import org.jetbrains.changelog.Changelog

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.10"
    id("org.jetbrains.intellij.platform") version "2.0.0-beta5"
    id("org.jetbrains.qodana") version "0.1.13"
    id("org.jetbrains.changelog") version "2.2.0"
    id("org.jetbrains.kotlinx.kover") version "0.7.3"
//    id("org.jetbrains.intellij.platform.migration") version "2.0.0-beta5"
}

fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)


val sinceBuildPluginXml: String by project
val untilBuildPluginXml: String by project
val ideaVersion: String by project
val targetIdePlatform: String by project
val pluginVersion: String by project

group = "com.github.manu156"
version = "$pluginVersion-$ideaVersion-$targetIdePlatform"

repositories {
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    gradlePluginPortal()
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // https://mvnrepository.com/artifact/org.eclipse.persistence/org.eclipse.persistence.jpa.jpql
    implementation("org.eclipse.persistence:org.eclipse.persistence.jpa.jpql:4.0.2")
    // https://mvnrepository.com/artifact/org.mockito/mockito-core
    testImplementation("org.mockito:mockito-core:5.5.0")
    intellijPlatform {
        intellijIdeaUltimate("2023.2")//todo
//        plugins("com.intellij.java")
        bundledPlugin("com.intellij.java")

        pluginVerifier()
        zipSigner()
        instrumentationTools()

    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellijPlatform {
    version = "$targetIdePlatform-$ideaVersion"
//    type.set(targetIdePlatform) // Target IDE Platform
    pluginConfiguration {
        ideaVersion {
            sinceBuild = sinceBuildPluginXml
            untilBuild = untilBuildPluginXml
        }
    }

    signing {
        certificateChain = environment("CERTIFICATE_CHAIN")
        privateKey = environment("PRIVATE_KEY")
        password = environment("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = environment("PUBLISH_TOKEN")
        // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
        // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
        // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
        channels = properties("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
    }

    verifyPlugin {
        ides {
            recommended()
        }
    }
}

qodana {
    cachePath.set(provider { file(".qodana").canonicalPath })
    reportPath.set(provider { file("build/reports/inspections").canonicalPath })
    saveReport.set(true)
    showReport.set(environment("QODANA_SHOW_REPORT").map { it.toBoolean() }.getOrElse(false))
}

// Configure Gradle Changelog Plugin - read more: https://github.com/JetBrains/gradle-changelog-plugin
changelog {
    version.set(pluginVersion)
    groups.empty()
    repositoryUrl.set(properties("pluginRepositoryUrl"))
}

// Configure Gradle Kover Plugin - read more: https://github.com/Kotlin/kotlinx-kover#configuration
koverReport {
    defaults {
        xml {
            onCheck = true
        }
    }
}

tasks {

    wrapper {
        gradleVersion = properties("gradleVersion").get()
    }

    // Configure UI tests plugin
    // Read more: https://github.com/JetBrains/intellij-ui-test-robot
    testIdeUi {
        systemProperty("robot-server.port", "8082")
        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
        systemProperty("jb.consents.confirmation.enabled", "false")
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }
//
//    // Set the JVM compatibility versions
//    withType<JavaCompile> {
//        sourceCompatibility = "21"
//        targetCompatibility = "21"
//    }
//    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
//        kotlinOptions.jvmTarget = "20"
//    }
//
//    patchPluginXml {
//        sinceBuild.set(sinceBuildPluginXml)
//        untilBuild.set(untilBuildPluginXml)
//        changeNotes.set(provider {
//            changelog.renderItem(
//                changelog
//                    .getUnreleased()
//                    .withHeader(false)
//                    .withEmptySections(false),
//                Changelog.OutputType.HTML
//            )
//        })
//    }
//
//    // Configure UI tests plugin
//    // Read more: https://github.com/JetBrains/intellij-ui-test-robot
////    runIdeForUiTests {
////        systemProperty("robot-server.port", "8082")
////        systemProperty("ide.mac.message.dialogs.as.sheets", "false")
////        systemProperty("jb.privacy.policy.text", "<!--999.999-->")
////        systemProperty("jb.consents.confirmation.enabled", "false")
////    }
//
//    signPlugin {
//        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
//        privateKey.set(System.getenv("PRIVATE_KEY"))
//        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
//    }
//
//    publishPlugin {
//        token.set(System.getenv("PUBLISH_TOKEN"))
//    }
}
