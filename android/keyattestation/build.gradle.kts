// Biblioteca oficial de verificação de Key Attestation (github.com/android/keyattestation), compilada a
// partir do submódulo em third_party/ com o Kotlin e o JDK deste build. O build próprio dela não roda
// aqui: o plugin foojay 0.8.0 que ela usa é incompatível com o Gradle 9 (research R4).
plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

val upstream = rootProject.file("third_party/keyattestation")

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

sourceSets {
    main {
        kotlin.srcDir(upstream.resolve("src/main/kotlin"))
        kotlin.srcDir(layout.buildDirectory.dir("generated/main/kotlin"))
    }
    // Testes originais da biblioteca: confirmam que este empacotamento se comporta como o build dela
    test {
        kotlin.srcDir(upstream.resolve("src/test/kotlin"))
    }
}

// Mesmo gerador do build original: embute as raízes de roots.json em GoogleTrustAnchors
val googleTrustAnchors = tasks.register("googleTrustAnchors") {
    val jsonFile = upstream.resolve("roots.json")
    val generatedFile = layout.buildDirectory.file("generated/main/kotlin/GoogleTrustAnchors.kt")
    inputs.file(jsonFile)
    outputs.file(generatedFile)
    doLast {
        val quotes = "\"\"\""
        generatedFile.get().asFile.writeText(
            """
            |package com.android.keyattestation.verifier
            |
            |import com.google.gson.Gson
            |import java.security.cert.TrustAnchor
            |
            |object GoogleTrustAnchors : () -> Set<TrustAnchor> {
            |  const val JSON = $quotes
            |${jsonFile.readText()}
            |$quotes
            |
            |  override operator fun invoke(): Set<TrustAnchor> = Gson()
            |    .fromJson(JSON, Array<String>::class.java)
            |    .map { TrustAnchor(it.asX509Certificate(), null) }
            |    .toSet()
            |}
            |
            """.trimMargin()
        )
    }
}

tasks.named("compileKotlin") {
    dependsOn(googleTrustAnchors)
}

tasks.test {
    useJUnitPlatform()
    // Os testes leem Path("testdata") relativo ao diretório da biblioteca
    workingDir = upstream
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().named { it == "compileTestKotlin" }.configureEach {
    compilerOptions.javaParameters.set(true)
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.cbor)
    implementation(libs.gson)
    api(libs.errorprone.annotations)
    api(libs.protobuf.javalite)
    implementation(libs.protobuf.kotlin.lite)
    api(libs.bouncycastle.bcpkix)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.guava)
    api(libs.guava.jre)

    testImplementation(kotlin("test"))
    testImplementation(libs.truth)
    testImplementation(libs.test.parameter.injector)
    testImplementation(platform(libs.junit.bom))
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
