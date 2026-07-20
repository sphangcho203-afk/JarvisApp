plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStorePath = providers.environmentVariable("FRIDAY_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("FRIDAY_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("FRIDAY_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("FRIDAY_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

val integrateUniversalProviderMesh by tasks.registering(Exec::class) {
    group = "friday"
    description = "Deterministically wires provider, voice, sensory, cinematic OS and world-map systems into FRIDAY."
    workingDir(rootProject.projectDir)
    commandLine("python3", "scripts/apply_provider_integrations.py")
    inputs.files(
        rootProject.file("scripts/apply_provider_integrations.py"),
        rootProject.file("scripts/integration_provider_mesh.py"),
        rootProject.file("scripts/integration_provider_workspace.py"),
        rootProject.file("scripts/integration_voice_lab.py"),
        rootProject.file("scripts/integration_operational_sensory.py"),
        rootProject.file("scripts/integration_reference_locked_ui.py"),
        rootProject.file("scripts/integration_cinematic_os_reference_fix.py"),
        rootProject.file("scripts/integration_world_map_master.py"),
        rootProject.file("app/src/main/java/com/seongja/jarvis/UniversalProviderMesh.kt"),
        rootProject.file("app/src/main/java/com/seongja/jarvis/FridayLocationRuntime.kt"),
        rootProject.file("app/src/main/java/com/seongja/jarvis/JarvisBrain.kt"),
        rootProject.file("app/src/main/java/com/seongja/jarvis/MainActivity.kt"),
        rootProject.file("helix-ui/src/FridayCinematicOS.tsx"),
        rootProject.file("helix-ui/src/WorldMapMaster.tsx"),
        rootProject.file("helix-ui/src/worldMapData.ts"),
        rootProject.file("helix-ui/src/worldMapMath.ts"),
        rootProject.file("helix-ui/src/WorldMapSonic.ts"),
        rootProject.file("helix-ui/src/fridayCinematicOS.css"),
        rootProject.file("helix-ui/src/fridayCinematicOSFixes.css"),
        rootProject.file("helix-ui/src/worldMapMaster.css")
    )
    outputs.upToDateWhen { false }
}

tasks.named("preBuild").configure {
    dependsOn(integrateUniversalProviderMesh)
}

android {
    namespace = "com.seongja.jarvis"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.seongja.jarvis"
        minSdk = 28
        targetSdk = 36
        versionCode = 45
        versionName = "0.17.0-world-map-master"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseStorePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
        }
        release {
            isDebuggable = false
            isMinifyEnabled = false
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        htmlReport = true
        xmlReport = true
    }

    testOptions {
        animationsDisabled = true
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-auth:21.6.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.activity:activity-ktx:1.13.0")

    val cameraXVersion = "1.6.1"
    implementation("androidx.camera:camera-core:$cameraXVersion")
    implementation("androidx.camera:camera-camera2:$cameraXVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraXVersion")
    implementation("androidx.camera:camera-view:$cameraXVersion")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
}
