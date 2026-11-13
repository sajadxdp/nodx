plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.write4me.llama_flutter_android"
    compileSdk = 36

    defaultConfig {
        minSdk = 28 // Vulkan 1.1 symbols required by the CPU/GPU backends

        ndk {
            // arm64: full build (CPU variants + Vulkan). armeabi-v7a: plain
            // CPU build for phones running 32-bit Android.
            abiFilters.addAll(listOf("arm64-v8a", "armeabi-v7a"))
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3", "-fvisibility=hidden")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_ARM_NEON=ON",
                    "-DGGML_CPU_AARCH64=ON",
                    "-DGGML_OPENMP=OFF",
                    // NDK glslc can't compile the vendored coopmat shaders yet;
                    // CPU backends cover all devices (incl. this one) for now.
                    "-DLLAMA_ANDROID_VULKAN=OFF",
                    "-DLLAMA_ANDROID_RPC=OFF",
                )
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.4.0")
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test> {
    useJUnit()
}
