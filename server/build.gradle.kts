// AMBiFy backend: Ktor + SQLite, runs on the home server.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

version = "0.3.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("pl.ambif.ambify.server.ApplicationKt")
}

tasks.jar {
    manifest {
        attributes["Implementation-Version"] = project.version
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.sqlite.jdbc)
    implementation(libs.logback.classic)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(kotlin("test-junit"))
}
