import java.text.SimpleDateFormat
import java.util.*

plugins {
    id("com.gradleup.shadow") version "9.3.0"
    kotlin("jvm") version "2.3.20"
}

kotlin {
    jvmToolchain(25)
}

group = "org.winlogon.skuld"

fun getTime(): String {
    val sdf = SimpleDateFormat("yyMMdd-HHmm")
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date()).toString()
}

val shortVersion: String? = if (project.hasProperty("ver")) {
    val ver = project.property("ver").toString()
    if (ver.startsWith("v")) {
        ver.substring(1).uppercase()
    } else {
        ver.uppercase()
    }
} else {
    null
}

val version: String = when {
    shortVersion.isNullOrEmpty() -> "${getTime()}-SNAPSHOT"
    shortVersion.contains("-RC-") -> shortVersion.substringBefore("-RC-") + "-SNAPSHOT"
    else -> shortVersion
}

val pluginName = rootProject.name
val pluginVersion = version
val pluginPackage = project.group.toString()
val projectName = rootProject.name

repositories {
    mavenCentral()
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
        content {
            includeModule("io.papermc.paper", "paper-api")
            includeModule("net.md-5", "bungeecord-chat")
        }
    }
    maven {
        name = "minecraft"
        url = uri("https://libraries.minecraft.net")
        content {
            includeModule("com.mojang", "brigadier")
        }
    }
    maven {
        url = uri("https://maven.winlogon.org/releases")
    }
    maven {
        url = uri("https://repo.codemc.org/repository/maven-public/")
    }
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")

    compileOnly("org.json:json:20250107")
    compileOnly("com.github.ben-manes.caffeine:caffeine:3.2.0")
    compileOnly("org.winlogon:xpconomy:0.2.3")
    compileOnly("de.exlll:configlib-paper:4.8.1")

    // Database dependencies: PostgreSQL, MySQL, SQLite, Exposed and HikariCP
    compileOnly("org.jetbrains.exposed:exposed-core:1.3.0")
    compileOnly("org.jetbrains.exposed:exposed-jdbc:1.3.0")
    compileOnly("com.zaxxer:HikariCP:6.2.1")
    compileOnly("org.postgresql:postgresql:42.7.7")
    compileOnly("com.mysql:mysql-connector-j:9.3.0")
    runtimeOnly("org.xerial:sqlite-jdbc:3.50.3.0")
    
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    testImplementation("de.exlll:configlib-paper:4.8.1")
    testImplementation("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.99.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.3.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.0-M1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("com.github.ben-manes.caffeine:caffeine:3.2.0")
    testImplementation("org.winlogon:xpconomy:0.2.3")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.0-M1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    filesMatching("**/paper-plugin.yml") {
        expand(
            "NAME" to pluginName,
            "VERSION" to pluginVersion,
            "PACKAGE" to pluginPackage
        )
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    minimize()
}

// Disable jar and replace with shadowJar
tasks.jar {
    enabled = false
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

// Utility tasks
tasks.register("printProjectName") {
    doLast {
        println(projectName)
    }
}

var shadowJarTask = tasks.shadowJar.get()
tasks.register("release") {
    dependsOn(tasks.build)
    doLast {
        if (!version.endsWith("-SNAPSHOT")) {
            shadowJarTask.archiveFile.get().asFile.renameTo(
                file("${layout.buildDirectory.get()}/libs/${rootProject.name}.jar")
            )
        }
    }
}
