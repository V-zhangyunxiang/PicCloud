// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        classpath (libs.dokka.gradle.plugin)
        classpath( libs.rust.gradle.plugin)
        classpath (libs.gradleMavenPublishPlugin)
    }
}
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlinCocoapods) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlinx.atomicfu) apply false
}

subprojects {
    pluginManager.withPlugin("com.vanniktech.maven.publish") {
        extensions.configure<PublishingExtension>("publishing") {
            repositories {
                maven {
                    name = "xal"
                    val releaseUrl = findProperty("RELEASE_REPOSITORY_URL")?.toString()
                        ?: "https://maven.apuscn.com/nexus/content/repositories/android-xal-releases/"
                    val snapshotUrl = findProperty("SNAPSHOT_REPOSITORY_URL")?.toString()
                        ?: "https://maven.apuscn.com/nexus/content/repositories/android-xal-snapshots/"
                    url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotUrl else releaseUrl)
                    credentials {
                        username = findProperty("MAVEN_USERNAME")?.toString() ?: ""
                        password = findProperty("MAVEN_PASSWORD")?.toString() ?: ""
                    }
                }
            }
        }
    }
}
