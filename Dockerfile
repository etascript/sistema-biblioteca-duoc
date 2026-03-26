# Etapa 1: Construcción con Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Compila y empaqueta la función con el plugin de Azure
RUN mvn clean package

# Etapa 2: Imagen oficial de Azure Functions en tiempo de ejecución
FROM mcr.microsoft.com/azure-functions/java:4-java17
WORKDIR /home/site/wwwroot

# Configuraciones para que la función se ejecute correctamente en Docker
ENV AzureWebJobsScriptRoot=/home/site/wwwroot \
    AzureFunctionsJobHost__Logging__Console__IsEnabled=true

# Copiamos la función empaquetada desde la etapa de construcción
# La ruta en target/... depende de tu artifactId (en este caso function-usuarios)
COPY --from=build /app/target/azure-functions/function-usuarios /home/site/wwwroot

# Azure Functions en Docker expone el puerto 80 por defecto
EXPOSE 80