# viajes-eventos-api

Backend de viajes en bus a eventos. Java 25 nativo, sin Spring. Usa la misma arquitectura
"library-free" de [`doscolas/dc-api-v2`](../../doscolas/dc-api-v2) y
[`calendario/api`](../../calendario/api):

- **HTTP**: `com.sun.net.httpserver.HttpServer` del JDK + un `Router` propio (segmentos `{name}`, CORS, errores JSON).
- **Persistencia**: JDBC plano sobre un pool de conexiones propio. Sin JPA/Hibernate.
- **JSON / JWT**: lector/escritor JSON y JWT HS256 hechos a mano. Sin Jackson ni JJWT.
- **Migraciones**: `MigrationRunner` aplica `src/main/resources/db/migrations/V*__*.sql` al arrancar.

Dependencias de runtime: driver de PostgreSQL, `at.favre.lib:bcrypt` y Jakarta Mail (SMTP).

## Empresas (multiempresa)

La plataforma la usan varias empresas de transporte, cada una con sus propios administradores, clientes y eventos
(tabla `companies`, `V5__companies.sql`):

- **Cada usuario y cada evento pertenece a una empresa** (`users.company_id`, `events.company_id`). Las rutas con
  módulo reciben un `Caller` (usuario + empresa) y todo lo que leen o escriben queda filtrado a esa empresa. Un evento
  o usuario de otra empresa responde 404, igual que uno que no existe.
- **Las empresas se registran solas** en `POST /auth/register-company` (la UI está en `/signup`). Se crea la empresa y
  su primer administrador con perfil `ADMIN`.
- **Los clientes se registran con el link de su empresa**, `/empresa/<slug>` en la UI (`POST /auth/register` con
  `company`). Quedan como `CLIENT` de esa empresa y solo ven sus eventos. El slug no cambia después de creado.
- El login es por correo, y el correo es único en toda la plataforma. Si una persona es cliente de dos empresas,
  necesita dos correos.
- **Correo:** hay un solo SMTP para toda la plataforma. Cada correo sale con el nombre de la empresa como remitente y
  su correo de contacto como Reply-To.
- **Dueño de la plataforma:** las cuentas con `users.platform_admin` reciben además los módulos de plataforma,
  `PROFILES`, `SETTINGS` y `COMPANIES`. Esos módulos nunca se otorgan por perfil, porque los perfiles son compartidos
  y un administrador de empresa podría dárselos. La migración marca como dueño a los administradores que ya existían.
  `AdminBootstrap` crea uno solo si no hay ninguno.
- Si el dueño deshabilita una empresa, ninguna de sus cuentas puede entrar y su link deja de funcionar. Los datos se
  conservan.

## Perfiles y módulos (patrón de condominios)

El acceso no está programado por rol, se maneja como datos:

- `AppModule` (enum) lista las áreas funcionales: `EVENTS`, `MY_BOOKINGS`, `BOOKINGS`, `EVENT_ADMIN`, `TRIP_ADMIN`,
  `BOARDING`, `REVIEWS`, `USERS`, `COMPANY`, y las de plataforma `PROFILES`, `SETTINGS` y `COMPANIES` (ver
  [Empresas](#empresas-multiempresa)).
- Un **perfil** (`profiles` + `profile_modules`) habilita un conjunto de módulos. Cada usuario tiene exactamente un perfil (`users.profile_id`).
- Cada ruta se registra con su módulo: `router.get("/users", AppModule.USERS, handler)`. Antes de ejecutar el
  handler, el `Router` llama a `ModuleAccess`, que es el equivalente al `@RequiresModule` + `ModuleAccessInterceptor`
  de condominios. Verifica que la persona tenga sesión, que su cuenta esté habilitada y que su perfil tenga el módulo.
- El JWT solo identifica a la persona. Los módulos se leen en cada request, así que editar un perfil, reasignarlo o
  deshabilitar una cuenta tiene efecto de inmediato.
- El front recibe `modules` en `/auth/me` y en el login, y con eso arma la navegación y protege las páginas.

Perfiles del sistema (se crean en `V1__init.sql`):

| Perfil | Código | Módulos | Reglas |
|---|---|---|---|
| Administrador | `ADMIN` | todos los de empresa menos `MY_BOOKINGS` | Por ahora el administrador no reserva viajes (`AppModule.adminModules()`). Al arrancar se le agregan los módulos nuevos. No se puede eliminar. |
| Cliente | `CLIENT` | `EVENTS`, `MY_BOOKINGS` | El registro con el link de la empresa siempre asigna este perfil. Sus módulos se pueden editar. No se puede eliminar. |

Desde el mantenedor se pueden crear más perfiles, por ejemplo "Coordinador" con `EVENTS` + `USERS`, o
**"Conductor" con solo `BOARDING`**: ve el manifiesto de las salidas que parten en las próximas 48 horas y marca
quién sube, y nada más.

Además hay estas protecciones:
- No se puede eliminar un perfil que tenga usuarios asignados.
- Nadie puede deshabilitarse ni cambiarse el perfil a sí mismo.
- Cada empresa debe tener siempre al menos un administrador habilitado.

**Módulos deshabilitados por ahora:** `COMPANY`, `PROFILES`, `SETTINGS` y `COMPANIES` (`AppModule.DISABLED`).
`ModuleAccess` responde 403 en sus rutas a todos, tenga el perfil que tenga, y la UI oculta sus páginas. Los tendrá un
super admin en una versión futura. Las rutas públicas (`/companies/public/{slug}`, `/auth/register-company`) siguen
funcionando.

**Agregar un módulo:** súmalo a `AppModule`, registra sus rutas con él y agrega su entrada en `ui/src/lib/modules.ts`
y en `ModuleKey`.

## Endpoints

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/auth/register-company` | público — registrar una empresa y su administrador |
| POST | `/auth/register` (con `company`), `/auth/login`, `/auth/verify-email`, `/auth/resend-verification`, `/auth/forgot-password`, `/auth/reset-password` | público (con límite de intentos) |
| GET | `/companies/public/{slug}` | público — nombre de la empresa del link de registro (histórico) |
| GET | `/public/company` | público — la empresa de este despliegue |
| GET | `/public/events` | público — el catálogo indexable: eventos con sus salidas y notas |
| GET | `/public/events/{slug}` | público — una página de evento, con salidas y reseñas |
| GET | `/public/trips/{id}` | público — una salida, para la página de reserva antes de iniciar sesión |
| GET | `/public/policies` | público — las condiciones del viaje |
| GET | `/public/sitemap` | público — los slugs que valen indexar |
| GET / PUT | `/company` | `COMPANY` — mi empresa (nombre, correo de contacto) |
| GET | `/companies` | `COMPANIES` — todas las empresas (plataforma) |
| PATCH | `/companies/{id}` | `COMPANIES` — habilitar o deshabilitar |
| GET / PUT | `/auth/me` | sesión (mi perfil) |
| GET | `/events` | `EVENTS` — eventos próximos con sus salidas disponibles |
| GET | `/trips/{id}` | `EVENTS` — una salida con sus paradas |
| POST | `/trips/{id}/bookings` | `MY_BOOKINGS` — reservar asientos (1 a 20 pasajeros) |
| POST / DELETE | `/trips/{id}/waitlist` | `MY_BOOKINGS` — anotarse o salir de la lista de espera |
| GET | `/bookings/me` | `MY_BOOKINGS` — mis reservas y en qué listas de espera estoy |
| POST | `/bookings/{id}/cancel` | `MY_BOOKINGS` — cancelar una reserva propia |
| GET | `/admin/bookings/trips`, `/admin/bookings?tripId=&eventId=` | `BOOKINGS` — todas las reservas y pasajeros |
| GET / POST | `/admin/events` | `EVENT_ADMIN` — el catálogo de eventos (con salidas y pasajeros) / crear |
| GET / PUT / DELETE | `/admin/events/{id}` | `EVENT_ADMIN` |
| GET | `/admin/trips/events` | `TRIP_ADMIN` — eventos para elegir al crear una salida |
| GET / POST | `/admin/trips?eventId=` | `TRIP_ADMIN` — las salidas de la empresa / publicar una |
| GET / PUT / PATCH / DELETE | `/admin/trips/{id}` | `TRIP_ADMIN` — `PATCH` solo cambia el estado |
| GET | `/admin/trips/{id}/waitlist` | `TRIP_ADMIN` — quién espera un cupo |
| GET | `/boarding/trips` | `BOARDING` — las salidas que parten alrededor de ahora |
| GET | `/boarding/trips/{id}` | `BOARDING` — manifiesto agrupado por parada |
| GET | `/boarding/trips/{id}/ticket?code=` | `BOARDING` — buscar una reserva por su código, sin marcar nada |
| POST | `/boarding/trips/{id}/checkin` | `BOARDING` — marcar un grupo (`ticketCode`) o un pasajero (`bookingId` + `position`) |
| GET | `/policies` | `EVENTS` — las condiciones del viaje que el cliente lee antes de reservar |
| GET | `/reviews?tripId=` | `EVENTS` — reseñas publicadas y la nota promedio por salida |
| GET | `/reviews/reviewable`, `/reviews/me` | `MY_BOOKINGS` — qué salidas puedo reseñar y mis reseñas |
| PUT | `/trips/{id}/review` | `MY_BOOKINGS` — escribir o editar mi reseña de una salida |
| GET | `/admin/reviews?tripId=` | `REVIEWS` — todas las reseñas, ocultas incluidas |
| PATCH | `/admin/reviews/{id}` | `REVIEWS` — ocultar o volver a publicar |
| GET / PUT | `/admin/policies` | `REVIEWS` — las condiciones del viaje de la empresa |
| POST | `/auth/change-password` | sesión |
| GET | `/profiles/modules` | `PROFILES` |
| GET / POST | `/profiles` | `PROFILES` |
| GET / PUT / DELETE | `/profiles/{id}` | `PROFILES` |
| GET | `/users/profile-options` | `USERS` |
| GET / POST | `/users` | `USERS` |
| GET / PUT | `/users/{id}` | `USERS` |
| POST | `/users/{id}/verify-email`, `/users/{id}/resend-verification` | `USERS` — verificar a mano o reenviar el enlace |
| GET / PUT | `/settings/smtp` | `SETTINGS` |
| POST | `/settings/smtp/test` | `SETTINGS` — correo de prueba |

## Eventos, salidas y reservas

Desde `V8__trips.sql` el **evento** (qué ocurre y dónde) está separado de la **salida** (el bus que
lleva gente a él). Antes eran lo mismo, así que llevar gente al mismo recital desde dos comunas
obligaba a cargar el evento dos veces, y no había dónde guardar el cupo ni el precio.

- **`events`** es el catálogo de la empresa (módulo `EVENT_ADMIN`). Sigue teniendo `company_id`:
  esto **no** convierte la plataforma en un marketplace, cada empresa mantiene su propio catálogo.
  `V3__events_bookings.sql` carga los 10 eventos tomados de puntoticket.com. El slug se genera del
  nombre al crear y después no cambia. Un evento desactivado no se muestra a los clientes y cierra
  las reservas de todas sus salidas, pero conserva las que ya tiene. Solo se puede eliminar un
  evento sin salidas publicadas.
- **`trips`** son las salidas (módulo `TRIP_ADMIN`): `origin_commune`, `departure_at`, `return_at`,
  `price_clp` (pesos, sin decimales), `seats_total`, `min_seats` (quórum), `booking_deadline` y
  `status` (`DRAFT` → `PUBLISHED` → `CONFIRMED`, o `CANCELLED`). Solo `PUBLISHED` y `CONFIRMED`
  reciben reservas; una en borrador no la ven los clientes y una cancelada conserva sus reservas.
  Al alcanzar `min_seats` la salida pasa sola a `CONFIRMED`.
- **`trip_stops`** son las paradas de ida, en orden, con hora y un `price_delta_clp` opcional (una
  parada más adelante en la ruta suele ser más barata). Reemplazan el texto libre que cada pasajero
  escribía: ahora el operador define las paradas y el pasajero elige una
  (`booking_passengers.stop_id`). `departure_place` y `departure_time` quedan como la foto de lo que
  se le dijo al pasajero, así que corregir una parada no reescribe los comprobantes ya emitidos.
  No se puede eliminar una parada que ya tiene pasajeros; editarla sí.
- Una reserva (`bookings`) corresponde a un envío del modal "Viajar": una cuenta registra un grupo
  de pasajeros (`booking_passengers`) **en una salida**. Cada pasajero tiene nombre, celular, parada
  y lugar de retorno. La validación es la misma que en el front; los errores se devuelven con la
  clave `passengers.<i>.<campo>` y el celular se guarda normalizado como `+56 9 XXXX XXXX`.
- **El cupo se cuenta y se toma en la misma transacción.** `BookingRepository.insert` hace
  `SELECT … FOR UPDATE` sobre la fila del viaje antes de contar los asientos ocupados. Sin eso, dos
  personas reservando los últimos asientos a la vez cuentan los mismos cupos libres y ambas entran,
  y la sobreventa aparece recién al costado de la carretera.
- **`trip_waitlist`**: si la salida está llena, el cliente se anota. Al cancelarse una reserva se
  recorre la cola de la más antigua a la más nueva y se avisa por correo a todos los que todavía
  caben. A nadie se le mueve al bus automáticamente: se le avisa y reserva, así que un grupo
  desactualizado no se come los cupos en silencio.
- Un evento acepta reservas hasta su último día en hora de Chile (`America/Santiago`); además la
  salida puede cerrar antes con `booking_deadline`.
- Solo quien hizo la reserva puede cancelarla, y solo mientras el evento no haya terminado. Cancelar
  marca la reserva como `CANCELLED`, no la borra, y devuelve sus asientos al cupo.
- El módulo `BOOKINGS` es la vista del operador: todas las reservas, filtrables por salida, con el
  total por punto de subida, los asientos vendidos y lo recaudado.

## Operación del viaje

`V9__boarding.sql` agrega lo que pasa el día del viaje:

- **Ticket.** Cada reserva tiene un `ticket_code` de 7 caracteres (`TicketCodes`). El alfabeto deja fuera las
  vocales, para que no se formen palabras, y el `0/O/1/I/L`, para que nadie se equivoque al dictarlo por teléfono.
  Es por reserva y no por pasajero porque un grupo reserva junto y llega junto. El índice único es la garantía real:
  si el código sale repetido, el repositorio saca otro. El front dibuja el QR en el navegador a partir del código,
  así que el ticket se ve sin señal, que es justo donde se necesita.
- **Check-in.** Es por pasajero (`booking_passengers.checked_in_at` / `checked_in_by`): un bus puede salir con tres
  de los cuatro de una reserva, y eso es lo que el conductor necesita ver. Se puede deshacer.
- **Manifiesto** (módulo `BOARDING`): los pasajeros agrupados por parada y hora, en orden alfabético dentro de cada
  parada, con el teléfono como enlace `tel:`. Cada acción responde con el manifiesto completo, así que la pantalla
  nunca es una suposición sobre lo que cree el servidor. Los pasajeros de reservas anteriores a las paradas (V8) no
  tienen dónde agruparse y salen en "sin parada asignada" en vez de desaparecer de la lista: dejar a alguien fuera
  de un manifiesto es como se queda la gente en tierra.
- **Recordatorio de 24 horas.** `TripReminderJob` corre cada 30 minutos desde `Main`. El intervalo solo decide qué
  tan pronto se entera una salida que entra a la ventana; `trips.reminder_sent_at` es lo que garantiza un solo
  mensaje por salida, incluso si el proceso se reinicia.
- **Panel por salida:** el módulo `BOOKINGS` filtrado por una salida muestra asientos vendidos sobre el total, lo
  que falta para el quórum y lo recaudado al precio de la parada de cada pasajero.

## Confianza: reseñas y políticas

`V10__reviews_policies.sql`:

- **La reseña es por salida, no por empresa** (`reviews`), y solo la puede escribir quien hizo check-in en esa
  salida y después de que el bus haya partido. Esa es toda la diferencia entre una reseña que significa algo y un
  formulario que cualquiera llena: el que opina se subió al bus. Una por persona por salida, editable.
- **Ocultar no es borrar.** Una reseña moderada pasa a `HIDDEN` y queda con quién la ocultó y cuándo
  (`moderated_by` / `moderated_at`). El autor sigue viendo la suya. No hay eliminar a propósito: un operador que
  puede borrar las críticas vuelve la nota inútil para el próximo cliente que la lea. La nota promedio solo cuenta
  las publicadas.
- **Las políticas** (`companies.policy_*`) se muestran dentro del modal de reserva, **antes** de confirmar, que es
  el único momento en que sirven de algo. Texto libre, y lo que la empresa deja en blanco simplemente no se muestra.
  Se editan desde el módulo `REVIEWS`.

### Notificaciones

Todo lo que una salida le dice a un pasajero pasa por `NotificationSender` (paquete `notify`), no por
`EmailSender` directamente. Hoy la única implementación es `EmailNotificationSender`, que delega en el `EmailSender`
de siempre; agregar WhatsApp o SMS es otra implementación detrás de la misma interfaz en vez de un cambio en cada
punto de llamada. Cada mensaje lleva cuerpo HTML y un `shortText` de una o dos líneas para los canales que solo
tienen espacio para eso. Un error de envío se registra, nunca se propaga: una reserva no puede fallar porque el
relay de correo esté caído.

`TripNotifier` arma los cuatro mensajes, para que los tres que los mandan no digan lo mismo de tres maneras
distintas:

| Mensaje | Lo dispara |
|---|---|
| Reserva confirmada, con paradas, horas y código del ticket | `BookingService` al reservar |
| Se liberó un cupo | `BookingService` al cancelarse una reserva, recorriendo la lista de espera |
| Mañana viajas, con la parada y la hora | `TripReminderJob`, 24 horas antes |
| Se canceló la salida | `TripService` al pasarla a `CANCELLED` |

## Catálogo público y SEO

Hasta aquí la única ruta pública de lectura era `/companies/public/{slug}`: un cliente solo llegaba
si alguien le mandaba el link de registro por WhatsApp. `PublicCatalogService` y `PublicController`
agregan el catálogo que un buscador puede leer — nadie busca "/empresa/buses-rancagua", buscan
"bus a Lollapalooza desde Rancagua".

**Un despliegue sirve a una empresa.** `COMPANY_SLUG` dice cuál; en blanco (el valor por defecto)
toma la única que haya. Por eso la empresa no aparece en la URL: las páginas públicas viven en la
raíz (`/`, `/evento/<slug>`). El esquema multiempresa se mantiene intacto — `users.company_id`,
el filtrado por `Caller` y el aislamiento que verifican los tests siguen igual — simplemente hay
una fila en `companies`, que es donde viven el nombre, el correo de contacto y las políticas.

**Qué sale y qué no.** Todo lo público es de solo lectura y deliberadamente angosto: eventos
próximos que tengan un bus que todavía se pueda tomar, sus salidas con precio, hora, paradas y
cupos restantes, las reseñas publicadas y las políticas. Ningún pasajero aparece nunca en un
payload público, y tampoco las notas del operador: `TripResponse.publicView()` saca `notes`, que el
formulario llama "notas internas" (patente del bus, nombre del conductor).
`PublicCatalogServiceIT.thePublicDepartureViewLeaksNeitherPassengersNorInternalNotes` es el test que
se rompe si alguien lo vuelve a agregar.

**La cuenta se pide al confirmar.** El visitante navega, abre `/reservar/<id>`, ve precio, paradas,
hora y cupos, y solo entonces se le pide crear cuenta o entrar, con `next` apuntando de vuelta a la
misma salida. Un muro de login delante del precio es justo como se desperdicia el SEO. `/registro`
resuelve la empresa sola, así que ya nadie necesita conocer un slug para crear una cuenta;
`/empresa/<slug>` sigue funcionando para los links ya repartidos.

**Del lado del front** (`ui/`): `/` y `/evento/[slug]` son server components con `revalidate`, así
que lo que recibe un crawler es el contenido y no un esqueleto — el resto de la app sigue siendo
client, que es lo correcto porque nadie indexa un panel. Hay `generateMetadata` por evento (título,
descripción, canonical, Open Graph con `events.image_url`), `sitemap.xml` armado desde las salidas
abiertas y `robots.txt` que bloquea todo lo que está detrás de sesión. `NEXT_PUBLIC_SITE_URL` es la
URL absoluta que necesitan el sitemap y los canonical.

El filtro por comuna de la página de evento es client-side a propósito: un query string por comuna
serían doce URLs casi idénticas compitiendo entre ellas por el mismo evento, que es como un sitio
se parte su propio ranking. Un evento, una URL.

> **Salidas migradas:** `V8__trips.sql` no tenía de dónde sacar la comuna de origen (nunca se pidió),
> así que escribió `Por definir`. El front la filtra de títulos y meta descripciones y la muestra
> como "origen por confirmar", pero conviene editar esas salidas: el título del evento queda mejor
> con la comuna real.

## Ejecutar

Necesita un Postgres local. En esta máquina se usa el contenedor `postgres-db`.

```bash
createdb viajes_eventos          # la primera vez (o: docker exec postgres-db psql -U postgres -c "CREATE DATABASE viajes_eventos")
mvn package
java -jar target/viajes-eventos-api.jar
```

Queda escuchando en `http://localhost:8080/api`. La configuración se resuelve igual que en calendario, en este orden:
variable de entorno, luego `./config.yml` local (en `.gitignore`), luego `src/main/resources/config.yml`. Los secretos
(`JWT_SECRET`, `DB_PASSWORD`, `ADMIN_BOOTSTRAP_PASSWORD`) van en el `config.yml` local, nunca en el que se sube al repo.

**CORS:** se aceptan `FRONTEND_URL`, los `localhost` de desarrollo y lo que venga en `CORS_ORIGINS`, separado por
comas, igual que en condominios. Los `/` finales se quitan solos.

**`COMPANY_SLUG`:** la empresa que sirve este despliegue, para las páginas públicas. En blanco toma
la única empresa que haya, que es lo correcto en una instalación nueva.

**Render:** [.env.example](.env.example) trae las variables listas para "Add from .env". Del lado del front,
`NEXT_PUBLIC_API_URL` debe estar definida en Render: el build de producción falla si falta, o si en Render apunta a
`localhost` (ver `ui/next.config.ts`).

Si no hay ningún dueño de la plataforma, `AdminBootstrap` crea `admin@viajeseventos.local` en la empresa inicial
(`viajes-eventos`). Usa `ADMIN_BOOTSTRAP_PASSWORD` si está definida. Si no, genera una contraseña y la muestra una sola
vez en el log.

### Correo (SMTP)

Sigue el patrón de doscolas. `ConfigurableEmailSender` elige el servidor en este orden, y lo revisa en cada envío, así
que los cambios aplican sin reiniciar:

1. **Configuración → Correo** (módulo `SETTINGS`, tabla `smtp_settings`), si está habilitada y completa. Tiene un
   botón para enviar un correo de prueba antes de habilitarla. La contraseña nunca se devuelve al front.
2. **`SMTP_*`** en variables de entorno o en `config.yml`. En desarrollo usa la misma cuenta Maileroo que calendario.
3. Si no hay ninguna de las dos, los correos solo se escriben en el log.

Un usuario con el módulo `USERS` puede marcar a mano el correo de una cuenta como verificado, o reenviarle el enlace
de verificación.

## Tests

```bash
createdb viajes_eventos_test     # la primera vez
mvn test
```

Los `*IT` corren contra una base propia (`TEST_DB_URL`, por defecto `viajes_eventos_test`), no contra la de
desarrollo, porque algunos reorganizan datos. `TripFixtures` arma el cableado y los cuerpos que
necesitan los tests de salidas y reservas.

`BookingServiceIT.twoPeopleTakingTheLastSeatAtOnceDoNotBothGetIt` reserva el último asiento desde dos
hilos a la vez: es el test que justifica el `SELECT … FOR UPDATE` y el que se rompe si alguien lo
saca.
