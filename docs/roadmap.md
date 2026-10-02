# Evolución y deuda técnica

El alcance inicial entrega creación durable, atención con concurrencia, SLA, plantillas, auditoría, consola y pipeline. Las siguientes iniciativas requieren nuevos criterios de aceptación y evidencia propia.

| Prioridad | Iniciativa | Motivo / aceptación pendiente |
| --- | --- | --- |
| 1 | Identidad OIDC individual y autorización por organización | Separar usuarios reales, revocar sesiones, verificar aislamiento tenant |
| 1 | TLS, límites de cuerpo y tasa en proxy | Controlar exposición externa y carga abusiva |
| 1 | Automatizar ensayo de restore y política de retención | Acordar RPO/RTO y demostrar recuperación de respaldo |
| 2 | Outbox y notificaciones durables | Entregar avisos sin perder eventos después de commit |
| 2 | Calendarios laborales y políticas SLA por servicio | Validar husos horarios, pausas y plazos de reapertura |
| 2 | Correlación de peticiones y OpenTelemetry | Reconstruir errores sin exponer contenido sensible |
| 2 | Pruebas de carga representativa | Medir p95/p99 y capacidad antes de declarar SLO |
| 3 | Asignación individual y búsqueda | Incorporar el flujo completo de mesa de servicio |

## Registro de deuda

| Decisión consciente | Riesgo | Acción de seguimiento |
| --- | --- | --- |
| Dos identidades de máquina | Sin atribución individual de acciones | OIDC antes de uso multiusuario |
| Auditoría protegida solo por API | Administrador DB puede alterarla | Roles DB separados y almacenamiento externo verificable si se exige |
| Paginación offset | Variación bajo escrituras concurrentes | Cursor por fecha/UUID si las bandejas crecen |
| Catálogo de plantillas en composición | Requiere entrega para editar categorías | Puerto de catálogo con aprobación/versionado cuando sea necesario |
| Errores internos sin detalle de stack | Diagnóstico limitado | Correlación y logs estructurados con revisión de datos |
| Pruebas rápidas con H2 | Diferencias semánticas con PostgreSQL | Mantener gate E2E PostgreSQL para cada entrega |

Estas elecciones son prudentes y deliberadas para un proyecto de alcance acotado. Una vulnerabilidad corregible, una ruptura de arquitectura o una pérdida de integridad no se acepta como deuda: bloquea la entrega. Una actualización de dependencia debe pasar las mismas comprobaciones antes de promoverse.
