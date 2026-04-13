FROM gradle:8.10-jdk17 AS builder
WORKDIR /app
COPY . .
RUN gradle shadowJar --no-daemon -x test

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*-all.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
