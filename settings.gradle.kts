rootProject.name = "nazdar-baby"

pluginManagement {
    val kotlinVersion: String by settings
    val springBootVersion: String by settings
    val vaadinVersion: String by settings

    plugins {
        id("com.vaadin") version vaadinVersion
        id("org.springframework.boot") version springBootVersion
        kotlin("jvm") version kotlinVersion
    }
}
