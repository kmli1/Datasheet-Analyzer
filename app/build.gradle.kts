import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val stage7ReleaseSigning = providers.gradleProperty("releaseSigningFile").orNull?.let { path ->
    val file = rootProject.file(path)
    Properties().also { properties -> file.inputStream().use { properties.load(it) } } to file.parentFile
}

android {
    namespace = "com.example.datasheetanalyzer"
    compileSdk = 36

    // Development APK only. Kept with the project so later test builds can update this APK.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("signing/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    signingConfigs.create("production") {
        stage7ReleaseSigning?.let { (properties, folder) ->
            storeFile = File(folder, properties.getProperty("storeFile"))
            storePassword = properties.getProperty("storePassword")
            keyAlias = properties.getProperty("keyAlias")
            keyPassword = properties.getProperty("keyPassword")
        }
    }
    buildTypes.getByName("release") {
        isMinifyEnabled = true
        isShrinkResources = true
        signingConfig = signingConfigs.getByName("production")
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    testBuildType = providers.gradleProperty("deviceTestBuildType").getOrElse("debug")
    defaultConfig {
        applicationId = providers.gradleProperty("releaseApplicationId").getOrElse("com.example.datasheetanalyzer")
        minSdk = 26
        targetSdk = 36
        testProguardFiles("test-proguard-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 18
        versionName = "1.0.10"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
        aidl = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    providers.gradleProperty("qaDirectory").orNull?.let { systemProperty("datasheet.qaDir",it) }
    providers.gradleProperty("realDatasheetDirectory").orNull?.let { systemProperty("datasheet.realDir",it) }
    listOf("https.proxyHost","https.proxyPort","http.proxyHost","http.proxyPort","javax.net.ssl.trustStore").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key,it) }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    constraints {
        implementation("org.bouncycastle:bcprov-jdk15to18:1.86")
        implementation("org.bouncycastle:bcpkix-jdk15to18:1.86")
        implementation("org.bouncycastle:bcutil-jdk15to18:1.86")
    }
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.23.2")
    val composeBom = platform("androidx.compose:compose-bom:2026.01.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.core:core-ktx:1.17.0")
    val cameraX = "1.5.3"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    // Bundled Latin OCR: no Play Services model download, cloud key or account.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

gradle.taskGraph.whenReady {
    if (allTasks.any { it.name in listOf("bundleRelease", "assembleRelease", "packageRelease") }) {
        require(stage7ReleaseSigning != null && listOf("storeFile", "storePassword", "keyAlias", "keyPassword").all { stage7ReleaseSigning!!.first.getProperty(it)?.isNotBlank() == true }) { "Release requires -PreleaseSigningFile=/private/keystore.properties" }
    }
}
tasks.register("stage7DependencyManifest") {
    doLast {
        val file = layout.buildDirectory.file("reports/runtime-dependencies.tsv").get().asFile
        file.parentFile.mkdirs()
        file.writeText(configurations.getByName("releaseRuntimeClasspath").resolvedConfiguration.resolvedArtifacts.sortedBy { it.moduleVersion.id.toString() }.joinToString("\n") { "${it.moduleVersion.id}\t${it.file.absolutePath}" })
    }
}
