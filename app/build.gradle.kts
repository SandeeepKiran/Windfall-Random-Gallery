plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.mousy.windfall"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mousy.windfall"
        minSdk = 30
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Release signing comes from ~/.gradle/gradle.properties locally or env vars on CI, so the
    // keystore and its passwords never enter the repo. Without them, release builds come out unsigned.
    fun signingValue(name: String): String? =
        (project.findProperty(name) as String?) ?: System.getenv(name)
    val releaseKeystore = signingValue("WINDFALL_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signingValue("WINDFALL_KEYSTORE_PASSWORD")
                keyAlias = signingValue("WINDFALL_KEY_ALIAS")
                keyPassword = signingValue("WINDFALL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            // A debug build installs NEXT TO the real app ("Windfall Debug"), never over it: its
            // own data, its own permissions, and no signing clash with the release key. So a
            // test build can run on the phone with test photos only.
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        viewBinding = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    // navigation-compose removed (#12): tabs stay ViewModel + AnimatedContent (low-risk).

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.media3.exoplayer)
    // Only ContentFrame is used; the View-based media3-ui and the Material player widgets are not.
    implementation(libs.media3.ui.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.gif)
    implementation(libs.androidx.profileinstaller)

    baselineProfile(project(":baselineprofile"))

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Plain-JVM unit tests (app/src/test). org.json is built into Android but not into the JVM.
    testImplementation(libs.junit4)
    testImplementation(libs.json)
}
