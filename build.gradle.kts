plugins {
    java
    application
}

group = "com.paulfrmbrn"
version = "1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_23
    targetCompatibility = JavaVersion.VERSION_23
}

application {
    mainClass = "com.paulfrmbrn.Main"
}

repositories {
    mavenCentral()
}

dependencies {
    // CLI
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    // Google Calendar API + OAuth
    implementation("com.google.apis:google-api-services-calendar:v3-rev20240111-2.0.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")

    // Google Drive + Docs API (create-agenda)
    implementation("com.google.apis:google-api-services-drive:v3-rev20260405-2.0.0")
    implementation("com.google.apis:google-api-services-docs:v1-rev20260406-2.0.0")

    // YAML settings + Trello JSON parsing
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.4")

    // Transitive CVE fixes
    implementation("com.google.guava:guava:33.4.8-jre")

    // Web UI
    implementation("io.javalin:javalin:6.3.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.4")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.17")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.18")

    // Tests
    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-junit-jupiter:5.14.2")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.compileJava {
    options.compilerArgs.add("-Aproject=${project.group}/${project.name}")
}
