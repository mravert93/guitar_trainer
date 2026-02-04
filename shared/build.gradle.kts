import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

val ktor_version: String by project

plugins {
    alias(libs.plugins.kotlinMultiplatform)

    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.21"
}

kotlin {
    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Ktor client (multiplatform)
                implementation("io.ktor:ktor-client-core:$ktor_version")
                implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")

                // Kotlinx serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:$ktor_version")
            }
        }

        val jsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:$ktor_version")
            }
        }

        val wasmJsMain by getting {
            dependencies {
                // Wasm currently reuses the JS engine
                implementation("io.ktor:ktor-client-js:$ktor_version")
            }
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
