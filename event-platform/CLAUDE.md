# Event Platform API — instrucciones para el agente

## Proyecto
API REST (Java 21, Spring Boot 4, Spring Web MVC, Spring Data JPA, PostgreSQL 16, Flyway, Gradle Wrapper)
para administrar eventos e inscripciones garantizando que NUNCA se supere el aforo.
Estado actual: la gestión de usuarios (/api/users) está completa. Las entidades y tablas de
events y registrations ya existen (migración V1), pero no tienen repositorio/servicio/controlador.

## Idioma
- Todo el código, nombres de clases, métodos, variables, endpoints, mensajes de error,
  escenarios Gherkin, nombres de ramas y mensajes de commit: en INGLÉS.
- Las explicaciones que me des en el chat: en español.

## Convenciones de código
- Seguir exactamente el estilo existente del módulo de usuarios: paquetes controller/, dto/,
  exception/, model/, repository/, service/ bajo com.eventplatform.api.
- DTOs como records con Bean Validation. Nunca exponer entidades JPA en los controladores.
- Errores con el formato estructurado actual del GlobalExceptionHandler
  (timestamp, status, error, message, path, details):
  400 = validación/JSON inválido, 404 = recurso inexistente, 409 = conflicto/regla de negocio violada.
- No modificar la migración V1. Si el esquema cambia, crear V2__..., V3__... en db/migration.
- Antes de crear algo, revisar lo que ya existe en model/ y en V1__create_initial_schema.sql
  y reutilizar los nombres de columnas y enums existentes.
- Usar un bean java.time.Clock inyectado para "ahora" (para poder probar fechas).

## Pruebas (obligatorias en cada tarea)
- Unitarias de servicio con Mockito (*ServiceTests).
- Integración con MockMvc y perfil test/H2 (*ControllerIntegrationTests).
- Escenarios Cucumber en inglés en src/test/resources/features cuando la tarea toque reglas de negocio.
- Al terminar: ejecutar `.\gradlew.bat test` (y `acceptanceTest` si hay features nuevas) y que todo pase.
- No bajar la cobertura JaCoCo ni romper PIT.

## Git
- Ramas: feature/<kebab-case> creadas desde develop.
- Commits pequeños con Conventional Commits en inglés:
  <type>(<scope>): <imperative summary in lowercase, max 72 chars>
  types: feat, fix, test, refactor, docs, chore, ci
  scopes: users, events, registrations, waitlist, concurrency, api-docs, readme
- Hacer commit al final de cada paso lógico. NO hacer push ni merge; eso lo hago yo.

## Reglas de negocio (fuente de verdad)
1. Un usuario no puede inscribirse dos veces en el mismo evento (inscripción CONFIRMED o WAITLISTED activa → 409).
2. Un evento nunca puede superar su capacidad (registeredCount <= capacity siempre).
3. No se aceptan inscripciones cuando now >= startDate.
4. Evento CANCELLED no acepta inscripciones.
5. Evento FINISHED no acepta inscripciones.
6. capacity > 0.
7. Cancelar una inscripción CONFIRMED libera un cupo.
8. Con el evento lleno, la nueva inscripción queda WAITLISTED.
9. Al liberarse un cupo, la inscripción WAITLISTED más antigua (registrationDate, luego id) pasa a CONFIRMED automáticamente.
10. Un usuario INACTIVE no puede inscribirse.

## Estados
- Event: SCHEDULED, ACTIVE, FINISHED, CANCELLED  (PROGRAMADO, ACTIVO, FINALIZADO, CANCELADO)
- Registration: CONFIRMED, CANCELLED, WAITLISTED  (CONFIRMADA, CANCELADA, LISTA_ESPERA)
  Si los enums existentes usan otros nombres, conservar los existentes.

## Concurrencia
Toda operación que cambie registeredCount (inscribir, cancelar, promover, cambiar capacidad) debe
bloquear la fila del evento con PESSIMISTIC_WRITE dentro de una sola transacción.
Escenario de estrés esperado: 100 cupos, 1000 usuarios → 100 CONFIRMED, 900 WAITLISTED, 0 sobrecupos.