# Resumen de rendimiento: escenario aforo

- Fecha: 2026-11-20 14:25:11
- Parametros: users=1000, capacity=100, rampup=10
- API: event-platform-inmemory.jar (perfil inmemory, sin base de datos), limitada a 2 CPU y 1 GiB
- Criterios: errores < 1 %; p95 < 500 ms en consultas y < 1000 ms en creacion de usuario e inscripcion; 0 sobrecupos en aforo

| Peticion | Tipo | Muestras | Promedio (ms) | p90 (ms) | p95 (ms) | p99 (ms) | Throughput (req/s) | Errores (%) | Criterio | Resultado |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| GET /api/events/{id}/availability (tearDown) | Preparacion | 1 | 11 | 11 | 11 | 11 | 90.9 | 0.00 | errores < 1 % | CUMPLE |
| POST /api/auth/login (setUp) | Preparacion | 1 | 309 | 309 | 309 | 309 | 3.2 | 0.00 | errores < 1 % | CUMPLE |
| POST /api/events (setUp) | Preparacion | 1 | 61 | 61 | 61 | 61 | 16.4 | 0.00 | errores < 1 % | CUMPLE |
| POST /api/events/{id}/registrations | Creacion | 1000 | 1003 | 1836 | 2011 | 2155 | 430.7 | 0.00 | p95 < 1000 ms; errores < 1 % | NO CUMPLE |
| POST /api/users (setUp) | Preparacion | 1000 | 4 | 5 | 6 | 10 | 196.0 | 0.00 | errores < 1 % | CUMPLE |
| Total | Total | 2003 | 503 | 1546 | 1836 | 2146 | 111.4 | 0.00 | errores < 1 % | CUMPLE |

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
| event-platform-perf-api-1 | 5 | 114.0 | 42.9 | 276.6 | 1024.0 |
| event-platform-perf-jmeter-run-9b3f991fdd19 | 4 | 102.9 | 51.6 | 479.9 | 2048.0 |

La CPU de docker stats es relativa a un nucleo: con el limite de 2 CPU, el maximo posible es 200 %.

**Resultado global: NO CUMPLE** 1 criterio(s):

- POST /api/events/{id}/registrations: p95 < 1000 ms; errores < 1 %
