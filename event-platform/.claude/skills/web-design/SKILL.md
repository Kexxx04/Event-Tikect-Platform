---
name: web-design
description: Guía visual del frontend React de Event Platform basada en los mockups de diseño (login, events, event detail, event form, users, user form, user detail, 404). Úsala siempre que crees, rediseñes o ajustes pantallas, estilos o componentes en ../frontend (index.css, Layout, páginas de events y users, login, 404).
---

# Web design — Event Platform frontend

Esta skill define cómo debe verse el frontend (`../frontend`, Vite + React 19 + TypeScript +
React Router) para que coincida con los mockups. Es la fuente de verdad visual; la fuente de
verdad funcional sigue siendo la API y el `CLAUDE.md` del backend.

## Mockups

Están en `../frontend/docs/design/event-platform (1)/` (rutas relativas a `event-platform/`).
La carpeta está en `frontend/.gitignore`, así que solo existe en la máquina donde se copiaron.
Si no están, usa las especificaciones de esta skill.

| Mockup | Ruta | Página actual |
| --- | --- | --- |
| `01 · login.png` | `/login` | `src/pages/LoginPage.tsx` |
| `02 · register.png` | — | No existe: ver "Fuera de alcance" |
| `03 · 404.png` | `*` | `src/pages/NotFoundPage.tsx` |
| `04 · events.png` | `/events` | `src/pages/events/EventsPage.tsx` |
| `05 · events id.png` | `/events/:id` | `EventDetailPage.tsx`, `AvailabilityCard.tsx`, `RegistrationsCard.tsx` |
| `06 · events new · events id edit.png` | `/events/new`, `/events/:id/edit` | `EventFormPage.tsx` |
| `07 · users.png` | `/users` | `src/pages/users/UsersPage.tsx` |
| `08 · users new.png` | `/users/new` | `UserFormPage.tsx` |
| `09 · users id.png` | `/users/:id` | `UserFormPage.tsx`, `UserEventsCard.tsx` |

Antes de tocar una pantalla, abre su PNG con la herramienta Read y compáralo con el resultado.
Las imágenes están a 2x: divide las medidas que veas entre 2 para obtener px de CSS.

## Reglas que no se negocian

1. **Los datos salen de la API, nunca del mockup.** Nombres, cifras ("87 / 100", "128 users",
   "Diego R.") son de ejemplo. No inventes campos que los DTOs de `src/api/*.ts` no tienen.
2. **No quites funcionalidad que el mockup no muestra.** Se conservan: cambio de estado del
   evento según `NEXT_STATUSES`, aviso de quién fue promovido al cancelar, posición en la lista
   de espera, mensajes de `ErrorAlert` con `details` por campo, expiración de sesión.
3. **Mismo stack.** Sin Tailwind, sin librerías de componentes ni de iconos. Todo el estilo vive
   en `src/index.css` con variables CSS; los iconos son SVG inline (trazo 1.75–2 px, estilo
   lucide) en `src/components/icons.tsx`.
4. **Reutiliza antes de crear:** `Layout`, `FormField`, `ErrorAlert`, `Feedback` (`Loading`,
   `EmptyState`, `SuccessAlert`, `StatusBadge`), `useLoader`, `utils/dates.ts`. La capa
   `src/api/` no cambia por motivos visuales.
5. **Textos de la interfaz en inglés**, con la redacción de los mockups cuando aplique.

## Tokens

Sustituyen o amplían los de `:root` en `src/index.css`. Mantén los nombres existentes.

```css
:root {
  --color-bg: #f2f4f9;            /* fondo de la app */
  --color-surface: #ffffff;       /* tarjetas, sidebar */
  --color-surface-muted: #f6f7fb; /* filas de tabla, inputs */
  --color-text: #111827;
  --color-muted: #5f6b7a;
  --color-border: #e2e6ee;
  --color-ink: #141b2d;           /* pill de filtro activa */
  --color-primary: #2f5bea;
  --color-primary-hover: #2449c4;
  --color-primary-soft: #e8eefd;  /* date chip, avatar, item activo del menú */
  --color-success: #1b7a3d;
  --color-success-bg: #e7f6ec;
  --color-warning: #b45309;       /* "Full · 12 waiting", número de waitlist */
  --color-warning-bg: #fdf1e1;
  --color-warning-bar: #d97706;   /* barra de ocupación llena */
  --color-danger: #c62828;
  --color-danger-bg: #fdecec;
  --radius-sm: 10px;              /* botones, inputs */
  --radius: 16px;                 /* tarjetas */
  --radius-lg: 28px;              /* panel azul del login */
  --shadow: 0 1px 2px rgba(16, 24, 40, 0.04), 0 1px 3px rgba(16, 24, 40, 0.06);
  font-family: 'Plus Jakarta Sans', system-ui, -apple-system, 'Segoe UI', Roboto, sans-serif;
}
```

- **Tipografía:** Plus Jakarta Sans (Google Fonts, pesos 400/500/600/700/800) con fallback de
  sistema. H1 de página ~2rem/800 con `letter-spacing: -0.02em`; H2 de tarjeta ~1.25rem/700;
  subtítulos en `--color-muted`.
- **Números grandes** (stat cards, date chip, 404): 700–800, `font-variant-numeric: tabular-nums`.

## Layout

- **Sidebar fija a la izquierda** (~240 px, fondo `--color-surface`, borde derecho) que
  reemplaza la navbar oscura actual:
  - Arriba: logo (cuadrado redondeado `--color-primary` con icono de calendario blanco) +
    "Event Platform" en 700.
  - Menú: **Events** e **Users** con icono. Activo: fondo `--color-primary-soft`, texto
    `--color-primary`, radio `--radius-sm`.
  - Abajo: tarjeta de usuario con avatar de iniciales, `session.username` y el rol "Admin", y
    un botón-icono de logout con `aria-label="Log out"`.
- **Contenido:** fondo `--color-bg`, padding ~2.5rem, ancho máximo ~1200 px.
- **Cabecera de página:** H1 + subtítulo a la izquierda y la acción principal a la derecha
  ("+ New event", "+ New user").
- **Breadcrumbs** (`Events / Tech Summit Medellín`) en lugar del `back-link` en las pantallas de
  detalle y formulario. El primer tramo es un link en `--color-primary` y el último es texto muted.
- **Responsive:** por debajo de ~900 px la sidebar pasa a barra superior; los grids de 3
  columnas bajan a 1 y las tablas hacen scroll horizontal. No debe haber scroll horizontal de
  página a 375 px.

## Componentes

- **Card:** `--color-surface`, `--radius`, `--shadow`, padding 1.5–2rem y sin borde visible.
- **Botones:** radio `--radius-sm`, peso 600.
  - Primario: relleno `--color-primary`.
  - Secundario ("Edit", "Cancel", "View"): blanco con borde `--color-border`.
  - Peligro ("Deactivate"): blanco con texto y borde `--color-danger`.
  - Los botones con "+" llevan icono.
- **Filter pills:** completamente redondeadas. Activa: relleno `--color-ink` y texto blanco.
  Inactiva: blanco con borde. Pueden llevar contador: `Confirmed · 87`.
- **Search input:** icono de lupa a la izquierda dentro del input y radio `--radius-sm`.
- **Badges de estado:** pill con fondo suave y texto en Title Case, nunca el enum crudo.

  | Enum | Texto | Color |
  | --- | --- | --- |
  | Event `SCHEDULED` | Scheduled | primary sobre primary-soft |
  | Event `ACTIVE` | Active | success |
  | Event `FINISHED` | Finished | muted sobre gris `#eceff3` |
  | Event `CANCELLED` | Cancelled | danger |
  | Registration `CONFIRMED` | Confirmed | success |
  | Registration `WAITING_LIST` | Waitlisted | warning |
  | Registration `CANCELLED` | Cancelled | danger |
  | User `ACTIVE` | Active | success |
  | User `INACTIVE` | Inactive | muted sobre gris (no rojo) |

- **Date chip:** cuadrado redondeado (radio 12 px) con fondo `--color-primary-soft`. Mes abreviado
  en mayúsculas pequeñas con letter-spacing (`NOV`) y día con 2 dígitos en 800 (`05`), ambos en
  `--color-primary`. Fórmalo con `toLocaleDateString('en-US', …)` desde `startDate`.
- **Avatar de iniciales:** círculo `--color-primary-soft` con iniciales en `--color-primary`
  (primeras letras de las dos primeras palabras del nombre).
- **Tablas:** cabecera en mayúsculas, 0.75rem, letter-spacing, `--color-muted` y sin fondo.
  Cada fila es un bloque redondeado `--color-surface-muted` separado por ~8 px de espacio en
  lugar de líneas.
- **Stat card:** etiqueta muted arriba y número grande debajo, con una línea de contexto
  opcional en color semántico.
- **Barra de ocupación:** track gris claro y relleno `--color-primary`. Cuando
  `registeredCount >= capacity` el relleno es `--color-warning-bar`, no rojo.
- **Donut de ocupación:** SVG con dos `circle` y `stroke-dasharray`; el porcentaje va al lado,
  en 800.
- **Formularios:**
  - Labels en 600 y asterisco rojo en los obligatorios.
  - Inputs con fondo `--color-surface-muted` y borde `--color-border`.
  - Error: borde `--color-danger` y mensaje rojo debajo. Ayuda: texto muted debajo.
  - Pie con separador y botones a la derecha ("Cancel" secundario + acción primaria).
- **Callout de aviso:** fondo `--color-warning-bg`, texto `--color-warning` y la primera palabra
  en negrita ("**Editing:** …").

## Pantallas

- **Login (01):** dos columnas.
  - Izquierda: panel `--color-primary` con `--radius-lg`, logo, titular "Every seat, accounted
    for.", subtítulo y una tarjeta de ejemplo abajo (puramente decorativa).
  - Derecha: "Welcome back" / "Sign in to manage your events.", campos y botón "Sign in" de
    ancho completo.
  - El campo es **Username**, no Email, porque la API recibe `username`.
  - El botón "Show" del password es solo de frontend y se permite.
  - Omite "Forgot password?" y "Create an attendee account".
  - En móvil se oculta el panel azul.
- **404 (03):** tarjeta centrada con chip `ERR / 404` (estilo date chip, más grande), título
  "This page has no seats", texto y botones "Back to events" (primario) y "Sign in" (secundario,
  solo sin sesión).
- **Events (04):**
  - Cabecera "Events" / "Create events and follow their availability in real time.".
  - Filter pills (All, Scheduled, Active, Finished, Cancelled) y buscador por nombre, ambos
    filtrando en cliente.
  - Grid de tarjetas (3 columnas): date chip, badge arriba a la derecha, nombre, fecha
    (`Nov 28, 2026 · 6:00 PM`, o rango si dura varios días), barra de ocupación y
    `X / Y registered`. A la derecha: "N seats left" en primary; "Full" en warning si está
    lleno; "Closed" si está `FINISHED` o `CANCELLED`.
  - El listado no trae `waitlistCount`: no hagas una llamada a availability por tarjeta solo
    para mostrar "· 12 waiting".
  - La tarjeta completa enlaza al detalle.
- **Event detail (05):**
  - Tarjeta cabecera con date chip, nombre + badge, rango de fechas · `Capacity N`, y acciones
    "Edit", los botones de cambio de estado válidos y "+ Register user".
  - Debajo, 3 stat cards desde `availability`:
    - Occupancy: donut + %.
    - Seats available: número en primary y "Accepting registrations" en success o el motivo
      en muted.
    - Waitlist: número en warning y "Promoted automatically".
  - Después, la tarjeta "Registrations" con pills con contador por estado y filas con avatar,
    nombre, email, badge, fecha y "Cancel".
- **Event form (06):**
  - Breadcrumb, título ("New event" / "Edit event") y subtítulo "The event starts as Scheduled
    with 0 registrations." (solo al crear).
  - Formulario en tarjeta: Name, Description, Start date + End date en 2 columnas y Capacity
    con la ayuda "Must be greater than 0.".
  - Columna lateral con la tarjeta "Good to know" (lista con checks: registrations close when
    the event starts, full events go to the waitlist, raising capacity promotes people) y, solo
    al editar, el callout de aviso sobre capacidad y estados.
- **Users (07):**
  - Cabecera "Users" / "People who can register to events. Inactive users cannot register.".
  - 3 stat cards: Total users, Active (número en success) e Inactive (número en muted).
  - Tarjeta con buscador ("Search by name, email or document", en cliente), tabla NAME / EMAIL /
    DOCUMENT / STATUS con avatar y "View", y pie "Showing X of Y" con Previous/Next. La
    paginación es en cliente, porque la API devuelve la lista completa.
- **User form (08) y user detail (09):**
  - Tarjeta cabecera con avatar grande, nombre + badge, `email · Doc. 123`, "Edit" y
    "Deactivate" (peligro) o "Activate" (primario) según el estado.
  - Tarjeta "Events" con pills (All, Confirmed, Waitlisted, Cancelled) y tabla DATE (date chip) /
    EVENT / STATUS / REGISTRATION / REGISTERED ON.

## Fuera de alcance (pregunta antes)

El mockup 02 (registro de asistentes), "Forgot password?" y cualquier login de asistentes
necesitan cambios en el backend que hoy no existen: los usuarios no tienen contraseña y el único
login es el del admin. No los implementes ni simules en el frontend sin que el usuario lo pida
explícitamente.

## Accesibilidad

- Contraste AA. No cambies `--color-muted` a un gris más claro.
- Foco visible: `outline: 2px solid var(--color-primary)` con offset.
- Los botones-icono llevan `aria-label`.
- Las pills de filtro son `button` con `aria-pressed`.
- El donut lleva `role="img"` y `aria-label` con el porcentaje.
- Los cambios de carga y éxito usan `role="status"`.

## Verificación

Desde `../frontend`:

1. `npm run build` (incluye `tsc -b`) y `npm run lint` sin errores.
2. Levanta la API con el perfil test (ver `../frontend/README.md`) y `npm run dev`.
3. Recorre cada pantalla tocada y compárala con su PNG a ~1280 px y a 375 px.
4. Prueba los estados vacío, cargando, error y evento lleno.

## Commits

Sigue el `CLAUDE.md` con el scope `frontend`, por ejemplo:

- `feat(frontend): add sidebar layout and design tokens`
- `feat(frontend): restyle events list as cards`

Haz un commit por pantalla o pieza lógica.
