# Operación y entrega

## Arranque y apagado

Desde la raíz del proyecto: `python scripts/init_env.py`, luego `docker compose up -d --build --wait --wait-timeout 180`. La consola está en `http://127.0.0.1:18110`. Los tokens se consultan únicamente en `.env` local. `docker compose stop` pausa servicios; `docker compose up -d --wait` los recupera. `docker compose down` elimina contenedores y red, conservando el volumen.

`docker compose down --volumes` elimina datos: se reserva para stacks descartables de CI o una limpieza deliberada. El pipeline trabaja sobre su propio runner y elimina su volumen al terminar.

## Verificación integrada

`python scripts/e2e.py` necesita Python 3.11+, Docker y el stack activo. Crea solicitudes, introduce temporalmente una auditoría con revisión duplicada para comprobar rollback, reinicia API y corta DB. Se ejecuta en un entorno de desarrollo o CI que puede sufrir esa interrupción. El script no debe ejecutarse contra una instancia compartida en uso.

El reporte `artifacts/e2e.json` incluye nombres de escenarios y resultado, sin secretos. La evidencia Java reside en `target/surefire-reports` y `target/site/jacoco`.

Compose utiliza por defecto la subred `10.241.110.0/24`, evitando depender de los pools automáticos de Docker. Antes del primer arranque comprueba que no se solape con redes o VPN existentes; puedes cambiarla con `COMPOSE_SUBNET` en `.env`. El volumen y servicios pertenecen al proyecto `service-request-platform`.

## Salud y diagnóstico

- `/actuator/health/liveness` valida el proceso; un corte DB no provoca reinicios de aplicación por liveness.
- `/actuator/health/readiness` incorpora el pool y conexión DB; devuelve 503 si la base no está disponible.
- `/actuator/metrics` requiere operador e incluye métricas de HTTP, JVM y pool JDBC.
- `docker compose ps` muestra estado; `docker compose logs --tail 100 api` permite inspeccionar arranque, migraciones y errores.

La aplicación no registra credenciales ni cuerpos de solicitudes. Los errores internos responden con un problema genérico y registran únicamente la clase de excepción. Esto limita exposición, pero no aporta correlación distribuida; se considera en roadmap. Los logs Docker rotan a tres archivos de hasta 10 MiB por servicio.

## Credenciales y rotación

`.env` está excluido de Git y del contexto Docker. El script crea el archivo con modo 0600 en sistemas que soportan permisos POSIX; en Windows se aplican los permisos heredados de la carpeta del usuario. Mantén acceso local limitado al directorio.

Para rotar un token, genera uno con `python -c "import secrets; print(secrets.token_hex(32))"`, actualiza el campo correspondiente en `.env` y recrea la API con `docker compose up -d --force-recreate api`. No guardes tokens en commits, issues, logs ni argumentos de build. Rotar la contraseña de PostgreSQL requiere cambiar primero la credencial dentro de DB y luego la configuración del cliente: modificar `.env` por sí solo no cambia una base ya inicializada.

La API usa dos identidades de máquina compartidas. Antes de una adopción multiusuario: integrar OIDC, identificar usuarios individualmente, definir permisos y tenancy, habilitar TLS y limitar tráfico en un proxy. Los límites de longitud de campos y cabeceras no equivalen a un límite total de cuerpo JSON; el proxy debe fijarlo para exposición externa.

## Respaldo y restauración

Para guardar un respaldo SQL UTF-8 sin la redirección binaria de PowerShell, utiliza Python estándar desde la raíz:

```powershell
New-Item -ItemType Directory -Force backups | Out-Null
python -c "import subprocess,pathlib; p=pathlib.Path('backups/requests.sql'); p.write_bytes(subprocess.check_output(['docker','compose','exec','-T','database','pg_dump','-U','requests','-d','requests']))"
```

El archivo contiene datos de solicitudes e idempotencia: guárdalo fuera del repositorio y con acceso restringido. La restauración se ensaya sobre una base vacía y aislada:

```powershell
python -c "import subprocess,pathlib; subprocess.run(['docker','compose','exec','-T','database','psql','-v','ON_ERROR_STOP=1','-U','requests','-d','requests'],input=pathlib.Path('backups/requests.sql').read_bytes(),check=True)"
```

No se declara un RPO/RTO operativo hasta verificar políticas y restauraciones en la infraestructura elegida. Los tests de reinicio verifican conservación del volumen, no reemplazan un respaldo.

## CI y registro

1. `quality`: JDK 21 en Windows/Linux; Maven verify, pruebas y cobertura.
2. `integration`: validación de workflow, stack PostgreSQL, pruebas E2E, escaneo y SBOM.
3. `publish`: solo `main`, después de integración correcta; imagen por SHA y atestación GHCR.

Se bloquean vulnerabilidades HIGH/CRITICAL con corrección disponible; las no corregibles se conservan en el SBOM y requieren revisión mediante análisis completo cuando se evalúe una adopción. El escáner de imagen analiza dependencias empaquetadas y el runtime. No se publica la imagen de build ni las dependencias de prueba.

El modo `--offline-scan` evita consultas Maven adicionales durante la identificación de dependencias; la base de vulnerabilidades de Trivy continúa descargándose. El análisis usa metadatos de las bibliotecas incluidas en el JAR. `verify_sbom.py` bloquea la entrega si alguna biblioteca Java empaquetada falta en el SBOM, para detectar identificación incompleta. Esta distinción sigue la [documentación de Trivy para Java](https://trivy.dev/docs/latest/guide/coverage/language/java/).

Imagen de entrega: `ghcr.io/jorgefprietol/service-request-platform:sha-<commit>`. Usa su digest para despliegues inmutables. GHCR controla el acceso al paquete independientemente de la visibilidad del repositorio; autentica tu cliente si el paquete requiere credenciales. No se configura un runner local ni se ejecutan contribuciones públicas en esta computadora.

La procedencia se verifica con `gh attestation verify oci://ghcr.io/jorgefprietol/service-request-platform@sha256:<digest> -R jorgefprietol/service-request-platform`.

Para actualizar, conserva respaldo, comprueba migraciones, verifica la procedencia y usa `APP_IMAGE` en `.env` con una imagen aprobada. Ejecuta `docker compose up -d --no-build --wait`; en rollback selecciona el digest anterior solo si es compatible con el esquema. Flyway aplica migraciones progresivas; no se deshacen automáticamente.
