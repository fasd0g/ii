plugins {
    java
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.example"
version = "1.2.1"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("com.google.code.gson:gson:2.11.0")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.shadowJar {
    archiveClassifier.set("")
    minimize() // keep jar smaller
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
