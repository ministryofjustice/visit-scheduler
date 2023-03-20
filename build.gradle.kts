plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "5.1.2"
  kotlin("jvm") version "1.8.10"
  kotlin("plugin.spring") version "1.8.10"
  kotlin("plugin.jpa") version "1.8.10"
  idea
}

springBoot {
  mainClass.value("uk.gov.justice.digital.hmpps.visitscheduler.VisitSchedulerApplicationKt")
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

repositories {
  maven { url = uri("https://repo.spring.io/milestone") }
  mavenCentral()
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:2.0.0-beta-10")

  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:1.22.1")

  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.0")
  implementation("org.springframework.data:spring-data-commons:3.0.0")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.0.2")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.0.2")
  implementation("org.springdoc:springdoc-openapi-starter-common:2.0.2")

  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("net.javacrumbs.shedlock:shedlock-spring:5.0.1")
  implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.0.1")
  implementation("org.apache.commons:commons-csv:1.9.0")
  implementation("org.freemarker:freemarker:2.3.31")

  runtimeOnly("org.postgresql:postgresql:42.5.1")
  runtimeOnly("org.flywaydb:flyway-core")

  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.11")
  testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:2.35.0")
  testImplementation("org.mockito:mockito-inline:5.1.1")
  testImplementation("org.testcontainers:localstack:1.17.6")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
  testImplementation("javax.xml.bind:jaxb-api:2.3.1")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.testcontainers:postgresql:1.17.6")
  testImplementation("org.testcontainers:localstack:1.17.6")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(19))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "18"
    }
  }
}

allOpen {
  annotation("javax.persistence.Entity")
}
