# Registro de verificación

Fecha de verificación inicial: **2 de octubre de 2026**. Los reportes de cada commit se generan en GitHub Actions; los resultados locales corresponden al código implementado en este repositorio.

## Java

Ejecutado `mvn -B -ntp verify` con JDK 21 en Windows:

| Grupo | Pruebas | Resultado |
| --- | --- | --- |
| Dominio | 34 | Correcto |
| API y transacciones | 10 | Correcto |
| Reglas ArchUnit | 3 | Correcto |
| Total | 47 | Sin fallos, errores ni omisiones |

JaCoCo: **51/51 líneas del dominio cubiertas (100%)**; **36/38 ramas (94,7%)**. El gate exige al menos 90% de líneas del dominio. Estas métricas corresponden a ese paquete y no se atribuyen al repositorio completo.

Las pruebas API usan H2 en modo PostgreSQL para verificación rápida. Las garantías específicas de PostgreSQL se verifican separadamente con el stack Docker.

## Integración y entrega

La verificación end-to-end ejecuta `python scripts/e2e.py` con PostgreSQL real y genera `artifacts/e2e.json`. Se aprobaron **39 escenarios HTTP/PostgreSQL** en la ejecución [37051327988](https://github.com/jorgefprietol/service-request-platform/actions/runs/37051327988). Esa ejecución quedó cancelada al actualizar el build y su análisis de seguridad encontró un límite temporal de consultas Maven; no se considera una entrega completa aprobada. Las evidencias vigentes se consultan en [GitHub Actions](https://github.com/jorgefprietol/service-request-platform/actions).

El pipeline conserva resultados JUnit, cobertura HTML/XML, reporte E2E, diagnósticos de contenedores, vulnerabilidades y SBOM. La publicación depende de que los jobs previos terminen correctamente. Una configuración de workflow por sí sola no acredita que un run haya sido aprobado: debe consultarse su estado y evidencia.

Las pruebas de reinicio comprueban conservación de datos e idempotencia; no constituyen una medición de disponibilidad, benchmark, certificación de seguridad ni prueba de restauración de backup.
