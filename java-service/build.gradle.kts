import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.1"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.spring") version "1.9.21"
    id("org.shipkit.shipkit-changelog") version "2.0.1"
    id("org.shipkit.shipkit-github-release") version "2.0.1"
    id("org.shipkit.shipkit-auto-version") version "2.1.0"
    java
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

extra["springCloudVersion"] = "2023.0.0"

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")

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
        task.setProperty("repository", "petrmac/flux-demo")
        task.setProperty("changelog", file("build/changelog.md"))
        task.setProperty("newTagRevision", "HEAD")
        task.setProperty("githubToken", System.getenv("GITHUB_TOKEN") ?: "")
    }
}

// Task to print the version
tasks.register("printVersion") {
    doLast {
        println(project.version)
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