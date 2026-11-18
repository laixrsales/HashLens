// Política de atestação do HashLens, compartilhada pelo app e pelo atestador para que os dois decidam
// igual (research R4, contracts/attestation-policy.md)
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
    alias(libs.plugins.detekt)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

dependencies {
    api(project(":keyattestation"))
    // Keccak-256 de attestationHash (AttestationHash.kt)
    implementation(libs.bouncycastle.bcpkix)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotest.assertions.core)
}

tasks.test {
    useJUnitPlatform()
}
