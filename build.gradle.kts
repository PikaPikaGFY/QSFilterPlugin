plugins {
    `java-library`
    id("io.papermc.paperweight.userdev") version "1.7.1"
    id("com.gradleup.shadow") version "9.4.2"
}

group = "cn.hdbstudio"
version = "1.0.0-SNAPSHOT"
description = "QS Filter Plugin - 跨服务器 QS 商店过滤与 API 暴露"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.21-R0.1-SNAPSHOT")

    // QuickShop-Hikari API — compileOnly，运行时由 QS 插件提供
    compileOnly("com.ghostchu:quickshop-api:6.2.0.11")

    // Javalin — 嵌入式 HTTP 服务器
    implementation("io.javalin:javalin:6.3.0") {
        exclude("org.eclipse.jetty.websocket", "websocket-server")
    }
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.1")

    // SQLite JDBC
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
}

tasks.shadowJar {
    archiveClassifier.set("")
    minimize()

    relocate("io.javalin", "cn.hdbstudio.qsfilter.lib.javalin")
    relocate("org.eclipse.jetty", "cn.hdbstudio.qsfilter.lib.jetty")
    relocate("com.fasterxml.jackson", "cn.hdbstudio.qsfilter.lib.jackson")
    relocate("org.sqlite", "cn.hdbstudio.qsfilter.lib.sqlite")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
