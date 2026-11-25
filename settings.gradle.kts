pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroupByRegex("com\\.github\\..*")
                includeGroupByRegex("io\\.github\\.junkfood02.*")
                includeGroupByRegex("cz\\.adaptech\\..*")
            }
        }
    }
}

rootProject.name = "Jumlo"
include(":app")
include(":benchmark")

include(":core-common", ":downloader-core", ":document-core", ":storage-core")
