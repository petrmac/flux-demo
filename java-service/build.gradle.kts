import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream

plugins {
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.spring") version "1.9.21"
    id("org.shipkit.shipkit-changelog") version "2.0.1"
    id("org.shipkit.shipkit-github-release") version "2.0.1"
    id("org.shipkit.shipkit-auto-version") version "2.1.0"
    java
    groovy
}

group = "com.example"
// Version will be managed by Shipkit auto-version
// It reads from version.properties file or previous git tags
version = project.findProperty("version") ?: "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

extra["springCloudVersion"] = "2025.0.0"

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // Database
    implementation("org.postgresql:postgresql")
    implementation("org.liquibase:liquibase-core")

    // OpenTelemetry for trace context
    implementation("io.opentelemetry:opentelemetry-api:1.31.0")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Micrometer for Prometheus metrics
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("com.h2database:h2")

    // Spock Framework
    testImplementation("org.spockframework:spock-core:2.4-M1-groovy-4.0")
    testImplementation("org.spockframework:spock-spring:2.4-M1-groovy-4.0")
    testImplementation("org.apache.groovy:groovy-all:4.0.15")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}

tasks.named<Jar>("jar") {
    enabled = false
}

// Shipkit configuration - tasks are created by plugins
// Configure existing tasks if they exist
afterEvaluate {
    tasks.findByName("generateChangelog")?.let { task ->
        task.outputs.upToDateWhen { false }
        // Configure changelog task properties
        task.setProperty("repository", "petrmac/flux-demo")
        task.setProperty("outputFile", file("build/changelog.md"))
    }

    tasks.findByName("githubRelease")?.let { task ->
        task.dependsOn("generateChangelog")
        task.doFirst {
            // Check if the tag already exists
            val tagName = "v${project.version}"
            val outputStream = ByteArrayOutputStream()
            val result = exec {
                commandLine("git", "tag", "-l", tagName)
                standardOutput = outputStream
                isIgnoreExitValue = true
            }
            if (result.exitValue == 0 && outputStream.toString().trim().isNotEmpty()) {
                println("Tag $tagName already exists, skipping release")
                task.enabled = false
            }
        }
        task.setProperty("repository", "petrmac/flux-demo")
        task.setProperty("changelog", file("build/changelog.md"))
        task.setProperty("newTagRevision", "main")
        task.setProperty("githubToken", System.getenv("GITHUB_TOKEN") ?: "")
    }
}

// Task to print the version
tasks.register("printVersion") {
    doLast {
        println(project.version)
    }
}

// Task to check if release is needed
tasks.register("checkReleaseNeeded") {
    doLast {
        val tagName = "v${project.version}"
        val outputStream = ByteArrayOutputStream()
        val result = exec {
            commandLine("git", "tag", "-l", tagName)
            standardOutput = outputStream
            isIgnoreExitValue = true
        }
        val tagExists = result.exitValue == 0 && outputStream.toString().trim().isNotEmpty()
        if (tagExists) {
            println("RELEASE_NEEDED=false")
            println("Tag $tagName already exists")
        } else {
            println("RELEASE_NEEDED=true")
            println("Tag $tagName does not exist, release needed")
        }
    }
}

// Task to increment version
tasks.register("incrementVersion") {
    doLast {
        val versionFile = file("version.properties")
        val lines = versionFile.readLines()
        val newLines = lines.map { line ->
            if (line.startsWith("version=")) {
                val currentVersion = line.substringAfter("version=")
                val parts = currentVersion.split(".")
                val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
                val newVersion = "${parts[0]}.${parts[1]}.${patch + 1}"
                "version=$newVersion"
            } else if (line.startsWith("previousVersion=")) {
                "previousVersion=${project.version}"
            } else {
                line
            }
        }
        versionFile.writeText(newLines.joinToString("\n"))
        println("Version incremented to: ${newLines.find { it.startsWith("version=") }?.substringAfter("version=")}")
    }
}