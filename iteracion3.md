# Iteracion 3 — Azure Event Grid sobre Sistema de Biblioteca

## Contexto

En esta iteracion se integró Azure Event Grid al sistema, completando las tres formas distintas de comunicación entre componentes:

| # | Mecanismo | Funciones | Trigger |
|---|-----------|-----------|---------|
| 1 | REST API HTTP | `function-usuarios`, `function-prestamos` | HTTP Trigger |
| 2 | GraphQL | `function-graphql-consultas`, `function-graphql-operaciones` | HTTP Trigger |
| 3 | Event Grid | `function-eventgrid-publicador`, `function-eventgrid-suscriptor` | HTTP Trigger → Event Grid → EventGrid Trigger |

---

## Arquitectura Event Grid

```
Cliente (Postman/BFF)
       │
       │ POST /api/eventos/publicar
       ▼
┌─────────────────────────────────┐
│  function-eventgrid-publicador  │  Azure Function (HTTP Trigger)
│  biblio-eg-publicador-g11       │  Java 21
│                                 │
│  Publica EventGridEvent al      │
│  Custom Topic de Azure          │
└────────────────┬────────────────┘
                 │
                 │ Azure Event Grid
                 │ (enruta el evento a suscriptores registrados)
                 ▼
┌─────────────────────────────────┐
│  function-eventgrid-suscriptor  │  Azure Function (EventGrid Trigger)
│  biblio-eg-suscriptor-g11       │  Java 21
│                                 │
│  Recibe evento, lo persiste     │
│  en Oracle (tabla eventos_log)  │
│                                 │
│  GET /api/eventos/log           │  (HTTP Trigger para consultar log)
└─────────────────────────────────┘
                 │
                 ▼
          Oracle XE 21c
          tabla: eventos_log
```

---

## Nuevos Recursos Azure Requeridos

### 1. Event Grid Custom Topic

Crear en Azure Portal o con Azure CLI:

```bash
# Crear Resource Group (si no existe)
az group create --name java-functions-group --location westus

# Crear Custom Topic
az eventgrid topic create \
  --name biblioteca-eventos \
  --resource-group java-functions-group \
  --location westus

# Obtener endpoint y clave (para configurar el publicador)
az eventgrid topic show \
  --name biblioteca-eventos \
  --resource-group java-functions-group \
  --query "endpoint" -o tsv

az eventgrid topic key list \
  --name biblioteca-eventos \
  --resource-group java-functions-group \
  --query "key1" -o tsv
```

### 2. Event Grid Subscription

Después de desplegar `function-eventgrid-suscriptor`, crear la suscripción:

```bash
az eventgrid event-subscription create \
  --name biblioteca-suscripcion \
  --source-resource-id /subscriptions/<SUB_ID>/resourceGroups/java-functions-group/providers/Microsoft.EventGrid/topics/biblioteca-eventos \
  --endpoint https://biblio-eg-suscriptor-g11.azurewebsites.net/runtime/webhooks/EventGrid?functionName=procesarEventoBiblioteca \
  --endpoint-type webhook
```

Azure Event Grid realizará automáticamente el handshake de validación (SubscriptionValidationEvent). El runtime de Azure Functions responde a este handshake de forma transparente.

---

## Nueva Tabla en Base de Datos

### `eventos_log`

Almacena el historial de todos los eventos recibidos desde Event Grid.

| Columna | Tipo | Descripcion |
|---------|------|-------------|
| id_evento | NUMBER (PK) | Identificador autoincremental |
| id_evento_externo | VARCHAR2(100) | UUID asignado por el publicador |
| tipo_evento | VARCHAR2(100) | Ej: `biblioteca.prestamo.creado` |
| asunto | VARCHAR2(200) | Sujeto del evento, ej: `prestamos/5` |
| datos | CLOB | JSON con el payload del evento |
| fecha_recibido | DATE | Timestamp de recepción (SYSDATE) |

Script de migración para DB existente: [database/migration_eventgrid.sql](database/migration_eventgrid.sql)

---

## Nuevas Azure Functions

### 1. `function-eventgrid-publicador`

**App Name:** `biblio-eg-publicador-g11`
**Trigger:** HTTP POST
**Ruta:** `POST /api/eventos/publicar`

**Variables de entorno requeridas:**

| Variable | Descripcion |
|----------|-------------|
| `EVENT_GRID_TOPIC_ENDPOINT` | URL del Custom Topic (termina en `.eventgrid.azure.net/api/events`) |
| `EVENT_GRID_TOPIC_KEY` | Clave de acceso del Custom Topic |

**Cuerpo de la solicitud:**

```json
{
  "tipo": "PRESTAMO_CREADO",
  "asunto": "prestamos/5",
  "datos": {
    "idPrestamo": "5",
    "idUsuario": "1",
    "idLibro": "2",
    "tituloLibro": "Cien Anos de Soledad"
  }
}
```

**Tipos de evento disponibles:**

| tipo (entrada) | eventType publicado |
|----------------|---------------------|
| `PRESTAMO_CREADO` | `biblioteca.prestamo.creado` |
| `LIBRO_DEVUELTO` | `biblioteca.prestamo.devuelto` |
| `USUARIO_REGISTRADO` | `biblioteca.usuario.registrado` |

**Respuesta exitosa (200):**

```json
{
  "mensaje": "Evento publicado correctamente en Azure Event Grid",
  "idEvento": "a1b2c3d4-...",
  "tipoEvento": "biblioteca.prestamo.creado",
  "asunto": "prestamos/5",
  "datos": { "idPrestamo": "5", ... }
}
```

---

### 2. `function-eventgrid-suscriptor`

**App Name:** `biblio-eg-suscriptor-g11`

Contiene dos funciones:

#### `procesarEventoBiblioteca` — EventGrid Trigger

Recibe eventos de Azure Event Grid, extrae los campos del EventGridEvent JSON y los persiste en `eventos_log`.

El endpoint de webhook al que Event Grid debe apuntar (se configura en la suscripción):
```
POST https://biblio-eg-suscriptor-g11.azurewebsites.net/runtime/webhooks/EventGrid?functionName=procesarEventoBiblioteca
```

#### `consultarEventosLog` — HTTP Trigger

Permite consultar el historial de eventos procesados.

**Ruta:** `GET /api/eventos/log`
**Parámetro opcional:** `?tipo=biblioteca.prestamo.creado`

**Respuesta:**
```json
[
  {
    "idEvento": 1,
    "tipoEvento": "biblioteca.prestamo.creado",
    "asunto": "prestamos/5",
    "datos": "{\"idPrestamo\":\"5\",\"tituloLibro\":\"Cien Anos de Soledad\"}",
    "fechaRecibido": "2026-05-03"
  }
]
```

---

## Despliegue en Azure

### Publicador

```bash
cd function-eventgrid-publicador
mvn clean package azure-functions:package -DskipTests
mvn azure-functions:deploy

# Configurar variables de entorno
az functionapp config appsettings set \
  --name biblio-eg-publicador-g11 \
  --resource-group java-functions-group \
  --settings \
    EVENT_GRID_TOPIC_ENDPOINT="https://biblioteca-eventos.westus-1.eventgrid.azure.net/api/events" \
    EVENT_GRID_TOPIC_KEY="<KEY>"
```

### Suscriptor

```bash
cd function-eventgrid-suscriptor
mvn clean package azure-functions:package -DskipTests
mvn azure-functions:deploy

# Configurar variables de entorno
az functionapp config appsettings set \
  --name biblio-eg-suscriptor-g11 \
  --resource-group java-functions-group \
  --settings \
    ORACLE_DB_URL="jdbc:oracle:thin:@44.226.152.17:1521/XEPDB1" \
    ORACLE_DB_USER="biblioteca" \
    ORACLE_DB_PASSWORD="Biblioteca123"
```

---

## Flujo completo de prueba

1. **Crear un préstamo** via REST API:
   `POST /api/prestamos` → `function-prestamos`

2. **Publicar el evento** correspondiente:
   `POST /api/eventos/publicar` → `function-eventgrid-publicador`
   ```json
   {
     "tipo": "PRESTAMO_CREADO",
     "asunto": "prestamos/1",
     "datos": { "idPrestamo": "1", "idUsuario": "1", "idLibro": "2" }
   }
   ```

3. Azure Event Grid **enruta el evento** al suscriptor registrado.

4. `function-eventgrid-suscriptor` **recibe y almacena** el evento en `eventos_log`.

5. **Verificar** que el evento fue procesado:
   `GET /api/eventos/log` → `function-eventgrid-suscriptor`

---

## Puertos locales (Docker)

| Servicio | Puerto local | Ruta principal |
|----------|-------------|----------------|
| EventGrid Publicador | 8083 | `http://localhost:8083/api/eventos/publicar` |
| EventGrid Suscriptor | 8084 | `http://localhost:8084/api/eventos/log` |

> **Nota:** El trigger EventGrid del suscriptor requiere `AzureWebJobsStorage` con una cuenta real de Azure Storage y una suscripción Event Grid configurada. Para pruebas locales completas, desplegar en Azure y usar ngrok o Azure Dev Tunnels para exponer el endpoint local.

---

## Estructura de Archivos Agregados en esta Iteracion

```
sistema-biblioteca-duoc/
|-- function-eventgrid-publicador/
|   |-- Dockerfile
|   |-- host.json
|   |-- local.settings.json
|   |-- pom.xml
|   |-- src/main/java/com/function/
|       |-- EventGridPublicadorFunction.java
|
|-- function-eventgrid-suscriptor/
|   |-- Dockerfile
|   |-- host.json
|   |-- local.settings.json
|   |-- pom.xml
|   |-- src/main/java/com/function/
|       |-- DatabaseHelper.java
|       |-- EventGridSuscriptorFunction.java
|
|-- database/
|   |-- migration_eventgrid.sql   (migración para DB existente)
|   |-- db.sql                    (actualizado con tabla eventos_log)
|
|-- docker-compose.yml            (actualizado con 2 nuevos servicios)
|-- iteracion3.md                 (este archivo)
```
