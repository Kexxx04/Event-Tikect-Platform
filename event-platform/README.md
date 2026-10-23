# Event Platform API

API REST para administrar eventos e inscripciones garantizando que **nunca se supere el aforo**.
Está construida con Java 21, Spring Boot 4, Spring Web MVC, Spring Data JPA, Spring Security (JWT),
PostgreSQL 16 y Flyway. El repositorio también incluye un frontend en React que consume la API.

```text
event-platform (1)/          raíz del repositorio Git
|-- event-platform/          API Spring Boot (este README)
`-- frontend/                Interfaz web Vite + React + TypeScript (ver frontend/README.md)
```

## Contenido

1. [Estado del proyecto](#estado-del-proyecto)
2. [Arquitectura](#arquitectura)
3. [Modelo de datos](#modelo-de-datos)
4. [Reglas de negocio](#reglas-de-negocio)
5. [Concurrencia y aforo](#concurrencia-y-aforo)
6. [Tecnologías](#tecnologías)
7. [Requisitos y modos de ejecución](#requisitos-y-modos-de-ejecución)
8. [Configuración](#configuración)
9. [Ejecutar la API](#ejecutar-la-api)
10. [Autenticación](#autenticación)
11. [Usar la API desde PowerShell](#usar-la-api-desde-powershell)
12. [Endpoints](#endpoints)
13. [Errores](#errores)
14. [Pruebas automatizadas](#pruebas-automatizadas)
15. [Calidad: JaCoCo, PIT, SonarQube y pipeline](#calidad-jacoco-pit-sonarqube-y-pipeline)
16. [Construir la aplicación](#construir-la-aplicación)
17. [Estructura del código](#estructura-del-código)
18. [Flujo de trabajo con Git](#flujo-de-trabajo-con-git)
19. [Problemas conocidos y limitaciones](#problemas-conocidos-y-limitaciones)
20. [Próximos pasos sugeridos](#próximos-pasos-sugeridos)

## Estado del proyecto

Están implementadas todas las funcionalidades mínimas del enunciado:

| Funcionalidad del enunciado | Estado | Endpoint |
| --- | --- | --- |
| Registrar usuarios | Hecho | `POST /api/users` |
| Crear eventos | Hecho | `POST /api/events` |
| Modificar eventos | Hecho | `PUT /api/events/{id}`, `PATCH /api/events/{id}/status` |
| Consultar eventos | Hecho | `GET /api/events`, `GET /api/events/{id}` |
| Consultar disponibilidad | Hecho | `GET /api/events/{id}/availability` |
| Inscribir usuarios | Hecho | `POST /api/events/{eventId}/registrations` |
| Cancelar inscripciones | Hecho | `DELETE /api/events/{eventId}/registrations/{userId}` |
| Consultar inscritos | Hecho | `GET /api/events/{eventId}/registrations?status=CONFIRMED` |
| Consultar eventos de un usuario | Hecho | `GET /api/users/{userId}/events` |
| Administrar lista de espera | Hecho | `GET /api/events/{eventId}/waitlist` + promoción automática |
| Escenario de estrés (100 cupos, 1000 usuarios) | Probado | `RegistrationConcurrencyIntegrationTests`, `PostgresRegistrationConcurrencyTests` |

Además del enunciado: login JWT para un administrador, CORS para el frontend, frontend React,
imagen Docker y pipeline de Azure DevOps con JaCoCo, PIT y SonarQube opcional.

## Arquitectura

La API sigue una arquitectura por capas dentro de `com.eventplatform.api`:

```text
HTTP (JSON)
   |
   v
Spring Security --------- JWT (Authorization: Bearer ...), CORS, 401 estructurado
   |
   v
controller/  ------------ Recibe y valida DTOs (Bean Validation); nunca expone entidades JPA
   |
   v
service/  --------------- Reglas de negocio y transacciones (@Transactional)
   |                      UserService, EventService, RegistrationService, WaitlistService, AuthService
   v
repository/  ------------ Spring Data JPA; EventRepository.findByIdForUpdate bloquea la fila
   |
   v
PostgreSQL (Flyway V1)    CHECK constraints como segunda barrera contra datos inválidos

exception/GlobalExceptionHandler -> errores con formato único (400, 401, 404, 409)
config/ClockConfig               -> bean java.time.Clock usado para "ahora" (permite probar fechas)
```

- Los DTO son `record` en `dto/user`, `dto/event`, `dto/registration` y `dto/auth`.
- Las respuestas se construyen con métodos `from(...)` a partir de las entidades.
- Todo "ahora" del negocio sale de `LocalDateTime.now(clock)`. En producción el `Clock` es
  `Clock.systemDefaultZone()`; en las pruebas unitarias se usa un `Clock.fixed(...)`.
- `spring.jpa.open-in-view=false`: las entidades no se cargan fuera de la transacción del servicio.
- Hibernate valida el esquema (`ddl-auto=validate`); el esquema lo crea Flyway.

## Modelo de datos

El esquema está en `src/main/resources/db/migration/V1__create_initial_schema.sql`. **No se modifica
la V1**: los cambios de esquema van en nuevas migraciones `V2__...`, `V3__...`.

### Correspondencia con el enunciado

| Enunciado | Entidad / campo JSON | Columna |
| --- | --- | --- |
| Usuario: id, nombre, correo, documento, estado | `User`: `id`, `name`, `email`, `document`, `status` | `users.id`, `name`, `email`, `document`, `status` |
| Evento: id, nombre, descripción, fechaInicio, fechaFin, capacidad, inscritos, estado | `Event`: `id`, `name`, `description`, `startDate`, `endDate`, `capacity`, `registeredCount`, `status` | `events.id`, `name`, `description`, `start_date`, `end_date`, `capacity`, `registered_count`, `status` |
| Inscripción: id, usuario, evento, fechaInscripcion, estado | `Registration`: `id`, `user`, `event`, `registrationDate`, `status` | `registrations.id`, `user_id`, `event_id`, `registration_date`, `status` |

| Estados del enunciado | Enum en el código |
| --- | --- |
| Evento: `PROGRAMADO`, `ACTIVO`, `FINALIZADO`, `CANCELADO` | `EventStatus`: `SCHEDULED`, `ACTIVE`, `FINISHED`, `CANCELLED` |
| Inscripción: `CONFIRMADA`, `CANCELADA`, `LISTA_ESPERA` | `RegistrationStatus`: `CONFIRMED`, `CANCELLED`, `WAITING_LIST` |
| Usuario (activo / inactivo) | `UserStatus`: `ACTIVE`, `INACTIVE` |

### Restricciones en la base de datos

| Tabla | Restricción |
| --- | --- |
| `users` | `email` y `document` únicos; `status IN ('ACTIVE','INACTIVE')` |
| `events` | `end_date > start_date`; `capacity > 0`; `0 <= registered_count <= capacity`; `status` en los 4 valores |
| `registrations` | FK a `users` y `events`; **única `(user_id, event_id)`**; `status` en los 3 valores |
| Índices | `idx_registrations_event`, `idx_registrations_user` e índice parcial `idx_registrations_waiting_list (event_id, registration_date) WHERE status = 'WAITING_LIST'` |

`registered_count` guarda **solo las inscripciones `CONFIRMED`**. Las de la lista de espera se cuentan
aparte (`waitlistCount` en la disponibilidad).

### Ciclo de vida de un evento

```text
            +--> ACTIVE --+--> FINISHED
SCHEDULED --+             |
            +-------------+--> CANCELLED
```

- Un evento se crea siempre `SCHEDULED` y con `registeredCount = 0`.
- Transiciones válidas: `SCHEDULED → ACTIVE`, `SCHEDULED → CANCELLED`, `ACTIVE → FINISHED`,
  `ACTIVE → CANCELLED`. Cualquier otra responde `409`. `FINISHED` y `CANCELLED` son finales.
- El estado **se cambia manualmente** con `PATCH /api/events/{id}/status`; no cambia solo con el paso del tiempo.
- Un evento `FINISHED` o `CANCELLED` no se puede editar con `PUT` (`409`).

## Reglas de negocio

| # | Regla del enunciado | Implementación | Respuesta |
| --- | --- | --- | --- |
| 1 | Un usuario no puede inscribirse dos veces en el mismo evento | Se rechaza si ya tiene una inscripción `CONFIRMED` o `WAITING_LIST` | `409` |
| 2 | Un evento no puede superar su capacidad | Bloqueo pesimista de la fila del evento + `CHECK registered_count <= capacity` | Nunca hay sobrecupo |
| 3 | No hay inscripciones después de iniciado el evento | Se rechaza cuando `now >= startDate` | `409` |
| 4 | Un evento cancelado no acepta inscripciones | Estado `CANCELLED` | `409` |
| 5 | Un evento finalizado no acepta inscripciones | Estado `FINISHED` | `409` |
| 6 | La capacidad debe ser mayor que cero | `@Positive` en el DTO + validación en el servicio + `CHECK` en la BD | `400` |
| 7 | Al cancelar una inscripción se libera un cupo | Cancelar una `CONFIRMED` resta 1 a `registeredCount` | `200` |
| 8 | Con el evento lleno, los nuevos usuarios van a lista de espera | Si `registeredCount == capacity`, la inscripción queda `WAITING_LIST` | `201` |
| 9 | Al liberarse un cupo, el primero de la lista de espera queda confirmado | `WaitlistService` confirma la inscripción en espera más antigua (`registrationDate`, luego `id`) | Automático |
| 10 | Un usuario inactivo no puede inscribirse | Usuario `INACTIVE` | `409` |

### Comportamiento detallado

**Inscribir** (`POST /api/events/{eventId}/registrations`). Las validaciones se aplican en este orden:

1. El evento existe (`404`) y se bloquea su fila hasta el final de la transacción.
2. El usuario existe (`404`).
3. El evento no está `CANCELLED` ni `FINISHED` (`409`).
4. El evento no ha iniciado: `now < startDate` (`409`). Un evento `ACTIVE` acepta inscripciones mientras no haya llegado su fecha de inicio.
5. El usuario está `ACTIVE` (`409`).
6. El usuario no tiene ya una inscripción activa en el evento (`409`).
7. Si hay cupo queda `CONFIRMED` y `registeredCount` aumenta en 1; si no, queda `WAITING_LIST`.

Un usuario que canceló puede volver a inscribirse. Como la tabla tiene la restricción única
`(user_id, event_id)`, se **reutiliza su fila** con una nueva `registrationDate`; si el evento está
lleno, entra al final de la lista de espera.

**Cancelar** (`DELETE /api/events/{eventId}/registrations/{userId}`):

1. Evento (`404`, se bloquea su fila), usuario (`404`) e inscripción (`404`) deben existir.
2. No se cancelan inscripciones de eventos `CANCELLED` o `FINISHED` (`409`).
3. Cancelar dos veces responde `409`.
4. Si la inscripción era `CONFIRMED`, se libera el cupo y, en la misma transacción, se confirma la
   primera inscripción de la lista de espera. La respuesta indica quién fue promovido (`promoted`).
5. Si la inscripción estaba en `WAITING_LIST`, solo sale de la lista; no cambia `registeredCount`.

Se puede cancelar después de iniciado el evento mientras su estado no sea `CANCELLED` o `FINISHED`.

**Cambiar la capacidad** (`PUT /api/events/{id}`):

- La capacidad no puede quedar por debajo de `registeredCount` (`409`).
- Si la capacidad aumenta, los cupos nuevos se asignan automáticamente a la lista de espera, en orden.

**Disponibilidad** (`GET /api/events/{id}/availability`):

- `availableSeats = max(0, capacity - registeredCount)`.
- `acceptingRegistrations` es `true` cuando el evento está `SCHEDULED` o `ACTIVE` y todavía no inicia.
  Un evento lleno sigue aceptando inscripciones, que van a lista de espera.

**Usuarios**: se crean `ACTIVE`; nombre, correo y documento se recortan; el correo se guarda en
minúsculas y es único sin distinguir mayúsculas; el documento también es único. `PATCH` solo cambia
los campos enviados. Inactivar a un usuario no cancela sus inscripciones existentes.

## Concurrencia y aforo

Toda operación que cambia `registeredCount` (inscribir, cancelar, promover, cambiar capacidad) se
ejecuta en **una sola transacción** que primero bloquea la fila del evento:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select e from Event e where e.id = :id")
Optional<Event> findByIdForUpdate(@Param("id") Long id);   // SELECT ... FOR UPDATE
```

Las peticiones concurrentes sobre el mismo evento esperan su turno, así que cada una lee el conteo
real antes de decidir entre `CONFIRMED` y `WAITING_LIST`. La revisión de duplicados también ocurre
dentro del bloqueo. Eventos distintos no se bloquean entre sí. `WaitlistService.fillFreeSeats` exige
una transacción existente (`Propagation.MANDATORY`) para que nunca se promueva sin el bloqueo.

Escenario de estrés del enunciado, automatizado:

| Prueba | Base de datos | Escenario | Resultado esperado |
| --- | --- | --- | --- |
| `RegistrationConcurrencyIntegrationTests` (tarea `test`) | H2 | 1000 usuarios, 32 hilos, 100 cupos | 100 `CONFIRMED`, 900 `WAITING_LIST`, `registeredCount = 100` |
| `RegistrationConcurrencyIntegrationTests` (tarea `test`) | H2 | 20 cancelaciones simultáneas | 20 promociones de usuarios distintos, sigue en 100 |
| `PostgresRegistrationConcurrencyTests` (tarea `postgresTest`) | PostgreSQL 16 real | 1000 usuarios, 64 hilos, 100 cupos | 100 `CONFIRMED`, 900 `WAITING_LIST`, 0 sobrecupos |

Como control, al quitar temporalmente el bloqueo, la prueba en H2 falla con 899 y 924 confirmados
sobre 100 cupos. Es decir, la prueba sí detecta los sobrecupos.

## Tecnologías

| Área | Herramienta |
| --- | --- |
| Lenguaje y build | Java 21 (toolchain), Gradle Wrapper 9.7.1 |
| Framework | Spring Boot 4.1.1: Web MVC, Data JPA, Validation, Security, OAuth2 Resource Server (JWT) |
| Base de datos | PostgreSQL 16, Flyway; H2 en memoria para pruebas |
| Pruebas | JUnit 5, Mockito, MockMvc, Spring Security Test, Cucumber 7.34 (Gherkin), Testcontainers 2 |
| Calidad | JaCoCo 0.8.14, PIT 1.19, SonarQube (plugin 7.3) |
| Contenedores | Docker, Docker Compose |
| CI | Azure DevOps (`azure-pipelines.yml`) |
| Frontend | Vite, React 19, TypeScript, React Router 7 (ver `../frontend/README.md`) |

## Requisitos y modos de ejecución

| Objetivo | Qué se necesita | Archivo | Comando principal |
| --- | --- | --- | --- |
| Desarrollar con PostgreSQL | JDK 21 y Docker | `compose.dev.yaml` | `docker compose -f compose.dev.yaml up -d` y `.\gradlew.bat bootRun` |
| Probar rápido sin PostgreSQL | JDK 21 | — | `.\gradlew.bat bootTestRun --args="--spring.profiles.active=test"` |
| Ejecutar todo con Docker (API + BD) | Docker | `compose.yaml` | `docker compose up -d --wait` (ver [DOCKER.md](DOCKER.md)) |
| Usar la interfaz web | Node.js 20+ y la API activa | `../frontend` | `npm install` y `npm run dev` |

No es necesario instalar Gradle: el repositorio incluye Gradle Wrapper. Los dos archivos Compose son
alternativas independientes (cada uno con su propio volumen); el `Dockerfile` construye la imagen de
la API. Todos los comandos de este README se ejecutan desde la carpeta `event-platform`.

## Configuración

### Archivo `.env`

La forma recomendada es un archivo `.env` en esta carpeta (Git lo ignora). **Docker Compose y la API
(`bootRun` o IntelliJ) lo leen automáticamente**:

```powershell
Copy-Item .env.example .env
notepad .env   # pon el mismo usuario y contraseña en POSTGRES_* y en DB_*
```

La API importa el archivo con `spring.config.import=optional:file:.env[.properties]`, relativo al
directorio de trabajo, así que ejecútala desde esta carpeta. Las variables de entorno reales tienen
prioridad sobre el `.env`. Para el modo Docker completo se usa otra plantilla: `.env.docker.example`.

### Variables

| Variable | Quién la usa | Valor predeterminado | Notas |
| --- | --- | --- | --- |
| `DB_URL` | API | `jdbc:postgresql://localhost:5432/event_platform` | En Docker Compose apunta a `postgres:5432` |
| `DB_USERNAME`, `DB_PASSWORD` | API | Obligatorias | Deben coincidir con `POSTGRES_USER` y `POSTGRES_PASSWORD` |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Compose | `event_platform` / obligatorias | Credenciales con las que se crea la base |
| `POSTGRES_PORT` | `compose.dev.yaml` | `5432` | Puerto publicado en el equipo |
| `API_PORT` | `compose.yaml` | `8080` | Se publica solo en `127.0.0.1` |
| `ADMIN_USERNAME` | API | `admin` | Usuario del login |
| `ADMIN_PASSWORD` | API | Obligatoria | Sin ella la API no arranca |
| `JWT_SECRET` | API | Obligatoria | Mínimo 32 caracteres; firma los tokens HS256 |
| `JWT_EXPIRATION` | API | `PT8H` | Duración ISO-8601 (`PT30M`, `PT8H`, `P1D`) |
| `CORS_ALLOWED_ORIGINS` | API | `http://localhost:5173` | Orígenes del navegador separados por comas |
| `SONAR_TOKEN`, `SONAR_HOST_URL` | Tarea `sonar` | `SONAR_HOST_URL=http://localhost:9000` | El token nunca se guarda en `build.gradle` |
| `VITE_API_URL` | Frontend (`frontend/.env`) | `http://localhost:8080` | URL de la API que usa el navegador |

Si falta `ADMIN_PASSWORD` o `JWT_SECRET`, o el secreto tiene menos de 32 caracteres, la API no
arranca y el error indica qué variable definir (validación de `SecurityProperties`).

Alternativa sin `.env`: define las variables en la misma sesión de PowerShell antes de cada comando:

```powershell
$env:DB_USERNAME = Read-Host 'Usuario de PostgreSQL'
$dbSecret = Read-Host 'Contraseña de PostgreSQL' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbSecret).Password
$env:POSTGRES_USER = $env:DB_USERNAME
$env:POSTGRES_PASSWORD = $env:DB_PASSWORD
```

### PostgreSQL para desarrollo

```powershell
docker compose -f compose.dev.yaml up -d
docker compose -f compose.dev.yaml ps
```

Flyway ejecuta `V1__create_initial_schema.sql` al arrancar la API. Para un volumen existente, usa sus
credenciales actuales: cambiar el `.env` no cambia la contraseña ya guardada en PostgreSQL (o borra el
volumen con `docker compose -f compose.dev.yaml down -v`, perdiendo los datos).

Si el puerto `5432` está ocupado, publica PostgreSQL en otro puerto y ajusta la URL de Spring:

```powershell
$env:POSTGRES_PORT = "5433"
$env:DB_URL = "jdbc:postgresql://localhost:5433/event_platform"
docker compose -f compose.dev.yaml up -d
```

## Ejecutar la API

Con PostgreSQL activo:

```powershell
.\gradlew.bat bootRun
```

La API queda en `http://localhost:8080`; no tiene página de inicio (todo está bajo `/api`). Para
detenerla usa `Ctrl+C`, y para detener la base `docker compose -f compose.dev.yaml down` (conserva los
datos; con `-v` también borra el volumen). También puedes iniciar `EventPlatformApplication` desde
IntelliJ con el directorio de trabajo en esta carpeta, para que lea el `.env`.

### Modo rápido sin PostgreSQL

Para probar la API o el frontend sin Docker, se puede arrancar con el perfil `test` (H2 en memoria):

```powershell
.\gradlew.bat bootTestRun --args="--spring.profiles.active=test"
```

- Usuario `admin`, contraseña `test-admin-password` (valores de `src/test/resources/application-test.properties`).
- Los datos se pierden al detener la API.
- El esquema lo crea Hibernate, no Flyway, así que no reemplaza las pruebas con PostgreSQL.

## Autenticación

Todos los endpoints de `/api/**` requieren un token JWT, excepto `POST /api/auth/login`.

| Método | Ruta | Función | Respuesta |
| --- | --- | --- | --- |
| `POST` | `/api/auth/login` | Inicia sesión con `{"username","password"}` | `200` con `token`, `tokenType`, `expiresAt`, `username`; `401` si las credenciales no son válidas; `400` si faltan |
| `GET` | `/api/auth/me` | Devuelve el usuario del token | `200` con `{"username":"admin"}`; `401` sin token |

- Hay una única cuenta de administrador, definida con `ADMIN_USERNAME` y `ADMIN_PASSWORD`. La
  contraseña se guarda en memoria cifrada con BCrypt al arrancar.
- El token es un JWT HS256 con `iss=event-platform`, `sub=<usuario>`, `iat` y `exp`; vence según
  `JWT_EXPIRATION`. `expiresAt` se devuelve en UTC (`...Z`).
- La API es *stateless*: no hay sesión ni cookies (CSRF desactivado). El logout del frontend solo
  borra el token del navegador; el token sigue siendo válido hasta que vence.
- Sin token responde `401` con `Authentication is required`; con un token inválido o vencido,
  `Invalid or expired token`. Las credenciales incorrectas siempre responden `Invalid username or
  password`, sin revelar si el usuario existe. Los `401` incluyen `WWW-Authenticate: Bearer`.

### CORS

Solo las rutas `/api/**` aceptan peticiones de navegador desde los orígenes de `CORS_ALLOWED_ORIGINS`,
con los métodos `GET, POST, PUT, PATCH, DELETE` y las cabeceras `Content-Type` y `Authorization`.
Otro origen, método o cabecera recibe `403`. Los `401` también llevan las cabeceras CORS, para que el
frontend pueda leer el error.

## Usar la API desde PowerShell

> **Importante:** en Windows PowerShell 5.1 no uses `curl.exe -d '{"name":"Ada"}'`. PowerShell 5.1
> elimina las comillas dobles al pasar argumentos a programas externos: la API recibe `{name:Ada}` y
> responde `400 Malformed JSON request`. Usa `Invoke-RestMethod` como en los ejemplos.

Pega esta función una vez por sesión. Envía el token, convierte el cuerpo a JSON y, cuando la API
responde un error, muestra su JSON (`status`, `message`, `details`) en lugar de una excepción genérica:

```powershell
$api = 'http://localhost:8080'
$headers = @{}
function Invoke-Api([string]$Method, [string]$Path, $Body) {
  $request = @{ Method = $Method; Uri = "$api$Path"; Headers = $headers }
  if ($null -ne $Body) { $request.ContentType = 'application/json'; $request.Body = $Body | ConvertTo-Json }
  try { Invoke-RestMethod @request }
  catch {
    if ($_.ErrorDetails.Message) { return $_.ErrorDetails.Message | ConvertFrom-Json }
    if (-not $_.Exception.Response) { throw }
    $stream = $_.Exception.Response.GetResponseStream(); $stream.Position = 0
    [System.IO.StreamReader]::new($stream).ReadToEnd() | ConvertFrom-Json
  }
}

# Iniciar sesión y guardar el token para las siguientes llamadas
$login = Invoke-Api POST /api/auth/login @{ username = 'admin'; password = (Read-Host 'Contraseña del administrador') }
$headers.Authorization = "Bearer $($login.token)"
```

Ejemplo completo (el caso del enunciado con un evento de 2 cupos):

```powershell
$ada   = Invoke-Api POST /api/users @{ name = 'Ada Lovelace'; email = 'ada@example.com';   document = '12345' }
$alan  = Invoke-Api POST /api/users @{ name = 'Alan Turing';  email = 'alan@example.com';  document = '67890' }
$grace = Invoke-Api POST /api/users @{ name = 'Grace Hopper'; email = 'grace@example.com'; document = '24680' }

$event = Invoke-Api POST /api/events @{
  name = 'Java Conf'; description = 'Talks and workshops'
  startDate = '2030-05-10T18:00:00'; endDate = '2030-05-10T21:00:00'; capacity = 2
}

Invoke-Api POST "/api/events/$($event.id)/registrations" @{ userId = $ada.id }    # CONFIRMED
Invoke-Api POST "/api/events/$($event.id)/registrations" @{ userId = $alan.id }   # CONFIRMED
Invoke-Api POST "/api/events/$($event.id)/registrations" @{ userId = $grace.id }  # WAITING_LIST

Invoke-Api GET "/api/events/$($event.id)/availability"   # registeredCount 2, availableSeats 0, waitlistCount 1
Invoke-Api GET "/api/events/$($event.id)/waitlist"       # Grace en la posición 1

Invoke-Api DELETE "/api/events/$($event.id)/registrations/$($ada.id)"   # cancelled: Ada, promoted: Grace

Invoke-Api GET "/api/events/$($event.id)/registrations?status=CONFIRMED" | Format-Table userName, status
Invoke-Api GET "/api/users/$($grace.id)/events"
```

Este flujo se verificó contra la API real: Grace queda en `WAITING_LIST` y pasa automáticamente a
`CONFIRMED` cuando Ada cancela, mientras `registeredCount` se mantiene en 2.

## Endpoints

### Correspondencia con los endpoints mínimos del enunciado

| Enunciado | Endpoint de la API |
| --- | --- |
| `POST /usuarios` | `POST /api/users` |
| `POST /eventos` | `POST /api/events` |
| `GET /eventos` | `GET /api/events` |
| `GET /eventos/{id}` | `GET /api/events/{id}` |
| `PUT /eventos/{id}` | `PUT /api/events/{id}` |
| `POST /eventos/{id}/inscripciones` | `POST /api/events/{eventId}/registrations` |
| `DELETE /eventos/{eventoId}/inscripciones/{usuarioId}` | `DELETE /api/events/{eventId}/registrations/{userId}` |
| `GET /eventos/{id}/inscritos` | `GET /api/events/{eventId}/registrations?status=CONFIRMED` |
| `GET /eventos/{id}/disponibilidad` | `GET /api/events/{id}/availability` |
| `GET /usuarios/{id}/eventos` | `GET /api/users/{userId}/events` |

Las rutas y los mensajes están en inglés, por convención del proyecto.

### Usuarios

| Método | Ruta | Cuerpo | Función | Respuesta correcta |
| --- | --- | --- | --- | --- |
| `POST` | `/api/users` | `{"name","email","document"}` | Crear un usuario (`ACTIVE`) | `201` + `Location` |
| `GET` | `/api/users` | — | Listar usuarios ordenados por ID | `200` |
| `GET` | `/api/users/{id}` | — | Consultar un usuario | `200` |
| `PATCH` | `/api/users/{id}` | Cualquiera de `name`, `email`, `document` | Actualizar solo los campos enviados | `200` |
| `PATCH` | `/api/users/{id}/status` | `{"status":"ACTIVE"\|"INACTIVE"}` | Cambiar el estado | `200` |
| `GET` | `/api/users/{id}/events` | `?status=` opcional | Eventos del usuario ordenados por fecha de inicio | `200` |

Respuesta de usuario:

```json
{ "id": 1, "name": "Ada Lovelace", "email": "ada@example.com", "document": "12345", "status": "ACTIVE" }
```

Respuesta de `GET /api/users/{id}/events`:

```json
[
  {
    "registrationId": 3, "eventId": 1, "eventName": "Java Conf",
    "startDate": "2030-05-10T18:00:00", "endDate": "2030-05-10T21:00:00", "eventStatus": "SCHEDULED",
    "registrationDate": "2026-10-16T14:01:58.732764", "registrationStatus": "CONFIRMED"
  }
]
```

### Eventos

| Método | Ruta | Cuerpo | Función | Respuesta correcta |
| --- | --- | --- | --- | --- |
| `POST` | `/api/events` | `{"name","description","startDate","endDate","capacity"}` | Crear un evento (`SCHEDULED`) | `201` + `Location` |
| `GET` | `/api/events` | — | Listar eventos ordenados por ID | `200` |
| `GET` | `/api/events/{id}` | — | Consultar un evento | `200` |
| `PUT` | `/api/events/{id}` | Igual que `POST` (todos los campos) | Reemplazar los datos editables | `200` |
| `PATCH` | `/api/events/{id}/status` | `{"status":"ACTIVE"}` | Cambiar el estado según el ciclo de vida | `200` |
| `GET` | `/api/events/{id}/availability` | — | Capacidad, inscritos, cupos libres y lista de espera | `200` |

- `name` es obligatorio (máx. 255), `description` es opcional (máx. 1000; vacía se guarda como `null`),
  `startDate` y `endDate` son obligatorias con `endDate > startDate`, y `capacity > 0`.
- Las fechas son `LocalDateTime` sin zona horaria (`"2030-05-10T18:00:00"`) y se comparan con la hora
  del servidor.
- Se pueden crear eventos con fechas pasadas; simplemente no aceptan inscripciones.

Respuestas de evento y de disponibilidad:

```json
{ "id": 1, "name": "Java Conf", "description": "Talks and workshops", "startDate": "2030-05-10T18:00:00",
  "endDate": "2030-05-10T21:00:00", "capacity": 2, "registeredCount": 2, "status": "SCHEDULED" }
```

```json
{ "eventId": 1, "capacity": 2, "registeredCount": 2, "availableSeats": 0, "waitlistCount": 1,
  "status": "SCHEDULED", "acceptingRegistrations": true }
```

### Inscripciones y lista de espera

| Método | Ruta | Cuerpo | Función | Respuesta correcta |
| --- | --- | --- | --- | --- |
| `POST` | `/api/events/{eventId}/registrations` | `{"userId": 1}` | Inscribir; queda `CONFIRMED` o `WAITING_LIST` | `201` + `Location` |
| `GET` | `/api/events/{eventId}/registrations` | `?status=` opcional | Inscripciones del evento, de la más antigua a la más reciente | `200` |
| `GET` | `/api/events/{eventId}/registrations/{userId}` | — | Inscripción de un usuario en el evento (cualquier estado) | `200` |
| `DELETE` | `/api/events/{eventId}/registrations/{userId}` | — | Cancelar; devuelve la cancelada y la promovida | `200` |
| `GET` | `/api/events/{eventId}/waitlist` | — | Lista de espera numerada en orden de promoción | `200` |

- Sin `status`, `GET .../registrations` devuelve todas, incluidas las `CANCELLED`. Para los inscritos
  confirmados usa `?status=CONFIRMED`; los valores válidos son `CONFIRMED`, `WAITING_LIST` y `CANCELLED`.
- El `Location` del `POST` apunta a `/api/events/{eventId}/registrations/{userId}`.

Respuesta de inscripción:

```json
{ "id": 3, "eventId": 1, "eventName": "Java Conf", "userId": 3, "userName": "Grace Hopper",
  "userEmail": "grace@example.com", "registrationDate": "2026-10-16T14:01:58.732764", "status": "WAITING_LIST" }
```

Respuesta de la cancelación (`promoted` es `null` si se cancela una inscripción en espera o nadie esperaba):

```json
{
  "cancelled": { "id": 1, "userId": 1, "userName": "Ada Lovelace", "status": "CANCELLED", "...": "..." },
  "promoted":  { "id": 3, "userId": 3, "userName": "Grace Hopper", "status": "CONFIRMED", "...": "..." }
}
```

Respuesta de la lista de espera:

```json
[ { "position": 1, "registrationId": 3, "userId": 3, "userName": "Grace Hopper",
    "userEmail": "grace@example.com", "registrationDate": "2026-10-16T14:01:58.732764" } ]
```

## Errores

Todos los errores usan el mismo formato (`GlobalExceptionHandler` y `ApiAuthenticationEntryPoint`):

```json
{
  "timestamp": "2026-10-16T19:02:36.998Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/users",
  "details": { "email": "Email must be valid" }
}
```

| Código | Cuándo | Ejemplos de `message` |
| --- | --- | --- |
| `400` | JSON mal formado, validación de campos (`details` trae un mensaje por campo), parámetro con tipo inválido | `Malformed JSON request`, `Request validation failed`, `Invalid value for parameter 'status'`, `End date must be after start date` |
| `401` | Sin token, token inválido o vencido, credenciales incorrectas | `Authentication is required`, `Invalid or expired token`, `Invalid username or password` |
| `403` | Petición de navegador desde un origen no permitido (CORS) | — |
| `404` | Usuario, evento o inscripción inexistente | `User with id 9 was not found`, `Event with id 9 was not found`, `Registration of user 2 for event 1 was not found` |
| `409` | Dato duplicado o regla de negocio violada | Ver la tabla siguiente |

| Situación | `message` del `409` |
| --- | --- |
| Correo o documento repetido | `Email is already registered`, `Document is already registered` |
| Inscripción repetida | `User is already registered for this event` |
| Usuario inactivo | `Inactive users cannot register for events` |
| Evento cancelado o finalizado | `Event with status CANCELLED does not accept registrations` |
| Evento ya iniciado | `Registrations are closed because the event has already started` |
| Cancelar dos veces | `Registration is already cancelled` |
| Cancelar en un evento cerrado | `Registrations of an event with status FINISHED cannot be cancelled` |
| Capacidad menor que los inscritos | `Capacity cannot be lower than the registered count (90)` |
| Editar un evento cerrado | `Event with status FINISHED cannot be modified` |
| Transición de estado inválida | `Cannot change event status from SCHEDULED to FINISHED` |

## Pruebas automatizadas

No se necesita PostgreSQL ni la API en ejecución: las pruebas usan Mockito o H2 en memoria.

| Tipo | Clases | Qué verifican |
| --- | --- | --- |
| Unitarias de servicio (Mockito) | `UserServiceTests`, `EventServiceTests`, `RegistrationServiceTests`, `WaitlistServiceTests`, `AuthServiceTests` | Reglas de negocio con repositorios simulados y `Clock` fijo |
| Unitarias de configuración y modelo | `SecurityConfigTests`, `SecurityPropertiesTests`, `ClockConfigTests`, `EventTests` | Beans de seguridad, validación de variables, reloj, `Event.validateForPublication` |
| Integración (MockMvc + H2, perfil `test`) | `UserControllerIntegrationTests`, `EventControllerIntegrationTests`, `RegistrationControllerIntegrationTests`, `AuthControllerIntegrationTests`, `CorsIntegrationTests`, `EventPlatformApplicationTests` | Endpoints completos, códigos HTTP, formato de errores, seguridad y CORS; cada prueba revierte su transacción |
| Concurrencia (H2, transacciones reales) | `RegistrationConcurrencyIntegrationTests` | Estrés de 1000 usuarios por 100 cupos y cancelaciones simultáneas |
| Aceptación (Cucumber, perfil `acceptance`) | `src/test/resources/features/*.feature` y `src/test/java/steps` | Escenarios Gherkin en inglés de las reglas de negocio |
| PostgreSQL real (Testcontainers) | `PostgresIntegrationTests`, `PostgresRegistrationConcurrencyTests` | Migración Flyway, restricciones, arranque HTTP y estrés con bloqueos reales |

### Comandos

```powershell
.\gradlew.bat test             # JUnit + Cucumber, genera también el reporte JaCoCo
.\gradlew.bat acceptanceTest   # solo los escenarios Cucumber
.\gradlew.bat postgresTest     # requiere Docker en ejecución
.\gradlew.bat pitest           # pruebas de mutación

# Una sola clase
.\gradlew.bat test --tests "com.eventplatform.api.service.RegistrationServiceTests"
```

| Reporte | Ubicación |
| --- | --- |
| Pruebas JUnit | `build/reports/tests/test/index.html` |
| Cucumber | `build/reports/cucumber/user-registration.html` (incluye todos los features) |
| JaCoCo | `build/reports/jacoco/test/html/index.html` y `build/reports/jacoco/test/jacocoTestReport.xml` |
| PIT | `build/reports/pitest/index.html` |
| PostgreSQL | `build/reports/tests/postgresTest/index.html` |

### Escenarios de aceptación (Gherkin)

| Feature | Escenarios |
| --- | --- |
| `user_registration.feature` | Registro exitoso de un usuario; rechazo de un correo duplicado |
| `event_management.feature` | Creación; capacidad no positiva; transiciones válidas e inválidas; disponibilidad de eventos abiertos, cerrados e iniciados; edición de eventos cerrados |
| `event_registration.feature` | Inscripción confirmada; lista de espera; **ejemplo del enunciado** (100 cupos, el usuario 101 espera y queda confirmado cuando alguien cancela); orden de la lista de espera; liberar cupo; salir de la lista de espera; reinscripción; duplicados; usuario inactivo; evento cancelado, finalizado o iniciado |
| `authentication.feature` | Login válido; credenciales inválidas; endpoints sin token o con token inválido |

`runners/RunCucumberTest` ejecuta los features con JUnit Platform. Cada escenario se ejecuta dentro de
una transacción que se revierte al final, autenticado como administrador (`SecurityHooks`), salvo los
etiquetados `@authentication`, que usan el login real.

### Perfiles de prueba

| Perfil | Base | Usado por |
| --- | --- | --- |
| `test` | H2 `jdbc:h2:mem:user-tests` (modo PostgreSQL) | Integración, concurrencia y `bootTestRun` |
| `acceptance` | H2 `jdbc:h2:mem:user-acceptance` | Cucumber |

En ambos perfiles Flyway está desactivado y Hibernate crea el esquema (`create-drop`), así que no
validan la migración ni las particularidades de PostgreSQL; para eso existe `postgresTest`. Las
credenciales y la clave JWT de prueba están en `src/test/resources/application-*.properties` y solo
sirven para las pruebas.

### Pruebas con PostgreSQL aislado

`.\gradlew.bat postgresTest` necesita Docker en ejecución. Testcontainers crea un PostgreSQL 16
desechable con un puerto aleatorio y lo elimina al terminar: no usa la base de desarrollo, el `.env` ni
el volumen de Compose. Ejecuta la migración Flyway real, valida las entidades, comprueba las tablas, el
índice parcial y las restricciones de eventos, arranca el servidor HTTP con login y creación de usuario,
y repite el escenario de estrés con 64 hilos. Si Docker no está disponible la tarea falla; no se omite
en silencio. Está separada de `build`, `test`, `acceptanceTest` y PIT, y su cobertura no se suma a JaCoCo.

## Calidad: JaCoCo, PIT, SonarQube y pipeline

Mediciones del 2026-10-16 en la rama `feature/event-registrations`:

| Métrica | Valor |
| --- | --- |
| Pruebas en `test` | 269 ejecuciones, 0 fallos |
| Escenarios en `acceptanceTest` | 37, 0 fallos |
| Cobertura de líneas (JaCoCo) | 99.1 % (573/578) |
| Cobertura de ramas (JaCoCo) | 98.4 % (120/122) |
| Mutantes eliminados (PIT) | 94 % (217/230) |

### JaCoCo

`.\gradlew.bat test` genera automáticamente el reporte de cobertura combinada de JUnit y Cucumber,
sin exclusiones. La tarea `acceptanceTest` no suma cobertura a ese reporte.

### PIT

`.\gradlew.bat pitest` muta las clases de `com.eventplatform.api.*` y ejecuta las pruebas de ese
paquete con 4 hilos. El reporte HTML queda en `build/reports/pitest/index.html`.

### SonarQube

El token se toma de `SONAR_TOKEN` (no se guarda en `build.gradle`) y el servidor de `SONAR_HOST_URL`
(por defecto `http://localhost:9000`). La clave del proyecto es `event-ticket-platform`.

```powershell
$sonarSecret = Read-Host 'Token de SonarQube' -AsSecureString
$env:SONAR_TOKEN = [System.Net.NetworkCredential]::new('', $sonarSecret).Password
.\gradlew.bat test sonar
```

Revoca cualquier token que haya estado escrito en el código: quitarlo del archivo no lo borra del
historial de Git. El informe de calidad elaborado con SonarQube está en la rama `feature/testSonar`
(`event-platform/informe.md`).

### Pipeline de Azure DevOps

Selecciona `event-platform/azure-pipelines.yml` al crear el pipeline. Se ejecuta con cambios en
`develop`, `main`, `feature/testSonar` y `feature/testAprobation`, en un agente Windows del pool
`Nuevos agentes` que debe tener JDK 21, `JAVA_HOME` configurado y acceso a las descargas de Gradle,
Maven y .NET.

Pasos: verificar Java y Gradle → `clean build jacocoTestReport` → `acceptanceTest` → publicar
resultados JUnit/Cucumber → publicar cobertura → `pitest` → SonarQube (opcional) → OWASP ZAP
Baseline (activado por defecto) → publicar el artefacto `quality-reports` con todos los reportes.

SonarQube es opcional: crea `SONAR_TOKEN` como variable secreta y `SONAR_HOST_URL` como variable, y
marca `runSonar` al ejecutar el pipeline manualmente. `localhost` apunta al agente, no a tu equipo.
El pipeline no ejecuta `postgresTest`.

### Análisis de seguridad con OWASP ZAP

Los pasos `ZAP 01` a `ZAP 07` hacen un análisis pasivo (DAST) de la API en ejecución:

1. Levantan un PostgreSQL desechable en Docker (contraseña aleatoria, puerto aleatorio en `127.0.0.1`).
2. Arrancan el bootJar con el perfil `zap` (`application-zap.properties`) en el puerto `8081`, con un
   administrador `zap_admin` y un `JWT_SECRET` aleatorios en cada ejecución (ocultos en el log).
3. Usan `POST /api/auth/login` como chequeo de salud (es el único endpoint público), crean un evento de
   prueba y pasan el token a ZAP con `ZAP_AUTH_HEADER_VALUE`, limitado al host `host.docker.internal`.
4. Ejecutan `zap-baseline.py` contra `http://host.docker.internal:8081/api/events` (spider de 2 minutos;
   las advertencias no fallan el pipeline por `-I`).
5. Siempre detienen la API y borran el contenedor, y después publican el artefacto
   `reporte-seguridad-zap` (con la API corriendo, sus logs siguen abiertos y no se pueden subir).

El artefacto contiene `reporte-zap.html`, `reporte-zap.json`, `ejecucion.txt` (build, commit, fecha y
códigos HTTP que recibió ZAP), `acceso-http.log` (cada petición y su código) y los logs de la API.
Si ZAP solo recibe `401`, el paso 05 deja una advertencia: el token no llegó y el análisis no es válido.

Requisitos del agente: Docker Desktop abierto con contenedores Linux y el agente ejecutándose con un
usuario que tenga acceso a Docker. Si Windows pide permiso de firewall para Java en el puerto `8081`,
permite redes privadas. Desmarca `runZap` para ejecutar el pipeline sin Docker. En el paso 04 puede
aparecer `The STDIO streams did not close within 10 seconds`: es normal, la API sigue en segundo plano.

Alcance: como la API solo responde JSON, el spider encuentra pocas rutas (`/api/events`, `/`, `/api`,
`/robots.txt`, `/sitemap.xml`). En la prueba local (ZAP 2.17) el resultado fue 0 alertas y 61 reglas
`PASS`: Spring Security ya envía `X-Content-Type-Options`, `X-Frame-Options` y `Cache-Control`, y la
regla de CSP solo aplica a respuestas HTML.

## Construir la aplicación

```powershell
.\gradlew.bat clean build
java -jar build\libs\event-platform-0.0.1-SNAPSHOT.jar   # con PostgreSQL y el .env disponibles
```

`build` compila y ejecuta las pruebas de `test`. Para construir la imagen Docker y compartirla con el
grupo, consulta [DOCKER.md](DOCKER.md).

## Estructura del código

```text
event-platform/
|-- build.gradle, settings.gradle, gradlew(.bat)   Build con Gradle Wrapper (JaCoCo, PIT, Sonar, tareas extra)
|-- compose.yaml / compose.dev.yaml / Dockerfile    Docker (ver DOCKER.md)
|-- .env.example / .env.docker.example              Plantillas de configuración
|-- azure-pipelines.yml                             Pipeline de Azure DevOps
|-- CLAUDE.md                                       Convenciones del proyecto para el asistente de código
`-- src/
    |-- main/java/com/eventplatform/api/
    |   |-- config/       ClockConfig, CorsConfig, SecurityConfig, SecurityProperties
    |   |-- controller/   AuthController, UserController, EventController, RegistrationController
    |   |-- dto/          auth/, user/, event/, registration/ (records de entrada y salida)
    |   |-- exception/    GlobalExceptionHandler, ApiError, ApiAuthenticationEntryPoint y excepciones
    |   |-- model/        User, Event, Registration y enums/ (UserStatus, EventStatus, RegistrationStatus)
    |   |-- repository/   UserRepository, EventRepository (bloqueo), RegistrationRepository
    |   `-- service/      UserService, EventService, RegistrationService, WaitlistService, AuthService
    |-- main/resources/
    |   |-- application.properties
    |   `-- db/migration/V1__create_initial_schema.sql
    |-- test/java/
    |   |-- com/eventplatform/api/   Pruebas unitarias, de integración y de concurrencia
    |   |-- steps/                   Pasos de Cucumber y configuración Spring/seguridad
    |   `-- runners/                 RunCucumberTest
    |-- test/resources/
    |   |-- features/                Escenarios Gherkin
    |   `-- application-test.properties, application-acceptance.properties, junit-platform.properties
    `-- postgresTest/java/           Pruebas con PostgreSQL real (Testcontainers)
```

## Flujo de trabajo con Git

- Remotos: `origin` (GitHub `Kexxx04/Event-Tikect-Platform`) y `azure` (Azure DevOps).
- Ramas `feature/<kebab-case>` creadas desde `develop`, que se integran a `develop` por pull request.
  `main` es la rama por defecto en GitHub; al 2026-10-16 está 25 commits detrás de `develop` (su último
  merge es el PR #8, de Docker), así que no incluye eventos, inscripciones, login ni frontend.
- Commits pequeños con Conventional Commits en inglés: `<type>(<scope>): <resumen en minúsculas>`.
  Tipos: `feat`, `fix`, `test`, `refactor`, `docs`, `chore`, `ci`. Alcances: `users`, `events`,
  `registrations`, `waitlist`, `concurrency`, `api-docs`, `readme` (y `api`, `frontend` en el historial).
- Cada tarea incluye pruebas unitarias, de integración y, si toca reglas de negocio, escenarios Cucumber.
  `.\gradlew.bat test` y `acceptanceTest` deben pasar antes de abrir el pull request.

## Problemas conocidos y limitaciones

| # | Problema | Impacto | Solución propuesta |
| --- | --- | --- | --- |
| 1 | El `HEALTHCHECK` del `Dockerfile` consulta `GET /api/users` sin token; desde que existe el login la API responde `401` y `curl --fail` falla (verificado: código de salida 22) | En Docker la API nunca queda `healthy` y `docker compose up --wait` falla o espera hasta el timeout, aunque la API funcione | Aceptar el `401` como señal de vida o exponer un endpoint de salud público (por ejemplo Spring Boot Actuator `/actuator/health`) |
| 2 | La imagen Docker no define zona horaria: la JVM usa UTC, mientras que las fechas de los eventos se escriben en hora local (Colombia, UTC-5) | En Docker las inscripciones se cerrarían 5 horas antes del inicio real | Definir `TZ=America/Bogota` (o `-Duser.timezone`) en `compose.yaml` |
| 3 | La promoción de la lista de espera no revisa el estado del usuario | Un usuario inactivado mientras esperaba puede quedar `CONFIRMED` | Decidir si se debe saltar a los usuarios `INACTIVE` |
| 4 | El estado del evento no cambia con el tiempo | Un evento pasado sigue `SCHEDULED` hasta que se cambie a mano (igual no acepta inscripciones) | Tarea programada o cálculo del estado, si se requiere |
| 5 | Cancelar un evento no cancela sus inscripciones | Las inscripciones de un evento `CANCELLED` quedan como estaban | Cancelarlas en cascada, si se requiere |
| 6 | Todo `DataIntegrityViolationException` responde `Email or document is already registered` | Mensaje engañoso si la BD rechaza otro dato | Mensaje genérico o por restricción |
| 7 | Las pruebas de integración y Cucumber usan eventos del 2030-05-10 con el reloj real | Empezarán a fallar en esa fecha | Usar fechas relativas o un `Clock` fijo en los perfiles de prueba |
| 8 | `Event.validateForPublication` no lo usa ningún servicio; imprime con `System.out` y usa `LocalDateTime.now()` sin `Clock` | Deuda técnica (código heredado del ejercicio de SonarQube) | Eliminarlo o integrarlo a `EventService` |
| 9 | Un solo administrador, sin revocación de tokens ni paginación en los listados | Suficiente para el alcance del proyecto | Usuarios administradores en BD, *refresh tokens* y `Pageable` si crece |

## Próximos pasos sugeridos

1. Corregir el healthcheck de Docker y la zona horaria del contenedor (problemas 1 y 2).
2. Integrar `develop` en `main` antes de la entrega.
3. Ejecutar `postgresTest` en un equipo o agente con Docker.
4. Añadir documentación OpenAPI/Swagger.
5. Ejecutar el escenario de estrés contra la API desplegada con una herramienta de carga (JMeter, k6 o Gatling).
