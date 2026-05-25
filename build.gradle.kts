plugins {
    id("fabric-loom") version "1.13-SNAPSHOT"
    kotlin("jvm") version "2.3.20"
}

version = project.property("mod_version") as String
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 21

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = targetJavaVersion
}

kotlin {
    jvmToolchain(targetJavaVersion)
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    withSourcesJar()
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
