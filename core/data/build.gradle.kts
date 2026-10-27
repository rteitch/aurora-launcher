plugins {
    id("aurora.android.library")
    id("aurora.android.room")
    id("aurora.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.aurora.launcher.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:platform"))
    implementation(project(":core:common"))
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.room.testing)
    testImplementation(project(":core:testing"))
}
