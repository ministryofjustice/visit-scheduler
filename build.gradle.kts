plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "4.1.0"
  kotlin("plugin.spring") version "1.6.10"
  kotlin("plugin.jpa") version "1.6.10"
  idea
}

configurations {
  testImplementation { exclude(group = "org.junit.vintage") }
}

dependencies {

  // Spring
  annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
  implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")

  // Swagger
  implementation("org.springdoc:springdoc-openapi-webmvc-core:1.6.6")
  implementation("org.springdoc:springdoc-openapi-ui:1.6.6")
  implementation("org.springdoc:springdoc-openapi-kotlin:1.6.6")
  implementation("org.springdoc:springdoc-openapi-data-rest:1.6.6")

  // AWS

  // Insights
  agentDeps("com.microsoft.azure:applicationinsights-agent:3.2.7")

  // DB
  runtimeOnly("org.postgresql:postgresql:42.3.3")
  runtimeOnly("org.flywaydb:flyway-core")
  implementation("com.vladmihalcea:hibernate-types-52:2.14.0")
  implementation("javax.transaction:javax.transaction-api:1.3")
  implementation("javax.xml.bind:jaxb-api:2.3.1")
  implementation("com.google.code.gson:gson:2.9.0")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.2")

  // HMPPS Libs

  // Test
  testImplementation("org.springframework.security:spring-security-test")
  testImplementation("com.github.tomakehurst:wiremock-standalone:2.27.2")
  testImplementation("net.javacrumbs.json-unit:json-unit-assertj:2.32.0")
  testImplementation("org.testcontainers:postgresql:1.16.3")
  testImplementation("io.jsonwebtoken:jjwt:0.9.1")
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
