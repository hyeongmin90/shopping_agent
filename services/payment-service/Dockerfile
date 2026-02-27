FROM gradle:8.10-jdk17 AS build
WORKDIR /app

COPY build.gradle settings.gradle /app/
COPY src /app/src

RUN gradle --no-daemon bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/build/libs/payment-service-*.jar /app/app.jar

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
