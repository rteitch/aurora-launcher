pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "aurora-launcher"

include(":app")
include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:data")
include(":core:platform")
include(":core:testing")
include(":feature:onboarding")
include(":feature:home")
include(":feature:folder")
include(":feature:applibrary")
include(":feature:spotlight")
include(":feature:widgets")
include(":feature:controlpanel")
include(":feature:settings")
include(":benchmark")
include(":baselineprofile")
