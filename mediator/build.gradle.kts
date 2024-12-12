import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("application")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("jvm")
}

group = "no.nav"
version = "unspecified"

repositories {
    mavenCentral()
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
}

dependencies {
    implementation(libs.rapids.and.rivers)
    implementation(libs.konfig)
    implementation(libs.kotlin.logging)


    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    implementation("io.ktor:ktor-server-netty:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-config-yaml:${libs.versions.ktor.get()}")

    testImplementation(libs.bundles.kotest.assertions)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.rapids.and.rivers.test)


    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("no.nav.dagpenger.arbeidssokerregister.mediator.ApplicationKt")
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()
}
