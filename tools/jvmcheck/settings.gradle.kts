pluginManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        google() // only the CI renderer resolves from here (dl.google.com is blocked in the cloud container)
    }
}
rootProject.name = "jvmcheck"
