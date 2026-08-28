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

rootProject.name = "assistant-android"

include(":app")
include(":core:model")
include(":core:network")
include(":core:security")
include(":core:database")
include(":core:designsystem")
include(":backend-client")
include(":feature:onboarding")
include(":feature:chat")
include(":feature:localmodel")
