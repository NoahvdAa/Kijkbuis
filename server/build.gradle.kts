import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    application
}

group = "nl.noahvanderaa.kijkbuis"
// Shared with the rest of Kijkbuis, see gradle.properties
version = providers.gradleProperty("kijkbuisVersion").get()

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.bytecode.space/repository/maven-public/")
}

sourceSets {
    // A plugin for PluginTest, which gets its own jar like any other plugin
    create("testPlugin")
}

dependencies {
    implementation(project(":api"))
    implementation("org.apache.logging.log4j:log4j-api:2.25.2")
    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("me.noahvdaa:schemashift:1.0.0-SNAPSHOT")
    implementation("io.undertow:undertow-core:2.4.4.Final")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.spongepowered:configurate-core:4.2.0")
    implementation("me.lucko.configurate:configurate-toml:4.1")
    implementation("com.nimbusds:oauth2-oidc-sdk:11.38.2")
    compileOnly("org.jetbrains:annotations:26.1.0")
    runtimeOnly("org.xerial:sqlite-jdbc:3.51.3.0")
    runtimeOnly("org.apache.logging.log4j:log4j-core:2.25.2")
    runtimeOnly("org.apache.logging.log4j:log4j-slf4j2-impl:2.25.2")
    // Plugins log through System.Logger
    runtimeOnly("org.apache.logging.log4j:log4j-jpl:2.25.2")

    testCompileOnly("org.jetbrains:annotations:26.1.0")
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    "testPluginCompileOnly"(project(":api"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.processResources {
    // Lets Kijkbuis know its own version, so it can tell which plugins are made for it
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("nl/noahvanderaa/kijkbuis/version.txt") {
        expand("version" to version)
    }
}

application {
    // The start script is bin/kijkbuis, rather than bin/server after the project
    applicationName = "kijkbuis"
    mainClass = "nl.noahvanderaa.kijkbuis.KijkbuisApp"
    // sqlite-jdbc loads a native library
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

val testPluginJar = tasks.register<Jar>("testPluginJar") {
    from(sourceSets["testPlugin"].output)
    archiveFileName = "test-plugin.jar"
    destinationDirectory = layout.buildDirectory.dir("test-plugins")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    val testPlugin = testPluginJar.flatMap { it.archiveFile }
    inputs.file(testPlugin)
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-Dkijkbuis.test-plugin=" + testPlugin.get().asFile.absolutePath) })
    // CI logs list every test, so it's clear they ran
    if (providers.environmentVariable("CI").isPresent) {
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}

tasks.register<JavaExec>("devIdentityProvider") {
    group = "application"
    description = "Runs a fake identity provider that logs everyone in as an admin, for local development."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "nl.noahvanderaa.kijkbuis.DevIdentityProvider"
}
