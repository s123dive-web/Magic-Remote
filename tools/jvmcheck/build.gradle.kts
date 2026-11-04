// Type-check harness: compiles the app's Kotlin against JetBrains Compose (desktop) + Robolectric's
// android framework jar + small stubs, and runs the pure unit tests. It is NOT the Android build;
// it exists so that most compile errors are caught where the Android SDK is unavailable.
plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21"
}
val app = rootDir.resolve("../../app/src").canonicalPath
kotlin { jvmToolchain(21) }
sourceSets {
    main { kotlin { srcDir("$app/main/java"); srcDir("stubs") } }
    test { kotlin { srcDir("$app/test/java") } }
}
val cmp = "1.6.11"
configurations.all {
    // Google-only base libraries (not on Maven Central); the stubs/ folder covers what the app touches.
    exclude(group = "androidx.annotation"); exclude(group = "androidx.collection"); exclude(group = "androidx.lifecycle")
    exclude(group = "org.jetbrains.androidx.lifecycle"); exclude(group = "org.jetbrains.androidx.navigation")
    exclude(group = "org.jetbrains.androidx.core"); exclude(group = "org.jetbrains.androidx.savedstate")
}
dependencies {
    compileOnly("org.robolectric:android-all:16-robolectric-13921718")
    testCompileOnly("org.robolectric:android-all:16-robolectric-13921718")
    implementation("org.jetbrains.compose.runtime:runtime-desktop:$cmp")
    implementation("org.jetbrains.compose.ui:ui-desktop:$cmp")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:$cmp")
    implementation("org.jetbrains.compose.material3:material3-desktop:$cmp")
    implementation("org.jetbrains.compose.material:material-icons-core-desktop:$cmp")
    implementation("org.jetbrains.compose.material:material-icons-extended-desktop:$cmp")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xskip-prerelease-check")
}
tasks.test { useJUnit(); testLogging { events("failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL } }
