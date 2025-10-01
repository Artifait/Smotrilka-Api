
FROM maven:3.9.6-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY . .

RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=builder /build/target/*.jar app.jar

VOLUME ["/app/data"]

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
