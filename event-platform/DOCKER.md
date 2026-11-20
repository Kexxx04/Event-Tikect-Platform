# Compartir Event Platform con Docker

El paquete contiene la API Java y PostgreSQL como dos imágenes. Compose los inicia
juntos. Incluye las migraciones que crean las tablas automáticamente; no incluye
datos de desarrollo, contraseñas personales ni herramientas como SonarQube.

## Qué archivo usar

| Archivo | Para qué sirve | Quién lo usa |
| --- | --- | --- |
| `compose.yaml` | Inicia la API y PostgreSQL juntos. Es la opción predeterminada. | El grupo, para ejecutar el proyecto completo. |
| `compose.dev.yaml` | Inicia solo PostgreSQL; la API se ejecuta desde IntelliJ o Gradle. | Quien esté modificando el código fuente. |
| `Dockerfile` | Construye la imagen de la API a partir del código. | Quien genere una nueva versión de la imagen. |
| `.env.docker.example` | Plantilla para ejecutar todo con Docker. | Quien recibe el ZIP. |
| `.env.example` | Plantilla de desarrollo para Java, PostgreSQL y SonarQube. | Quien trabaja desde el repositorio. |

Los dos archivos Compose son alternativas independientes: no se combinan.
El ZIP contiene únicamente `compose.yaml`, porque ya incluye las imágenes listas.
El repositorio contiene también `compose.dev.yaml` y `Dockerfile` para desarrollo.
Cada modo usa su propio volumen de PostgreSQL; cambiar de modo no copia los datos.

## Para el grupo: ejecutar el paquete recibido

Requisito: Docker Desktop iniciado con contenedores Linux. La imagen exportada
está construida para Linux AMD64 (equipos Intel/AMD). No necesitas Java ni Gradle.

1. Descomprime `event-platform-docker-1.0.0.zip` y abre una terminal en esa carpeta.
2. Carga las dos imágenes:

   ```powershell
   docker load -i event-platform-images.tar
   ```

3. Copia la plantilla y cambia `POSTGRES_PASSWORD`, `ADMIN_PASSWORD` (contraseña para iniciar sesión como `admin`) y `JWT_SECRET` (mínimo 32 caracteres aleatorios):

   ```powershell
   Copy-Item .env.docker.example .env
   notepad .env
   ```

   En macOS/Linux puedes usar `cp .env.docker.example .env` y tu editor preferido.
   Si el puerto 8080 está ocupado, cambia `API_PORT`, por ejemplo a `8081`.

4. Inicia los servicios desde esa misma carpeta:

   ```powershell
   docker compose up -d --wait --wait-timeout 180
   ```

   > **Problema conocido:** el `HEALTHCHECK` de la imagen consulta `/api/users` sin token, y desde
   > que la API exige login esa ruta responde `401`. Por eso el contenedor `api` aparece como
   > `unhealthy` y `--wait` termina con error aunque la API funcione. Mientras se corrige, inicia con
   > `docker compose up -d` y revisa con `docker compose logs api` que aparezca
   > `Started EventPlatformApplication`. Ver "Problemas conocidos" en `README.md`.

5. Comprueba que la API responde. Todas las rutas exigen token, así que abrir
   `http://localhost:8080/api/users` en el navegador devuelve `401 Authentication is required`:
   eso confirma que la API está activa. Si cambiaste `API_PORT`, usa ese puerto en la URL.

Para usarla desde PowerShell, primero inicia sesión con `ADMIN_USERNAME` y `ADMIN_PASSWORD` del `.env`
y luego envía el token en cada petición:

```powershell
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/auth/login -ContentType 'application/json' `
  -Body (@{ username = 'admin'; password = (Read-Host 'Contraseña del administrador') } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($login.token)" }

$body = @{ name = 'Ada Lovelace'; email = 'ada@example.com'; document = '12345' } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/users -Headers $headers -ContentType 'application/json' -Body $body
Invoke-RestMethod -Uri http://localhost:8080/api/users -Headers $headers
```

En `README.md` está la función `Invoke-Api`, que simplifica estas llamadas y muestra los errores de la
API, y la lista completa de endpoints. El paquete Docker contiene solo la API y PostgreSQL; la interfaz
web (`frontend/`) se ejecuta aparte con `npm run dev` y se conecta a `http://localhost:8080`.

Para consultar el estado, los logs y detener los servicios:

```powershell
docker compose ps
docker compose logs --tail 100 api
docker compose down
```

`down` conserva los datos en un volumen local. Cambiar la contraseña en `.env`
después del primer arranque no cambia la contraseña de una base ya creada.
Cada integrante tiene su propia base de datos; los datos no se sincronizan.

## Para desarrollar desde IntelliJ (requiere el repositorio)

Usa este modo si vas a editar Java y ejecutar la API desde tu IDE. Requiere JDK 21.
Sigue la configuración de credenciales de `README.md` en el repositorio y ejecuta:

```powershell
docker compose -f compose.dev.yaml up -d
.\gradlew.bat bootRun
```

También puedes iniciar `EventPlatformApplication` desde IntelliJ, configurando
`DB_USERNAME` y `DB_PASSWORD` en su configuración de ejecución.
La API local se conecta a `localhost:5432`; la API dentro de Docker usa
`postgres:5432`, el nombre del servicio en su red interna.

Para detener la base de desarrollo:

```powershell
docker compose -f compose.dev.yaml down
```

Antes de iniciar la API local en el puerto 8080, detén el modo completo con
`docker compose down` si está activo, o configura otro puerto para uno de los modos.

## Para reconstruir desde el código fuente

Desde la raíz del repositorio:

```powershell
docker build -t event-platform:1.0.0 .
```

Después configura `.env` como en los pasos del grupo y ejecuta
`docker compose up -d --wait`. Reconstruye la imagen cuando cambies el código;
reiniciar el contenedor por sí solo no incorpora los cambios Java.

La compilación usa Java 21 dentro de Docker y requiere Internet para descargar
dependencias. El contenedor final ejecuta únicamente la API con un usuario sin
privilegios. Las pruebas se ejecutan por separado con Gradle antes de distribuir.
La imagen no incluye el código fuente editable; comparte también el repositorio
si tus compañeros van a desarrollar.

Para exportar otra versión del paquete (ajusta la etiqueta en Compose al cambiarla):

```powershell
docker pull postgres:16-alpine
docker save -o event-platform-images.tar event-platform:1.0.0 postgres:16-alpine
```

Envía el archivo `.tar`, `compose.yaml`, `.env.docker.example` y este documento.
Las credenciales de `.env` se configuran en cada equipo; no se incluyen en la imagen.

Documentación: [guardar imágenes](https://docs.docker.com/reference/cli/docker/image/save/)
y [cargar imágenes](https://docs.docker.com/reference/cli/docker/image/load/).
