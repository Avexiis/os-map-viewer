plugins {
    kotlin("jvm") version "1.9.22"
}

group = "com.displee"
version = "8.1.0-local"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("com.github.jponge:lzma-java:1.3")
    implementation("org.apache.ant:ant:1.10.14")
    implementation("com.displee:disio:2.3")
}
