# jvmcheck

Type-check harness for the app's Kotlin where no Android SDK is available (e.g. a cloud container
that cannot reach dl.google.com). It compiles `app/src/main/java` against JetBrains Compose for
desktop (same `androidx.compose.*` packages), Robolectric's `android-all` framework jar and the
stubs in `stubs/` (DataStore, activity-compose, BuildConfig, R), then runs `app/src/test`.

    cd tools/jvmcheck && gradle test --no-daemon

It is not a substitute for `./gradlew :app:assembleDebug`; AndroidX-only APIs are stubbed, not verified.
