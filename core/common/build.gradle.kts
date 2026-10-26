plugins {
    id("aurora.android.library")
}

android {
    namespace = "app.aurora.launcher.core.common"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
}
