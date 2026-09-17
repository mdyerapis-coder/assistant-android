plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.chaquopy)
}

android {
    namespace = "com.mdyerapis.sable.spike.chaquopy"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.mdyerapis.sable.spike.chaquopy"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1-spike"
        ndk {
            // arm64 is the device ABI that matters for Option A.
            // x86_64 is included so an emulator can run the import check.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }
    flavorDimensions += "wheels"
    productFlavors {
        create("empty") {
            dimension = "wheels"
            applicationIdSuffix = ".empty"
            versionNameSuffix = "-empty"
        }
        create("resolved") {
            dimension = "wheels"
            applicationIdSuffix = ".resolved"
            versionNameSuffix = "-resolved"
        }
        create("full") {
            dimension = "wheels"
            applicationIdSuffix = ".full"
            versionNameSuffix = "-full"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

chaquopy {
    defaultConfig {
        version = "3.12"
        buildPython("python3.12")
        pyc {
            src = false
        }
    }
    productFlavors {
        getByName("empty") {
            // Python interpreter only — size baseline, no extra pip packages.
        }
        getByName("resolved") {
            pip {
                install("-r", "requirements-android-resolved.txt")
            }
        }
        getByName("full") {
            pip {
                install("-r", "requirements-android.txt")
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
}

tasks.register<Exec>("probeChaquopyWheels") {
    group = "verification"
    description = "Per-package Android wheel probe (pip + Chaquopy/Flet indexes)."
    workingDir = projectDir
    commandLine("python3", "scripts/probe_wheels.py")
}
