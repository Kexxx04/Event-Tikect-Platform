# Evidencias de calidad — Event Platform

Reportes en el formato original de cada herramienta, más capturas de pantalla. Todo se ejecutó el 20 de noviembre de 2026 sobre la rama `develop`. El pipeline corresponde a la ejecución #20261120.1 (commit `6b6a509`, merge del PR #23).

Ninguna prueba usa base de datos: las pruebas automatizadas y JMeter corren sobre los repositorios en memoria. Los reportes HTML se abren directamente en el navegador desde su `index.html`.

## Resultados

| Herramienta | Resultado |
| --- | --- |
| JUnit (`test`) | 393 pruebas, 0 fallos, 0 omitidas |
| Cucumber (`acceptanceTest`) | 37 pruebas, 0 fallos |
| JaCoCo | Líneas 99.6 % (534/536), ramas 100 % (86/86) |
| PIT | 94 % (217/230) antes y 100 % (194/194) después de eliminar código muerto y reforzar la prueba de `SecurityConfig` |
| SonarQube | 10 hallazgos: 8 corregidos y 2 aceptados con justificación. 0 abiertos, calificación A en seguridad, confiabilidad y mantenibilidad, quality gate aprobado, cobertura 99.7 %, duplicación 0 %, 0 security hotspots |
| Serenity BDD (E2E) | 11 escenarios, 15 casos, 0 fallos |
| OWASP ZAP | 20 operaciones del contrato OpenAPI con JWT. 0 alertas altas, medias o bajas; 3 informativas |
| JMeter normal (50 usuarios, 5 min) | 14 302 peticiones, 0 % de errores, p95 de consultas 3 ms: **CUMPLE** |
| JMeter pico (200 usuarios, 5 min) | 54 038 peticiones, 0 % de errores, p95 de consultas 2 ms: **CUMPLE** |
| JMeter aforo (1000 inscripciones simultáneas, 100 cupos) | 100 CONFIRMED y 900 WAITING_LIST, 0 sobrecupos. p95 de inscripción 1894 ms, por encima del límite de 1000 ms: **NO CUMPLE** el tiempo de respuesta |

En JMeter la API corre con el perfil en memoria, limitada a 2 CPU y 1 GiB. En el escenario aforo el cuello de botella es el bloqueo por evento: serializa las inscripciones del mismo evento a propósito, para que nunca haya sobrecupos.

## Carpeta principal

| Carpeta | Herramienta | Contenido |
| --- | --- | --- |
| [`docs/`](docs/) | Entrega | [`Plan de pruebas — Event Platform.pdf`](<docs/Plan de pruebas — Event Platform.pdf>) y [`Conclusión técnica — Event Platform.pdf`](<docs/Conclusión técnica — Event Platform.pdf>) |
| [`pit-antes/`](pit-antes/index.html), [`pit-despues/`](pit-despues/index.html) | PIT | Reporte HTML antes y después de las correcciones |
| [`jmeter/normal-20261120-123918/`](jmeter/normal-20261120-123918/resumen.md), [`jmeter/pico-20261120-124446/`](jmeter/pico-20261120-124446/resumen.md), [`jmeter/aforo-20261120-125016/`](jmeter/aforo-20261120-125016/resumen.md) | JMeter | Escenarios oficiales ejecutados en local: `resumen.md`, `dashboard/index.html`, `resultados.jtl`, `docker-stats.csv` y `jmeter.log`. Aforo incluye también `aforo-resultado.json` |
| [`jmeter/capturas/`](jmeter/capturas/) | JMeter | Tabla de resultados de los tres escenarios y análisis del cuello de botella |
| [`e2e/serenity/`](e2e/serenity/index.html) | Serenity BDD | Reporte E2E (`index.html`, `summary.txt`) |

## `masEvidencias/`

| Carpeta | Herramienta | Contenido |
| --- | --- | --- |
| [`pit/capturas/`](masEvidencias/pit/capturas/) | PIT | Resultados sin base de datos, mutantes sobrevivientes antes de corregir, comparación antes/después y análisis del mutante de `SecurityConfig` |
| [`sonar/antes/`](masEvidencias/sonar/antes/) | SonarQube | Overview y lista de los 10 hallazgos iniciales |
| [`sonar/despues/`](masEvidencias/sonar/despues/) | SonarQube | Issues tras las correcciones, gráfica de actividad (10 → 2), hallazgos aceptados, overview del código nuevo y overview final |
| [`zap/`](masEvidencias/zap/reporte-zap.html) | OWASP ZAP | `reporte-zap.html`, `reporte-zap.json`, contrato `openapi.json` usado y `ejecucion.txt` con el resumen de la ejecución |
| [`pipeline/capturas/`](masEvidencias/pipeline/capturas/) | Azure Pipelines | Ejecución #20261120.1: ejecución en curso, log del escenario normal de JMeter, pasos en verde, resumen de pruebas y cobertura, advertencias, el error de la publicación de cobertura y el log de su reintento |
| [`pipeline/artefactos/quality-reports/`](masEvidencias/pipeline/artefactos/quality-reports/) | Azure Pipelines | Reportes de JUnit (`tests/test`), Cucumber (`tests/acceptanceTest` y `cucumber/`), JaCoCo (`jacoco/`) y PIT (`pitest/`) |
| [`pipeline/artefactos/code-coverage-report/`](masEvidencias/pipeline/artefactos/code-coverage-report/index.html) | Azure Pipelines | Reporte de la pestaña Code Coverage |
| [`pipeline/artefactos/reporte-seguridad-zap/`](masEvidencias/pipeline/artefactos/reporte-seguridad-zap/) | Azure Pipelines | El mismo reporte de ZAP de `zap/`, más los logs de la aplicación y de acceso HTTP durante el escaneo |
| [`pipeline/artefactos/reporte-rendimiento-jmeter/`](masEvidencias/pipeline/artefactos/reporte-rendimiento-jmeter/) | Azure Pipelines | Escenarios que corre el pipeline. Normal: 14 190 peticiones, 0 % de errores, p95 de consultas 4 ms (CUMPLE). Aforo: 0 sobrecupos, p95 de inscripción 2011 ms (NO CUMPLE el tiempo). El escenario pico solo se ejecuta en local |
| [`jmeter/capturas/`](masEvidencias/jmeter/capturas/) | JMeter | Copia de `jmeter/capturas/` |
