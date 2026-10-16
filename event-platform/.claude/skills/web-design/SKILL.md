---
name: web-design
description: Use for any screen, component, layout or styling work in ../frontend. Defines the "Fresh" visual direction, design tokens, screen references in docs/design, how every screen maps to the existing REST API, and CSP-safe styling rules.
---

# Web design — Event Platform "Fresh"

This skill lives in the backend project (`event-platform/`). The frontend is `../frontend`
(Vite + React 19 + TypeScript + React Router 7). Every `src/...` path below is inside
`../frontend`, and every `docs/design/...` path is inside `event-platform/`.

## Working on the existing frontend

- **Restyle before you create.** Adapt and restyle the existing components and pages before
  creating new ones. Do not change business logic, API calls (`src/api/*.ts`) or URLs that
  already work. Change only the visual layer and the component structure.
- **Keep behaviour the mockups don't show:**
  - event status transitions (`NEXT_STATUSES`);
  - the "X was promoted from the waitlist" notice after a cancellation;
  - waitlist positions;
  - the "Your session has expired" notice;
  - per-field 400 errors.
- **Tests.** The frontend has no automated tests, because there is no test runner in
  `package.json`. Verify with build, lint and a visual check (see Verification). If a task
  also touches the backend, `.\gradlew.bat test` must stay green.

## Reference screens

ALWAYS open the matching PNG with Read before building a screen. Match its layout, spacing,
hierarchy and colors. The images are 2x, so divide measured sizes by 2 to get CSS px. Sample
data in the images is fake: render API data only.

| Mockup | Route | Files |
| --- | --- | --- |
| `docs/design/01-login.png` | `/login` | `src/pages/LoginPage.tsx` |
| `docs/design/02-register.png` | `/register` | Not built: see "Not supported by the API" |
| `docs/design/03-not-found.png` | `*` | `src/pages/NotFoundPage.tsx` |
| `docs/design/04-events-list.png` | `/events` | `src/pages/events/EventsPage.tsx` |
| `docs/design/05-event-detail.png` | `/events/:id` | `EventDetailPage.tsx`, `AvailabilityCard.tsx`, `RegistrationsCard.tsx` |
| `docs/design/06-event-form.png` | `/events/new`, `/events/:id/edit` | `EventFormPage.tsx` |
| `docs/design/07-users-list.png` | `/users` | `src/pages/users/UsersPage.tsx` |
| `docs/design/08-user-form.png` | `/users/new`, `/users/:id/edit` | `UserFormPage.tsx` |
| `docs/design/09-user-detail.png` | `/users/:id` | New `UserDetailPage.tsx` + `UserEventsCard.tsx` |

The PNGs are git-ignored and exist only locally. If they are missing, the originals are in
`../frontend/docs/design/event-platform (1).zip`. If neither exists, follow this spec.

## API contract (source of truth for data)

Every endpoint except `POST /api/auth/login` requires the admin JWT, which `src/api/client.ts`
already adds.

| Screen | Calls | Data used |
| --- | --- | --- |
| Login | `POST /api/auth/login` `{ username, password }` | `token`, `username`, `expiresAt` |
| Events list | `GET /api/events` | `id`, `name`, `startDate`, `endDate`, `capacity`, `registeredCount`, `status` |
| Event detail | `GET /api/events/{id}`, `GET /api/events/{id}/availability`, `GET /api/events/{id}/registrations`, `GET /api/users`, `POST /api/events/{id}/registrations` `{ userId }`, `DELETE /api/events/{id}/registrations/{userId}`, `PATCH /api/events/{id}/status` | `availableSeats`, `waitlistCount`, `acceptingRegistrations`; for each registration: `userName`, `userEmail`, `registrationDate`, `status`; after a cancellation: `promoted` |
| Event form | `POST /api/events`, `PUT /api/events/{id}` | `name`, `description`, `startDate`, `endDate`, `capacity` |
| Users list | `GET /api/users` | `name`, `email`, `document`, `status` |
| User form | `POST /api/users`, `PATCH /api/users/{id}` | `name`, `email`, `document` |
| User detail | `GET /api/users/{id}`, `GET /api/users/{id}/events`, `PATCH /api/users/{id}/status` | `eventId`, `eventName`, `startDate`, `eventStatus`, `registrationStatus`, `registrationDate` |

### Enums

Keep the backend names in code and use the labels only in the UI.

- **EventStatus:** `SCHEDULED`, `ACTIVE`, `FINISHED`, `CANCELLED`.
  - Valid transitions: `SCHEDULED → ACTIVE | CANCELLED` and `ACTIVE → FINISHED | CANCELLED`.
  - `FINISHED` and `CANCELLED` events cannot be edited (409).
- **RegistrationStatus:** `CONFIRMED`, `WAITING_LIST`, `CANCELLED`. The UI label for
  `WAITING_LIST` is "Waitlisted", or "Waitlist" in tabs.
- **UserStatus:** `ACTIVE`, `INACTIVE`.

### Business rules

These rules appear in the UI copy and have been checked against the backend:

- Registrations close when `now >= startDate`.
- `acceptingRegistrations` is true only when the event is `SCHEDULED` or `ACTIVE` and has not
  started yet. A full event still accepts registrations: they go to the waitlist.
- Capacity must be greater than 0 and cannot be lowered below `registeredCount` (409).
- Raising capacity promotes the oldest waitlisted registrations.
- Inactive users cannot register (409).

### Errors

Errors arrive as `ApiError` from `client.ts`.

- **400:** each entry in `details` (field → message) goes under its field. A 400 with no
  `details` (for example "End date must be after start date") goes in the top Alert.
- **404:** shown in an Alert.
- **409:** the backend `message` goes in the top Alert. A 409 has no `details`, so never parse
  the message to guess a field. Duplicate users return "Email is already registered" or
  "Document is already registered".
- **401:** already handled: the session is cleared and the user is sent to the login.

### Data the API does not have

Never fake it.

- The events list has no waitlist count. Cards show "Full", not "Full · 12 waiting". Do not
  call availability once per card.
- The admin has no display name or role. The sidebar shows `session.username` and "Admin".
- There is no server-side search, filtering or pagination. Do these on the client.

## Not supported by the API (ask before building)

- **`/register` (02-register.png):** there is no public endpoint, users have no password and
  there is no attendee role. Do not add the route or any link to it.
- **Login extras:** omit "Forgot password?" and "Create an attendee account".
- **Login field:** the API authenticates with a username, so the mockup's "Email" field is
  "Username".

## Visual direction

- **Look:** soft, friendly SaaS. Light gray-blue background, white rounded cards, subtle
  shadows, a white left sidebar and one blue accent.
- **Font:** Plus Jakarta Sans 400/500/600/700/800, loaded in `index.html` from Google Fonts with
  the system font stack as fallback.
- **Type scale:**
  - Page titles 32px/800 with letter-spacing -0.02em.
  - Card titles 20px/800.
  - Body 15px.
- **Icons:** inline stroke SVG components (2px stroke, round caps) in `src/components/icons/`.
  `lucide-react` is not installed, so do not add it without asking. Never use emoji.

## Design tokens (`src/styles/tokens.css`)

Never hardcode hex values in components. Import `tokens.css` first in `src/main.tsx`.

```css
:root {
  --color-bg: #F2F4F9;
  --color-surface: #FFFFFF;
  --color-surface-alt: #F7F8FC;
  --color-border: #E3E7F0;
  --color-border-strong: #D6DCE8;
  --color-text: #141A2E;
  --color-text-muted: #4A526A;
  --color-accent: #2F5BEA;
  --color-accent-text: #2449C9;
  --color-accent-soft: #EAF0FF;
  --color-avatar: #DCE5FF;
  --color-danger: #B42318;
  --color-focus: #B9C9FF;

  --status-scheduled-bg: #EAF0FF;
  --status-scheduled-fg: #2449C9;
  --status-active-bg: #DFF6E7;    /* event ACTIVE, CONFIRMED, user ACTIVE */
  --status-active-fg: #17703A;
  --status-waitlist-bg: #FEF3E2;  /* WAITING_LIST, full events */
  --status-waitlist-fg: #B45309;
  --status-cancelled-bg: #FDE8E7;
  --status-cancelled-fg: #B42318;
  --status-finished-bg: #EEF0F5;  /* FINISHED, user INACTIVE */
  --status-finished-fg: #4A526A;

  --radius-sm: 10px;
  --radius-md: 12px;
  --radius-lg: 20px;
  --radius-xl: 24px;
  --shadow-card: 0 1px 2px rgba(20, 26, 46, 0.06);

  --space-1: 4px;
  --space-2: 8px;
  --space-3: 12px;
  --space-4: 16px;
  --space-5: 20px;
  --space-6: 24px;
  --space-8: 32px;
  --space-10: 40px;
}
```

### Migrating from the current `src/index.css`

| Old token | New token |
| --- | --- |
| `--color-primary` | `--color-accent` |
| `--color-primary-hover` | `--color-accent-text` (hover background) |
| `--color-muted` | `--color-text-muted` |
| `--color-success*` | `--status-active-*` |
| `--color-warning*` | `--status-waitlist-*` |
| `--color-danger-bg` | `--status-cancelled-bg` |
| `--radius` | `--radius-md` |

After the migration:

- `index.css` keeps only the reset, base typography and focus styles.
- Delete each old global class (`.card`, `.badge-*`, `.filter-tab`, …) once nothing uses it.

## Layout

- **AppShell:** the current `src/components/Layout.tsx`, restyled. It keeps `<Outlet />`,
  `useAuth()` and `logout()`.
  - White sidebar, 240px wide, containing the logo (accent rounded square with a calendar icon
    and "Event Platform") and the nav (Events, Users).
  - Active nav item: accent-soft background, accent-text color, bold.
  - At the bottom, a user card: Avatar with the initials of `session.username`, the username,
    the caption "Admin" and an icon button `aria-label="Sign out"`.
- **Content:** max-width 1080px, padding 32px 40px. Detail and form pages show a breadcrumb
  ("Events / Tech Summit Medellín") instead of the current `back-link`.
- **Below 768px:**
  - The sidebar becomes a top bar.
  - Grids go to 1 column.
  - Tables scroll horizontally inside their card.
  - There is no page-level horizontal scroll at 375px.
- **Auth pages (login):** no sidebar. Split screen with the accent panel on the left and the
  form on the right. On mobile the panel stacks on top.

### Route changes

These are the only route changes. Every URL that works today still works.

1. **404:** move `<Route path="*">` out of the `RequireAuth` layout so the page renders on its
   own, like 03-not-found.png.
2. **User detail and edit:**
   - `/users/:id` becomes the detail page (09).
   - The edit form moves to a new route, `/users/:id/edit`, remounted with `key={id}` the same
     way as `EventEditRoute`.
   - Update every link that pointed to `/users/:id` to edit a user.

## Components (`src/components`, CSS modules)

- **Button:**
  - Variants: primary (accent background, white text), secondary (white, border-strong) and
    danger-outline (white, danger text and border).
  - Min height 44px, radius 12px, weight 600–700.
  - Can render as `<Link>` for navigation.
- **StatusBadge:** replaces the one in `Feedback.tsx`. Pill, 12px/700, always shows the label as
  text.

  | Value | Label | Tokens |
  | --- | --- | --- |
  | Event `SCHEDULED` | Scheduled | `--status-scheduled-*` |
  | Event `ACTIVE` | Active | `--status-active-*` |
  | Event `FINISHED` | Finished | `--status-finished-*` |
  | Event `CANCELLED` | Cancelled | `--status-cancelled-*` |
  | Registration `CONFIRMED` | Confirmed | `--status-active-*` |
  | Registration `WAITING_LIST` | Waitlisted | `--status-waitlist-*` |
  | Registration `CANCELLED` | Cancelled | `--status-cancelled-*` |
  | User `ACTIVE` | Active | `--status-active-*` |
  | User `INACTIVE` | Inactive | `--status-finished-*` |

- **Card:** surface background, radius-xl, shadow-card, padding 24–28px.
- **StatTile:** label (13px, muted) + big number (28–36px/800, `tabular-nums`) + optional
  caption.
- **OccupancyRing:** SVG donut with the track in the border color and the value in the accent.
  Uses `role="img"` and an `aria-label` with the percentage.
- **ProgressBar:** 8px track.
  - Fill: accent by default; amber (`--status-waitlist-fg`) when
    `registeredCount >= capacity`; gray when the event is closed and not full.
  - Replaces the current `<progress>`.
- **PillTabs:** the active pill is filled with `--color-text` and has white text; the others are
  outlined. Each pill is a `button` with `aria-pressed`. Pills can show a count:
  `Confirmed · 87`.
- **DataRow:** rounded row (radius 14px) on surface-alt, with an 8px gap between rows instead
  of borders. Column headers are uppercase, 12px, letter-spaced and muted.
- **Avatar:** circle with initials (first letter of the first two words) on `--color-avatar`,
  in accent-text. Sizes sm (list rows) and lg (detail header).
- **DateTile:** rounded square on accent-soft with the month in small caps (`NOV`) and the
  2-digit day (`05`). Build it from `startDate` with `toLocaleDateString('en-US', …)` and reuse
  `src/utils/dates.ts`.
- **FormField:** restyle the existing component: label + input + hint + error.
- **Alert:**
  - Variants: error (cancelled tokens), warning (waitlist tokens) and success (active tokens).
    Error uses `role="alert"`; success uses `role="status"`.
  - Keep the logic of `ErrorAlert` (ApiError → message + list of `details`) and
    `SuccessAlert`.
- **ConfirmPanel:** inline confirmation with a title, text, a "Keep as is" button and the
  action button.
  - Replaces `window.confirm` in `EventDetailPage` (finish or cancel an event) and
    `RegistrationsCard` (cancel a registration).
  - Also used for "Deactivate" on the user detail page.
- **EmptyState, Spinner, SearchInput:** SearchInput has a search icon and a visually hidden
  `<label>`.

## Screens

### Login (01)

- **Accent panel:**
  - Logo, the title "Every seat, accounted for." and the text "Create events, manage
    registrations and let the waitlist fill freed seats automatically."
  - The bottom card is a static decorative illustration with `aria-hidden="true"`. No API
    data is available before login.
- **Form:**
  - Title "Welcome back" and subtitle "Sign in to manage your events."
  - Fields: Username, and Password with a Show/Hide toggle button.
  - A full-width "Sign in" button with a spinner while the request is pending.
- **Session expired:** the session-expired notice is a warning Alert.

### Not found (03)

- A centered card with a tile reading "ERR / 404".
- Title "This page has no seats" and the text "The page you are looking for does not exist or
  was moved. Check the address or go back to the events."
- Buttons: "Back to events" (primary link) and "Sign in" (secondary, shown only without a
  session).

### Events list (04)

- **Header:**
  - Title "Events" with the text "Create events and follow their availability in real time."
  - "+ New event" on the right.
- **Filters:** PillTabs All / Scheduled / Active / Finished / Cancelled and a SearchInput
  "Search events" (by name). Both filter on the client.
- **Cards:** a 3-column grid. Each card links to `/events/:id` and shows:
  - a DateTile and a StatusBadge in the top-right corner;
  - the name and a date line:
    - same day: `Nov 28, 2026 · 6:00 PM`;
    - multi-day: `Nov 20 — 21, 2026`;
  - a ProgressBar and `X / Y registered`.
- **Right-hand label on each card:**
  - "Closed" for `FINISHED` or `CANCELLED`;
  - otherwise "Full" in waitlist-fg when the event is full;
  - otherwise "N seats left" in accent-text.
- **Empty state:** when there are no events or no matches.

### Event detail (05)

- **Breadcrumb:** Events / {name}.
- **Header card:** DateTile, name + StatusBadge, `{start} — {end} · Capacity N`, and these
  actions:
  - "Edit" (secondary), hidden for `FINISHED` and `CANCELLED`;
  - one button per entry in `NEXT_STATUSES[status]`: "Activate" (secondary), "Finish"
    (secondary + ConfirmPanel), "Cancel event" (danger-outline + ConfirmPanel);
  - "+ Register user" (primary), disabled when `!acceptingRegistrations`.
- **Register user:** the button opens an inline panel with the existing user picker: users
  without an active registration, same logic as today. When the event is full, show the
  warning "The event is full: the user will join the waitlist."
- **Stat tiles** (from availability):
  - Occupancy: OccupancyRing + `min(registeredCount, capacity) / capacity` as %.
  - Seats available: `availableSeats` in accent-text, with the caption "Accepting
    registrations" (active fg) or "Registrations closed" (muted).
  - Waitlist: `waitlistCount` in waitlist-fg, with the caption "Promoted automatically".
- **Registrations card:**
  - Title "Registrations" with PillTabs: "Confirmed · N" (default), "Waitlist · N" and
    "Cancelled · N".
  - Each DataRow shows an Avatar, `userName`, `userEmail`, a StatusBadge, `registrationDate`
    (date only) and a "Cancel" button (secondary; only for `CONFIRMED` and `WAITING_LIST`).
  - "Cancel" opens a ConfirmPanel and then calls DELETE. Afterwards, show a success Alert that
    names the `promoted` user when there is one.
  - Waitlist rows show their position (`#1`, `#2`, …) in list order: oldest first.

### Event form (06)

- **Header:**
  - Breadcrumb: Events / New event, or Events / {name} / Edit.
  - Title: "New event" or "Edit event".
  - Create only: the subtitle "The event starts as Scheduled with 0 registrations."
- **Form card:**
  - Fields: Name*, Description, Start date* + End date* (`datetime-local`, 2 columns) and
    Capacity* with the hint "Must be greater than 0."
  - Footer with a divider, then "Cancel" + "Create event" / "Save changes".
- **Client-side checks before submit** (they mirror the backend):
  - end date after start date, with the error under End date: "End date must be after the
    start date.";
  - capacity > 0.
- **Side column:**
  - A "Good to know" card with checkmarks: "Registrations close automatically when the event
    starts.", "When full, new registrations go to the waitlist." and "Raising capacity later
    promotes people from the waitlist."
  - Edit only: a warning Alert, "**Editing:** capacity cannot be lower than current
    registrations, and finished or cancelled events cannot be edited."

### Users list (07)

- **Header:**
  - Title "Users" with the text "People who can register to events. Inactive users cannot
    register."
  - "+ New user" on the right.
- **Stat tiles** (computed from the list): Total users, Active (active fg) and Inactive (muted).
- **Table card:**
  - A SearchInput "Search by name, email or document".
  - Columns NAME (Avatar + name), EMAIL, DOCUMENT and STATUS, plus a "View" link to
    `/users/:id`.
  - Footer "Showing X of Y" with Previous/Next. Pagination is client-side, 10 users per page.

### User form (08)

- **Header:**
  - Breadcrumb: Users / New user, or Users / {name} / Edit.
  - Title: "New user" or "Edit user".
  - Create only: the subtitle "New users are created as Active."
- **Fields:** Full name* (`name`), Email* (`email`) and Document number* (`document`).
- **Errors:** a 409 goes in the top Alert; 400 `details` go under each field.
- **Footer:** "Cancel" + "Create user" / "Save changes". When editing, keep the current PATCH
  logic.

### User detail (09)

- **Breadcrumb:** Users / {name}.
- **Header card:**
  - A large Avatar, name + StatusBadge and `{email} · Doc. {document}`.
  - Actions: "Edit" (→ `/users/:id/edit`), and "Deactivate" (danger-outline + ConfirmPanel)
    or "Activate" (primary) depending on the status.
- **Events card:**
  - Title "Events" with PillTabs All / Confirmed / Waitlisted / Cancelled, filtered on the
    client.
  - Table columns: DATE (DateTile), EVENT (link to `/events/:eventId`), STATUS, REGISTRATION
    and REGISTERED ON.
  - Empty state: "This user has no registrations yet."

## Forms

- The label goes above the input (14px/600), with the required mark in the danger color.
- Inputs are 48px high, with radius 12px, a border-strong border and a surface-alt
  background.
- Error state: a 1.5px danger border and `aria-invalid`, with the message below linked by
  `aria-describedby`.
- Backend 400 errors appear per field; backend 409 messages appear in an Alert at the top.
- The submit button is disabled and shows a spinner while the request is pending.

## Accessibility

- Contrast meets WCAG AA. The focus ring is visible: `3px solid var(--color-focus)`.
- Use real `<button>`, `<a href>` / `<Link>` and `<label for>` elements. Icon-only buttons get
  an `aria-label`.
- Status is always shown as text, never only as color. Errors use `role="alert"`.
- Touch targets are at least 44px.

## Security (mandatory, CSP compatible)

There is no CSP today. These rules keep the app ready for `style-src 'self'`.

- Use CSS modules only (`*.module.css`, supported natively by Vite). No `style` attributes, no
  inline `<style>` and no runtime CSS-in-JS.
- Drive dynamic visuals (progress bar, ring) through SVG attributes, such as the `width` of a
  `<rect>` or `stroke-dasharray`, or through predefined classes. Never use `style={{}}`.
- No `dangerouslySetInnerHTML`, `innerHTML` or `eval`. Render user data as text.
- If a CSP is added later, Google Fonts needs `fonts.googleapis.com` (style) and
  `fonts.gstatic.com` (font), or the font must be self-hosted.

## Verification

From `../frontend`:

1. Run `npm run build` (it includes `tsc -b`) and `npm run lint`. Both must pass with no errors.
2. Start the API from `event-platform/` with
   `.\gradlew.bat bootTestRun --args="--spring.profiles.active=test"` (H2; user `admin`,
   password `test-admin-password`), then run `npm run dev`.
3. Compare every screen you touched with its PNG at about 1280px and at 375px. Check these
   states: loading, empty, error, a full event, a closed event and an inactive user.
4. Check that this grep returns nothing:
   `grep -rnE "style=|dangerouslySetInnerHTML|innerHTML" src`. Also check that the only hex
   colors are in `src/styles/tokens.css`.

## Commits

Follow `CLAUDE.md`: Conventional Commits in English with the scope `frontend`, one commit per
component or screen. For example:

- `feat(frontend): add design tokens and app shell`
- `feat(frontend): restyle events list as cards`
