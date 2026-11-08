plugins {
    java
}

group = "net.craftavia"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.opencollab.dev/main/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    // Built against the oldest supported API; runs on 1.21.4+ and 26.x.
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.geysermc.floodgate:api:2.2.5-SNAPSHOT")
    // Adventure always comes from the server (paper-api), never from PacketEvents.
    compileOnly("com.github.retrooper:packetevents-api:2.14.0") { exclude(group = "net.kyori") }
    compileOnly("com.github.retrooper:packetevents-spigot:2.14.0") { exclude(group = "net.kyori") }
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal", "-Xlint:unchecked"))
    }

    processResources {
        val pluginVersion = project.version.toString()
        inputs.property("version", pluginVersion)
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand("version" to pluginVersion)
        }
    }

    jar {
        archiveBaseName.set("BedrockMenus")
        from("LICENSE")
    }
}
