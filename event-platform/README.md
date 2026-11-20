# Event Platform API

API REST para administrar eventos e inscripciones garantizando que **nunca se supere el aforo**.
Está construida con Java 21, Spring Boot 4, Spring Web MVC, Spring Data JPA, Spring Security (JWT),
PostgreSQL 16 y Flyway. El repositorio también incluye un frontend en React que consume la API.

```text
event-platform (1)/          raíz del repositorio Git
|-- event-platform/          API Spring Boot (este README)
|-- frontend/                Interfaz web Vite + React + TypeScript (ver frontend/README.md)
|-- e2e-tests/               Pruebas end-to-end de la interfaz con Serenity BDD (ver e2e-tests/README.md)
`-- performance-tests/       Pruebas de rendimiento con JMeter en Docker (ver performance-tests/README.md)
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
15. [Estrategia de pruebas sin base de datos](#estrategia-de-pruebas-sin-base-de-datos)
16. [Pruebas de rendimiento (JMeter)](#pruebas-de-rendimiento-jmeter)
17. [Calidad: JaCoCo, PIT, SonarQube y pipeline](#calidad-jacoco-pit-sonarqube-y-pipeline)
18. [Construir la aplicación](#construir-la-aplicación)
19. [Estructura del código](#estructura-del-código)
20. [Flujo de trabajo con Git](#flujo-de-trabajo-con-git)
21. [Problemas conocidos y limitaciones](#problemas-conocidos-y-limitaciones)
22. [Próximos pasos sugeridos](#próximos-pasos-sugeridos)

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
| Escenario de estrés (100 cupos, 1000 usuarios) | Probado sin base de datos | `RegistrationConcurrencyIntegrationTests` |

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
  `Clock.systemDefaultZone()`; en las pruebas unitarias se usa un `Clock.fixed(...)`, y las de
  integración y Cucumber crean eventos con fechas relativas al día actual, así que no vencen.
- `spring.jpa.open-in-view=false`: las entidades no se cargan fuera de la transacción del servicio.
- Hibernate valida el esquema (`ddl-auto=validate`); el esquema lo crea Flyway.
- Ninguna prueba usa base de datos: el perfil `inmemory` (source set `src/inmemory`, fuera del JAR de
  producción) reemplaza `repository/` y el gestor de transacciones por fakes en memoria. Ver
  [Estrategia de pruebas sin base de datos](#estrategia-de-pruebas-sin-base-de-datos).

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

Escenario de estrés del enunciado, automatizado y sin base de datos:

| Prueba | Repositorios | Escenario | Resultado esperado |
| --- | --- | --- | --- |
| `RegistrationConcurrencyIntegrationTests` (tarea `test`) | Fakes en memoria (perfil `inmemory`) | 1000 usuarios, 32 hilos, 100 cupos | 100 `CONFIRMED`, 900 `WAITING_LIST`, `registeredCount = 100` |
| `RegistrationConcurrencyIntegrationTests` (tarea `test`) | Fakes en memoria (perfil `inmemory`) | 20 cancelaciones simultáneas | 20 promociones de usuarios distintos, sigue en 100 |

Cada inscripción corre en su propio hilo y en su propia transacción. `InMemoryEventRepository`
emula el `SELECT ... FOR UPDATE` de `findByIdForUpdate` con un `ReentrantLock` por evento, que la
transacción toma una sola vez y libera al terminar (commit o rollback).

Como control (2026-11-20), al quitar temporalmente ese bloqueo del fake, las dos pruebas fallaron en
las 7 ejecuciones: la de inscripciones con 107 a 111 `CONFIRMED` sobre 100 cupos, y la de
cancelaciones con `DataIntegrityViolationException` por `chk_events_registered_count`, la misma
restricción que rechaza PostgreSQL. Con el bloqueo pasan siempre. Es decir, la prueba sí detecta los
sobrecupos. El bloqueo real de PostgreSQL queda para un ambiente staging (ver la
[limitación](#limitaciones-del-fake)).

## Tecnologías

| Área | Herramienta |
| --- | --- |
| Lenguaje y build | Java 21 (toolchain), Gradle Wrapper 9.7.1 |
| Framework | Spring Boot 4.1.1: Web MVC, Data JPA, Validation, Security, OAuth2 Resource Server (JWT), Actuator (solo `/actuator/health`) |
| Base de datos | PostgreSQL 16 y Flyway en producción; las pruebas no usan base de datos (fakes en memoria, perfil `inmemory`) |
| Pruebas | JUnit 5, Mockito, MockMvc, Spring Security Test, Cucumber 7.34 (Gherkin), repositorios fake en memoria |
| Rendimiento | Apache JMeter 5.6.3 (non-GUI) en Docker, contra la API con el perfil `inmemory` limitada a 2 CPU y 1 GiB |
| Calidad | JaCoCo 0.8.14, PIT 1.19, SonarQube (plugin 7.3), OWASP ZAP API Scan con el contrato de springdoc 3.2 |
| Contenedores | Docker, Docker Compose |
| CI | Azure DevOps (`azure-pipelines.yml`) |
| Frontend | Vite, React 19, TypeScript, React Router 7 (ver `../frontend/README.md`) |

## Requisitos y modos de ejecución

| Objetivo | Qué se necesita | Archivo | Comando principal |
| --- | --- | --- | --- |
| Desarrollar con PostgreSQL | JDK 21 y Docker | `compose.dev.yaml` | `docker compose -f compose.dev.yaml up -d` y `.\gradlew.bat bootRun` |
| Probar rápido sin PostgreSQL | JDK 21 | — | `.\gradlew.bat bootTestRun --args="--spring.profiles.active=test"` |
| API sin base de datos con tus credenciales | JDK 21 y `ADMIN_PASSWORD`, `JWT_SECRET` | `.env` | `.\gradlew.bat bootRunInMemory` |
| JAR sin base de datos (E2E, ZAP, JMeter) | JDK 21 y `ADMIN_PASSWORD`, `JWT_SECRET` | — | `.\gradlew.bat inMemoryBootJar` |
| Ejecutar todo con Docker (API + BD) | Docker | `compose.yaml` | `docker compose up -d --wait` (ver [DOCKER.md](DOCKER.md)) |
| Prueba de rendimiento (JMeter) | JDK 21 y Docker | `../performance-tests/compose.perf.yaml` | `..\performance-tests\run-perf.ps1 -Scenario normal` (ver [Pruebas de rendimiento](#pruebas-de-rendimiento-jmeter)) |
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

Para probar la API o el frontend sin Docker ni base de datos, se puede arrancar con el perfil `test`,
que activa el perfil `inmemory` (repositorios en memoria):

```powershell
.\gradlew.bat bootTestRun --args="--spring.profiles.active=test"
```

- Usuario `admin`, contraseña `test-admin-password` (valores de `src/test/resources/application-test.properties`).
- Los datos se pierden al detener la API.
- No hay esquema ni Flyway: los repositorios en memoria emulan las restricciones de la V1, pero no
  reemplazan una prueba con PostgreSQL (ver [Estrategia de pruebas sin base de datos](#estrategia-de-pruebas-sin-base-de-datos)).

Otras formas de arrancar la API sin base de datos. No necesitan las variables `DB_*`, pero sí
`ADMIN_PASSWORD` y `JWT_SECRET` (del `.env` o como variables de entorno):

```powershell
.\gradlew.bat bootRunInMemory      # perfil inmemory, con el código fuente
.\gradlew.bat inMemoryBootJar      # genera build\libs\event-platform-inmemory.jar
java -jar build\libs\event-platform-inmemory.jar --spring.profiles.active=inmemory       # E2E, JMeter
java -jar build\libs\event-platform-inmemory.jar --spring.profiles.active=inmemory,zap   # como el escaneo ZAP
```

`event-platform-inmemory.jar` es un JAR aparte (código de `main` + `inmemory`). El bootJar de
producción (`event-platform-0.0.1-SNAPSHOT.jar`) no contiene el paquete `inmemory` y sigue usando
PostgreSQL.

## Autenticación

Todos los endpoints requieren un token JWT, excepto `POST /api/auth/login` y `GET /actuator/health`.

| Método | Ruta | Función | Respuesta |
| --- | --- | --- | --- |
| `POST` | `/api/auth/login` | Inicia sesión con `{"username","password"}` | `200` con `token`, `tokenType`, `expiresAt`, `username`; `401` si las credenciales no son válidas; `400` si faltan |
| `GET` | `/api/auth/me` | Devuelve el usuario del token | `200` con `{"username":"admin"}`; `401` sin token |
| `GET` | `/actuator/health` | Chequeo de salud (Spring Boot Actuator) para el `HEALTHCHECK` de Docker | `200` con `{"status":"UP"}`; `503` con `{"status":"DOWN"}` si la base de datos no responde |

- `/actuator/health` no muestra detalles ni componentes. Es el único endpoint de Actuator expuesto: el resto
  de `/actuator/**` responde `401` sin token y `404` con token.

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

No se necesita PostgreSQL, Docker ni la API en ejecución: **ninguna prueba usa base de datos** (ni H2,
ni PostgreSQL, ni Testcontainers). Las unitarias usan mocks y stubs; las demás, los fakes en memoria del
perfil `inmemory` (ver [Estrategia de pruebas sin base de datos](#estrategia-de-pruebas-sin-base-de-datos)).

| Tipo | Clases | Qué verifican |
| --- | --- | --- |
| Unitarias de servicio (Mockito) | `UserServiceTests`, `EventServiceTests`, `RegistrationServiceTests`, `WaitlistServiceTests`, `AuthServiceTests` | Reglas de negocio con repositorios simulados y `Clock` fijo |
| Unitarias de configuración y modelo | `SecurityConfigTests`, `SecurityPropertiesTests`, `ClockConfigTests`, `EventTests`, `JpaConstructorTests` | Beans de seguridad (la cadena de filtros, en un contexto mínimo nuevo en cada ejecución), validación de variables, reloj y valores iniciales de las entidades |
| Contrato del fake (sin Spring) | `InMemoryUserRepositoryTests`, `InMemoryEventRepositoryTests`, `InMemoryRegistrationRepositoryTests`, `InMemoryDatabaseTests`, `InMemoryTransactionManagerTests` | Restricciones de la V1, orden de cada consulta, índices, ids, `clear()`, bloqueo por evento, *dirty checking* y propagación de transacciones |
| Integración (MockMvc + fakes, perfil `test`) | `UserControllerIntegrationTests`, `EventControllerIntegrationTests`, `RegistrationControllerIntegrationTests`, `AuthControllerIntegrationTests`, `UserUniqueConstraintIntegrationTests`, `CorsIntegrationTests`, `SecurityHeadersIntegrationTests`, `OpenApiDocsIntegrationTests`, `ActuatorIntegrationTests`, `EventPlatformApplicationTests` | Endpoints completos, códigos HTTP, formato de errores, seguridad, CORS, OpenAPI y `/actuator/health`; cada prueba empieza con `InMemoryDatabase.clear()` |
| Concurrencia (fakes, transacciones reales en 32 hilos) | `RegistrationConcurrencyIntegrationTests` | Estrés de 1000 usuarios por 100 cupos y 20 cancelaciones simultáneas |
| Aceptación (Cucumber, perfil `acceptance`) | `src/test/resources/features/*.feature` y `src/test/java/steps` | Escenarios Gherkin en inglés de las reglas de negocio |

### Comandos

```powershell
.\gradlew.bat test             # JUnit + Cucumber, genera también el reporte JaCoCo
.\gradlew.bat acceptanceTest   # solo los escenarios Cucumber
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

### Escenarios de aceptación (Gherkin)

| Feature | Escenarios |
| --- | --- |
| `user_registration.feature` | Registro exitoso de un usuario; rechazo de un correo duplicado |
| `event_management.feature` | Creación; capacidad no positiva; transiciones válidas e inválidas; disponibilidad de eventos abiertos, cerrados e iniciados; edición de eventos cerrados |
| `event_registration.feature` | Inscripción confirmada; lista de espera; **ejemplo del enunciado** (100 cupos, el usuario 101 espera y queda confirmado cuando alguien cancela); orden de la lista de espera; liberar cupo; salir de la lista de espera; reinscripción; duplicados; usuario inactivo; evento cancelado, finalizado o iniciado |
| `authentication.feature` | Login válido; credenciales inválidas; endpoints sin token o con token inválido |

`runners/RunCucumberTest` ejecuta los features con JUnit Platform. Cada escenario empieza con las tablas
en memoria vacías (`DatabaseHooks` llama a `InMemoryDatabase.clear()`) y autenticado como administrador
(`SecurityHooks`), salvo los etiquetados `@authentication`, que usan el login real. Las fechas de los
eventos son relativas al día actual (`in 30 days at 18:00` en los features), así que no vencen.

### Perfiles de prueba

| Perfil | Qué activa | Usado por |
| --- | --- | --- |
| `test` | `inmemory` + credenciales de prueba | Integración, concurrencia y `bootTestRun` |
| `acceptance` | `inmemory` + credenciales de prueba | Cucumber |
| `inmemory` | Repositorios y gestor de transacciones en memoria; excluye la autoconfiguración de DataSource, JPA/Hibernate, repositorios JPA y Flyway | Lo activan `test` y `acceptance`; también `bootRunInMemory` y `event-platform-inmemory.jar` |

`test` y `acceptance` activan `inmemory` con grupos de perfiles (`spring.profiles.group.test=inmemory`)
declarados en `src/test/resources/config/application.properties`, porque Spring Boot no permite grupos
en los archivos de un perfil. Ningún perfil de prueba tiene propiedades de base de datos. Las
credenciales y la clave JWT de prueba están en `src/test/resources/application-*.properties` y solo
sirven para las pruebas.

## Estrategia de pruebas sin base de datos

Requisito del curso: ninguna prueba usa base de datos (ni H2, ni PostgreSQL, ni Testcontainers). La
aplicación de producción no cambia: sigue con PostgreSQL, Flyway y JPA, y el código de prueba no entra
en su JAR. En cada nivel se usa el doble de prueba más simple que alcanza:

| Nivel | Doble | Qué reemplaza | Dónde |
| --- | --- | --- | --- |
| Unitarias de servicio | **Mock** (Mockito) | Repositorios, `WaitlistService` y `AuthenticationManager`: la prueba programa respuestas y verifica las llamadas | `*ServiceTests` |
| Unitarias de servicio | **Stub** | El "ahora": `Clock.fixed(...)` devuelve siempre la misma hora | `EventServiceTests`, `RegistrationServiceTests`, `AuthServiceTests` |
| Contrato del fake | Ninguno: se prueba el fake mismo | — | `InMemory*Tests` |
| Integración, concurrencia y Cucumber | **Fake** | La base de datos completa: repositorios en memoria que se comportan como PostgreSQL, más un gestor de transacciones | Perfil `inmemory` (`src/inmemory/java/com/eventplatform/api/inmemory`) |
| E2E (Serenity), OWASP ZAP y JMeter | **Fake** | Lo mismo, dentro de la API en ejecución | `bootTestRun` o `event-platform-inmemory.jar` |

El source set `inmemory` compila contra `main`; `test`, `acceptanceTest` y `bootTestRun` lo tienen en
su classpath, y el bootJar de producción no. Todas sus clases llevan `@Profile("inmemory")`.

### Qué emula el fake

- **Tablas**: `InMemoryDatabase` tiene un `ConcurrentHashMap` por entidad y un `AtomicLong` por
  secuencia. `clear()` borra las filas y reinicia los ids en 1.
- **Ids `IDENTITY`**: `save` asigna el siguiente id cuando es `null`, con `ReflectionUtils` (sin setters
  públicos nuevos en las entidades).
- **Restricciones de la V1**, con `DataIntegrityViolationException` y el mismo mensaje de PostgreSQL:
  `NOT NULL`; email único (sin distinguir mayúsculas) y documento único; `(user_id, event_id)` único;
  llaves foráneas de `registrations` (al insertar y al borrar usuarios o eventos); `capacity > 0`;
  `end_date > start_date`; `0 <= registered_count <= capacity`. Se revisan en `save` y otra vez antes
  del commit, que es cuando Hibernate hace el *flush*, pero como Hibernate (*dirty checking*) solo para
  las entidades que la transacción cambió: leer una fila nunca toma el candado de escritura de su tabla.
- **Consultas**: los métodos de `JpaRepository` que usan los servicios y las pruebas (`save`, `saveAll`,
  `findById`, `findAll`, `existsById`, `count`, `deleteById`, `deleteAllInBatch`, `deleteAllByIdInBatch`)
  y todas las consultas derivadas, con el mismo filtro y orden que su nombre (incluida la de `Limit`).
  Los métodos que la aplicación no usa lanzan `UnsupportedOperationException`.
- **Índices**: las inscripciones tienen índices por evento, por usuario y por (usuario, evento), como
  `idx_registrations_event`, `idx_registrations_user` y `uk_registrations_user_event`. Las consultas por
  evento cuestan O(k log k) con k inscripciones del evento, las de un usuario O(m log m), y la búsqueda
  por (usuario, evento), la unicidad y las llaves foráneas O(1); ninguna recorre toda la tabla. Las de
  usuarios por correo o documento sí la recorren (O(n)), pero solo al crear o editar un usuario.
- **Bloqueo pesimista**: `findByIdForUpdate` toma un `ReentrantLock` por evento una sola vez por
  transacción y lo libera en `afterCompletion` de una `TransactionSynchronization`. Dos transacciones
  sobre el mismo evento se ejecutan una después de la otra; eventos distintos no se bloquean.
- **Transacciones**: `InMemoryTransactionManager` (extiende `AbstractPlatformTransactionManager`)
  detecta la transacción existente y maneja sincronizaciones, solo lectura y *rollback-only*, así que
  `@Transactional` y el `Propagation.MANDATORY` de `WaitlistService` funcionan igual.

### Limitaciones del fake

- **No valida Flyway ni la migración V1, el SQL que genera Hibernate (ni los tipos y longitudes de
  columna) ni el bloqueo real de PostgreSQL** (`SELECT ... FOR UPDATE`, aislamiento, *deadlocks*). Eso
  queda para un ambiente **staging** con PostgreSQL 16 (por ejemplo, el `compose.yaml`) antes de
  producción.
- Un rollback **no deshace cambios**: los repositorios escriben al instante y devuelven las instancias
  guardadas. Los servicios validan todo antes de modificar y cada prueba empieza con `clear()`, así que
  ninguna depende del rollback.
- Las entidades leídas fuera de una transacción también son las guardadas (con JPA serían copias
  separadas).
- PostgreSQL compara el email con mayúsculas; el fake, sin ellas. El resultado es el mismo porque
  `UserService` siempre guarda el email en minúsculas.
- El fake no cuenta para JaCoCo ni PIT (miden solo `src/main`); su comportamiento lo cubren las pruebas
  de contrato `InMemory*Tests`.

## Pruebas de rendimiento (JMeter)

La carpeta `../performance-tests` tiene la prueba de carga con **Apache JMeter 5.6.3** (detalle completo en
[performance-tests/README.md](../performance-tests/README.md)). No se ejecuta "suelta" en el equipo:
`run-perf.ps1` levanta en Docker la API con el perfil `inmemory` (sin base de datos), limitada a **2 CPU y
1 GiB**, y un contenedor de JMeter que solo la alcanza por una red interna; genera secretos aleatorios para
cada ejecución y siempre baja los contenedores al final.

```powershell
cd ..\performance-tests
powershell -ExecutionPolicy Bypass -File .\run-perf.ps1 -Scenario normal   # 50 usuarios, 5 min
powershell -ExecutionPolicy Bypass -File .\run-perf.ps1 -Scenario pico     # 200 usuarios, 5 min
powershell -ExecutionPolicy Bypass -File .\run-perf.ps1 -Scenario aforo    # 1000 inscripciones a la vez
```

Cada usuario virtual crea su usuario, se inscribe una vez en uno de 20 eventos y luego consulta eventos,
disponibilidad, lista de espera y sus eventos con 0,5 a 1,5 s de *think time*. El escenario `aforo` repite
el escenario de estrés del enunciado por HTTP: 1000 inscripciones simultáneas (Synchronizing Timer) a un
evento de 100 cupos. Cada ejecución deja en `performance-tests/results/<escenario>-<fecha>/` el `.jtl`, el
dashboard HTML de JMeter, `docker-stats.csv` (CPU y memoria cada 5 s) y `resumen.md` con CUMPLE / NO CUMPLE
por petición.

Criterios: errores < 1 %; p95 < 500 ms en consultas y < 1000 ms en creación de usuario e inscripción;
0 sobrecupos en `aforo`.

Resultados de referencia del 2026-11-20 (Docker Desktop en Windows; la API con su límite de 2 CPU y 1 GiB).
La CPU de `docker stats` es relativa a un núcleo, así que el máximo posible es 200 %:

| Escenario | Peticiones | Errores | p95 consultas | p95 crear usuario / inscribir | Throughput | CPU máx. API | Memoria máx. API | Resultado |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `normal` (50 usuarios, 5 min) | 14302 | 0 % | 3 ms | 10 ms / 11 ms | 47.6 req/s | 71 % | 244 MiB | CUMPLE |
| `pico` (200 usuarios, 5 min) | 54038 | 0 % | 2 ms | 6 ms / 6 ms | 179.6 req/s | 101 % | 224 MiB | CUMPLE |
| `aforo` (1000 inscripciones a la vez) | 2003 | 0 % | — | 7 ms / 1894 ms | 430 inscripciones/s | 112 % | 272 MiB | 0 sobrecupos (100 `CONFIRMED`, 900 `WAITING_LIST`); el p95 de la inscripción NO CUMPLE |

Lectura de los resultados:
- En `normal` y `pico` no hay cuello de botella: la API usa en promedio 12 % y 18 % de CPU y menos de
  250 MiB. El *think time* limita la carga a cerca de una petición por segundo por usuario.
- En `aforo`, el cuello de botella es el bloqueo por evento, y es intencional: las 1000 inscripciones al
  mismo evento se atienden de una en una (unos 2,3 ms cada una, 430 por segundo). Por eso la última espera
  unos 2 s y el p95 queda en 1,9 s. No falta CPU ni memoria; esa espera es la que garantiza 0 sobrecupos.
  Bajar ese p95 exigiría otro diseño (por ejemplo, una cola de inscripciones), no más recursos. Con
  PostgreSQL el bloqueo de fila también atiende de a una, así que en staging el p95 será mayor.

**Limitación:** mide la API (Spring MVC, JWT, JSON, reglas de negocio y el bloqueo por evento) con el fake
en memoria. No mide PostgreSQL (pool de conexiones, SQL, E/S ni el bloqueo real de fila); eso queda para
un ambiente staging con los mismos planes.

## Calidad: JaCoCo, PIT, SonarQube y pipeline

Mediciones del 2026-11-20 en la rama `fix/pit-surviving-mutants`, sin ninguna base de datos:

| Métrica | Valor |
| --- | --- |
| Pruebas en `test` | 374 ejecuciones, 0 fallos (incluye los 37 escenarios Cucumber y 90 pruebas de contrato del fake) |
| Escenarios en `acceptanceTest` | 37, 0 fallos |
| Cobertura de líneas (JaCoCo) | 99.6 % (533/535) |
| Cobertura de ramas (JaCoCo) | 100 % (86/86) |
| Mutantes eliminados (PIT) | 100 % (194/194) |

Las dos líneas sin cubrir son el `main` de `EventPlatformApplication`. Ya no sobrevive ningún mutante:
se eliminó `Event.validateForPublication` (código heredado sin uso, con 12 de los 13 sobrevivientes) y
`SecurityConfigTests` prueba `securityFilterChain` en un contexto nuevo (ver [PIT](#pit)).

### JaCoCo

`.\gradlew.bat test` genera automáticamente el reporte de cobertura combinada de JUnit y Cucumber,
sin exclusiones. La tarea `acceptanceTest` no suma cobertura a ese reporte.

### PIT

`.\gradlew.bat pitest` muta las clases de `com.eventplatform.api.*` de `src/main` (no las del fake
`inmemory`) y ejecuta las pruebas de ese paquete con 4 hilos. El reporte HTML queda en
`build/reports/pitest/index.html`.

Los métodos `@Bean` solo se ejecutan cuando se crea un contexto. PIT primero corre las pruebas sin
mutar y después inserta cada mutante en la misma JVM, donde Spring reutiliza los contextos que ya tiene
en caché, así que una prueba `@SpringBootTest` nunca ve un `@Bean` mutado. Por eso esos métodos se
prueban llamándolos directamente o, como `securityFilterChain`, en un contexto mínimo nuevo en cada
ejecución (`WebApplicationContextRunner` en `SecurityConfigTests`).

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
API Scan (activado por defecto) → pruebas de rendimiento con JMeter (activadas por defecto) → publicar
el artefacto `quality-reports` con todos los reportes.

Los pasos que descargan o suben a Azure (SDK de .NET, cobertura, evidencias de ZAP y de JMeter, imágenes
de rendimiento y reportes) se reintentan hasta 2 veces (`retryCountOnTaskFailure`) si la red del agente falla un momento, por ejemplo
con `No such host is known`; sin eso, un corte breve se salta PIT, SonarQube y ZAP. La publicación de
pruebas no se reintenta: falla a propósito si hay pruebas fallidas y repetirla duplicaría los resultados.

SonarQube es opcional: crea `SONAR_TOKEN` como variable secreta y `SONAR_HOST_URL` como variable, y
marca `runSonar` al ejecutar el pipeline manualmente. `localhost` apunta al agente, no a tu equipo.
Ningún paso usa base de datos ni variables `DB_*`.

### Análisis de seguridad con OWASP ZAP

Los pasos `ZAP 01` a `ZAP 06` hacen un análisis pasivo (DAST) de la API en ejecución, sin base de datos:

1. Generan `build/libs/event-platform-inmemory.jar` con la tarea `inMemoryBootJar` (el bootJar de
   producción no tiene los repositorios en memoria).
2. Lo arrancan con los perfiles `inmemory,zap` (`application-zap.properties`) en el puerto `8081`, sin
   variables `DB_*` y con un administrador `zap_admin` y un `JWT_SECRET` aleatorios en cada ejecución
   (ocultos en el log).
3. Esperan a que `GET /actuator/health` responda `UP`, inician sesión con `POST /api/auth/login`, crean
   datos de prueba, descargan con el token el contrato OpenAPI (`/v3/api-docs`, con las 19 operaciones de
   la API más `GET /actuator/health`, que el perfil `zap` agrega con `springdoc.show-actuator=true` para
   que también se escanee) y pasan el token a ZAP con `ZAP_AUTH_HEADER_VALUE`, limitado al host `host.docker.internal`. ZAP
   rellena los ids de ruta (`{id}`, `{eventId}`, `{userId}`) con `10` (variable `zapSampleId`), así que
   se crean eventos y usuarios hasta el id 10 y se inscribe al usuario 10 en el evento 10; sin esos
   datos casi todas las rutas responden `404`. Si no se logran esos ids, el paso deja una advertencia.
4. Ejecutan `zap-api-scan.py -f openapi -S` con ese contrato: ZAP llama a cada endpoint (GET, POST, PUT,
   PATCH y DELETE) y analiza las respuestas. `-S` (modo seguro) omite el escaneo activo, así que el
   análisis sigue siendo pasivo; `-O` cambia el servidor del contrato (`127.0.0.1`) por
   `host.docker.internal`, y las advertencias no fallan el pipeline por `-I`.
5. Siempre detienen la API (sus datos en memoria se descartan) y después publican el artefacto
   `reporte-seguridad-zap` (con la API corriendo, sus logs siguen abiertos y no se pueden subir).

El artefacto contiene `reporte-zap.html`, `reporte-zap.json`, `openapi.json` (el contrato que usó ZAP),
`ejecucion.txt` (build, commit, fecha, métodos HTTP que envió ZAP y códigos que recibió),
`acceso-http.log` (cada petición y su código) y los logs de la API. El paso 04 deja una advertencia si
ZAP solo recibe `401` (el token no llegó) o si solo envía `GET` (no usó el contrato): en ambos casos el
análisis no es válido.

El contrato OpenAPI lo genera springdoc (`springdoc-openapi-starter-webmvc-api`, sin Swagger UI). Está
apagado por defecto (`springdoc.api-docs.enabled=false`), solo el perfil `zap` lo activa, siempre exige
el JWT y se genera en OpenAPI 3.0 porque ZAP solo tiene soporte inicial para 3.1.

Requisitos del agente: Docker Desktop abierto con contenedores Linux y el agente ejecutándose con un
usuario que tenga acceso a Docker. Si Windows pide permiso de firewall para Java en el puerto `8081`,
permite redes privadas. Desmarca `runZap` para ejecutar el pipeline sin Docker. En el paso 03 puede
aparecer `The STDIO streams did not close within 10 seconds`: es normal, la API sigue en segundo plano.

Alcance: antes se usaba `zap-baseline.py`, pero su spider sigue enlaces HTML y la API solo responde
JSON, así que encontraba 5 rutas GET (`/api/events`, `/`, `/api`, `/robots.txt`, `/sitemap.xml`) y dio
0 alertas con 61 reglas `PASS`. Con el contrato, la prueba local (ZAP 2.17) llegó a las 19 operaciones
(10 GET, 4 POST, 3 PATCH, 1 PUT, 1 DELETE) y recibió 13 respuestas `2xx`. Las demás son errores
esperados con los cuerpos de ejemplo de ZAP: `400` (datos inválidos), `401` (login con credenciales
falsas) y `409` (inscripción duplicada o cambio no permitido). Así encontró que faltaba
`Cross-Origin-Resource-Policy` (regla 90004, riesgo bajo); `SecurityConfig` ahora la envía con
`same-origin`, y el resultado final fue 0 advertencias y 120 reglas `PASS`, más alertas informativas
(códigos 4xx, petición de login detectada y respuestas que no se guardan en caché). Spring Security
también envía `X-Content-Type-Options`, `X-Frame-Options` y `Cache-Control`, y la regla de CSP solo
aplica a respuestas HTML. Esas mediciones se hicieron cuando el escaneo usaba un PostgreSQL desechable;
ahora las escrituras de ZAP solo afectan a la memoria de la API de laboratorio.

### Rendimiento con JMeter en el pipeline

Con `runPerf` (activado por defecto), los pasos `JMeter 01` a `JMeter 05` ejecutan
`performance-tests/run-perf.ps1` en el agente:

1. `JMeter 01` comprueba que Docker esté disponible con contenedores Linux; si no, deja una advertencia y
   los demás pasos se omiten.
2. `JMeter 02` construye el jar en memoria y las imágenes de la API y de JMeter (`-BuildOnly`). Es el único
   paso que descarga (Gradle, imágenes base y JMeter), así que se reintenta hasta 2 veces.
3. `JMeter 03` ejecuta el escenario `normal` (50 usuarios, 5 minutos) y `JMeter 04` el escenario `aforo`
   (1000 inscripciones simultáneas), ambos con `-SkipBuild` y sin red.
4. `JMeter 05` publica `performance-tests/results` como el artefacto `reporte-rendimiento-jmeter`.

Un sobrecupo en `aforo` hace fallar el pipeline (el script termina con código 1). Los demás criterios
(errores y p95) solo dejan advertencias, porque dependen de la carga del agente. El escenario `pico` no se
ejecuta en el pipeline para no alargarlo; se ejecuta a mano con `-Scenario pico`.

## Construir la aplicación

```powershell
.\gradlew.bat clean build
java -jar build\libs\event-platform-0.0.1-SNAPSHOT.jar   # con PostgreSQL y el .env disponibles

.\gradlew.bat inMemoryBootJar                            # JAR aparte, sin base de datos
java -jar build\libs\event-platform-inmemory.jar --spring.profiles.active=inmemory
```

`build` compila y ejecuta las pruebas de `test`. El bootJar de producción solo contiene `src/main`;
`inMemoryBootJar` genera `event-platform-inmemory.jar` con `main` + `inmemory` para E2E, OWASP ZAP y
JMeter. Para construir la imagen Docker y compartirla con el grupo, consulta [DOCKER.md](DOCKER.md).

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
    |   |-- application.properties, application-zap.properties
    |   `-- db/migration/V1__create_initial_schema.sql
    |-- inmemory/java/com/eventplatform/api/inmemory/   Fake de la base (perfil inmemory, fuera del JAR de producción)
    |   |-- InMemoryDatabase           Tablas (ConcurrentHashMap) y secuencias (AtomicLong); clear()
    |   |-- AbstractInMemoryRepository JpaRepository genérico sobre una tabla
    |   |-- InMemoryUserRepository, InMemoryEventRepository (bloqueo), InMemoryRegistrationRepository
    |   |-- InMemoryTransactionManager, SchemaConstraints
    |-- inmemory/resources/application-inmemory.properties   Excluye DataSource, JPA y Flyway
    |-- test/java/
    |   |-- com/eventplatform/api/   Pruebas unitarias, de integración, de concurrencia y del fake (inmemory/)
    |   |-- com/eventplatform/api/support/EventDates   Fechas relativas al día actual
    |   |-- steps/                   Pasos de Cucumber, DatabaseHooks y configuración Spring/seguridad
    |   `-- runners/                 RunCucumberTest
    `-- test/resources/
        |-- features/                Escenarios Gherkin
        |-- config/application.properties   Grupos de perfiles: test y acceptance activan inmemory
        `-- application-test.properties, application-acceptance.properties, junit-platform.properties
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
| 1 | La promoción de la lista de espera no revisa el estado del usuario | Un usuario inactivado mientras esperaba puede quedar `CONFIRMED` | Decidir si se debe saltar a los usuarios `INACTIVE` |
| 2 | El estado del evento no cambia con el tiempo | Un evento pasado sigue `SCHEDULED` hasta que se cambie a mano (igual no acepta inscripciones) | Tarea programada o cálculo del estado, si se requiere |
| 3 | Cancelar un evento no cancela sus inscripciones | Las inscripciones de un evento `CANCELLED` quedan como estaban | Cancelarlas en cascada, si se requiere |
| 4 | Todo `DataIntegrityViolationException` responde `Email or document is already registered` | Mensaje engañoso si la BD rechaza otro dato | Mensaje genérico o por restricción |
| 5 | Un solo administrador, sin revocación de tokens ni paginación en los listados | Suficiente para el alcance del proyecto | Usuarios administradores en BD, *refresh tokens* y `Pageable` si crece |

El `HEALTHCHECK` de Docker (que consultaba `/api/users` y recibía `401`) y la zona horaria del contenedor
(UTC en vez de `America/Bogota`) ya están corregidos: ver [DOCKER.md](DOCKER.md).

## Próximos pasos sugeridos

1. Integrar `develop` en `main` antes de la entrega.
2. Validar en un ambiente staging con PostgreSQL 16 lo que el fake no cubre: Flyway, el SQL de
   Hibernate y el bloqueo real del escenario de estrés, con los mismos planes de JMeter.
3. Publicar Swagger UI para el equipo (el contrato OpenAPI ya existe, pero solo lo activa el perfil `zap`).
