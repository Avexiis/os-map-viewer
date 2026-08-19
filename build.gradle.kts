plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

version = "1.1.0"
// Update this when replacing lib/runelite-cache/cache-<version>-SNAPSHOT-shaded.jar.
val runeliteCacheVersion = "1.12.37"
val runeliteCacheShadedJar = layout.projectDirectory
    .file("lib/runelite-cache/cache-$runeliteCacheVersion-SNAPSHOT-shaded.jar")
    .asFile
val lwjglVersion = "3.4.0"
val jomlVersion = "1.10.5"
val lwjglNatives = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    when {
        os.contains("windows") && arch.contains("aarch64") -> "natives-windows-arm64"
        os.contains("windows") -> "natives-windows"
        os.contains("mac") && arch.contains("aarch64") -> "natives-macos-arm64"
        os.contains("mac") -> "natives-macos"
        os.contains("linux") && arch.contains("aarch64") -> "natives-linux-arm64"
        os.contains("linux") && (arch == "arm" || arch.startsWith("armv7")) -> "natives-linux-arm32"
        os.contains("linux") -> "natives-linux"
        else -> throw GradleException("Unsupported LWJGL platform: $os/$arch")
    }
}
val nvidiaPrimeEnvironment = mapOf(
    "__NV_PRIME_RENDER_OFFLOAD" to "1",
    "__VK_LAYER_NV_optimus" to "NVIDIA_only",
    "__GLX_VENDOR_LIBRARY_NAME" to "nvidia"
)
val useNvidiaPrime = providers.gradleProperty("osmapviewer.nvidiaPrime")
    .map { value ->
        value.equals("true", ignoreCase = true)
            || value.equals("yes", ignoreCase = true)
            || value.equals("on", ignoreCase = true)
            || value == "1"
    }
    .orElse(false)

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.runelite.net")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("com.xeon.App")
}

tasks.named<JavaExec>("run") {
    if (useNvidiaPrime.get())
    {
        environment(nvidiaPrimeEnvironment)
    }
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
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.joml:joml:$jomlVersion")
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("net.runelite:rlawt:1.8")
    compileOnly(files(runeliteCacheShadedJar))
    runtimeOnly(files(runeliteCacheShadedJar))
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
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

tasks.register<JavaExec>("inspectAtlas") {
    group = "verification"
    description = "Opens a Swing inspector for a .atlas file. Pass --args=\"path/to/file.atlas\" to inspect another atlas."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.xeon.tools.AtlasInspectorApp")
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
