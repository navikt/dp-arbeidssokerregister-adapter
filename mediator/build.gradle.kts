import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("application")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.5.0"
    kotlin("jvm")
}

group = "no.nav"
version = "unspecified"

repositories {
    mavenCentral()
    maven { setUrl("https://github-package-registry-mirror.gc.nav.no/cached/maven-release") }
    maven { setUrl("https://packages.confluent.io/maven/") }
}

val schema by configurations.creating {
    isTransitive = false
}

dependencies {
    implementation(libs.rapids.and.rivers)
    implementation(libs.konfig)
    implementation(libs.kotlin.logging)

    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    implementation("io.ktor:ktor-server-netty:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-config-yaml:${libs.versions.ktor.get()}")

    implementation("io.confluent:kafka-avro-serializer:7.8.0")
    implementation("io.confluent:kafka-schema-registry:7.8.0")
    implementation("io.confluent:kafka-streams-avro-serde:7.8.0")
    // schema("no.nav.paw.arbeidssokerregisteret.api:bekreftelse-paavegneav-schema:beta-v1")
    implementation("org.apache.avro:avro:1.10.2")

    implementation("no.nav.dagpenger:oauth2-klient:2024.10.31-15.02.1d4f08a38d24")

    testImplementation(libs.bundles.kotest.assertions)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.rapids.and.rivers.test)

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.testcontainers:kafka:1.19.0")
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
