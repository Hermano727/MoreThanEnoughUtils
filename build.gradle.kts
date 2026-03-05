plugins {
    // Loom 1.11.x is recommended for Minecraft 1.21.9/1.21.10
    id("fabric-loom") version "1.11.6"
    java
}

group = "com.jelly.farmhelperv2"
version = "3.0.0-alpha"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/releases/")
}

val minecraftVersion: String by project
val yarnMappings: String by project
val loaderVersion: String by project
val fabricApiVersion: String by project

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    modApi("com.terraformersmc:modmenu:11.0.3")
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

loom {
    mixin {
        defaultRefmapName.set("mixins.farmhelper.refmap.json")
    }
}
