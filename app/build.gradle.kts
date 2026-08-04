plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.ksp) // ✅ keep this
    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.hilt)
    alias(libs.plugins.serialization)
    alias(libs.plugins.compose.compiler)
    // ❌ REMOVED: id("com.google.devtools.ksp") (duplicate)
}

android {
    namespace = "com.kaajjo.libresudoku"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.swayamkrushi.kanjisudoku"
        minSdk = 26
        targetSdk = 36
        versionCode = 59
        versionName = "3.5.9"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // F-Droid
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    flavorDimensions += "version"
    productFlavors {
        create("foss") {
            dimension = "version"
        }
        create("nonFOSS") {
            dimension = "version"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    lint {
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

aboutLibraries {
    excludeFields = arrayOf("generated")
}

dependencies {

    implementation(libs.core.ktx)
    implementation("com.google.android.gms:play-services-ads:23.1.0")

    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)

    implementation(libs.activity.compose)
    implementation(libs.ui)
    implementation(libs.ui.util)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)

    implementation(libs.material3)
    implementation(libs.material.icons.extended)

    implementation(project(":Color-Picker"))

    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
    testImplementation(libs.junit)

    implementation(libs.graphics.shape)

    implementation(libs.accompanist.systemuicontroller)
    implementation(libs.accompanist.pager.indicators)

    implementation(libs.hilt)
    implementation(libs.hilt.navigation)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)
    implementation(libs.appcompat)
    implementation(libs.aboutLibraries)

    // ✅ Compose Destinations (already correct)
    implementation(libs.compose.destinations)
    ksp(libs.compose.destinations.ksp)

    implementation(libs.serialization.json)
    implementation(libs.documentFile)

    implementation(libs.workRuntimeKtx)
    implementation(libs.hilt.work)
    implementation(libs.hilt.common)

    ksp(libs.hilt.common.compiler)
    ksp(libs.hilt.work)

    implementation(libs.materialKolor)
    implementation(libs.okhttp)
    implementation(libs.composeMarkdown)
}

ksp {
    arg("room.schemaLocation", "${projectDir}/schemas")
}