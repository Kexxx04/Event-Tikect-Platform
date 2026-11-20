# Pruebas de rendimiento con Apache JMeter

Pruebas de carga de la API de Event Platform con **Apache JMeter 5.6.3** en modo non-GUI. La API corre en
Docker con el perfil `inmemory` (sin base de datos), limitada a **2 CPU y 1 GiB**, y JMeter la llama por una
red interna de Docker. Todo lo levanta y lo baja `run-perf.ps1`; nada corre "suelto" en el equipo.

```text
performance-tests/
|-- run-perf.ps1                         Ejecuta un escenario de principio a fin (Windows PowerShell 5.1+)
|-- compose.perf.yaml                    API (2 CPU, 1 GiB) + JMeter en una red interna
|-- Dockerfile.inmemory                  Imagen de la API con event-platform-inmemory.jar
|-- Dockerfile.inmemory.dockerignore     Su contexto (la raiz del repositorio) solo incluye ese jar
|-- Dockerfile.jmeter                    JMeter 5.6.3 verificado con el SHA-512 de Apache
|-- plans/
|   |-- carga.jmx                        Escenarios normal y pico
|   `-- aforo-concurrente.jmx            1000 inscripciones simultaneas a un evento de 100 cupos
`-- results/                             Una carpeta por ejecucion (ignorada por Git)
```

## Por qué así

- **Sin base de datos.** El requisito del curso es que las pruebas no usen base de datos. La API usa los
  repositorios fake del perfil `inmemory` (ver "Estrategia de pruebas sin base de datos" en
  `../event-platform/README.md`), que emulan las restricciones, los índices y el bloqueo por evento.
- **Aislada y con recursos limitados.** Una prueba de carga sin control compite con todo lo que corre en el
  equipo y sus números no se pueden comparar. Aquí la API tiene siempre 2 CPU y 1 GiB (sin swap), JMeter
  tiene 2 CPU y 2 GiB, y solo se ven entre ellos por la red `perf` (`internal: true`, sin salida a internet).
  La API además se publica en `127.0.0.1:18080` para el chequeo de salud del script; nunca fuera del equipo.
- **Sin secretos en archivos.** `run-perf.ps1` genera `ADMIN_PASSWORD` y `JWT_SECRET` aleatorios en cada
  ejecución y los pasa por variables de entorno; `compose.perf.yaml` no tiene valores por defecto para ellos.
  Los planes leen la contraseña del entorno del contenedor, así que no aparece en la línea de comandos ni en
  `jmeter.log`.

## Requisitos

| Herramienta | Para qué |
| --- | --- |
| Docker Desktop con contenedores Linux | Imágenes de la API y de JMeter |
| JDK 21 (`JAVA_HOME`) | `gradlew inMemoryBootJar` construye el jar |
| Windows PowerShell 5.1 o PowerShell 7 | `run-perf.ps1` |
| Internet la primera vez | Imágenes base, JMeter (87 MB) y dependencias de Gradle; después queda en caché |

## Ejecutar

Desde esta carpeta:

```powershell
powershell -ExecutionPolicy Bypass -File .\run-perf.ps1 -Scenario normal
powershell -ExecutionPolicy Bypass -File .\run-perf.ps1 -Scenario pico
powershell -ExecutionPolicy Bypass -File .\run-perf.ps1 -Scenario aforo

# Prueba rápida del script (1 minuto en lugar de 5)
powershell -ExecutionPolicy Bypass -File .\run-perf.ps1 -Scenario normal -DurationSeconds 60

# Construir una vez y ejecutar sin red (lo que hace el pipeline)
powershell -ExecutionPolicy Bypass -File .\run-perf.ps1 -BuildOnly
powershell -ExecutionPolicy Bypass -File .\run-perf.ps1 -Scenario aforo -SkipBuild
```

| Escenario | Plan | Carga | Duración |
| --- | --- | --- | --- |
| `normal` | `carga.jmx` | 50 usuarios virtuales, ramp-up 30 s | 5 min |
| `pico` | `carga.jmx` | 200 usuarios virtuales, ramp-up 60 s | 5 min |
| `aforo` | `aforo-concurrente.jmx` | 1000 inscripciones a la vez, 100 cupos | ~20 s |

El script:
1. Construye el jar (`gradlew inMemoryBootJar`) y las dos imágenes, salvo con `-SkipBuild`.
2. Genera los secretos, levanta la API (`docker compose up --wait`) y espera a que `/actuator/health`
   responda `UP`.
3. Muestrea `docker stats` cada 5 s en segundo plano mientras corre JMeter.
4. Ejecuta JMeter (`-n -t ... -l ... -e -o ...`) contra `http://api:8080`.
5. Genera `resumen.md` y siempre baja los contenedores (`docker compose down`), aunque algo falle.

Código de salida: `0` si la prueba se ejecutó, aunque algún criterio no se cumpla (eso solo deja una
advertencia). `1` si hubo un error de ejecución o si el escenario `aforo` encontró sobrecupo.

Para cambiar el puerto local de la API: `$env:PERF_API_PORT = '18081'` antes de ejecutar.

## Qué hace cada plan

Ambos planes aceptan propiedades `-J`: `users`, `rampup`, `duration` (segundos), `host`, `port`, `adminUser`
y `adminPassword` (sin estas dos, las leen de `ADMIN_USERNAME` y `ADMIN_PASSWORD`). `aforo-concurrente.jmx`
acepta además `capacity`, `syncTimeout` y `resultsDir`.

**`carga.jmx`**
- *setUp*: `POST /api/auth/login` (el JSON Extractor toma el token) y crea 20 eventos de capacidad 50 con
  fechas dentro de los próximos 30 días. El token y los ids quedan como propiedades.
- *Usuarios virtuales*: cada uno, **una sola vez** (Once Only Controller), crea su usuario con correo y
  documento únicos y se inscribe en un evento aleatorio de los 20. Luego, en bucle hasta que termina el
  tiempo: `GET /api/events`, `GET /api/events/{id}`, `GET /api/events/{id}/availability`,
  `GET /api/events/{id}/waitlist` y `GET /api/users/{id}/events`, con un Uniform Random Timer de 0,5 a 1,5 s
  antes de cada petición (think time).
- Cada petición tiene un Response Assertion con su código esperado (200 o 201); la inscripción además un JSON
  Assertion (`status` debe ser `CONFIRMED` o `WAITING_LIST`).

**`aforo-concurrente.jmx`**
- *setUp*: login, 1 evento de capacidad 100 y 1000 usuarios.
- *Inscripciones simultáneas*: 1000 hilos; un Synchronizing Timer los retiene hasta que están todos y los
  suelta a la vez contra el mismo evento. Cuenta las respuestas por estado.
- *tearDown*: consulta `/availability` y falla con JSON Assertion si `registeredCount` no es 100 o si
  `waitlistCount` no es 900, y con un JSR223 Assertion si `registeredCount > capacity`. Guarda lo obtenido en
  `aforo-resultado.json`. Lo correcto es 100 `CONFIRMED` y 900 `WAITING_LIST`.

## Resultados

Cada ejecución deja `results/<escenario>-<fecha>/`:

| Archivo | Contenido |
| --- | --- |
| `resumen.md` | Por petición: muestras, promedio, p90, p95, p99, throughput y % de errores, con CUMPLE / NO CUMPLE; uso de CPU y memoria; en `aforo`, los conteos esperados y obtenidos |
| `dashboard/index.html` | Dashboard HTML de JMeter (abre `index.html` en el navegador) |
| `dashboard/statistics.json` | La tabla de estadísticas del dashboard; de aquí sale `resumen.md` |
| `resultados.jtl` | Cada muestra (CSV de JMeter) |
| `docker-stats.csv` | `timestamp, container, cpu_percent, mem_used_mib, mem_limit_mib, mem_percent` cada 5 s |
| `jmeter.log` | Log de JMeter |
| `aforo-resultado.json` | Solo en `aforo`: disponibilidad final y respuestas por estado |

**Criterios** (los aplica `resumen.md`):

| Criterio | Umbral |
| --- | --- |
| Errores por petición y total | < 1 % |
| p95 de las consultas (`GET`) | < 500 ms |
| p95 de la creación de usuario y la inscripción | < 1000 ms |
| Sobrecupos en `aforo` | 0 (exactamente 100 `CONFIRMED` y 900 `WAITING_LIST`) |

Las peticiones del setUp y del tearDown se marcan como *Preparacion* y solo se les exige el criterio de
errores.

**Cómo leer el dashboard.** *Statistics* tiene la misma tabla que `resumen.md` (más mínimo, máximo y KB/s).
*Response Times Over Time* y *Active Threads Over Time* muestran si la latencia sube con los usuarios
activos; *Response Time Percentiles* muestra la cola (p99 y máximo); *Errors* y *Top 5 Errors by sampler*
dicen qué falló y con qué código. El APDEX usa los umbrales por defecto de JMeter (500 ms y 1500 ms).

**Cómo leer `docker-stats.csv`.** La CPU es relativa a un núcleo: con el límite de 2 CPU el máximo es 200 %.
Si la API se acerca a 200 % o a 1024 MiB, el límite es el cuello de botella; si JMeter se acerca a su
límite, el generador de carga es el que limita y los resultados no son confiables.

## Resultados de referencia (2026-11-20)

Docker Desktop en Windows (equipo de 12 núcleos); la API con su límite de 2 CPU y 1 GiB.

| Escenario | Peticiones | Errores | p95 consultas | p95 crear usuario / inscribir | Throughput | CPU máx. / prom. API | Memoria máx. API | Resultado |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `normal` | 14302 | 0 % | 3 ms | 10 ms / 11 ms | 47.6 req/s | 71 % / 12 % | 244 MiB | CUMPLE |
| `pico` | 54038 | 0 % | 2 ms | 6 ms / 6 ms | 179.6 req/s | 101 % / 18 % | 224 MiB | CUMPLE |
| `aforo` | 2003 | 0 % | — | 7 ms / 1894 ms | 430 inscripciones/s | 112 % / 28 % | 272 MiB | 0 sobrecupos; p95 de inscripción NO CUMPLE |

En `aforo` la API respondió exactamente 100 `CONFIRMED` y 900 `WAITING_LIST`, y `/availability` quedó con
`registeredCount = 100` y `waitlistCount = 900`. JMeter nunca pasó de 126 % de CPU ni de 520 MiB, así que el
generador de carga no limitó los resultados.

**Dónde está el cuello de botella.**
- En `normal` y `pico` no hay: la API está lejos de sus límites. El *think time* de 0,5 a 1,5 s mantiene
  cada usuario en cerca de una petición por segundo.
- En `aforo` es el bloqueo por evento, y es intencional. Las 1000 inscripciones al mismo evento se atienden
  de una en una, unos 2,3 ms cada una (430 por segundo). La última espera unos 2 s, el promedio es 1 s y el
  p95 queda en 1,9 s. No es CPU (máximo 112 % de 200 %) ni memoria: es la espera que garantiza que nunca
  haya sobrecupo. Para bajar ese p95 haría falta otro diseño (por ejemplo, una cola de inscripciones), no
  más recursos. Con PostgreSQL el bloqueo de fila también atiende de a una y cada inscripción tarda más, así
  que en staging el p95 será mayor.
- La prueba de `aforo` dura unos 20 s y `docker stats` toma una muestra cada 5 s, así que el pico real de
  CPU puede ser algo mayor que el registrado.

## Limitación

Estas pruebas miden la API (Spring MVC, seguridad JWT, serialización JSON, reglas de negocio y el bloqueo por
evento) con los repositorios en memoria. **No miden PostgreSQL**: ni el pool de conexiones, ni el SQL de
Hibernate, ni la E/S de disco, ni el bloqueo real de fila (`SELECT ... FOR UPDATE`). Con la base de datos los
tiempos serán mayores; esa medición queda para un ambiente **staging** con PostgreSQL 16 y los mismos planes
(`-Jhost` y `-Jport` apuntando a ese ambiente).
