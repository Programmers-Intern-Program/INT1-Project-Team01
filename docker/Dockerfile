FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

RUN chmod +x ./gradlew

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon || true

COPY src ./src

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew bootJar --no-daemon

RUN mkdir -p /app/out && \
    JAR_FILE=$(find /app -type f -name "*.jar" ! -name "*plain.jar" | head -n 1) && \
    echo "Selected jar: $JAR_FILE" && \
    test -n "$JAR_FILE" && \
    cp "$JAR_FILE" /app/out/app.jar

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=builder /app/out/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]