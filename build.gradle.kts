import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.vaadin")
    id("org.springframework.boot")
    kotlin("jvm")
}

group = "com.lafi.cardgame.nazdarbaby"
version = "2.0-SNAPSHOT"

tasks.compileKotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

tasks.wrapper {
    gradleVersion = "8.9"
}

repositories {
    mavenCentral()
}

dependencies {
    val commonsRngVersion: String by project
    val springBootVersion: String by project
    val vaadinVersion: String by project

    implementation("com.vaadin:vaadin-spring-boot-starter:$vaadinVersion")
    implementation("org.apache.commons:commons-rng-simple:$commonsRngVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

vaadin {
    productionMode = true
}

tasks.test {
    useJUnitPlatform()
}
