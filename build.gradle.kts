plugins {
    // Loom 1.14.x for compatibility with cached dependencies built with newer Loom
    id("fabric-loom") version "1.14.9"
    java
}

group = "com.jelly.farmhelperv2"
version = "1.0.0-alpha"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/releases/")
    maven("https://maven.isxander.dev/releases/")
}

val minecraftVersion: String by project
val yarnMappings: String by project
val loaderVersion: String by project
val fabricApiVersion: String by project
@Suppress("UnstableApiUsage")
val yaclVersion: String = project.findProperty("yacl_version") as String? ?: "3.8.2+1.21.1-fabric"

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    modApi("com.terraformersmc:modmenu:11.0.3")
    modImplementation("dev.isxander:yet-another-config-lib:$yaclVersion")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

val jarBaseName = "MoreThanEnoughUtils"
val jarVersion: String = project.version.toString()
tasks.jar {
    archiveBaseName.set(jarBaseName)
    archiveFileName.set("$jarBaseName-$jarVersion.jar")
}
tasks.named("remapJar") {
    val t = this as org.gradle.api.tasks.bundling.AbstractArchiveTask
    t.archiveBaseName.set(jarBaseName)
    t.archiveFileName.set("$jarBaseName-$jarVersion.jar")
}

loom {
    mixin {
        defaultRefmapName.set("mixins.farmhelper.refmap.json")
    }
    // Avoid applying transitive access wideners from deps (can cause invalid header if cache is stale)
    enableTransitiveAccessWideners.set(false)
}
