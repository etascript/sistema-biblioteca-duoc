# Etapa 1: Construccion (Build) con Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Compila el proyecto omitiendo los tests
RUN mvn clean package -DskipTests

# Etapa 2: Ejecucion (Run) con un JDK mas ligero
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
