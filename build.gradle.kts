// Top-level build file
plugins {
    // AGP 9.0 has built-in Kotlin support
    id("com.android.application") version "9.0.1" apply false
    // Compose Compiler plugin required for Kotlin 2.0+
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
