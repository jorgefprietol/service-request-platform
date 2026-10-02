# Evolución y deuda técnica

El alcance actual entrega creación durable, atención y asignación con concurrencia, identidad OIDC individual, SLA, alertas persistentes, plantillas, auditoría, consola y pipeline. Las siguientes iniciativas requieren nuevos criterios de aceptación y evidencia propia.

| Prioridad | Iniciativa | Motivo / aceptación pendiente |
| --- | --- | --- |
| 1 | Autorización por organización y directorio sincronizado | Verificar aislamiento tenant, revocaciones y bajas del directorio |
| 1 | TLS, límites de cuerpo y tasa en proxy | Controlar exposición externa y carga abusiva |
| 1 | Automatizar ensayo de restore y política de retención | Acordar RPO/RTO y demostrar recuperación de respaldo |
| 2 | Outbox y notificaciones externas durables | Entregar avisos fuera de la consola sin perder eventos después de commit |
| 2 | Calendarios laborales y políticas SLA por servicio | Validar husos horarios, pausas y plazos de reapertura |
| 2 | Correlación de peticiones y OpenTelemetry | Reconstruir errores sin exponer contenido sensible |
| 2 | Pruebas de carga representativa | Medir p95/p99 y capacidad antes de declarar SLO |
| 3 | Búsqueda y filtros avanzados | Ampliar la consulta operativa para bandejas grandes |

## Registro de deuda

| Decisión consciente | Riesgo | Acción de seguimiento |
| --- | --- | --- |
| Proveedor OIDC local en start-dev | Configuración e identidad de verificación, sin alta disponibilidad | Proveedor de producción, TLS y respaldo antes de adopción compartida |
| Directorio basado en operadores autenticados | Puede conservar subjects cuyo rol cambió en el IdP | Sincronizar altas/bajas según necesidades organizacionales |
| Auditoría protegida solo por API | Administrador DB puede alterarla | Roles DB separados y almacenamiento externo verificable si se exige |
| Paginación offset | Variación bajo escrituras concurrentes | Cursor por fecha/UUID si las bandejas crecen |
| Catálogo de plantillas en composición | Requiere entrega para editar categorías | Puerto de catálogo con aprobación/versionado cuando sea necesario |
| Errores internos sin detalle de stack | Diagnóstico limitado | Correlación y logs estructurados con revisión de datos |
| Pruebas rápidas con H2 | Diferencias semánticas con PostgreSQL | Mantener gate E2E PostgreSQL para cada entrega |

Estas elecciones son prudentes y deliberadas para un proyecto de alcance acotado. Una vulnerabilidad corregible, una ruptura de arquitectura o una pérdida de integridad no se acepta como deuda: bloquea la entrega. Una actualización de dependencia debe pasar las mismas comprobaciones antes de promoverse.
