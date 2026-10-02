# Experiencia técnica — Service Request Platform

**Jorge Prieto · Proyecto independiente de ingeniería de software · 2026**

Diseño e implementación de una plataforma de solicitudes de servicio con Java 21, Spring Boot y PostgreSQL. El proyecto incorpora arquitectura limpia, requisitos trazables, patrones de diseño aplicados a casos de negocio y entrega automatizada de contenedores.

## Responsabilidades y resultados

- Definición de requisitos funcionales y atributos de calidad, priorización MoSCoW y criterios de aceptación vinculados a pruebas.
- Diseño de un dominio inmutable, flujo de estados y políticas SLA extensibles mediante interfaces.
- Implementación de creación idempotente y control optimista de concurrencia con garantías apoyadas por constraints y transacciones PostgreSQL.
- Desarrollo de API REST, plantillas, consola operativa, autorización por rol y contrato OpenAPI.
- Validación de rollback, persistencia tras reinicios, aislamiento de datos y comportamiento ante interrupciones de base de datos.
- Automatización de pruebas, cobertura, análisis de arquitectura, escaneo, SBOM y publicación de la imagen verificada en GHCR con procedencia.
- Documentación de decisiones arquitectónicas, modelo de datos, diagramas de clases, operación y evolución.

## Descripción breve para perfil profesional

> Diseñé e implementé Service Request Platform, una plataforma de operaciones en Java y PostgreSQL con arquitectura limpia, SLA por prioridad, idempotencia durable, auditoría transaccional y control de concurrencia. Integré pruebas automatizadas y una entrega Docker mediante GitHub Actions, con análisis de seguridad, SBOM y procedencia del artefacto.

Repositorio y evidencia: [Service Request Platform](https://github.com/jorgefprietol/service-request-platform), [GitHub Actions](https://github.com/jorgefprietol/service-request-platform/actions) y [registro de verificación](verification.md).

La descripción se refiere al trabajo implementado y verificado en este repositorio. No atribuye uso empresarial, clientes, empleo, volumen de usuarios ni métricas de producción.
