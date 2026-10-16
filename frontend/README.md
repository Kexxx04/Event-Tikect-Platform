# Event Platform – Frontend

Frontend mínimo (Vite + React + TypeScript + React Router) que consume la API REST de `event-platform`.

## Requisitos

- Node.js 20 o superior (probado con Node 24) y npm.
- La API corriendo en `http://localhost:8080` (ver `../event-platform/README.md`).

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
| `/users` | Tabla de usuarios y botón **New user** |
| `/users/new` | Formulario de creación (`POST /api/users`) |
| `/users/:id` | Edición (`PATCH /api/users/{id}`) y **Activate/Deactivate** (`PATCH /api/users/{id}/status`) |
| `/events` | Tabla de eventos y botón **New event** |
| `/events/new` | Formulario de creación (`POST /api/events`) |
| `/events/:id` | Detalle, tarjeta de disponibilidad (`GET /api/events/{id}/availability`) y botones de estado (`PATCH /api/events/{id}/status`) |
| `/events/:id/edit` | Edición completa (`PUT /api/events/{id}`) |

## Estructura

```
src/
  api/          client.ts (fetch + ApiError), users.ts, events.ts (tipos = DTOs del backend)
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
