import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "app.aurora.launcher.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.room.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "aurora.android.application"
            implementationClass = "AuroraAndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "aurora.android.library"
            implementationClass = "AuroraAndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "aurora.android.compose"
            implementationClass = "AuroraAndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "aurora.android.hilt"
            implementationClass = "AuroraAndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "aurora.android.room"
            implementationClass = "AuroraAndroidRoomConventionPlugin"
        }
        register("jvmLibrary") {
            id = "aurora.jvm.library"
            implementationClass = "AuroraJvmLibraryConventionPlugin"
        }
    }
}
