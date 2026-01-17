plugins {
    id("com.vaadin")
    id("org.springframework.boot")
    kotlin("jvm")
}

group = "com.lafi.cardgame.nazdarbaby"
version = "2.0-SNAPSHOT"

kotlin {
    val jdkVersion = JavaVersion.VERSION_25
    jvmToolchain(jdkVersion.majorVersion.toInt())
}

repositories {
    mavenCentral()
}

dependencies {
    val commonsLang3Version: String by project
    val commonsRngVersion: String by project
    val springBootVersion: String by project
    val vaadinVersion: String by project

    implementation("com.vaadin:vaadin-dev:$vaadinVersion")
    implementation("com.vaadin:vaadin-spring-boot-starter:$vaadinVersion")
    implementation("org.apache.commons:commons-lang3:$commonsLang3Version")
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
