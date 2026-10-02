# Operación y entrega

## Arranque y apagado

Desde la raíz del proyecto: `python scripts/init_env.py`, luego `docker compose up -d --build --wait --wait-timeout 300`. La consola está en `http://127.0.0.1:18110`; el proveedor de identidad local está en `http://127.0.0.1:18112`. Las contraseñas de cuentas de verificación se consultan únicamente en `.env` local. `docker compose stop` pausa servicios; `docker compose up -d --wait` los recupera. `docker compose down` elimina contenedores y red, conservando los volúmenes.

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

OIDC es el modo predeterminado. Los tokens estáticos descritos anteriormente solo funcionan con `PLATFORM_AUTH_MODE=development-tokens`, un modo explícito para desarrollo y pruebas rápidas. En OIDC, rota contraseñas y sesiones desde el proveedor, sin cambiar el subject estable del usuario. El archivo de importación inicial no sobreescribe un realm existente al reiniciar: cambiar una contraseña en `.env` no modifica una cuenta ya creada.

La consola utiliza Authorization Code con PKCE; el cliente público `service-request-console` no admite password grant. Los clientes `service-request-verification` y `unrelated-verification` existen para probar tokens y audiencias automáticamente y deben deshabilitarse fuera del entorno de verificación. Keycloak usa start-dev y almacenamiento local en su volumen; configura un IdP de producción, TLS, backups y tenancy antes de una adopción compartida. Los límites de longitud de campos y cabeceras no equivalen a un límite total de cuerpo JSON; el proxy debe fijarlo para exposición externa.

El subject verificado del JWT identifica propietario, actor y operador asignado. Los roles provienen de claims firmados; el nombre visible no concede permisos. El operador se registra en el directorio al consultar `/api/me`, como hace la consola al ingresar. Asignar exige una revisión actual y queda auditado. El monitor detecta vencimientos cada cinco segundos en Compose; una clave única impide duplicación. Las alertas activas excluyen estados terminales, y sus registros sobreviven reinicios.

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
2. `integration`: validación de workflow, PostgreSQL y Keycloak reales, pruebas E2E, escaneo y SBOM.
3. `publish`: solo `main`, después de integración correcta; imagen por SHA y atestación GHCR.

Se bloquean vulnerabilidades HIGH/CRITICAL con corrección disponible; las no corregibles se conservan en el SBOM y requieren revisión mediante análisis completo cuando se evalúe una adopción. El escáner de imagen analiza dependencias empaquetadas y el runtime. No se publica la imagen de build ni las dependencias de prueba.

El modo `--offline-scan` evita consultas Maven adicionales durante la identificación de dependencias; la base de vulnerabilidades de Trivy continúa descargándose. El análisis usa metadatos de las bibliotecas incluidas en el JAR. `verify_sbom.py` bloquea la entrega si alguna biblioteca Java empaquetada falta en el SBOM, para detectar identificación incompleta. Esta distinción sigue la [documentación de Trivy para Java](https://trivy.dev/docs/latest/guide/coverage/language/java/).

El análisis inicial identificó versiones vulnerables administradas por el BOM del framework. Se fijaron actualizaciones compatibles de Jackson, Tomcat y JDBC PostgreSQL en `pom.xml`, manteniendo el gate de seguridad. Tomcat 10.1.58 no fue publicado; se utiliza 10.1.60, una versión publicada posterior a la corrección descrita en el [aviso oficial de Apache](https://tomcat.apache.org/security-10).

Imagen de entrega: `ghcr.io/jorgefprietol/service-request-platform:sha-<commit>`. Usa su digest para despliegues inmutables. GHCR controla el acceso al paquete independientemente de la visibilidad del repositorio; autentica tu cliente si el paquete requiere credenciales. No se configura un runner local ni se ejecutan contribuciones públicas en esta computadora.

La procedencia se verifica con `gh attestation verify oci://ghcr.io/jorgefprietol/service-request-platform@sha256:<digest> -R jorgefprietol/service-request-platform`.

Para actualizar, conserva respaldo, comprueba migraciones, verifica la procedencia y usa `APP_IMAGE` en `.env` con una imagen aprobada. Ejecuta `docker compose up -d --no-build --wait`; en rollback selecciona el digest anterior solo si es compatible con el esquema. Flyway aplica migraciones progresivas; no se deshacen automáticamente.
