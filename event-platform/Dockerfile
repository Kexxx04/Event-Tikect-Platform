FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY src/main ./src/main
RUN sed -i 's/\r$//' gradlew && sh gradlew --no-daemon bootJar --console=plain

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl tzdata \
    && addgroup -S app && adduser -S app -G app
# Event dates are local times in Colombia; without this the JVM uses UTC and closes registrations 5 hours early.
ENV TZ=America/Bogota
WORKDIR /app
COPY --from=build --chown=app:app /workspace/build/libs/*-SNAPSHOT.jar /app/app.jar
USER app
EXPOSE 8080
# Public endpoint without details (Spring Boot Actuator); it answers 503 when the database is down.
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 \
    CMD curl --fail --silent http://localhost:8080/actuator/health > /dev/null || exit 1
ENTRYPOINT ["java", "-Duser.timezone=America/Bogota", "-jar", "/app/app.jar"]
