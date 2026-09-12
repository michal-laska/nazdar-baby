plugins {
    alias(libs.plugins.vaadin)
    alias(libs.plugins.spring.boot)
    java
}

group = "com.lafi.cardgame.nazdarbaby"
version = "2.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    developmentOnly(libs.vaadin.dev)
    implementation(libs.vaadin.spring.boot.starter)
    implementation(libs.commons.lang3)
    implementation(libs.commons.rng.simple)

    testImplementation(libs.spring.boot.starter.test)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.jar {
    enabled = false
}

vaadin {
    productionMode = true
}

tasks.test {
    useJUnitPlatform()
}

// ./gradlew botArena --args="<deals> <players> <tricks> <threads>"
tasks.register<JavaExec>("botArena") {
    group = "verification"
    description = "Play paired deals between two bot versions to measure a change to the MCTS engine."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass = "com.lafi.cardgame.nazdarbaby.mcts.BotArena"
}
