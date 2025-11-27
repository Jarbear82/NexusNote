import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.components.uiToolingPreview)
            implementation(compose.components.resources)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.runtime)
            implementation(compose.ui)

            implementation(libs.androidx.datastore.preferences)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.jetbrains.markdown)
            implementation(libs.kamel.image.default)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.cio)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.sqlite.driver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.sqldelight.sqlite.driver)
            implementation("org.slf4j:slf4j-simple:2.0.9")
        }
    }
}


compose.desktop {
    application {
        mainClass = "com.tau.nexus_note.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.AppImage)

            packageName = "com.tau.nexus_note"
            packageVersion = "1.0.0"

        }

        // Add ProGuard rules for release builds
        buildTypes.release.proguard {
        }
    }
}

configurations.all {
    exclude(group = "androidx.compose.ui", module = "ui-util")
}

sqldelight {
    databases {
        create("AppDatabase") {
            packageName.set("com.tau.nexus_note.db")
            dialect(libs.sqldelight.sqlite.dialect)
        }
    }
}