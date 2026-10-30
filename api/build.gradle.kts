import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    `java-library`
}

group = "nl.noahvanderaa.kijkbuis"
// Shared with the rest of Kijkbuis, see gradle.properties
version = providers.gradleProperty("kijkbuisVersion").get()

repositories {
    mavenCentral()
}

dependencies {
    // Only needed to compile, so plugins don't get a dependency on them
    compileOnlyApi("org.jetbrains:annotations:26.1.0")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.test {
    useJUnitPlatform()
    // CI logs list every test, so it's clear they ran
    if (providers.environmentVariable("CI").isPresent) {
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}
