plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.3.2"
  kotlin("jvm") version "1.7.0"
  kotlin("plugin.spring") version "1.7.0"
  kotlin("plugin.jpa") version "1.7.0"
  idea
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

  implementation("org.springdoc:springdoc-openapi-webmvc-core:1.6.9")
  implementation("org.springdoc:springdoc-openapi-ui:1.6.9")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.6.9")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.6.9")

  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:1.1.6")

  runtimeOnly("org.postgresql:postgresql")
  runtimeOnly("org.flywaydb:flyway-core")

  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")

  testImplementation("org.mockito:mockito-inline:4.6.1")
  testImplementation("org.awaitility:awaitility-kotlin:4.2.0")

  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("org.testcontainers:postgresql:1.17.3")
  testImplementation("org.testcontainers:localstack:1.17.3")
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
