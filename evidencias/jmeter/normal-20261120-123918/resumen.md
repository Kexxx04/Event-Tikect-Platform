# Resumen de rendimiento: escenario normal

- Fecha: 2026-11-20 12:44:38
- Parametros: users=50, rampup=30, duration=300
- API: event-platform-inmemory.jar (perfil inmemory, sin base de datos), limitada a 2 CPU y 1 GiB
- Criterios: errores < 1 %; p95 < 500 ms en consultas y < 1000 ms en creacion de usuario e inscripcion; 0 sobrecupos en aforo

| Peticion | Tipo | Muestras | Promedio (ms) | p90 (ms) | p95 (ms) | p99 (ms) | Throughput (req/s) | Errores (%) | Criterio | Resultado |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |
| GET /api/events | Consulta | 2855 | 2 | 3 | 3 | 5 | 9.6 | 0.00 | p95 < 500 ms; errores < 1 % | CUMPLE |
| GET /api/events/{id} | Consulta | 2846 | 1 | 2 | 3 | 5 | 9.6 | 0.00 | p95 < 500 ms; errores < 1 % | CUMPLE |
| GET /api/events/{id}/availability | Consulta | 2837 | 1 | 3 | 3 | 5 | 9.6 | 0.00 | p95 < 500 ms; errores < 1 % | CUMPLE |
| GET /api/events/{id}/waitlist | Consulta | 2826 | 1 | 3 | 3 | 5 | 9.6 | 0.00 | p95 < 500 ms; errores < 1 % | CUMPLE |
| GET /api/users/{id}/events | Consulta | 2817 | 2 | 3 | 3 | 5 | 9.6 | 0.00 | p95 < 500 ms; errores < 1 % | CUMPLE |
| POST /api/auth/login (setUp) | Preparacion | 1 | 312 | 312 | 312 | 312 | 3.2 | 0.00 | errores < 1 % | CUMPLE |
| POST /api/events (setUp) | Preparacion | 20 | 10 | 9 | 66 | 69 | 76.6 | 0.00 | errores < 1 % | CUMPLE |
| POST /api/events/{id}/registrations | Creacion | 50 | 5 | 7 | 11 | 19 | 1.7 | 0.00 | p95 < 1000 ms; errores < 1 % | CUMPLE |
| POST /api/users | Creacion | 50 | 6 | 10 | 10 | 32 | 1.7 | 0.00 | p95 < 1000 ms; errores < 1 % | CUMPLE |
| Total | Total | 14302 | 2 | 3 | 3 | 5 | 47.6 | 0.00 | errores < 1 % | CUMPLE |

## Recursos de los contenedores (docker stats cada 5 s)

| Contenedor | Muestras | CPU maxima (%) | CPU promedio (%) | Memoria maxima (MiB) | Limite (MiB) |
| --- | ---: | ---: | ---: | ---: | ---: |
| event-platform-perf-api-1 | 61 | 71.1 | 11.8 | 244.1 | 1024.0 |
| event-platform-perf-jmeter-run-546966cf3703 | 60 | 87.1 | 6.3 | 476.6 | 2048.0 |

La CPU de docker stats es relativa a un nucleo: con el limite de 2 CPU, el maximo posible es 200 %.

**Resultado global: CUMPLE todos los criterios.**
