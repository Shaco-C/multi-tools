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
    }
}

rootProject.name = "PocketToolbox"

include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:navigation")
include(":core:backup")
include(":feature:electricity")
