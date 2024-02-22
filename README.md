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

Create a Spring Boot run configuration with active profile of dev/local, to run against te development environment.

Alternatively the service can be run using docker-compose with client 'book-a-prison-visit-client' and the usual dev secret.
```
docker-compose up -d
```
or for particular compose files
```
docker-compose -f docker-compose-local.yml up -d
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

## How to restore pre prod from production on demand

Normally the data in preprod is updated from prod on an interval bases (2 weeks) but some time you need to update now.

```
kubectl create job --dry-run=client -n visit-someone-in-prison-backend-svc-prod  --from=cronjob/<cron-job> <cron-job>-<user-name> -o "json" \
| jq ".spec.template.spec.containers[0].env += [{ \"name\": \"FORCE_RUN\", \"value\": \"true\"}]" | kubectl apply -f -
```

To get the <cron-job> (--from=cronjob/<cron-job>) name you must run the following command

```
kubectl get cronjobs -n visit-someone-in-prison-backend-svc-prod
```

The <user-name> is basically who did the restore in this case I have added **ae** see the actual command I used :

```
kubectl create job --dry-run=client -n visit-someone-in-prison-backend-svc-prod  --from=cronjob/visit-scheduler-postgres-restore visit-scheduler-postgres-restore-ae -o "json" \
| jq ".spec.template.spec.containers[0].env += [{ \"name\": \"FORCE_RUN\", \"value\": \"true\"}]" | kubectl apply -f -
```

for more information see

https://github.com/ministryofjustice/hmpps-helm-charts/tree/main/charts/generic-service#manually-running-the-database-restore-cronjob

## How to connect to DB from command line

**Prerequisites** : setup forwarding to the DB in question and use same port

Get db_name and user name and password using 

```
kubectl -n <name_space> get secrets <rds_name>   -o json | jq '.data | map_values(@base64d)'
```

Then run the below command with the acquired details from above:

```
psql \
--host localhost \
--port <FORWARD_PORT> \
--dbname <DB_NAME> \
--username <USER_NAME> \
--password <PASSWORD>
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

## Application Tracing
The application sends telemetry information to Azure Application Insights which allows log queries and end-to-end request tracing across services

##### Application Insights Events

Show all significant prison visit events
```azure
customEvents 
| where cloud_RoleName == 'visit-scheduler' 
| where name startswith "visit-scheduler-prison" 
| summarize count() by name
```

Available custom events
- `session-template-created` - a session template was created. It will contain the template id
- `session-template-deleted` - a session template was deleted. It will contain the template id

- `visit-slot-reserved` - a visit slot was reserved. It will contain the visit's application reference and basic information about the visit
- `visit-slot-changed` - a visit change was started. It will contain the visit's application reference, reference and status
- `visit-changed` - a booked visit was being changed. It will contain the visit's application reference, reference and basic information about the visit
- `visit-booked` - a visit was booked. It will contain the visit's application reference, reference, status and an isUpdated flag to denote if it's a new or an updated visit
- `visit-expired-visits-deleted` - expired visits were deleted. It will contain the list of expired application references that were deleted
- `visit-cancelled` - a visit was cancelled. It will contain the visit's reference, status and outcome status
- `visit-migrated` - a visit was migrated. It will contain the visit's reference and basic information about the visit

- `visit.booked-event` - indicates a visit booked event was published. It will contain the message id and visit reference
- `visit.cancelled-event` - indicates a visit cancelled event was published. It will contain the message id and visit reference

- `visit-publish-event-error` - indicates publish visit event failed. It will contain the available exception message and cause
- `visit-access-denied-error` - Access Denied Error. It will contain the available exception message and cause
- `visit-bad-request-error` - Bad Request Error. It will contain the available exception message and cause
- `visit-internal-server-error` - Internal Error. It will contain the available exception message and cause

```azure
customEvents 
| where cloud_RoleName == 'visit-scheduler' 
| where name == 'visit-migrated'
| extend reference_ = tostring(customDimensions.reference)
| extend prisonerId_ = tostring(customDimensions.prisonerId)
| extend visitStatus_ = tostring(customDimensions.visitStatus)
```

##### Example queries

Requests
```azure
requests 
| where cloud_RoleName == 'visit-scheduler' 
| summarize count() by name
```

Performance
```azure
requests
| where cloud_RoleName == 'visit-scheduler' 
| summarize RequestsCount=sum(itemCount), AverageDuration=avg(duration), percentiles(duration, 50, 95, 99) by operation_Name // you can replace 'operation_Name' with another value to segment by a different property
| order by RequestsCount desc // order from highest to lower (descending)
```

Charts
```azure
requests
| where cloud_RoleName == 'visit-scheduler' 
| where timestamp > ago(12h) 
| summarize avgRequestDuration=avg(duration) by bin(timestamp, 10m) // use a time grain of 10 minutes
| render timechart
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
