# Iteracion 2 — GraphQL sobre Sistema de Biblioteca

## Contexto

En esta iteracion se extendio el sistema de biblioteca incorporando dos nuevas Azure Functions que exponen una API GraphQL, permitiendo consultas y mutaciones mas complejas sobre la base de datos Oracle. Debido a problemas con la plataforma Azure al momento del despliegue, se implemento una alternativa local usando Docker con conexion a la base de datos remota en EC2.

---

## Nuevas Tablas en Base de Datos

Se agregaron 2 tablas al esquema Oracle existente:

### `categorias`
Almacena categorias literarias para clasificar los libros.

| Columna | Tipo | Descripcion |
|---------|------|-------------|
| id_categoria | NUMBER (PK) | Identificador autoincremental |
| nombre | VARCHAR2(100) | Nombre de la categoria |
| descripcion | VARCHAR2(300) | Descripcion opcional |

### `resenas`
Almacena resenas de libros hechas por usuarios, con calificacion del 1 al 5.

| Columna | Tipo | Descripcion |
|---------|------|-------------|
| id_resena | NUMBER (PK) | Identificador autoincremental |
| id_usuario | NUMBER (FK) | Usuario que resena |
| id_libro | NUMBER (FK) | Libro resenado |
| calificacion | NUMBER(1) | Puntuacion entre 1 y 5 |
| comentario | VARCHAR2(500) | Texto opcional |
| fecha | DATE | Fecha automatica (SYSDATE) |

La tabla `libros` fue alterada para agregar la columna `id_categoria` (FK a `categorias`).

El script de migracion para una DB ya existente se encuentra en [database/migration_graphql.sql](database/migration_graphql.sql).

---

## Nuevas Azure Functions GraphQL

### 1. `function-graphql-consultas`

Expone un endpoint GraphQL exclusivo para **lectura** (queries). Rechaza cualquier mutation con un error descriptivo.

**Ruta:** `POST /api/graphql/consultas`

**Queries disponibles:**

| Query | Descripcion |
|-------|-------------|
| `usuarios` | Lista todos los usuarios |
| `usuario(id)` | Detalle de un usuario con sus prestamos anidados |
| `libros` | Lista todos los libros con categoria y resenas anidadas |
| `libro(id)` | Detalle de un libro |
| `categorias` | Lista todas las categorias |
| `librosPorCategoria(idCategoria)` | Filtra libros por categoria |
| `prestamosPorUsuario(idUsuario)` | Historial de prestamos de un usuario |
| `resenasPorLibro(idLibro)` | Resenas de un libro ordenadas por fecha |

**Ejemplo de query con datos anidados:**
```graphql
{
  usuario(id: 1) {
    nombre
    email
    prestamos {
      tituloLibro
      fechaPrestamo
      estado
    }
  }
}
```

---

### 2. `function-graphql-operaciones`

Expone un endpoint GraphQL exclusivo para **escritura** (mutations).

**Ruta:** `POST /api/graphql/operaciones`

**Mutations disponibles:**

| Mutation | Descripcion |
|----------|-------------|
| `crearCategoria(nombre!, descripcion)` | Crea una nueva categoria literaria |
| `asignarCategoria(idLibro!, idCategoria!)` | Asigna una categoria a un libro existente |
| `crearResena(idUsuario!, idLibro!, calificacion!, comentario)` | Crea una resena. Valida que la calificacion sea entre 1 y 5 |
| `devolverLibro(idPrestamo!)` | Marca el prestamo como DEVUELTO y libera el libro automaticamente |

**Ejemplo de mutation:**
```graphql
mutation {
  devolverLibro(idPrestamo: 1) {
    idPrestamo
    estado
    fechaDevolucion
  }
}
```

---

## Implementacion Tecnica

### Stack GraphQL

- **Libreria:** `graphql-java 21.5`
- **Schema:** definido como SDL (Schema Definition Language) dentro de cada funcion
- **RuntimeWiring:** conecta cada campo del schema con su DataFetcher (logica Java + JDBC)
- **Inicializacion estatica:** el motor GraphQL se construye una sola vez al arrancar el contenedor (`static {}`) para no reconstruirlo en cada request

### Patron de separacion

| Endpoint | Proposito | Rechaza |
|----------|-----------|---------|
| `/api/graphql/consultas` | Solo lectura | Mutations |
| `/api/graphql/operaciones` | Solo escritura | No aplica (acepta query y mutation) |

### Logica de negocio destacada

- **`devolverLibro`**: en una sola mutation realiza 3 operaciones: verifica el estado del prestamo, actualiza `prestamos.estado = DEVUELTO` con `fecha_devolucion = SYSDATE`, y pone `libros.disponible = 1`.
- **`crearResena`**: valida que la calificacion sea un entero entre 1 y 5 antes de insertar.
- **`asignarCategoria`**: hace UPDATE y luego SELECT para retornar el libro ya actualizado.

---

## Alternativa Local con Docker

Al no poder desplegar en Azure Functions durante las pruebas, se dockerizaron ambas funciones usando la imagen oficial del runtime de Azure Functions.

### Archivos creados

| Archivo | Descripcion |
|---------|-------------|
| `function-graphql-consultas/Dockerfile` | Build Maven + runtime Azure Functions |
| `function-graphql-operaciones/Dockerfile` | Build Maven + runtime Azure Functions |
| `docker-compose.local.yml` | Orquesta ambas funciones localmente |

### Dockerfile (patron usado en ambas funciones)

```dockerfile
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
COPY --from=build /app/target/azure-functions/<appName>/ /home/site/wwwroot
```

### Arquitectura local

```
Tu PC (localhost)
  |
  +-- :8081  graphql-consultas  (Docker)  --+
  |                                         |
  +-- :8082  graphql-operaciones (Docker) --+
                                            |
                                            v
                                Oracle XE 21c en EC2
                                44.233.50.94:1521
```

### Comando para levantar

Ejecutar desde la **raiz del proyecto**:

```bash
docker compose -f docker-compose.local.yml up --build
```

### Puertos locales

| Servicio | Puerto local | Ruta |
|----------|-------------|------|
| GraphQL Consultas | 8081 | `http://localhost:8081/api/graphql/consultas` |
| GraphQL Operaciones | 8082 | `http://localhost:8082/api/graphql/operaciones` |

### Requisito de red

El puerto **1521** de la EC2 debe estar abierto en el Security Group de AWS para permitir conexiones entrantes desde la IP local hacia Oracle.

---

## Coleccion Postman

La coleccion `Sistema-Biblioteca-DuocUC.postman_collection.json` fue actualizada con:

- Carpeta **GraphQL - Consultas (Queries)**: 8 requests listos con queries de ejemplo
- Carpeta **GraphQL - Operaciones (Mutations)**: 4 requests con mutations de ejemplo

Variables de coleccion para entorno local:

| Variable | Valor |
|----------|-------|
| `AZ_GQL_CONSULTAS_URL` | `http://localhost:8081` |
| `AZ_GQL_OPERACIONES_URL` | `http://localhost:8082` |

---

## Estructura de Archivos Agregados en esta Iteracion

```
sistema-biblioteca-duoc/
|-- function-graphql-consultas/
|   |-- Dockerfile
|   |-- host.json
|   |-- local.settings.json
|   |-- pom.xml
|   |-- src/main/java/com/function/
|       |-- DatabaseHelper.java
|       |-- GraphQLConsultasFunction.java
|
|-- function-graphql-operaciones/
|   |-- Dockerfile
|   |-- host.json
|   |-- local.settings.json
|   |-- pom.xml
|   |-- src/main/java/com/function/
|       |-- DatabaseHelper.java
|       |-- GraphQLOperacionesFunction.java
|
|-- database/
|   |-- migration_graphql.sql     (migracion para DB existente)
|   |-- db.sql                    (actualizado con 5 tablas)
|
|-- docker-compose.local.yml      (levanta GraphQL localmente)
|-- iteracion2.md                 (este archivo)
```
