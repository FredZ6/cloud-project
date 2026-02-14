# syntax=docker/dockerfile:1.7

ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-17
ARG RUNTIME_IMAGE=eclipse-temurin:17-jre-jammy

FROM ${MAVEN_IMAGE} AS builder

ARG SERVICE_MODULE=order-service
WORKDIR /workspace

COPY pom.xml /workspace/pom.xml
COPY services /workspace/services

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -Dmaven.test.skip=true -pl services/${SERVICE_MODULE} -am package spring-boot:repackage && \
    cp /workspace/services/${SERVICE_MODULE}/target/*-SNAPSHOT.jar /tmp/app.jar

FROM ${RUNTIME_IMAGE}

WORKDIR /app
ENV SERVER_PORT=8080
ENV JAVA_OPTS=""

COPY --from=builder /tmp/app.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
