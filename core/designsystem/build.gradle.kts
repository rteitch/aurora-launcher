plugins {
    id("aurora.android.library")
    id("aurora.android.compose")
}

android {
    namespace = "app.aurora.launcher.core.designsystem"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    testImplementation(libs.junit4)
}
