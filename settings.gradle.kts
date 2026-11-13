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
        exclusiveContent {
            forRepository { maven("https://maven.aliyun.com/repository/public") }
            filter { includeModule("com.github.promeg", "tinypinyin") }
        }
    }
}

rootProject.name = "Sinan"
include(":app", ":core:search")
