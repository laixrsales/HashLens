import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.android.junit5)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.detekt)
}

// Configuração da rede e segredos: android/local.properties (fora do Git; ver quickstart.md)
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}

fun localProperty(key: String, default: String = ""): String = localProperties.getProperty(key, default).trim()

val defaultPendingTimeoutMinutes = 30

// Prazo para confirmação de uma transação pendente (research R23); valores inválidos voltam ao padrão
val pendingTimeoutMinutes = localProperty("registry.pendingTimeoutMinutes")
    .toIntOrNull()
    ?.takeIf { it > 0 }
    ?: defaultPendingTimeoutMinutes

android {
    namespace = "br.ufv.hashlens"
    compileSdk = 37

    defaultConfig {
        applicationId = "br.ufv.hashlens"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SEPOLIA_RPC_URL", "\"${localProperty("sepolia.rpcUrl")}\"")
        buildConfigField("String", "REGISTRY_ADDRESS", "\"${localProperty("registry.address")}\"")
        buildConfigField("String", "REOWN_PROJECT_ID", "\"${localProperty("reown.projectId")}\"")
        buildConfigField("int", "PENDING_TIMEOUT_MINUTES", pendingTimeoutMinutes.toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Robolectric e Roborazzi leem recursos e manifesto nos testes JVM
        unitTests.isIncludeAndroidResources = true
        // O Robolectric acessa internos do java.base que o JDK 25 (JBR do Android Studio) não expõe
        unitTests.all {
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED",
                "--enable-native-access=ALL-UNNAMED"
            )
        }
    }

    // Testes instrumentados leem as mesmas imagens golden dos testes JVM (assets "golden/..."),
    // sem cópia: o pHash depende do OpenCV nativo, que só roda no aparelho
    sourceSets.getByName("androidTest").assets.directories.add("src/test/resources")
}

room {
    schemaDirectory("$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/java", "src/test/java", "src/androidTest/java")
}

// ABI do contrato gerado pelo Foundry (cd contracts && forge build). Sem o artefato, a tarefa fica
// NO-SOURCE e o app usa a cópia já versionada em src/main/assets/abi/.
val copyRegistryAbi by tasks.registering(Copy::class) {
    group = "build"
    description = "Copia o ABI do ImageRegistry de contracts/out/ para os assets do app."
    from(rootProject.file("../contracts/out/ImageRegistry.sol")) {
        include("ImageRegistry.json")
    }
    into(layout.projectDirectory.dir("src/main/assets/abi"))
}

tasks.named("preBuild") {
    dependsOn(copyRegistryAbi)
}

dependencies {
    // AndroidX e Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    // Room, WorkManager, câmera e imagem
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.exifinterface)
    implementation(libs.opencv)

    // Blockchain e carteira
    implementation(libs.web3j.core)
    implementation(platform(libs.reown.bom))
    implementation(libs.reown.android.core)
    implementation(libs.reown.appkit)

    // Testes JVM: JUnit 5 (Jupiter) + JUnit 4 (vintage) para Robolectric/Roborazzi
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.junit4)
    testImplementation(libs.mockk)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    // Força o Espresso 3.7 (o 3.5 transitivo chama InputManager.getInstance(), removido nos SDKs recentes)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)

    // Testes instrumentados
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.kotest.assertions.core)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
