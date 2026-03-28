# Etapa 1: Construcción (Build) con Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Compila el proyecto omitiendo los tests
RUN mvn clean package -DskipTests

# Etapa 2: Ejecución (Run) con un JDK más ligero
FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
# El cambio clave: forzamos a que copie solo el jar principal, ignorando el -plain.jar
COPY --from=build /app/target/*SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]