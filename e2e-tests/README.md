# Event Platform – Pruebas E2E con Serenity BDD

Pruebas end-to-end de la interfaz web (`../frontend`) contra la API real (`../event-platform`).
Están escritas con **Serenity BDD 5.4.1**, **Cucumber 8** (Gherkin) y el **patrón Screenplay**, y se
adaptaron del proyecto base del curso (`serenity-login-test-final`, ver
[Cambios respecto al proyecto base](#cambios-respecto-al-proyecto-base)).

```text
Event-Tikect-Platform/       raíz del repositorio Git
|-- event-platform/          API Spring Boot
|-- frontend/                Interfaz web Vite + React
`-- e2e-tests/               Este proyecto (independiente: no toca el build ni el pipeline de la API)
```

## Contenido

1. [Requisitos](#requisitos)
2. [Levantar la aplicación](#1-levantar-la-aplicación)
3. [Ejecutar las pruebas](#2-ejecutar-las-pruebas)
4. [Ver el reporte de Serenity](#3-ver-el-reporte-de-serenity)
5. [Flujos automatizados](#flujos-automatizados)
6. [Configuración](#configuración)
7. [Estructura y patrón Screenplay](#estructura-y-patrón-screenplay)
8. [Datos de prueba y fechas](#datos-de-prueba-y-fechas)
9. [Selectores](#selectores)
10. [Cambios respecto al proyecto base](#cambios-respecto-al-proyecto-base)
11. [Grabar el video de la ejecución](#grabar-el-video-de-la-ejecución)
12. [Problemas comunes](#problemas-comunes)

## Requisitos

| Herramienta | Versión | Notas |
| --- | --- | --- |
| JDK | 21 | `JAVA_HOME` configurado. Gradle no se instala: el proyecto trae Gradle Wrapper 9.7.1 |
| Node.js | 20 o superior | Para el frontend (`npm`) |
| Navegador | Google Chrome (por defecto) o Microsoft Edge | Si Chrome no está instalado, Selenium Manager descarga **Chrome for Testing** automáticamente la primera vez (requiere internet). El driver (chromedriver/msedgedriver) también se descarga solo |

No se necesita Docker ni PostgreSQL: la API corre con H2 en memoria.

## 1. Levantar la aplicación

Usa **dos terminales** de PowerShell, desde la raíz del repositorio:

```powershell
# Terminal 1 – API con H2 en memoria (perfil test) en http://localhost:8080
cd event-platform
.\gradlew.bat bootTestRun --args="--spring.profiles.active=test"
```

```powershell
# Terminal 2 – Frontend en http://localhost:5173
cd frontend
npm install
npm run dev
```

Comprueba que <http://localhost:5173> muestra el login. El perfil `test` crea el administrador `admin` /
`test-admin-password` (valores de `event-platform/src/test/resources/application-test.properties`) y
pierde todos los datos al detener la API.

## 2. Ejecutar las pruebas

En una **tercera terminal**, desde `e2e-tests`:

| Objetivo | Comando |
| --- | --- |
| Todos los escenarios | `.\gradlew.bat clean test` |
| Solo el flujo de login | `.\gradlew.bat clean test -Ptags="@login"` |
| Solo el flujo de inscripciones | `.\gradlew.bat clean test -Ptags="@registrations"` |
| Todos los E2E (equivale a todos) | `.\gradlew.bat clean test -Ptags="@e2e"` |
| Sin ventana del navegador | `.\gradlew.bat clean test -Pheadless=true` |
| Con Microsoft Edge | `.\gradlew.bat clean test -Pbrowser=edge` |
| Regenerar solo el reporte | `.\gradlew.bat aggregate` |

- `-Ptags` acepta expresiones de Cucumber, por ejemplo `-Ptags="@registrations and not @login"`.
- El reporte agregado de Serenity se genera **siempre** al final de `test` (tarea `aggregate`), incluso si
  algún escenario falla. `clean` borra los resultados anteriores (`build/` y `target/`).
- La ejecución completa tarda unos 2 minutos (15 casos de prueba; el navegador se reinicia en cada uno).
- Si algún escenario falla, Gradle termina con `BUILD FAILED`, pero el reporte queda generado.

## 3. Ver el reporte de Serenity

```powershell
start target\site\serenity\index.html
```

Ruta completa: `e2e-tests/target/site/serenity/index.html`. El reporte muestra cada feature y escenario,
los pasos de Gherkin, las tareas y preguntas de Screenplay que los componen y **una captura de pantalla
por cada acción** (clic, escritura, selección). El resumen en texto queda en
`target/site/serenity/summary.txt` (11 escenarios, que suman 15 casos al contar cada ejemplo de los
*Scenario Outline*).

## Flujos automatizados

### Flujo 1 – Inicio de sesión (`features/login.feature`, etiquetas `@e2e @login`)

Antecedentes: el administrador abre `/login`.

| Escenario | Qué valida |
| --- | --- |
| The administrator signs in with valid credentials | Con las credenciales configuradas llega a `/events`, ve el título "Events" y su usuario en la barra lateral |
| Invalid credentials are rejected by the API (Esquema, 2 ejemplos) | Contraseña incorrecta y usuario inexistente: la API responde 401 y la UI muestra "Invalid username or password" sin salir de `/login` |
| Empty fields are validated before calling the API (Esquema, 2 ejemplos) | Usuario o contraseña vacíos: "Username is required" / "Password is required" debajo del campo |
| A protected page redirects to the login page without a session | Abrir `/users` sin sesión redirige a `/login` |
| Signing out closes the session | Tras "Sign out" vuelve al login y `/events` ya no es accesible |

### Flujo 2 – Gestión de eventos con inscripciones y lista de espera (`features/event_registrations.feature`, etiquetas `@e2e @registrations`)

Antecedentes: el administrador inicia sesión desde la interfaz. Todo se hace desde la UI, sin llamar a la API directamente.

| Escenario | Qué valida |
| --- | --- |
| A freed seat is given to the first person on the waitlist | Crea un evento de capacidad 2 dentro de 30 días y 3 usuarios; inscribe a los 3: dos quedan `CONFIRMED` y la tercera `WAITING_LIST` (#1 en la fila); la disponibilidad muestra 0 cupos y 1 en espera. Al cancelar a la primera, la UI avisa que la tercera fue promovida, queda `CONFIRMED` y la lista de espera vuelve a 0 |
| A new event is scheduled and can be activated | El evento nuevo aparece en el listado como `SCHEDULED` y pasa a `ACTIVE` con el botón "Activate" |
| The event form rejects invalid data (Esquema, 3 ejemplos) | Capacidad 0 o negativa y fecha de fin anterior al inicio muestran el error del campo y no crean el evento |
| A registered user is not offered again for the same event | Regla de no duplicados: quien ya está inscrito no aparece en la lista "User to register" |
| The capacity cannot be lowered below the confirmed registrations | Editar la capacidad a 1 con 2 inscritos muestra el 409 "Capacity cannot be lower than the registered count (2)" |
| A user cannot be created with an email that is already registered | El 409 "Email is already registered" se muestra en el formulario |

Cada escenario termina con aserciones (`seeThat`) sobre lo que muestra la interfaz.

## Configuración

Los valores por defecto están en `src/test/resources/serenity.conf`. Serenity solo resuelve
`${...}` con propiedades del sistema de Java, no con variables de entorno, así que las variables las
lee `build.gradle` y las pasa a la JVM de las pruebas. Esos valores tienen prioridad sobre el `.conf`.

| Variable de entorno | Propiedad de Gradle | Valor por defecto | Uso |
| --- | --- | --- | --- |
| `E2E_BASE_URL` | `-PbaseUrl` | `http://localhost:5173` | URL del frontend |
| `E2E_BROWSER` | `-Pbrowser` | `chrome` | `chrome` o `edge` |
| `E2E_HEADLESS` | `-Pheadless` | `false` | `true` para ejecutar sin ventana |
| `E2E_TAGS` | `-Ptags` | todos | Expresión de etiquetas de Cucumber |
| `E2E_ADMIN_USERNAME` | — | `admin` | Usuario administrador |
| `E2E_ADMIN_PASSWORD` | — | `test-admin-password` | Contraseña del administrador |
| `E2E_TIMEZONE` | — | `America/Bogota` | Zona horaria del servidor de la API |

Los valores por defecto son las credenciales **de prueba** del perfil `test` de la API. Para otra
instalación no las escribas en archivos: defínelas solo en la sesión de PowerShell.

```powershell
$env:E2E_ADMIN_USERNAME = 'admin'
$secret = Read-Host 'Contraseña del administrador' -AsSecureString
$env:E2E_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $secret).Password
.\gradlew.bat clean test
```

La contraseña nunca aparece en el reporte: se escribe con la interacción `EnterSecret`, que no muestra
su valor.

## Estructura y patrón Screenplay

```text
e2e-tests/
|-- build.gradle, settings.gradle, gradlew(.bat), gradle/wrapper/   Gradle 9.7.1 + plugin de Serenity (tarea aggregate)
`-- src/test/
    |-- resources/
    |   |-- serenity.conf               URL, navegador, esperas, credenciales y zona horaria por defecto
    |   |-- junit-platform.properties   Opciones de Cucumber
    |   `-- features/                   login.feature, event_registrations.feature
    `-- java/com/eventplatform/e2e/
        |-- runners/          CucumberTestSuite: JUnit Platform Suite + reporter de Serenity
        |-- stepdefinitions/  Traducen cada paso de Gherkin a tareas y preguntas; StageHooks crea el actor
        |-- tasks/            Lo que hace el actor: Login, SignOut, CreateEvent, CreateUser, RegisterUser,
        |                     CancelRegistration, ChangeEventStatus, ChangeEventCapacity, OpenTheEvent...
        |-- interactions/     Acciones de bajo nivel propias: EnterDateTime, EnterSecret
        |-- questions/        Lo que el actor observa: TheCurrentPage, TheSignedInUser, TheMessages,
        |                     TheEvent (estado, cupos, lista de espera), TheRegistrations
        |-- userinterfaces/   Targets (selectores) por pantalla: LoginPage, AppShell, EventListPage,
        |                     EventFormPage, EventDetailPage, RegistrationsSection, UserListPage, UserFormPage
        |-- models/           EventData, UserData, CreatedEvent, EventStatus, RegistrationStatus
        `-- utils/            TestConfiguration, UniqueData, RelativeDates, ActorMemory
```

- El actor **Administrator** tiene la habilidad *BrowseTheWeb* (`OnlineCast`); Serenity abre y cierra el
  navegador en cada escenario.
- **Tasks** = intención de negocio ("crear un evento"); **Interactions** = acciones de UI; **Questions** =
  lo que se verifica. Las verificaciones se hacen en los pasos `Then` con `seeThat(...)`.
- **Esperas explícitas**: `WaitUntil.the(target, isVisible() / containsText(...))` en las tareas y
  `eventually(seeThat(...))` en las aserciones, que reintenta hasta 10 s mientras la UI recarga datos. No
  hay `Thread.sleep`.
- El actor recuerda el evento creado (con su id, tomado de la URL `/events/{id}`) y los usuarios por nombre
  (`ActorMemory`), para usarlos en los pasos siguientes.

## Datos de prueba y fechas

- La API conserva sus datos en H2 mientras está encendida y rechaza correos y documentos repetidos (409).
  Por eso `UniqueData` agrega a cada nombre de evento, correo y documento un sufijo único por ejecución
  (base 36 del timestamp + secuencia), por ejemplo `ada.lovelace.mhb3x0k27@example.com` y
  `E2E-MHB3X0K27`. Las pruebas se pueden repetir sin reiniciar la API.
- Los nombres de los features ("Ada Lovelace", "Java Conf") son legibles; el sufijo lo pone el código.
- Las fechas de los eventos se calculan **relativas a hoy** (`starting in 30 days`): inicio a las 18:00 y
  fin a las 21:00. Nunca quedan en el pasado.
- La API compara `startDate` (un `LocalDateTime` sin zona) con la hora de su servidor. Las fechas se
  calculan en `America/Bogota` (UTC-5, configurable con `E2E_TIMEZONE`) para que coincidan con el reloj
  de la API aunque el equipo de pruebas tenga otra zona.
- El campo `datetime-local` se llena con la interacción `EnterDateTime`: escribir en ese control depende
  del idioma del navegador y del formato de fecha del sistema operativo, así que se asigna el valor ISO
  (`yyyy-MM-ddTHH:mm`) y se dispara el evento `input` que escucha React.

## Selectores

Los selectores usan `id`, atributos `aria-*` y atributos `data-testid`, nunca las clases CSS (son de CSS
Modules y cambian en cada build). Para eso se agregaron atributos `data-testid` al frontend:

| `data-testid` | Elemento |
| --- | --- |
| `alert-error`, `alert-success`, `alert-warning` | Avisos (`Alert.tsx`) |
| `current-username` | Usuario en la barra lateral (`Layout.tsx`) |
| `event-card`, `event-card-status` | Tarjetas del listado de eventos |
| `event-title`, `event-status`, `status-action-<estado>`, `open-register-panel` | Página del evento |
| `availability-occupancy`, `availability-seats`, `availability-waitlist` | Tarjetas de disponibilidad |
| `registration-row`, `registration-status`, `waitlist-position`, `cancel-registration` | Tabla de inscripciones |
| `filter-<estado>` | Filtros de estado (`PillTabs.tsx`) |
| `confirm-button` | Confirmación de acciones irreversibles (`ConfirmPanel.tsx`) |
| `user-row` | Filas de la tabla de usuarios |

Los selectores CSS llevan el prefijo `css:` (por ejemplo `css:a[href='/events/new']`). Sin él, Serenity
interpreta como XPath los que empiezan con `etiqueta[`. Solo las filas de las tablas usan XPath, para
encontrar la fila por el correo del usuario.

## Cambios respecto al proyecto base

| Proyecto base (`serenity-login-test-final`) | Este proyecto |
| --- | --- |
| Login de saucedemo.com | Flujos reales de Event Platform (login + eventos e inscripciones) |
| Page Objects + `@Steps` | Patrón Screenplay (actor, tasks, interactions, questions, targets) |
| Serenity 3.9.8, Cucumber 7, paquetes `net.thucydides.*` | Serenity 5.4.1, Cucumber 8.0.4, JUnit Platform 6, Gradle 9.7.1 |
| Sin reporter de Serenity en Cucumber: no se generaba el reporte | `SerenityReporterParallel` + tarea `aggregate` automática |
| Verificación con `assert` de Java (desactivado en la JVM: nunca fallaba) | Aserciones de Serenity/Hamcrest (`seeThat`) |
| Ruta fija a `chromedriver.exe` y WebDriverManager | Selenium Manager descarga el driver (y Chrome for Testing si hace falta) |
| Gherkin sin `Background`, `Scenario Outline` ni etiquetas | `Background`, `Scenario Outline` + `Examples` y etiquetas `@e2e`, `@login`, `@registrations` |
| Sin `.gitignore` | Ignora `build/`, `.gradle/` y `target/` |

Se eliminaron `LoginPage`, `LoginSteps`, `LoginDefinitions`, `LoginRunner` y el `login.feature` de saucedemo;
del proyecto base solo se conservó el Gradle Wrapper (actualizado a 9.7.1).

## Grabar el video de la ejecución

1. Levanta la API y el frontend ([paso 1](#1-levantar-la-aplicación)).
2. Ejecuta las pruebas **con ventana** (es el modo por defecto): `.\gradlew.bat clean test`. Chrome se abre
   en cada escenario y se ven los formularios llenándose.
3. Al terminar (`BUILD SUCCESSFUL`), abre el reporte con `start target\site\serenity\index.html` y
   muestra el resumen, un escenario de cada feature y sus capturas.

Para un video más corto, graba un flujo a la vez con `-Ptags="@login"` y `-Ptags="@registrations"`.

## Problemas comunes

| Síntoma | Causa y solución |
| --- | --- |
| Todos los escenarios fallan en el primer paso (no aparece el campo *username field*) y la captura muestra la página de error de Chrome | El frontend no está corriendo en `http://localhost:5173` (`npm run dev`) |
| El login muestra "Cannot reach the API at http://localhost:8080" | La API no está corriendo (`bootTestRun`) |
| "Invalid username or password" en el escenario de login válido | La API no usa el perfil `test` o las credenciales no coinciden: revisa `E2E_ADMIN_USERNAME` / `E2E_ADMIN_PASSWORD` |
| La primera ejecución tarda varios minutos | Selenium Manager está descargando el driver o Chrome for Testing; queda en `%USERPROFILE%\.cache\selenium` |
| `clearReports` falla con "The process cannot access the file" | Una terminal o el Explorador de archivos está abierto dentro de `target\site\serenity`: ciérralo y repite |
| `-Dalgo.con.puntos=...` llega cortado en PowerShell | PowerShell separa los argumentos con puntos; usa las propiedades `-P` de la tabla o ponlas entre comillas |
