# Event Platform API

API REST para gestionar una plataforma de eventos. El proyecto está construido con Java 21, Spring Boot, Spring Web MVC, Spring Data JPA, PostgreSQL y Flyway.

## Estado actual

Actualmente está implementada la gestión de usuarios:

- Crear un usuario.
- Listar todos los usuarios.
- Consultar un usuario por su identificador.
- Actualizar parcialmente el nombre, correo o documento de un usuario.
- Cambiar el estado de un usuario entre `ACTIVE` e `INACTIVE`.
- Validar los datos de entrada y evitar correos o documentos duplicados.
- Responder con errores estructurados para datos inválidos, duplicados y usuarios inexistentes.

También existen las entidades y tablas de eventos e inscripciones, pero todavía no hay controladores, servicios ni endpoints para operarlas.

## Tecnologías

- Java 21
- Spring Boot 4.1.1
- Gradle Wrapper
- PostgreSQL 16
- Flyway para migraciones de base de datos
- JUnit 5, Mockito y MockMvc para pruebas
- Docker Compose para levantar PostgreSQL

## Requisitos

- JDK 21 instalado y disponible en `PATH`.
- Docker Desktop o Docker Engine con Docker Compose.

No es necesario instalar Gradle: el repositorio incluye Gradle Wrapper.

## Configuración de la base de datos

La aplicación usa estos valores predeterminados:

| Propiedad | Valor predeterminado |
| --- | --- |
| Base de datos | `event_platform` |
| Usuario | Obligatorio: variable `DB_USERNAME` |
| Contraseña | Obligatoria: variable `DB_PASSWORD` |
| Puerto | `5432` |
| URL JDBC | `jdbc:postgresql://localhost:5432/event_platform` |

Antes de iniciar la aplicación, define las credenciales en la misma sesión de PowerShell:

```powershell
$env:DB_USERNAME = Read-Host 'Usuario de PostgreSQL'
$dbSecret = Read-Host 'Contraseña de PostgreSQL' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $dbSecret).Password
$env:POSTGRES_USER = $env:DB_USERNAME
$env:POSTGRES_PASSWORD = $env:DB_PASSWORD
```

Para un volumen existente, usa sus credenciales actuales: cambiar las variables no cambia la contraseña almacenada en PostgreSQL.

`.env.example` enumera las variables sin secretos reales. Si lo copias a `.env`, Git ignorará ese archivo. Docker Compose lo carga automáticamente; Spring y Gradle requieren las variables en su entorno (por ejemplo, con los comandos anteriores o la configuración del IDE).

Para iniciar PostgreSQL desde la raíz del proyecto:

```powershell
docker compose up -d
docker compose ps
```

Flyway ejecutará automáticamente `V1__create_initial_schema.sql` cuando arranque la aplicación. Esta migración crea las tablas `users`, `events` y `registrations`.

Si el puerto `5432` está ocupado, se puede publicar PostgreSQL en otro puerto y ajustar la URL usada por Spring:

```powershell
$env:POSTGRES_PORT = "5433"
$env:DB_URL = "jdbc:postgresql://localhost:5433/event_platform"
docker compose up -d
```

También se pueden sobrescribir estas variables:

- Docker Compose: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` y `POSTGRES_PORT`.
- Aplicación: `DB_URL`, `DB_USERNAME` y `DB_PASSWORD`.

Los valores de ambos grupos deben coincidir.

## Credenciales de SonarQube

El token se obtiene de `SONAR_TOKEN`; no se guarda en `build.gradle`. Configúralo en la sesión antes de ejecutar el análisis:

```powershell
$sonarSecret = Read-Host 'Token de SonarQube' -AsSecureString
$env:SONAR_TOKEN = [System.Net.NetworkCredential]::new('', $sonarSecret).Password
.\gradlew.bat sonar
```

En CI, configura `SONAR_TOKEN` como secreto y pásalo como variable de entorno a la tarea. Revoca el token que estuvo escrito en el código y genera uno nuevo; quitarlo del archivo no lo elimina del historial de Git.

## Ejecutar la API

Con PostgreSQL activo, ejecutar en PowerShell:

```powershell
.\gradlew.bat bootRun
```

La API quedará disponible en:

```text
http://localhost:8080
```

Para detenerla, usar `Ctrl+C`. Para detener posteriormente la base de datos:

```powershell
docker compose down
```

Este comando conserva los datos en el volumen de Docker. `docker compose down -v` también elimina el volumen y todos sus datos.

## Endpoints disponibles

La ruta base es `/api/users`.

| Método | Ruta | Función | Respuesta correcta |
| --- | --- | --- | --- |
| `POST` | `/api/users` | Crear un usuario | `201 Created` |
| `GET` | `/api/users` | Listar usuarios ordenados por ID | `200 OK` |
| `GET` | `/api/users/{id}` | Consultar un usuario | `200 OK` |
| `PATCH` | `/api/users/{id}` | Actualizar uno o varios datos | `200 OK` |
| `PATCH` | `/api/users/{id}/status` | Cambiar el estado | `200 OK` |

### 1. Crear un usuario

```powershell
curl.exe -i -X POST "http://localhost:8080/api/users" `
  -H "Content-Type: application/json" `
  -d '{"name":"Ada Lovelace","email":"ada@example.com","document":"12345"}'
```

Ejemplo de respuesta:

```json
{
  "id": 1,
  "name": "Ada Lovelace",
  "email": "ada@example.com",
  "document": "12345",
  "status": "ACTIVE"
}
```

El nombre, el correo y el documento son obligatorios. El correo se guarda en minúsculas y, junto con el documento, debe ser único.

### 2. Listar usuarios

```powershell
curl.exe "http://localhost:8080/api/users"
```

### 3. Consultar un usuario por ID

```powershell
curl.exe "http://localhost:8080/api/users/1"
```

### 4. Actualizar parcialmente un usuario

Solo es necesario enviar los campos que se quieran modificar:

```powershell
curl.exe -X PATCH "http://localhost:8080/api/users/1" `
  -H "Content-Type: application/json" `
  -d '{"name":"Ada Byron","email":"ada.byron@example.com"}'
```

Los campos permitidos son `name`, `email` y `document`.

### 5. Cambiar el estado de un usuario

```powershell
curl.exe -X PATCH "http://localhost:8080/api/users/1/status" `
  -H "Content-Type: application/json" `
  -d '{"status":"INACTIVE"}'
```

Los únicos estados válidos son `ACTIVE` e `INACTIVE`.

## Errores esperados

La API puede devolver:

- `400 Bad Request`: JSON inválido o campos que no cumplen las validaciones.
- `404 Not Found`: el usuario solicitado no existe.
- `409 Conflict`: el correo o documento ya está registrado.

Ejemplo de error de validación:

```json
{
  "timestamp": "2026-09-04T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/users",
  "details": {
    "email": "Email must be valid"
  }
}
```

## Ejecutar las pruebas automatizadas

Las pruebas unitarias del servicio usan Mockito para simular el repositorio con datos ficticios, sin conectarse a una base de datos. Las pruebas de integración y de arranque usan el perfil `test`, con una base H2 en memoria. Cucumber usa su propia base H2 con el perfil `acceptance`. No es necesario iniciar PostgreSQL para ejecutar las pruebas.

Los usuarios de prueba se crean con datos ficticios. Las pruebas del controlador y los escenarios de Cucumber revierten sus transacciones al terminar. Las bases H2 existen únicamente dentro del proceso de pruebas. Flyway está desactivado en estos perfiles y Hibernate crea el esquema, por lo que estas pruebas no validan las migraciones ni las particularidades de PostgreSQL.

```powershell
.\gradlew.bat test
```

El reporte HTML se genera en:

```text
build/reports/tests/test/index.html
```

Para ejecutar una clase concreta:

```powershell
.\gradlew.bat test --tests "com.eventplatform.api.service.UserServiceTests"
.\gradlew.bat test --tests "com.eventplatform.api.controller.UserControllerIntegrationTests"
```

Las pruebas actuales verifican:

- Creación de usuarios activos con datos normalizados.
- Rechazo de un correo duplicado.
- Creación y consulta de usuarios mediante los endpoints.

## Pruebas de aceptación con Gherkin

Los dos escenarios en inglés de `src/test/resources/features/user_registration.feature`
comprueban el registro exitoso de un usuario (`201`, estado `ACTIVE`) y el rechazo
de un correo duplicado (`409`, sin crear otro usuario ni modificar el existente).

`src/test/java/steps/RegistrationUser.java` implementa los pasos y
`src/test/java/runners/RunCucumberTest.java` ejecuta los escenarios con Cucumber.
Se carga la aplicación con MockMvc y el perfil `acceptance`, que usa H2 en memoria.
Cada escenario revierte su transacción al terminar. No requiere iniciar PostgreSQL
ni la API por separado. Estas pruebas no validan las migraciones de PostgreSQL.

Desde la carpeta `event-platform`:

```powershell
.\gradlew.bat acceptanceTest
```

El reporte de Cucumber queda en `build/reports/cucumber/user-registration.html`.
Las pruebas de integración existentes siguen requiriendo PostgreSQL cuando se
ejecuta toda la suite con `.\gradlew.bat test`.

## Cobertura con JaCoCo

Para ejecutar las pruebas y generar cobertura de líneas y ramas:

```powershell
.\gradlew.bat test jacocoTestReport
```

La tarea `test` ejecuta las pruebas JUnit Jupiter y los escenarios Cucumber mediante JUnit Platform. El reporte incluye la cobertura combinada de ambas y todas las clases del código principal, sin exclusiones. También se genera automáticamente al ejecutar `test`.

- Reporte HTML: `build/reports/jacoco/test/html/index.html`.
- Reporte XML: `build/reports/jacoco/test/jacocoTestReport.xml`.

Las pruebas usan Mockito o H2 en memoria; no requieren PostgreSQL. La tarea independiente `acceptanceTest` no agrega cobertura a este reporte.

## Construir el proyecto

```powershell
.\gradlew.bat clean build
```

El archivo ejecutable se genera dentro de `build/libs/`. Con PostgreSQL activo también se puede iniciar con:

```powershell
java -jar build\libs\event-platform-0.0.1-SNAPSHOT.jar
```

## Estructura principal

```text
src/main/java/com/eventplatform/api/
|-- controller/   Endpoints REST
|-- dto/          Objetos de entrada y salida
|-- exception/    Manejo centralizado de errores
|-- model/        Entidades JPA y enumeraciones
|-- repository/   Acceso a datos
`-- service/      Reglas de negocio

src/main/resources/
|-- application.properties
`-- db/migration/ Migraciones de Flyway
```

## Próximos pasos sugeridos

1. Implementar repositorios, DTO, servicios y controladores para eventos.
2. Implementar la inscripción y cancelación de usuarios en eventos.
3. Aplicar las reglas de capacidad y lista de espera.
4. Añadir pruebas para actualización, cambio de estado, validaciones y respuestas `404`.
5. Crear pruebas aisladas con una base de datos de prueba o Testcontainers para no depender de una instancia local compartida.
6. Añadir documentación OpenAPI/Swagger cuando crezca el número de endpoints.
