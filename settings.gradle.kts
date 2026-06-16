val gradleAppName = System.getProperty("org.gradle.appname").orEmpty()
if (gradleAppName == "gradle") {
    error(
        "This project must be built through the checked-in Gradle wrapper. " +
            "Use .\\gradlew.bat <task> on Windows or ./gradlew <task> on Unix-like shells."
    )
}

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
    }
}

rootProject.name = "LCSC_android_erp"
include(":app")
