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
        // Required for MPAndroidChart and other community libraries
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "RaiForm"
include(":app")
