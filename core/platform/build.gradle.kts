plugins {
    id("aurora.android.library")
    id("aurora.android.compose")
    id("aurora.android.hilt")
}

android {
    namespace = "app.aurora.launcher.core.platform"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.window)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
