import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar

gradle.beforeProject {
    if (project != rootProject || rootProject.name != "cache") {
        return@beforeProject
    }

    plugins.withId("java") {
        val sourceSets = extensions.getByType<JavaPluginExtension>().sourceSets
        val runtimeClasspath = configurations.named("runtimeClasspath")

        val shadowJar = tasks.register<Jar>("shadowJar") {
            group = BasePlugin.BUILD_GROUP
            description = "Assembles a shaded jar containing cache and its runtime dependencies."

            dependsOn(runtimeClasspath)
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE

            from(sourceSets.named("main").map { it.output })
            from(runtimeClasspath.map { configuration ->
                configuration.map { dependency ->
                    if (dependency.isDirectory) dependency else zipTree(dependency)
                }
            })

            exclude(
                "META-INF/INDEX.LIST",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "**/module-info.class",
            )

            archiveClassifier.set("shaded")
            archiveFileName.set("${project.name}-${project.version}-shaded.jar")
        }

        tasks.named("assemble") {
            dependsOn(shadowJar)
        }

        plugins.withId("maven-publish") {
            extensions.configure<PublishingExtension>("publishing") {
                publications.withType(MavenPublication::class.java).configureEach {
                    if (name == "cache") {
                        artifact(shadowJar) {
                            classifier = "shaded"
                        }
                    }
                }
            }
        }
    }
}
