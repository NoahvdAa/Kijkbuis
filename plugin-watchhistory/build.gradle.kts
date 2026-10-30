plugins {
    java
}

group = "nl.noahvanderaa.kijkbuis"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Kijkbuis provides the API when it loads the plugin
    compileOnly(project(":api"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.processResources {
    // Fills in the version in kijkbuis-plugin.json
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("kijkbuis-plugin.json") {
        expand("version" to version)
    }
}
