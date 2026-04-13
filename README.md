# Sistema de Biblioteca - DuocUC

Sistema de gestion de biblioteca basado en una arquitectura de microservicios cloud, con un BFF en AWS EC2 que orquesta llamadas a cuatro Azure Functions serverless, respaldadas por una base de datos Oracle.

## Arquitectura

```
Cliente (Postman / Frontend)
        |
        v
+------------------+
| BFF Service      |  Spring Boot 3.4.3 - Java 21
| EC2 :8080        |
+------------------+
        |
        +---> Azure Fn Usuarios        (REST)     biblio-users-g11
        +---> Azure Fn Prestamos/Libros(REST)     biblio-prestamos-g11
        |
        |     (llamadas directas del cliente)
        +---> Azure Fn GraphQL Consultas          biblio-graphql-consultas-g11
        +---> Azure Fn GraphQL Operaciones        biblio-graphql-operaciones-g11
                                |
                                v
                    +------------------+
                    | Oracle XE 21c    |
                    | EC2 Docker :1521 |
                    +------------------+
```

## Estructura del Proyecto

```
sistema-biblioteca-duoc/
|-- bff-service/                        # BFF REST - Spring Boot (Docker EC2)
|-- function-usuarios/                  # Azure Fn REST - CRUD Usuarios
|-- function-prestamos/                 # Azure Fn REST - Libros y Prestamos
|-- function-graphql-consultas/         # Azure Fn GraphQL - Queries
|-- function-graphql-operaciones/       # Azure Fn GraphQL - Mutations
|-- database/
|   |-- db.sql                          # DDL completo (5 tablas + datos prueba)
|   |-- init.sh                         # Script inicializacion Oracle Docker
|   |-- migration_graphql.sql           # Migracion para DB ya existente
|-- docker-compose.yml
|-- Sistema-Biblioteca-DuocUC.postman_collection.json
```

## Base de Datos (Oracle XE 21c - XEPDB1)

| Tabla | Descripcion |
|-------|-------------|
| `categorias` | Categorias literarias de los libros |
| `usuarios` | Usuarios registrados en la biblioteca |
| `libros` | Catalogo de libros con disponibilidad y categoria |
| `prestamos` | Registro de prestamos activos y devueltos |
| `resenas` | Resenas de libros por usuarios (calificacion 1-5) |

**Credenciales Docker**: usuario `biblioteca` / password `Biblioteca123`

---

## Endpoints REST — BFF Service

Base URL: `http://<IP_ELASTICA>:8080`

| Metodo | Ruta | Descripcion |
|--------|------|-------------|
| GET | `/api/bff/usuarios` | Listar todos los usuarios |
| POST | `/api/bff/usuarios` | Crear usuario |
| GET | `/api/bff/libros` | Listar catalogo de libros |
| POST | `/api/bff/libros` | Registrar nuevo libro |
| GET | `/api/bff/prestamos` | Listar prestamos con detalle |
| POST | `/api/bff/prestamos` | Crear prestamo (valida disponibilidad) |

---

## Endpoints GraphQL

Ambos endpoints reciben `POST` con body `{ "query": "..." }`.

### GraphQL Consultas — `biblio-graphql-consultas-g11`
Ruta: `POST /api/graphql/consultas`

**Queries disponibles:**

```graphql
# Todos los usuarios
{ usuarios { idUsuario nombre email fechaRegistro } }

# Usuario con sus prestamos
{ usuario(id: 1) { nombre prestamos { tituloLibro estado fechaPrestamo } } }

# Todos los libros con categoria y resenas
{ libros { titulo autor disponible categoria { nombre } resenas { calificacion comentario } } }

# Libro por ID
{ libro(id: 1) { titulo categoria { nombre } resenas { nombreUsuario calificacion comentario } } }

# Todas las categorias
{ categorias { idCategoria nombre descripcion } }

# Libros filtrados por categoria
{ librosPorCategoria(idCategoria: 1) { titulo autor disponible } }

# Prestamos de un usuario
{ prestamosPorUsuario(idUsuario: 1) { tituloLibro fechaPrestamo estado } }

# Resenas de un libro
{ resenasPorLibro(idLibro: 1) { nombreUsuario calificacion comentario fecha } }
```

### GraphQL Operaciones — `biblio-graphql-operaciones-g11`
Ruta: `POST /api/graphql/operaciones`

**Mutations disponibles:**

```graphql
# Crear categoria
mutation { crearCategoria(nombre: "Ciencia Ficcion", descripcion: "...") { idCategoria nombre } }

# Asignar categoria a libro
mutation { asignarCategoria(idLibro: 1, idCategoria: 2) { idLibro titulo } }

# Crear resena (calificacion: 1 a 5)
mutation { crearResena(idUsuario: 1, idLibro: 1, calificacion: 5, comentario: "Excelente") { idResena fecha } }

# Devolver libro (cambia estado a DEVUELTO y libera el libro)
mutation { devolverLibro(idPrestamo: 1) { idPrestamo estado fechaDevolucion } }
```

---

## Ejemplos de Body REST (JSON)

**Crear Usuario:**
```json
{ "nombre": "Maria Lopez", "email": "maria.lopez@duocuc.cl" }
```

**Crear Libro:**
```json
{ "titulo": "Cien Anos de Soledad", "autor": "Gabriel Garcia Marquez" }
```

**Crear Prestamo:**
```json
{ "idUsuario": 1, "idLibro": 1 }
```

---

## Despliegue

### EC2 — BFF + Oracle

```bash
# Levantar todo
docker compose up -d --build

# Solo reconstruir BFF
docker compose up -d --build bff-service

# Recrear desde cero (borra datos)
docker compose down -v && docker compose up -d

# Logs
docker compose logs -f oracle-db
docker compose logs -f bff-service
```

### Migrar tablas en DB ya existente

```bash
docker exec -it biblioteca-db sqlplus biblioteca/Biblioteca123@//localhost:1521/XEPDB1 @/tmp/migration_graphql.sql
```

### Azure Functions — Despliegue

Desde VS Code con la extension Azure Functions, o por CLI:

```bash
cd function-usuarios && mvn clean package azure-functions:deploy
cd function-prestamos && mvn clean package azure-functions:deploy
cd function-graphql-consultas && mvn clean package azure-functions:deploy
cd function-graphql-operaciones && mvn clean package azure-functions:deploy
```

**Variables de entorno (Azure Portal > Configuration > Application settings):**

| Variable | Valor |
|----------|-------|
| `ORACLE_DB_URL` | `jdbc:oracle:thin:@<IP_ELASTICA>:1521/XEPDB1` |
| `ORACLE_DB_USER` | `biblioteca` |
| `ORACLE_DB_PASSWORD` | `Biblioteca123` |

### Security Group EC2 — Puertos requeridos

| Puerto | Uso |
|--------|-----|
| 22 | SSH |
| 8080 | BFF Service |
| 1521 | Oracle DB (Azure Functions) |

---

## Postman

Importar `Sistema-Biblioteca-DuocUC.postman_collection.json`.

Variables de coleccion a configurar:

| Variable | Valor |
|----------|-------|
| `BFF_URL` | `http://<IP_ELASTICA>:8080` |
| `AZ_USUARIOS_URL` | `https://biblio-users-g11.azurewebsites.net` |
| `AZ_PRESTAMOS_URL` | `https://biblio-prestamos-g11.azurewebsites.net` |
| `AZ_GQL_CONSULTAS_URL` | `https://biblio-graphql-consultas-g11.azurewebsites.net` |
| `AZ_GQL_OPERACIONES_URL` | `https://biblio-graphql-operaciones-g11.azurewebsites.net` |

---

## Tecnologias

| Componente | Tecnologia |
|---|---|
| BFF | Spring Boot 3.4.3, Java 21 |
| Azure Functions | Azure Functions 4.x, Java 21 |
| GraphQL | graphql-java 21.5 |
| Base de datos | Oracle XE 21c |
| Infraestructura | Docker, AWS EC2, Azure |
| Build | Maven |
