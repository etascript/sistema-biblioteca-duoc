# Etapa 1: Build con Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package azure-functions:package -DskipTests

# Etapa 2: Runtime Azure Functions
FROM mcr.microsoft.com/azure-functions/java:4-java21

ENV AzureWebJobsScriptRoot=/home/site/wwwroot \
    AzureFunctionsJobHost__Logging__Console__IsEnabled=true

COPY --from=build /app/target/azure-functions/duoc-f-consumidora-g11/ /home/site/wwwroot
