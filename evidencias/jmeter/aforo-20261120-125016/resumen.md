# Resumen de rendimiento: escenario aforo

- Fecha: 2026-11-20 12:50:51
- Parametros: users=1000, capacity=100, rampup=10
- API: event-platform-inmemory.jar (perfil inmemory, sin base de datos), limitada a 2 CPU y 1 GiB
- Criterios: errores < 1 %; p95 < 500 ms en consultas y < 1000 ms en creacion de usuario e inscripcion; 0 sobrecupos en aforo

| Peticion | Tipo | Muestras | Promedio (ms) | p90 (ms) | p95 (ms) | p99 (ms) | Throughput (req/s) | Errores (%) | Criterio | Resultado |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| GET /api/events/{id}/availability (tearDown) | Preparacion | 1 | 68 | 68 | 68 | 68 | 14.7 | 0.00 | errores < 1 % | CUMPLE |
| POST /api/auth/login (setUp) | Preparacion | 1 | 317 | 317 | 317 | 317 | 3.2 | 0.00 | errores < 1 % | CUMPLE |
| POST /api/events (setUp) | Preparacion | 1 | 74 | 74 | 74 | 74 | 13.5 | 0.00 | errores < 1 % | CUMPLE |
| POST /api/events/{id}/registrations | Creacion | 1000 | 1006 | 1870 | 1894 | 2080 | 429.6 | 0.00 | p95 < 1000 ms; errores < 1 % | NO CUMPLE |
| POST /api/users (setUp) | Preparacion | 1000 | 4 | 5 | 7 | 9 | 202.4 | 0.00 | errores < 1 % | CUMPLE |
| Total | Total | 2003 | 504 | 1826 | 1870 | 2072 | 111.9 | 0.00 | errores < 1 % | CUMPLE |

## Aforo

| Dato | Esperado | Obtenido |
| --- | ---: | ---: |
| registeredCount (API) | 100 | 100 |
| waitlistCount (API) | 900 | 900 |
| Respuestas CONFIRMED | 100 | 100 |
| Respuestas WAITING_LIST | 900 | 900 |

**0 sobrecupos: CUMPLE.** Exactamente 100 CONFIRMED y 900 WAITING_LIST.

## Recursos de los contenedores (docker stats cada 5 s)

| Contenedor | Muestras | CPU maxima (%) | CPU promedio (%) | Memoria maxima (MiB) | Limite (MiB) |
| --- | ---: | ---: | ---: | ---: | ---: |
| event-platform-perf-api-1 | 5 | 111.5 | 28.1 | 271.8 | 1024.0 |
| event-platform-perf-jmeter-run-2238f1a0cbe3 | 4 | 126.1 | 61.8 | 458.5 | 2048.0 |

La CPU de docker stats es relativa a un nucleo: con el limite de 2 CPU, el maximo posible es 200 %.

**Resultado global: NO CUMPLE** 1 criterio(s):

- POST /api/events/{id}/registrations: p95 < 1000 ms; errores < 1 %
