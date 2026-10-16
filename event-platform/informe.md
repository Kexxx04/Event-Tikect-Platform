# Informe de calidad de código — Event-Ticket-Platform

**Fecha:** 08 de septiembre de 2026, 17:19
**Módulo analizado:** `event-platform`
**Rama:** `feature/testSonar` · commit `405c461`
**Proyecto en SonarQube:** `Event-Ticket-Platform` (http://localhost:9000/dashboard?id=Event-Ticket-Platform)

---

## 0. Datos generales del proyecto

| Campo | Valor |
| --- | --- |
| Nombre del proyecto | Event-Ticket-Platform |
| Tecnología utilizada | Spring Boot 4.1.1 (Spring Data JPA, Spring Web MVC, Spring Validation, Flyway), PostgreSQL, Gradle, JUnit 5, JaCoCo, Pitest, SonarQube |
| Lenguaje | Java 21 |
| Número aproximado de clases/componentes | 19 clases Java (`event-platform/src/main/java`) |
| Integrantes | Diego Romero, Keith Smith Balaguera Rodriguez, Jaider Rios Franco, Lina Paola Suarez Diaz |
| Fecha del análisis | 11 de septiembre de 2026 |

---

## 1. Ejecución del análisis completo

Se ejecutaron, en orden, las tres herramientas de calidad ya configuradas en `build.gradle`:

| Paso | Comando | Qué mide |
| --- | --- | --- |
| 1. Pruebas + cobertura | `./gradlew test` (dispara `jacocoTestReport`) | Ejecuta la suite de JUnit 5 y genera cobertura de líneas/ramas con JaCoCo |
| 2. Análisis estático | `./gradlew sonar` | Envía código + reporte de JaCoCo a SonarQube local (`localhost:9000`); detecta bugs, vulnerabilidades, code smells y duplicación |
| 3. Testing de mutaciones | `./gradlew pitest` | Introduce mutaciones en el bytecode y verifica si la suite de pruebas las detecta (mide qué tan *efectivas* son las pruebas, no solo si existen) |

Resultado de las tres corridas: `BUILD SUCCESSFUL`, Quality Gate de SonarQube en estado **OK** (no hay condiciones del gate por defecto incumplidas). Esto no significa "sin problemas" — solo que ninguno de los hallazgos es un *bug* o *vulnerabilidad* que el gate por defecto bloquee; los 18 hallazgos son *code smells* de mantenibilidad, detallados en la sección 3.

---

## 2. Métricas principales obtenidas

### 2.1 Resumen global (SonarQube)

| Métrica | Valor | Lectura |
| --- | --- | --- |
| Líneas de código (ncloc) | 602 | Tamaño actual del módulo |
| Bugs | 0 | — |
| Vulnerabilidades | 0 | — |
| Security hotspots | 0 | — |
| Code smells | 18 | Todos concentrados en 1 archivo (ver §3) |
| Deuda técnica estimada (`sqale_index`) | 195 min (≈ 3.3 h) | Tiempo estimado para resolver los 18 code smells |
| Duplicación de líneas | 0.0% | Sin duplicación detectada |
| Cobertura de líneas | 40.1% | 91 / 227 líneas |
| Cobertura de ramas | 14.3% | 8 / 56 ramas |
| Complejidad ciclomática | 99 | Sobre 602 líneas |
| Complejidad cognitiva | 41 | — |

### 2.2 Cobertura por paquete (JaCoCo, líneas)

| Paquete | Cobertura | Líneas cubiertas |
| --- | --- | --- |
| `model` | **18.8%** | 22 / 117 |
| `api` (raíz) | 33.3% | 1 / 3 |
| `service` | 52.0% | 26 / 50 |
| `exception` | 58.3% | 14 / 24 |
| `controller` | 72.7% | 8 / 11 |
| `dto.user` | 80.0% | 8 / 10 |
| `model.enums` | 100% | 12 / 12 |
| `repository` | N/A | interfaz sin lógica ejecutable |

El paquete `model` concentra **95 de las 136 líneas sin cubrir del proyecto (70%)**, principalmente en la clase `Event` (5/80 líneas, 6.2%).

### 2.3 Testing de mutaciones (Pitest)

| Paquete | Clases con mutaciones | Cobertura de línea | **Cobertura de mutaciones** | Test strength |
| --- | --- | --- | --- | --- |
| `service` | 1 | 52% | 50% (12/24) | 100% |
| `exception` | 1 | 65% | 43% (3/7) | 100% |
| `dto.user` | 1 | 100% | 100% (1/1) | 100% |
| `controller` | 1 | 73% | 40% (2/5) | 100% |
| `model` | 3 | 19% | **9% (5/53)** | 100% |
| **Total** | **7** | **37%** | **26% (23/90)** | **100%** |

**Lectura clave:** el *test strength* es 100% en todos los paquetes — es decir, cada vez que el código sí está cubierto, las pruebas existentes matan efectivamente casi todas las mutaciones (no son pruebas "vacías" que solo ejecutan código sin verificar nada). El problema no es la calidad de las pruebas que existen, sino que **67 de las 90 mutaciones (74%) caen en código que ninguna prueba ejecuta**, sobre todo en `model`.

---

## 3. Problemas más relevantes encontrados

Los 18 code smells reportados por Sonar están **100% concentrados en un solo método**: `Event.validateForPublication()` (`src/main/java/com/eventplatform/api/model/Event.java`, líneas 130–202).

| # | Regla Sonar | Ocurrencias | Severidad | Descripción |
| --- | --- | --- | --- | --- |
| 1 | `java:S106` | 12 | MAJOR | Uso de `System.out.println` en vez de un logger |
| 2 | `java:S112` | 2 | MAJOR | `throws Exception` / `throw new Exception(...)` genérico |
| 3 | `java:S3776` | 1 | CRITICAL | Complejidad cognitiva de 30 (límite permitido: 15) |
| 4 | `java:S1066` | 2 | MAJOR | Ifs anidados que deberían fusionarse con `&&` |
| 5 | `java:S8688` | 1 | INFO | `LocalDateTime.now()` sin zona horaria explícita |

El método hace todo a la vez: valida 8 campos distintos del evento con `if/else` anidados hasta 3 niveles, imprime cada error por consola, acumula un flag `valid`, y al final lanza `Exception` genérica si algo falló.

---

## 4. Impacto técnico

| Problema | Impacto |
| --- | --- |
| **`System.out.println` (×12)** | Los mensajes de validación no pasan por el sistema de logging de Spring: no tienen nivel (INFO/WARN/ERROR), no se pueden filtrar ni enviar a un agregador de logs, y ensucian la salida estándar en producción. En una entidad JPA, además, cualquier acceso durante la carga de Hibernate podría disparar salida no deseada. |
| **`throw new Exception` genérica (×2)** | El proyecto ya tiene un patrón establecido de excepciones específicas (`InvalidUserDataException`, `UserNotFoundException`, `DuplicateUserException`) manejadas por `GlobalExceptionHandler` para devolver `ApiError` estructurado. `Exception` genérica **no** es capturada por ese manejador, así que un evento inválido terminaría en un `500 Internal Server Error` sin cuerpo JSON útil para el cliente de la API. Además, obliga a quien llame el método a hacer `catch (Exception e)`, lo que puede ocultar errores no relacionados (`NullPointerException`, etc.). |
| **Complejidad cognitiva 30/15** | Un método con esta cantidad de ramas es difícil de leer, de modificar sin romper una condición vecina, y — más medible aún — **explica directamente el bajo mutation score del paquete `model` (9%)**: con tantas rutas posibles, cubrir todas las combinaciones con pruebas manuales es poco realista sin refactorizar primero. |
| **Ifs anidados sin fusionar** | No rompen el programa, pero cada nivel de anidación adicional es una rama más que las pruebas deben ejercitar para lograr cobertura completa; contribuyen directamente a la complejidad de arriba. |
| **`LocalDateTime.now()` sin zona horaria** | La comparación `startDate.isBefore(LocalDateTime.now())` depende de la zona horaria del servidor donde corra la JVM. Si la app se despliega en un servidor con zona horaria distinta a la esperada (o cambia por horario de verano), un evento válido podría rechazarse — o uno inválido aceptarse — de forma no determinista. También impide escribir pruebas unitarias deterministas para este caso límite (no hay forma de inyectar un "ahora" fijo). |
| **Cobertura casi nula en `model` (18.8% líneas, 9% mutaciones)** | Esta es precisamente la lógica que decide si un evento puede publicarse o no — una regla de negocio central — y es la menos verificada de todo el proyecto. El riesgo no es teórico: cualquier cambio futuro en esta clase puede romper una validación sin que ninguna prueba lo detecte. |

---

## 5. Priorización de correcciones

| Prioridad | Acción | Justificación |
| --- | --- | --- |
| **P0** | Reemplazar `throw new Exception` por una excepción específica (`InvalidEventDataException`) manejada por `GlobalExceptionHandler` | Afecta el contrato real de la API: hoy un evento inválido devuelve un 500 no estructurado en vez de un 400 con detalle del error. Es el único hallazgo con impacto directo en el comportamiento observable de la API. |
| **P0** | Escribir pruebas unitarias para `validateForPublication()` (todas las ramas: nombre nulo/vacío/corto, descripción larga, fechas nulas/pasadas/invertidas, capacidad nula/negativa, sobrecupo, estado nulo) | Es la regla de negocio menos cubierta del proyecto (6.2% en `Event`); dado que el *test strength* del proyecto ya es 100% donde hay pruebas, el retorno de escribir estas pruebas es alto y el riesgo de introducirlas es bajo. |
| **P1** | Refactorizar el método para bajar la complejidad cognitiva de 30 a ≤15 (guard clauses o acumulación de errores en una lista en vez de `if/else` anidado) | Es prerrequisito práctico para poder escribir las pruebas del punto anterior con un esfuerzo razonable; también resuelve los 2 hallazgos de "merge nested if" como efecto colateral. |
| **P1** | Eliminar los 12 `System.out.println` | Alto volumen (12/18 hallazgos) y bajo riesgo de corrección; se resuelve naturalmente si el refactor del punto anterior devuelve una lista de errores en vez de imprimirlos. |
| **P2** | Inyectar `Clock` en vez de `LocalDateTime.now()` | Baja probabilidad de manifestarse como bug real, pero facilita las pruebas deterministas de fechas del punto P0 — conviene resolverlo junto con el refactor, no después. |

---

## 6. Acciones de mejora propuestas

1. **Extraer la validación fuera de la entidad JPA.** Mover `validateForPublication()` a una clase dedicada (p. ej. `EventValidator` o un método de `EventService`, siguiendo el mismo patrón usado hoy para `User` en `UserService`). Una entidad no debería concentrar reglas de negocio ni tener dependencias de infraestructura (logging, `Exception`).
2. **Cambiar el diseño de "imprimir y devolver boolean" por "acumular errores y lanzar/retornar"**: en vez de `System.out.println` + `throw new Exception`, acumular los mensajes en un `List<String>` y, si no está vacío, lanzar una única `InvalidEventDataException` con el detalle — mismo patrón que `InvalidUserDataException` ya usa en `exception/`.
3. **Inyectar `Clock`** como parámetro (o campo del validador) para poder fijar "el ahora" en las pruebas y eliminar el warning `S8688`.
4. **Escribir la suite de pruebas faltante** para `Event`/`Registration` (hoy en 6.2% y 11.1% de líneas respectivamente) antes de implementar los endpoints de eventos e inscripciones mencionados como próximos pasos en el `README.md` — es más barato cubrir la regla de negocio ahora que después de construir controladores encima de ella.
5. **Configurar el Quality Gate de Sonar para fallar en código nuevo por debajo de un umbral de cobertura** (p. ej. 80% en "new code"), en vez de depender solo del gate por defecto (que hoy pasa con 40% de cobertura global porque no evalúa bugs/vulnerabilidades). Esto evita que se repita este patrón cuando se implemente la lógica de eventos e inscripciones.
6. **Ampliar el alcance de Pitest** (`targetClasses`/`targetTests` en `build.gradle`, hoy limitado a `com.eventplatform.api.*`) para confirmar que sigue cubriendo los paquetes nuevos a medida que crece el proyecto.

---

## Anexos

- Dashboard visual de cobertura (JaCoCo): `event-platform/build/reports/jacoco/test/html/index.html`
- Reporte de mutaciones (Pitest): `event-platform/build/reports/pitest/index.html`
- Detalle de issues por regla: `http://localhost:9000/project/issues?id=Event-Ticket-Platform`
