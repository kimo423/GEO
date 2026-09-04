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
        // repo.maven.apache.org (Maven Central CDN) can fail TLS from this host;
        // repo1 is the canonical Central layout used as a fallback.
        maven {
            url = uri("https://repo1.maven.org/maven2")
        }
    }
}

rootProject.name = "GEO"
include(":app")
