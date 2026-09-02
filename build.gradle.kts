plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

version = "1.3.0" //App version for 2D map version control
val runeliteCacheVersion = "1.12.39" //Bump this when updating the RL cache module.
val runeliteCacheShadedJar = layout.projectDirectory
    .file("lib/runelite-cache/cache-$runeliteCacheVersion-SNAPSHOT-shaded.jar")
    .asFile
val lwjglVersion = "3.4.0"
val jomlVersion = "1.10.5"
val lwjglBundledNativeClassifiers = linkedSetOf(
    "natives-windows",
    "natives-windows-arm64",
    "natives-linux",
    "natives-linux-arm64",
    "natives-linux-arm32",
    "natives-macos",
    "natives-macos-arm64"
)
val nvidiaPrimeEnvironment = linkedMapOf(
    "__NV_PRIME_RENDER_OFFLOAD" to "1",
    "__VK_LAYER_NV_optimus" to "NVIDIA_only",
    "__GLX_VENDOR_LIBRARY_NAME" to "nvidia",
    "DRI_PRIME" to "1"
)
fun primePropertyEnabled(value: String): Boolean
{
    return value.equals("true", ignoreCase = true)
        || value.equals("yes", ignoreCase = true)
        || value.equals("on", ignoreCase = true)
        || value == "1"
}

val useNvidiaPrime = providers.gradleProperty("osmapviewer.nvidiaPrime")
    .orElse(providers.environmentVariable("OSMAPVIEWER_NVIDIA_PRIME"))
    .map(::primePropertyEnabled)
    .orElse(false)
val nvidiaPrimeProvider = providers.gradleProperty("osmapviewer.nvidiaPrimeProvider")
    .orElse(providers.environmentVariable("OSMAPVIEWER_NVIDIA_PRIME_PROVIDER"))

fun JavaExec.useNvidiaPrimeOffload()
{
    environment(nvidiaPrimeEnvironment)
    nvidiaPrimeProvider.orNull
        ?.takeIf { it.isNotBlank() }
        ?.let { environment("__NV_PRIME_RENDER_OFFLOAD_PROVIDER", it) }
    systemProperty("osmapviewer.nvidiaPrime", "true")
}

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
    workingDir = projectDir
    if (useNvidiaPrime.get())
    {
        useNvidiaPrimeOffload()
    }
}

tasks.register<JavaExec>("runNvidiaPrime") {
    group = "application"
    description = "Runs OS Map Viewer with NVIDIA PRIME render offload enabled for hybrid-GPU Linux systems."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(application.mainClass)
    workingDir = projectDir
    useNvidiaPrimeOffload()
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
    lwjglBundledNativeClassifiers.forEach { classifier ->
        runtimeOnly("org.lwjgl:lwjgl::$classifier")
        runtimeOnly("org.lwjgl:lwjgl-opengl::$classifier")
    }
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
