pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.architectury.dev/") { name = "Architectury" }
        maven("https://maven.minecraftforge.net") { name = "Forge" }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "vs-shields"

include("common")
include("forge")
include("fabric")
