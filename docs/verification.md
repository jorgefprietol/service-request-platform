# Registro de verificación

Verificación: **2 de octubre de 2026**. La entrega del commit `004581bd5ba264e241e03b4598acac9ff351ba6d` completó correctamente todos los jobs de la [ejecución 37054441202](https://github.com/jorgefprietol/service-request-platform/actions/runs/37054441202).

## Java

Ejecutado `mvn -B -ntp verify` con JDK 21 en Windows local y en runners de Windows y Ubuntu:

| Grupo | Pruebas | Resultado |
| --- | --- | --- |
| Dominio | 35 | Correcto |
| API y transacciones | 13 | Correcto |
| Validación y roles OIDC | 2 | Correcto |
| Reglas ArchUnit | 3 | Correcto |
| Total | 53 | Sin fallos, errores ni omisiones |

JaCoCo: **58/58 líneas del dominio cubiertas (100%)** y **52/56 ramas (92,9%)**. El gate exige al menos 90% de líneas de ese paquete; la cifra no representa cobertura de todo el repositorio.

Las pruebas API usan H2 en modo PostgreSQL para verificación rápida. Las garantías específicas de PostgreSQL se verifican separadamente con el stack Docker.

## Integración

Se aprobaron **53 escenarios HTTP con PostgreSQL y Keycloak reales**, ejecutando `python scripts/e2e.py`:

- Identidades individuales, firma JWT, audiencia, roles y aislamiento entre solicitantes.
- Creación idempotente entre ocho clientes concurrentes y persistencia de claves tras reinicios.
- Transiciones, ETags y una única confirmación entre dos cambios simultáneos.
- Rollback de la solicitud cuando una constraint impide escribir su auditoría.
- Asignación a un subject registrado y auditoría del actor y destinatario.
- Detección de SLA vencido, deduplicación, persistencia y supresión de alertas en estados terminales.
- Reinicios de API y PostgreSQL; readiness 503 durante el corte de DB y liveness 200.

El [reporte conservado](evidence/e2e.json) contiene los 53 escenarios aprobados. GitHub Actions conserva los reportes JUnit, cobertura, diagnósticos de contenedores y análisis completos durante 14 días.

## Seguridad y entrega

El escaneo de secretos terminó correctamente. Trivy no reportó hallazgos HIGH/CRITICAL con corrección disponible bajo el filtro obligatorio de publicación. Esto describe el alcance del gate, no una ausencia de vulnerabilidades de cualquier severidad.

El SBOM CycloneDX identifica **las 56 bibliotecas Java empaquetadas**; la [comprobación de cobertura](evidence/sbom-coverage.json) terminó correctamente. El pipeline bloquea una identificación incompleta antes de publicar.

Se publicó la misma imagen sometida a integración y análisis, sin reconstrucción en el job de entrega:

```text
ghcr.io/jorgefprietol/service-request-platform:sha-004581bd5ba264e241e03b4598acac9ff351ba6d
sha256:3aa01ad90d4a03d477f05a9a0887ae958f8a843220d13fa9900242e25ab75f07
```

La [atestación de procedencia](https://github.com/jorgefprietol/service-request-platform/attestations/52267239) vincula ese digest con el repositorio y la ejecución de Actions. Los resultados posteriores se consultan en [GitHub Actions](https://github.com/jorgefprietol/service-request-platform/actions).

Las pruebas de reinicio comprueban conservación de datos e idempotencia; no constituyen una medición de disponibilidad, benchmark, certificación de seguridad ni prueba de restauración de backup.
