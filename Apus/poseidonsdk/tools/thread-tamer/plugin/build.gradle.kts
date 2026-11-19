import org.gradle.api.publish.PublishingExtension

plugins {
    `java-gradle-plugin`
    kotlin("jvm")
    id("com.vanniktech.maven.publish")
}

val groupBase = project.findProperty("GROUP")?.toString() ?: "io.threadtamer"
val artifactBase = project.findProperty("POM_ARTIFACT_ID")?.toString() ?: "thread-tamer-weaver"
val baseVersion = project.findProperty("THREAD_TAMER_VERSION_NAME")?.toString() ?: "1.0.0"
val pomLabel = project.findProperty("POM_NAME")?.toString() ?: "Thread Tamer Gradle Plugin"
val pomDesc = project.findProperty("POM_DESCRIPTION")?.toString()
    ?: "Android build-time thread governance plugin"

group = groupBase
version = baseVersion
base.archivesName.set(artifactBase)

dependencies {
    compileOnly("com.android.tools.build:gradle:8.7.3")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-tree:9.7.1")
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        create("threadTamer") {
            id = "io.threadtamer.weaver"
            implementationClass = "io.threadtamer.gradle.ThreadTamerPlugin"
            displayName = "Thread Tamer Instrumentation Plugin"
            description = pomDesc
        }
    }
}

kotlin {
    jvmToolchain(17)
}

mavenPublishing {
    pom {
        name.set(pomLabel)
        description.set(pomDesc)
        inceptionYear.set("2026")
    }
}

//extensions.configure<PublishingExtension>("publishing") {
//    repositories {
//        maven {
//            name = "xal"
//            val releaseRepository = providers.gradleProperty("RELEASE_REPOSITORY_URL")
//                .getOrElse("https://maven.apuscn.com/nexus/content/repositories/android-xal-releases/")
//            val snapshotRepository = providers.gradleProperty("SNAPSHOT_REPOSITORY_URL")
//                .getOrElse("https://maven.apuscn.com/nexus/content/repositories/android-xal-snapshots/")
//            url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotRepository else releaseRepository)
//            credentials {
//                username = providers.gradleProperty("MAVEN_USERNAME").orNull ?: ""
//                password = providers.gradleProperty("MAVEN_PASSWORD").orNull ?: ""
//            }
//        }
//    }
//}

if (!project.hasProperty("POM_ARTIFACT_ID")) {
    extra["POM_ARTIFACT_ID"] = artifactBase
}
