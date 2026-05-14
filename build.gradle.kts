plugins {
    id("java-library")
    alias(libs.plugins.run.paper)
    alias(libs.plugins.shadow)
    alias(libs.plugins.minotaur)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.paper.api)
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks {
    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G")
        pluginJars(shadowJar.flatMap { it.archiveFile })
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
    }

    jar {
        archiveClassifier.set("plain")
    }

    build {
        dependsOn(shadowJar)
    }
}

modrinth {
    token.set(providers.environmentVariable("MODRINTH_TOKEN"))
    projectId.set("chunkgenerator")
    versionNumber.set(project.version.toString())
    versionType.set(resolveVersionType(project.version.toString()))
    uploadFile.set(tasks.shadowJar.flatMap { it.archiveFile })
    gameVersions.addAll("26.1.2")
    loaders.addAll("paper", "purpur", "folia")
    changelog.set(provider { extractChangelogSection(project.version.toString()) })
    syncBodyFrom.set(provider { file("README.md").readText() })
    dependencies {
        optional.project("luckperms")
    }
}

fun resolveVersionType(version: String): String = when {
    version.contains("alpha") -> "alpha"
    version.contains("beta") -> "beta"
    else -> "release"
}

fun extractChangelogSection(version: String): String {
    val file = file("CHANGELOG.md")
    if (!file.exists()) return ""
    val out = StringBuilder()
    var capture = false
    for (line in file.readLines()) {
        if (line.startsWith("## ")) {
            if (capture) break
            capture = line.contains(version)
            continue
        }
        if (capture) out.appendLine(line)
    }
    return out.toString().trim()
}
