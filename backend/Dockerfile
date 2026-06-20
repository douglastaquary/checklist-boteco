FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/*-runner.jar /app/application.jar
ENV PORT=8080
EXPOSE 8080
CMD ["java", "-jar", "/app/application.jar"]
