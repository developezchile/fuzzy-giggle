# viajes-eventos-api

Backend de viajes en bus a eventos. Java 25 nativo, sin Spring. Usa la misma arquitectura
"library-free" de [`doscolas/dc-api-v2`](../../doscolas/dc-api-v2) y
[`calendario/api`](../../calendario/api):

- **HTTP**: `com.sun.net.httpserver.HttpServer` del JDK + un `Router` propio (segmentos `{name}`, CORS, errores JSON).
- **Persistencia**: JDBC plano sobre un pool de conexiones propio. Sin JPA/Hibernate.
- **JSON / JWT**: lector/escritor JSON y JWT HS256 hechos a mano. Sin Jackson ni JJWT.
- **Migraciones**: `MigrationRunner` aplica `src/main/resources/db/migrations/V*__*.sql` al arrancar.

Dependencias de runtime: driver de PostgreSQL, `at.favre.lib:bcrypt` y Jakarta Mail (SMTP).

## Perfiles y módulos (patrón de condominios)

El acceso no está programado por rol, se maneja como datos:

- `AppModule` (enum) lista las áreas funcionales: `EVENTS`, `BOOKINGS`, `EVENT_ADMIN`, `USERS`, `PROFILES`, `SETTINGS`.
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
| Administrador | `ADMIN` | todos | Siempre tiene todos los módulos. Al arrancar se le agregan los módulos nuevos. No se puede eliminar. |
| Cliente | `CLIENT` | `EVENTS` | El registro público siempre asigna este perfil. Sus módulos se pueden editar. No se puede eliminar. |

Desde el mantenedor se pueden crear más perfiles, por ejemplo "Coordinador" con `EVENTS` + `USERS`.

Además hay estas protecciones:
- No se puede eliminar un perfil que tenga usuarios asignados.
- Nadie puede deshabilitarse ni cambiarse el perfil a sí mismo.
- Siempre debe quedar al menos un administrador habilitado.

**Agregar un módulo:** súmalo a `AppModule`, registra sus rutas con él y agrega su entrada en `ui/src/lib/modules.ts`
y en `ModuleKey`.

## Endpoints

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/auth/register`, `/auth/login`, `/auth/verify-email`, `/auth/resend-verification`, `/auth/forgot-password`, `/auth/reset-password` | público (con límite de intentos) |
| GET / PUT | `/auth/me` | sesión (mi perfil) |
| GET | `/events` | `EVENTS` — eventos próximos + cuántos pasajeros tengo en cada uno |
| POST | `/events/{id}/bookings` | `EVENTS` — reservar (1 a 20 pasajeros) |
| GET | `/bookings/me` | `EVENTS` — mis reservas |
| POST | `/bookings/{id}/cancel` | `EVENTS` — cancelar una reserva propia |
| GET | `/admin/bookings/events`, `/admin/bookings?eventId=` | `BOOKINGS` — todas las reservas y pasajeros |
| GET / POST | `/admin/events` | `EVENT_ADMIN` — todos los eventos (con reservas y pasajeros) / crear |
| GET / PUT / DELETE | `/admin/events/{id}` | `EVENT_ADMIN` |
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

## Eventos y reservas

- Los eventos están en la tabla `events`. `V3__events_bookings.sql` carga los 10 eventos tomados de puntoticket.com.
- Una reserva (`bookings`) corresponde a un envío del modal "Viajar": una cuenta registra un grupo de pasajeros
  (`booking_passengers`) para un evento. Cada pasajero tiene nombre, celular, lugar y hora de salida, y lugar de retorno.
- La validación es la misma que en el front: nombre y apellido, celular chileno y los demás campos obligatorios. Los
  errores se devuelven con la clave `passengers.<i>.<campo>`. El celular se guarda normalizado como `+56 9 XXXX XXXX`.
- Un evento acepta reservas hasta su último día en hora de Chile (`America/Santiago`).
- Solo quien hizo la reserva puede cancelarla, y solo mientras el evento no haya terminado. Cancelar marca la reserva
  como `CANCELLED`, no la borra.
- El módulo `EVENT_ADMIN` permite crear, editar, activar/desactivar y eliminar eventos. El slug se genera a partir del
  nombre al crear el evento y después no cambia. Un evento desactivado no se muestra a los clientes ni acepta
  reservas nuevas, pero conserva las que ya tiene. Solo se puede eliminar un evento sin reservas (ni siquiera
  canceladas); si tiene, se desactiva.
- El módulo `BOOKINGS` es la vista del operador. Muestra todas las reservas, se puede filtrar por evento e incluye el
  total de pasajeros por punto y hora de salida. El perfil Administrador lo recibe automáticamente.

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

En el primer arranque `AdminBootstrap` crea `admin@viajeseventos.local`. Usa `ADMIN_BOOTSTRAP_PASSWORD` si está
definida. Si no, genera una contraseña y la muestra una sola vez en el log.

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
desarrollo, porque algunos reorganizan datos.
