# Decisiones de arquitectura

## ADR-001 — Monolito modular con puertos

Aceptada. Solicitudes y auditoría comparten una única transacción y un mismo ciclo de despliegue. Se elige un monolito modular con dependencias hacia el dominio y aplicación. Alternativas: arquitectura puramente por capas con dominio dependiente de ORM; microservicios con coordinación eventual. Consecuencia: menor costo operativo y reglas testeables; el límite de módulo debe sostenerse con ArchUnit y revisión.

## ADR-002 — PostgreSQL y SQL explícito

Aceptada. PostgreSQL guarda solicitudes y auditoría con constraints e índices. Spring JDBC implementa el puerto de persistencia y Flyway evoluciona el esquema. Alternativa: JPA, conveniente para grafos grandes pero innecesario para dos tablas y consultas concretas. Consecuencia: control visible de concurrencia y transacciones; mapeo manual que necesita pruebas de integración.

## ADR-003 — Idempotencia durable y control optimista

Aceptada. Una clave por principal y contenido identifica una creación. La unicidad en DB arbitra carreras. Una revisión en `If-Match` participa en el UPDATE condicional. Alternativas: caché de claves en memoria; locks distribuidos; último escritor gana. Consecuencia: reintentos sobreviven reinicios y se detectan ediciones obsoletas. El replay devuelve la representación actual, no una copia del HTTP original. El código 409 de versión obsoleta forma parte del contrato de esta API.

## ADR-004 — Auditoría transaccional

Aceptada. Cada creación y transición persiste su entrada de auditoría dentro de la transacción del agregado. Alternativa: escribir logs o publicar eventos después del commit sin outbox. Consecuencia: un fallo de auditoría revierte la operación; mayor latencia de escritura aceptada por integridad. Para integración externa se evaluará un outbox durable. La API no permite editar auditorías; no existe protección ante administradores DB.

## ADR-005 — OIDC individual y despliegue local

Aceptada. OIDC es el modo predeterminado; Keycloak suministra identidades individuales y la consola utiliza Authorization Code con PKCE. La API valida firma, issuer, audience, fechas y subject, y autoriza por roles. Los tokens permanecen en memoria de la consola; el verificador PKCE y state se conservan temporalmente en sessionStorage durante la redirección y se eliminan al consumirlos. Alternativa: tokens de máquina compartidos, disponibles solo bajo el modo explícito development-tokens para pruebas rápidas. Consecuencia: atribución individual y aislamiento por subject, con una dependencia adicional de identidad. El proveedor local usa start-dev; una adopción externa exige configuración de producción y TLS.

## ADR-008 — Asignación y alertas persistentes

Aceptada. Asignación comparte la revisión del agregado y transacción de auditoría. Solo se aceptan subjects de operadores que ya se autenticaron y quedaron registrados. El monitor SLA persiste una detección por solicitud mediante una clave única, sin enviar mensajes externos. Consecuencia: alertas sobreviven reinicios y no se duplican entre monitores; la asignación usa un directorio observado, que requerirá sincronización si una organización exige comprobar altas y bajas en tiempo real.

## ADR-006 — Entregar el artefacto probado

Aceptada. Se construye una imagen, se verifica con PostgreSQL, se analiza y se conserva como artefacto temporal. El job de publicación carga exactamente esa imagen, la etiqueta por commit y la publica con procedencia. Alternativa: reconstruir al publicar. Consecuencia: lo probado coincide con lo entregado; transferencia de un archivo de imagen aumenta tiempo y almacenamiento de CI. Las credenciales nunca entran en argumentos de build.

## ADR-007 — Aplicación selectiva de patrones

Aceptada. Builder, Prototype, Strategy, Command, Factory, Repository y Adapter resuelven necesidades presentes. No se crea una colección artificial de patrones sin casos de uso. Consecuencia: extensibilidad concreta con pocas abstracciones; nuevas jerarquías requieren demostrar una variación real y criterios de aceptación.
