# HMPPS Visit Scheduler API

[![CircleCI](https://circleci.com/gh/ministryofjustice/visit-scheduler/tree/main.svg?style=shield)](https://app.circleci.com/pipelines/github/ministryofjustice/visit-scheduler)

This is a Spring Boot application, written in Kotlin, providing visit schedule information. Used by [Visit Someone in Prison](https://github.com/ministryofjustice/book-a-prison-visit-staff-ui).

Posted event Specification [![Event docs](https://img.shields.io/badge/Event_docs-view-85EA2D.svg)](https://studio.asyncapi.com/?url=https://raw.githubusercontent.com/ministryofjustice/visit-scheduler/main/visit-scheduler-event-specification.yaml)

## Building

To build the project (without tests):
```
./gradlew clean build -x test
```

## Testing

Run:
```
./gradlew test 
```

## Running

Create a Spring Boot run configuration with active profile of dev, to run against te development environment.

Alternatively the service can be run using docker-compose with client 'book-a-prison-visit-client' and the usual dev secret.
```
docker-compose up
```

Ports

| Service            | Port |  
|--------------------|------|
| visit-scheduler    | 8080 |
| visit-scheduler-db | 5432 |
| hmpps-auth         | 8090 |
| prison-api         | 8091 |

To create a Token (local):
```
curl --location --request POST "http://localhost:8081/auth/oauth/token?grant_type=client_credentials" --header "Authorization: Basic $(echo -n {Client}:{ClientSecret} | base64)"
```

Call info endpoint:
```
$ curl 'http://localhost:8080/info' -i -X GET
```

## Swagger v3
Visit Scheduler
```
http://localhost:8080/swagger-ui/index.html
```

Export Spec
```
http://localhost:8080/v3/api-docs?group=full-api
```

## Common gradle tasks

To list project dependencies, run:

```
./gradlew dependencies
``` 

To check for dependency updates, run:
```
./gradlew dependencyUpdates --warning-mode all
```

To run an OWASP dependency check, run:
```
./gradlew clean dependencyCheckAnalyze --info
```

To upgrade the gradle wrapper version, run:
```
./gradlew wrapper --gradle-version=<VERSION>
```

To automatically update project dependencies, run:
```
./gradlew useLatestVersions
```

#### Ktlint Gradle Tasks

To run Ktlint check:
```
./gradlew ktlintCheck
```

To run Ktlint format:
```
./gradlew ktlintFormat
```

To apply ktlint styles to intellij
```
./gradlew ktlintApplyToIdea
```

To register pre-commit check to run Ktlint format:
```
./gradlew ktlintApplyToIdea addKtlintFormatGitPreCommitHook 
```

...or to register pre-commit check to only run Ktlint check:
```
./gradlew ktlintApplyToIdea addKtlintCheckGitPreCommitHook
```

#### Build checks

To run the CircleCI trivy scan locally download and install trivy, build the visit-scheduler docker image and run:
```
trivy image visit-scheduler_visit-scheduler
```
