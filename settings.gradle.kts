pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // libsu (topjohnwu) se publikuje přes JitPack
        maven("https://jitpack.io")
    }
}

rootProject.name = "Atop"
include(":app")