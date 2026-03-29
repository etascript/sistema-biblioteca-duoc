# Sistema de Biblioteca - DuocUC

Sistema de gestion de biblioteca basado en una arquitectura de microservicios cloud, utilizando un BFF (Backend For Frontend) desplegado en AWS EC2 que orquesta llamadas a Azure Functions serverless, respaldadas por una base de datos Oracle.

## Arquitectura

```
Cliente (Postman / Frontend)
        |
        v
+------------------+        +----------------------------+
| BFF Service      |  --->  | Azure Fn - Usuarios        |
| (Spring Boot)    |        | biblio-users-g11            |
| EC2 :8080        |  --->  +----------------------------+
|                  |        | Azure Fn - Prestamos/Libros |
+------------------+        | biblio-prestamos-g11        |
        |                   +----------------------------+
        |                               |
+------------------+                    |
| Oracle XE 21c   | <------------------+
| EC2 Docker :1521 |
+------------------+
```

- **BFF Service**: Spring Boot 3.4.3 (Java 21) corriendo en Docker sobre EC2. Actua como punto unico de entrada para el cliente.
- **Azure Functions**: Dos Function Apps serverless en Java 21 que contienen la logica de negocio y se conectan a la base de datos.
- **Oracle XE 21c**: Base de datos relacional corriendo en Docker sobre la misma EC2.

## Estructura del Proyecto

```
sistema-biblioteca-duoc/
|-- bff-service/                  # BFF Spring Boot (Docker - EC2)
|   |-- src/main/java/com/duoc/bff_service/
|   |   |-- Controllers/BffController.java
|   |   |-- Services/BffService.java
|   |-- Dockerfile
|   |-- pom.xml
|
|-- function-usuarios/            # Azure Function - Usuarios
|   |-- src/main/java/com/function/
|   |   |-- UsuarioFunction.java
|   |   |-- DatabaseHelper.java
|   |-- pom.xml
|
|-- function-prestamos/           # Azure Function - Prestamos y Libros
|   |-- src/main/java/com/function/
|   |   |-- PrestamoFunction.java
|   |   |-- LibroFunction.java
|   |   |-- DatabaseHelper.java
|   |-- pom.xml
|
|-- database/
|   |-- db.sql                    # Script DDL (tablas + datos de prueba)
|   |-- init.sh                   # Script de inicializacion Oracle
|
|-- docker-compose.yml            # Orquestacion de contenedores
|-- Sistema-Biblioteca-DuocUC.postman_collection.json
```

## Base de Datos

Oracle XE 21c con 3 tablas en el PDB `XEPDB1`:

| Tabla | Descripcion |
|-------|-------------|
| `usuarios` | id_usuario, nombre, email, fecha_registro |
| `libros` | id_libro, titulo, autor, disponible (1/0) |
| `prestamos` | id_prestamo, id_usuario (FK), id_libro (FK), fecha_prestamo, fecha_devolucion, estado |

**Credenciales**: usuario `biblioteca` / password `Biblioteca123`

## Endpoints

### BFF Service (EC2 - puerto 8080)

Todas las rutas pasan por el prefijo `/api/bff`.

| Metodo | Ruta | Descripcion |
|--------|------|-------------|
| GET | `/api/bff/usuarios` | Listar todos los usuarios |
| POST | `/api/bff/usuarios` | Crear usuario |
| GET | `/api/bff/libros` | Listar catalogo de libros |
| POST | `/api/bff/libros` | Registrar nuevo libro |
| GET | `/api/bff/prestamos` | Listar prestamos con detalle |
| POST | `/api/bff/prestamos` | Crear prestamo de libro |

### Azure Functions (acceso directo)

**Function App Usuarios** (`biblio-users-g11.azurewebsites.net`):

| Metodo | Ruta | Descripcion |
|--------|------|-------------|
| GET | `/api/usuarios` | Listar usuarios |
| POST | `/api/usuarios` | Crear usuario |

**Function App Prestamos** (`biblio-prestamos-g11.azurewebsites.net`):

| Metodo | Ruta | Descripcion |
|--------|------|-------------|
| GET | `/api/libros` | Listar libros |
| POST | `/api/libros` | Crear libro |
| GET | `/api/prestamos` | Listar prestamos |
| POST | `/api/prestamos` | Crear prestamo |

### Ejemplos de Body (JSON)

**Crear Usuario:**
```json
{
    "nombre": "Maria Lopez",
    "email": "maria.lopez@duocuc.cl"
}
```

**Crear Libro:**
```json
{
    "titulo": "Cien Anos de Soledad",
    "autor": "Gabriel Garcia Marquez"
}
```

**Crear Prestamo:**
```json
{
    "idUsuario": 1,
    "idLibro": 1
}
```

## Despliegue

### EC2 (BFF + Oracle)

```bash
# Levantar contenedores
docker compose up -d --build

# Ver logs
docker compose logs -f

# Reconstruir solo el BFF
docker compose up -d --build bff-service

# Recrear desde cero (elimina datos)
docker compose down -v
docker compose up -d
```

### Azure Functions

Desplegar desde VS Code con la extension Azure Functions, o por CLI:

```bash
# Usuarios
cd function-usuarios
mvn clean package azure-functions:deploy

# Prestamos
cd function-prestamos
mvn clean package azure-functions:deploy
```

**Variables de entorno requeridas en Azure Portal** (Configuration > Application settings):

| Variable | Valor |
|----------|-------|
| `ORACLE_DB_URL` | `jdbc:oracle:thin:@<IP_ELASTICA>:1521/XEPDB1` |
| `ORACLE_DB_USER` | `biblioteca` |
| `ORACLE_DB_PASSWORD` | `Biblioteca123` |

### Puertos requeridos en Security Group (EC2)

| Puerto | Protocolo | Uso |
|--------|-----------|-----|
| 8080 | TCP | BFF Service |
| 1521 | TCP | Oracle DB (acceso Azure Functions) |
| 22 | TCP | SSH |

## Postman

Importar el archivo `Sistema-Biblioteca-DuocUC.postman_collection.json` en Postman.

Variables de coleccion:

| Variable | Valor |
|----------|-------|
| `BFF_URL` | `http://<IP_ELASTICA>:8080` |
| `AZ_USUARIOS_URL` | `https://biblio-users-g11.azurewebsites.net` |
| `AZ_PRESTAMOS_URL` | `https://biblio-prestamos-g11.azurewebsites.net` |

## Tecnologias

- **Java 21**
- **Spring Boot 3.4.3** (BFF)
- **Azure Functions 4.x** (Serverless)
- **Oracle XE 21c** (Base de datos)
- **Docker / Docker Compose** (Contenedores)
- **AWS EC2** (Infraestructura)
- **Maven** (Build)
