plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

version = "1.0.0"

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

val generatedResourcesDir = layout.buildDirectory.dir("generated/resources/main")
val generateAppVersionProperties by tasks.registering {
    val outputFile = generatedResourcesDir.map { it.file("com/xeon/app.properties") }
    inputs.property("version", project.version.toString())
    outputs.file(outputFile)
    doLast {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText("version=${project.version}\n")
    }
}

sourceSets {
    main {
        resources.srcDir(generatedResourcesDir)
    }
}

dependencies {
    implementation(project(":api"))
    implementation("com.formdev:flatlaf:2.6")
    implementation(fileTree("lib/runelite-cache/runtime") {
        include("*.jar")
        exclude("gson-*.jar")
    })
}

tasks.register<JavaExec>("dumpMapAreaLabels") {
    group = "documentation"
    description = "Dumps location label metadata from the bundled atlas. Pass --args=\"--json\" to write JSON."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.xeon.tools.MapAreaLabelsDump")
    workingDir = projectDir
}

tasks.register<JavaExec>("checkShortestPathData") {
    group = "verification"
    description = "Validates bundled shortest-path collision and transport resources."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.xeon.tools.ShortestPathDataCheck")
    workingDir = projectDir
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.xeon.App"
        attributes["Implementation-Version"] = project.version
    }
}

tasks.processResources {
    dependsOn(generateAppVersionProperties)
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
