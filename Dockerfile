FROM openjdk:17-slim AS builder

ARG BUILD_NUMBER
ENV BUILD_NUMBER ${BUILD_NUMBER:-1_0_0}

WORKDIR /app
ADD . .
RUN ./gradlew clean assemble -Dorg.gradle.daemon=false

# Grab AWS RDS Root cert
RUN apt-get update && apt-get install -y curl
RUN curl https://s3.amazonaws.com/rds-downloads/rds-ca-2019-root.pem  > root.crt

FROM openjdk:17-slim
LABEL maintainer="HMPPS Digital Studio <info@digital.justice.gov.uk>"

ARG BUILD_NUMBER
ENV BUILD_NUMBER ${BUILD_NUMBER:-1_0_0}

RUN apt-get update && \
    apt-get -y upgrade && \
    apt-get autoremove -y && \
    rm -rf /var/lib/apt/lists/*

ENV TZ=Europe/London
RUN ln -snf "/usr/share/zoneinfo/$TZ" /etc/localtime && echo "$TZ" > /etc/timezone

RUN addgroup --gid 2000 --system appgroup && \
    adduser --uid 2000 --system appuser --gid 2000

# Install AWS RDS Root cert into Java truststore
RUN mkdir /home/appuser/.postgresql
COPY --from=builder --chown=appuser:appgroup /app/root.crt /home/appuser/.postgresql/root.crt

WORKDIR /app
COPY --from=builder --chown=appuser:appgroup /app/build/libs/visit-scheduler*.jar /app/app.jar
COPY --from=builder --chown=appuser:appgroup /app/build/libs/applicationinsights-agent*.jar /app/agent.jar
COPY --from=builder --chown=appuser:appgroup /app/applicationinsights.json /app
COPY --from=builder --chown=appuser:appgroup /app/applicationinsights.dev.json /app

USER 2000

ENTRYPOINT ["java", "-javaagent:/app/agent.jar", "-jar", "/app/app.jar"]
