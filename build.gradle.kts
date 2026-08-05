plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("com.xeon.App")
}

dependencies {
    implementation(project(":api"))
    implementation("com.formdev:flatlaf:2.6")
}

tasks.register<JavaExec>("dumpMapAreaLabels") {
    group = "documentation"
    description = "Dumps location label metadata from the bundled atlas. Pass --args=\"--json\" to write JSON."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.xeon.tools.MapAreaLabelsDump")
    workingDir = projectDir
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.xeon.App"
    }
}

tasks.shadowJar {
    archiveBaseName.set("OSMapViewer")
    archiveVersion.set("")
    archiveClassifier.set("")
    configurations = listOf(project.configurations.runtimeClasspath.get())
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.register("buildPluginApi") {
    group = "build"
    description = "Builds the OS Map Viewer API JAR for plugin development."
    dependsOn(":api:jar")
}

defaultTasks("shadowJar")
