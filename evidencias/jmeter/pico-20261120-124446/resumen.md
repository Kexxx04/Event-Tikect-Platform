# Resumen de rendimiento: escenario pico

- Fecha: 2026-11-20 12:50:07
- Parametros: users=200, rampup=60, duration=300
- API: event-platform-inmemory.jar (perfil inmemory, sin base de datos), limitada a 2 CPU y 1 GiB
- Criterios: errores < 1 %; p95 < 500 ms en consultas y < 1000 ms en creacion de usuario e inscripcion; 0 sobrecupos en aforo

| Peticion | Tipo | Muestras | Promedio (ms) | p90 (ms) | p95 (ms) | p99 (ms) | Throughput (req/s) | Errores (%) | Criterio | Resultado |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| GET /api/events | Consulta | 10809 | 1 | 2 | 2 | 3 | 36.4 | 0.00 | p95 < 500 ms; errores < 1 % | CUMPLE |
| GET /api/events/{id} | Consulta | 10761 | 1 | 2 | 2 | 3 | 36.3 | 0.00 | p95 < 500 ms; errores < 1 % | CUMPLE |
| GET /api/events/{id}/availability | Consulta | 10731 | 1 | 2 | 2 | 3 | 36.3 | 0.00 | p95 < 500 ms; errores < 1 % | CUMPLE |
| GET /api/events/{id}/waitlist | Consulta | 10679 | 1 | 2 | 2 | 3 | 36.3 | 0.00 | p95 < 500 ms; errores < 1 % | CUMPLE |
| GET /api/users/{id}/events | Consulta | 10637 | 1 | 2 | 2 | 3 | 36.3 | 0.00 | p95 < 500 ms; errores < 1 % | CUMPLE |
| POST /api/auth/login (setUp) | Preparacion | 1 | 305 | 305 | 305 | 305 | 3.3 | 0.00 | errores < 1 % | CUMPLE |
| POST /api/events (setUp) | Preparacion | 20 | 10 | 16 | 70 | 73 | 74.9 | 0.00 | errores < 1 % | CUMPLE |
| POST /api/events/{id}/registrations | Creacion | 200 | 3 | 5 | 6 | 15 | 3.4 | 0.00 | p95 < 1000 ms; errores < 1 % | CUMPLE |
| POST /api/users | Creacion | 200 | 4 | 6 | 6 | 21 | 3.4 | 0.00 | p95 < 1000 ms; errores < 1 % | CUMPLE |
| Total | Total | 54038 | 1 | 1 | 1 | 2 | 179.6 | 0.00 | errores < 1 % | CUMPLE |

## Recursos de los contenedores (docker stats cada 5 s)

| Contenedor | Muestras | CPU maxima (%) | CPU promedio (%) | Memoria maxima (MiB) | Limite (MiB) |
| --- | ---: | ---: | ---: | ---: | ---: |
| event-platform-perf-api-1 | 62 | 100.6 | 18.2 | 223.6 | 1024.0 |
| event-platform-perf-jmeter-run-22ede27365ff | 61 | 85.9 | 11.1 | 513.4 | 2048.0 |

La CPU de docker stats es relativa a un nucleo: con el limite de 2 CPU, el maximo posible es 200 %.

**Resultado global: CUMPLE todos los criterios.**
