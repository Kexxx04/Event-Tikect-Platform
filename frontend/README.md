# Event Platform – Frontend

Frontend mínimo (Vite + React + TypeScript + React Router) que consume la API REST de `event-platform`.

## Requisitos

- Node.js 20 o superior (probado con Node 24) y npm.
- La API corriendo en `http://localhost:8080` (ver `../event-platform/README.md`). Para probar sin
  PostgreSQL, desde `../event-platform` ejecuta `.\gradlew.bat bootTestRun --args="--spring.profiles.active=test"`
  (H2 en memoria; usuario `admin`, contraseña `test-admin-password`; los datos se pierden al detenerla).

## Cómo ejecutar

```powershell
cd frontend
npm install
Copy-Item .env.example .env   # en bash: cp .env.example .env
npm run dev
```

Abre <http://localhost:5173>.

`VITE_API_URL` en `.env` indica la URL de la API (por defecto `http://localhost:8080`).
Si cambias `.env`, reinicia `npm run dev`.

## Inicio de sesión

Todas las pantallas requieren iniciar sesión como administrador en `/login`. El usuario y la
contraseña son `ADMIN_USERNAME` y `ADMIN_PASSWORD` del archivo `../event-platform/.env`.

- El token JWT se guarda en `sessionStorage`: sobrevive a recargas, pero se pierde al cerrar la pestaña.
- Cada petición envía `Authorization: Bearer <token>`.
- Si la API responde `401` (token expirado o inválido), la sesión se cierra y se vuelve al
  login con el aviso "Your session has expired". También se cierra sola al llegar a `expiresAt`.
- **Logout** (barra superior) borra el token.

## CORS

La API solo acepta peticiones del navegador desde los orígenes de la propiedad
`app.cors.allowed-origins` (por defecto `http://localhost:5173`). Para servir el frontend
desde otro origen, define la variable de entorno antes de arrancar la API:

```powershell
$env:CORS_ALLOWED_ORIGINS = "http://localhost:5173,http://localhost:4173"
```

## Scripts

| Comando | Qué hace |
| --- | --- |
| `npm run dev` | Servidor de desarrollo con recarga en caliente (puerto 5173) |
| `npm run build` | Verifica tipos (`tsc -b`) y genera `dist/` |
| `npm run preview` | Sirve `dist/` en el puerto 4173 (añádelo a `CORS_ALLOWED_ORIGINS`) |
| `npm run lint` | Ejecuta oxlint |

## Pantallas

| Ruta | Contenido |
| --- | --- |
| `/login` | Inicio de sesión del administrador (`POST /api/auth/login`); el resto de rutas redirige aquí sin sesión |
| `/users` | Tabla de usuarios y botón **New user** |
| `/users/new` | Formulario de creación (`POST /api/users`) |
| `/users/:id` | Edición (`PATCH /api/users/{id}`), **Activate/Deactivate** (`PATCH /api/users/{id}/status`) y eventos del usuario (`GET /api/users/{id}/events`) |
| `/events` | Tabla de eventos y botón **New event** |
| `/events/new` | Formulario de creación (`POST /api/events`) |
| `/events/:id` | Detalle, tarjeta de disponibilidad (`GET /api/events/{id}/availability`), botones de estado (`PATCH /api/events/{id}/status`) e inscripciones: inscribir un usuario (`POST /api/events/{id}/registrations`), filtrar por estado, ver la posición en la lista de espera y cancelar (`DELETE /api/events/{id}/registrations/{userId}`), con aviso de quién fue promovido |
| `/events/:id/edit` | Edición completa (`PUT /api/events/{id}`) |

## Estructura

```
src/
  api/          client.ts (fetch + ApiError + token), auth.ts, users.ts, events.ts, registrations.ts (tipos = DTOs del backend)
  auth/         session.ts (sessionStorage), AuthContext/AuthProvider, RequireAuth
  components/   Layout, ErrorAlert, FormField, Feedback (loading, vacío, badges)
  hooks/        useLoader (carga con estados loading/error)
  pages/        users/, events/
  utils/        dates.ts (LocalDateTime <-> datetime-local)
  index.css     único archivo de estilos
```

## Manejo de errores

Cualquier respuesta no exitosa se convierte en `ApiError` con `status`, `message` y `details`
(el formato del `GlobalExceptionHandler`). Los formularios muestran `details` debajo de cada
campo (400) y `message` en un aviso superior (400, 404, 409). Si la API no responde, se
muestra "Cannot reach the API at …".
