plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.7.1"
  kotlin("jvm") version "1.7.20"
  kotlin("plugin.spring") version "1.7.20"
  kotlin("plugin.jpa") version "1.7.20"
  idea
}

springBoot {
  mainClass.value("uk.gov.justice.digital.hmpps.visitscheduler.VisitSchedulerApplicationKt")
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {

  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")

  implementation("org.springdoc:springdoc-openapi-webmvc-core:1.6.13")
  implementation("org.springdoc:springdoc-openapi-ui:1.6.13")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.6.13")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.6.13")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.1.12")
  implementation("net.javacrumbs.shedlock:shedlock-spring:4.42.0")
  implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:4.42.0")
  implementation("org.apache.commons:commons-csv:1.9.0")
  implementation("org.freemarker:freemarker:2.3.31")

  runtimeOnly("org.postgresql:postgresql:42.5.1")
  runtimeOnly("org.flywaydb:flyway-core")

  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")

  testImplementation("org.mockito:mockito-inline:4.8.0")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.0")

  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("org.testcontainers:postgresql:1.17.6")
  testImplementation("org.testcontainers:localstack:1.17.6")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
      jvmTarget = "17"
    }
  }
}
