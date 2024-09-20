plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "6.0.6"
  kotlin("plugin.spring") version "2.0.20"
  kotlin("plugin.jpa") version "2.0.20"
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
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:4.4.3")

  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
  implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.8.0")

  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
  implementation("org.springframework.data:spring-data-commons:3.3.4")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-api:2.6.0")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
  implementation("org.springdoc:springdoc-openapi-starter-common:2.6.0")

  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("net.javacrumbs.shedlock:shedlock-spring:5.16.0")
  implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:5.16.0")
  implementation("org.apache.commons:commons-csv:1.11.0")
  implementation("org.freemarker:freemarker:2.3.33")

  runtimeOnly("org.postgresql:postgresql:42.7.4")
  runtimeOnly("org.flywaydb:flyway-core")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")

  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  testImplementation("io.swagger.parser.v3:swagger-parser:2.1.22")
  testImplementation("com.github.tomakehurst:wiremock-jre8-standalone:3.0.1")
  testImplementation("org.mockito:mockito-inline:5.2.0")
  testImplementation("org.testcontainers:localstack:1.20.1")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.2")
  testImplementation("javax.xml.bind:jaxb-api:2.4.0-b180830.0359")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.jsonwebtoken:jjwt:0.12.6")
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("org.testcontainers:postgresql:1.20.1")
  testImplementation("org.testcontainers:localstack:1.20.1")
  testImplementation("com.amazonaws:aws-java-sdk-s3:1.12.772")
  testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
  testImplementation("org.junit-pioneer:junit-pioneer:2.2.0")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
  }
}

allOpen {
  annotation("javax.persistence.Entity")
}
