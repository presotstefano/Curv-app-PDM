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

        maven {
            url = uri("https://jitpack.io")
            isAllowInsecureProtocol = false
            // Nessuna auth qui!
        }
    }
}

rootProject.name = "CurvApp"
include(":app")
