# Backend del Helpdesk ERP

API REST del sistema de soporte para el ERP/POS de "Sistemas Integrados S.A.". Proyecto de aula de Bases de Datos No Relacionales, UFPS Ocaña.

Está hecho con Spring Boot 4.1 y Java 21, con Spring Data MongoDB para la base y Spring Security con JWT para el acceso por roles. La base de datos (esquema, validaciones y datos de prueba) está en el repositorio [base-de-datos](https://github.com/Helpdesk-erp/base-de-datos) y la interfaz web en el repositorio `frontend` de la organización.

## Requisitos

- JDK 21.
- La base `helpdesk_erp` cargada con los scripts de [base-de-datos](https://github.com/Helpdesk-erp/base-de-datos), en MongoDB Atlas o en un MongoDB local configurado como replica set. El registro de clientes usa transacciones multi-documento, y esas no funcionan en un `mongod` standalone.

Maven no hace falta instalarlo. `mvnw` descarga Maven 3.9.15 la primera vez. No conviene subir el wrapper a Maven 3.10: el importador de IntelliJ IDEA 2026.1 falla con esa versión.

## Configuración

Copie `.env.example` como `.env` en esta misma carpeta (en Linux o macOS, con `cp`):

```bash
copy .env.example .env
```

Complete los dos valores, sin comillas:

| Variable | Qué va |
|---|---|
| `JWT_SECRET` | Clave para firmar los tokens, de al menos 32 caracteres. El comando para generarla está en el mismo archivo |
| `MONGODB_URI` | Cadena de conexión. En Atlas es la `MONGODB_URI` del archivo `atlas-credentials.env` (allá viene entre comillas; aquí va sin ellas) |

El archivo `.env` está en `.gitignore` y nunca se sube. Si usa Atlas, su IP debe estar agregada en **Network Access**.

## Ejecutar

### Desde la terminal

En Windows:

```bash
mvnw.cmd spring-boot:run
```

En Linux o macOS:

```bash
./mvnw spring-boot:run
```

### Desde IntelliJ IDEA

1. **File → Open** y escoja esta carpeta. IntelliJ detecta el `pom.xml`.
2. En el panel de Maven (la "m" de la barra derecha), pulse **Reload All Maven Projects** y espere a que descargue las dependencias.
3. En **File → Project Structure → Project**, ponga el **SDK** en 21 y **Language level** en *SDK default*.
4. Abra `src/main/java/com/sistemasintegrados/helpdesk/HelpdeskApplication.java` y pulse el triángulo verde junto a la clase.

### Comprobar que arrancó

La consola muestra `Tomcat started on port 8080`. Una petición sin sesión debe responder 401:

```bash
curl http://localhost:8080/api/auth/me
```

```json
{"mensaje":"Debe iniciar sesión"}
```

Las pruebas se corren con `mvnw.cmd test` (o `./mvnw test`). No necesitan una base de datos real.

Los usuarios de prueba y su contraseña están en el [README de base-de-datos](https://github.com/Helpdesk-erp/base-de-datos#usuarios-de-prueba).

## Estructura

Los paquetes van por funcionalidad, para que cada integrante pueda trabajar en uno sin pisar a los demás. Todos están dentro de `com.sistemasintegrados.helpdesk`:

| Paquete | Contenido |
|---|---|
| `config` | Conexión a MongoDB, transacciones, seguridad, JWT y CORS |
| `comun` | Generador de códigos (`ContadorService`), contraseña temporal, manejo global de errores |
| `auth` | Login, registro, cierre de sesión y la validación de la sesión en cada petición |
| `usuarios` | Cuentas de acceso: crear la de un empleado, cambiar correo, rol y estado, marcar credenciales enviadas |
| `empleados` | Registro, edición y retiro de empleados |
| `clientes` | Registro por el administrador, contacto, contratación de paquetes, inactivación |
| `tickets` | Ciclo completo del ticket: crear, editar, eliminar, asignar, atender, resolver y cerrar |
| `conocimiento` | Búsqueda de texto y artículos nuevos a partir de tickets resueltos |
| `pagos` | Pagos aprobados y rechazados con la transacción que suspende o reactiva el paquete |
| `capacitaciones` | Programación, inscripción y registro de asistencia |
| `roles`, `paquetes` | Permisos por rol y catálogo de paquetes con su formulario de ticket |
| `reportes` | Pipelines del Framework de Agregación |
| `dashboard` | Un endpoint por rol que arma su dashboard con los reportes |

## Cómo funciona

### Sesiones y roles

El login devuelve un token JWT firmado con HS256. El `_id` de la sesión, que queda guardada en la colección `sesiones`, viaja dentro del token como `jti`. En cada petición, `JwtSesionConverter` revisa en MongoDB que esa sesión siga activa y que el usuario no esté bloqueado. Por eso cerrar sesión o bloquear una cuenta tiene efecto inmediato, aunque el token no haya vencido.

Los permisos de cada rol salen de la colección `roles` (por ejemplo `tickets:asignar` o `conocimiento:buscar`). El backend los carga como autoridades de Spring Security y protege cada endpoint con `@PreAuthorize`. `RolService` los guarda en memoria un minuto, así que un cambio en la base se aplica sin reiniciar.

### Validación en tres capas

El formulario del frontend valida mientras se escribe. El backend vuelve a validar con anotaciones de Jakarta Validation y devuelve un mensaje por campo. Al final, el `$jsonSchema` de cada colección rechaza cualquier documento que no cumpla las reglas. Las expresiones regulares son las mismas en las tres capas.

`ManejadorErrores` traduce los errores de MongoDB: el 11000 (índice único) pasa a 409 con un mensaje según el índice que lo rechazó, y el 121 (`$jsonSchema`) pasa a 400 con el detalle de la regla que falló.

### Transacciones y códigos

El registro de un cliente inserta el cliente y su usuario en una transacción (`@Transactional` con `MongoTransactionManager`). Si el nombre de usuario ya existe, el índice único rechaza el segundo insert y el cliente tampoco queda guardado.

Los códigos legibles (`CLI-005`, `USR-009`) salen de la colección `contadores` con `findAndModify` y `$inc`, que es atómico. El contador corre fuera de la transacción: si la transacción falla queda un número sin usar, igual que una secuencia en SQL.

### Nombres de campos

En Java los campos van en camelCase (`clienteId`) y MongoDB los guarda en snake_case (`cliente_id`) gracias a `SnakeCaseFieldNamingStrategy`. El JSON de la API usa los mismos nombres de la base. Spring no crea colecciones ni índices, y tampoco agrega el campo `_class`: el esquema lo definen los scripts de la base de datos.

### Reportes

`ReportesService` escribe cada pipeline como texto JSON, para poder copiarlo tal cual en mongosh. La misma versión está en `04_reportes.js` del repositorio de la base de datos, junto con el `explain("executionStats")` de las consultas que usan índices.

## API

| Método | Ruta | Acceso | Qué hace |
|---|---|---|---|
| POST | `/api/auth/login` | Público | Inicia sesión con usuario o correo |
| POST | `/api/auth/registro` | Público | Registra un cliente con su usuario e inicia sesión |
| POST | `/api/auth/logout` | Con sesión | Cierra la sesión actual |
| GET | `/api/auth/me` | Con sesión | Datos y permisos del usuario |
| GET | `/api/dashboard/administrador` | Rol administrador | Datos del dashboard |
| GET | `/api/dashboard/agente` | Rol agente_soporte | Datos del dashboard |
| GET | `/api/dashboard/cliente` | Rol cliente | Datos del dashboard |
| GET | `/api/dashboard/auditor` | Rol auditor | Datos del dashboard |
| GET | `/api/conocimiento/buscar?q=&paquete=` | Permiso `conocimiento:buscar` | Búsqueda de texto en la base de conocimiento |
| POST | `/api/conocimiento` | `conocimiento:crear` | RF-31. Artículo a partir de un ticket resuelto por el mismo agente |
| GET | `/api/paquetes` y `/api/paquetes/{id}` | Con sesión | Paquetes con los campos que pide el formulario de cada uno (RF-19) |
| GET | `/api/usuarios` | `usuarios:gestionar` | Cuentas con su vínculo y si ya se enviaron las credenciales |
| GET | `/api/usuarios/agentes` | `tickets:asignar` o `capacitaciones:gestionar` | Agentes activos con su carga |
| POST | `/api/usuarios` | `usuarios:gestionar` | RF-06. Cuenta de un empleado con rol interno y contraseña temporal |
| PUT | `/api/usuarios/{id}` | `usuarios:gestionar` | RF-07. Correo y rol; si cambia el rol se cierran sus sesiones |
| PATCH | `/api/usuarios/{id}/estado` | `usuarios:gestionar` | RF-08. Activo, inactivo o bloqueado |
| PATCH | `/api/usuarios/{id}/credenciales-enviadas` | `usuarios:gestionar` | RF-10 |
| GET | `/api/empleados` y `/api/empleados/{id}` | `empleados:gestionar` | Empleados con su cuenta |
| POST | `/api/empleados` | `empleados:gestionar` | RF-15 |
| PUT | `/api/empleados/{id}` | `empleados:gestionar` | RF-16 |
| POST | `/api/empleados/{id}/retiro` | `empleados:gestionar` | RF-17. Estado retirado y cuenta inactiva, en una transacción |
| GET | `/api/clientes` | `clientes:gestionar` o `clientes:ver` | Clientes con el estado de cada paquete |
| GET | `/api/clientes/{id}` | `clientes:gestionar` o `clientes:ver` | RF-13. Cliente, cuentas, tickets y pagos |
| GET | `/api/clientes/mio` | Rol cliente | RF-13 desde el lado del cliente |
| POST | `/api/clientes` | `clientes:gestionar` | RF-11. Cliente y cuenta en una transacción, con contraseña temporal |
| PUT | `/api/clientes/{id}` | `clientes:gestionar` | RF-12. Contacto y dirección |
| DELETE | `/api/clientes/{id}` | `clientes:gestionar` | RF-14. Eliminación lógica: cliente y cuentas inactivos |
| POST | `/api/clientes/{id}/reactivacion` | `clientes:gestionar` | Deshace RF-14 |
| POST | `/api/clientes/{id}/paquetes` | `clientes:gestionar` | Contrata un paquete del catálogo |
| GET | `/api/tickets?estado=&paquete=` | Ver tickets | RF-21. El cliente ve los suyos, el agente los asignados, los demás todos |
| GET | `/api/tickets/{id}` | Ver tickets | Detalle con historial, cliente, agente y artículo |
| POST | `/api/tickets` | `tickets:crear` | RF-20. Solo sobre paquetes activos; `detalle` se valida con `campos_ticket` |
| PUT | `/api/tickets/{id}` | `tickets:crear` | RF-22. Mientras siga sin atender |
| DELETE | `/api/tickets/{id}` | `tickets:crear` | RF-23. Mientras no tenga agente |
| POST | `/api/tickets/{id}/asignacion` | `tickets:asignar` | RF-24. Asigna o reasigna y lo deja en el historial |
| POST | `/api/tickets/{id}/estado` | `tickets:cambiar_estado` | RF-25. En proceso o resuelto (con solución) |
| PUT | `/api/tickets/{id}/solucion` | `tickets:cambiar_estado` | RF-26 |
| POST | `/api/tickets/{id}/cierre` | Rol cliente | RF-28. Confirma la solución y cierra |
| GET | `/api/pagos?cliente_id=&estado=` | `pagos:registrar` o `pagos:ver` | Historial de pagos |
| POST | `/api/pagos` | `pagos:registrar` | RF-34 y RF-35. Un rechazo suspende el paquete; un aprobado lo reactiva |
| GET | `/api/capacitaciones` | Gestionar, dictar o inscribirse | Las del rol: todas, las que dicta o las disponibles |
| GET | `/api/capacitaciones/{id}` | `capacitaciones:gestionar` o `:dictar` | Con la lista de inscritos |
| POST | `/api/capacitaciones` | `capacitaciones:gestionar` | RF-38 |
| POST | `/api/capacitaciones/{id}/cancelacion` | `capacitaciones:gestionar` | Cancela una programada o en curso |
| POST y DELETE | `/api/capacitaciones/{id}/inscripcion` | `capacitaciones:inscribirse` | RF-39. Inscribirse o cancelar la inscripción |
| PUT | `/api/capacitaciones/{id}/asistencia` | `capacitaciones:dictar` | RF-40. Solo el instructor; solo califica quien asistió |

Los errores llegan como `{ "mensaje": "...", "errores": { "campo": "..." } }`.

### Contraseñas temporales

Las cuentas que crea el administrador (RF-06 y RF-11) reciben una contraseña temporal que el backend genera y entrega una sola vez en la respuesta. La cuenta queda con `credenciales_enviadas: false` hasta que el administrador la marca como enviada (RF-10). El proyecto no envía correos: la pantalla de usuarios muestra la contraseña para copiarla.

## Problemas comunes

| Mensaje | Causa |
|---|---|
| `Could not resolve placeholder 'MONGODB_URI'` | No existe `.env`, o la aplicación no se ejecuta desde esta carpeta. En IntelliJ, revise el **Working directory** de la configuración de ejecución |
| `Falta JWT_SECRET o tiene menos de 32 caracteres` | Complete `JWT_SECRET` en `.env` |
| `Port 8080 was already in use` | Ya hay otra instancia del backend corriendo |
| Error de conexión o de autenticación al iniciar sesión | La IP no está en Network Access de Atlas, o la `MONGODB_URI` tiene comillas o una contraseña equivocada |
| `No existe el contador 'clientes'` | La base no tiene los datos de prueba. Cargue `02_datos_semilla.js` |
| IntelliJ no carga las dependencias ("Unable to create injector") | El wrapper apunta a Maven 3.10. Debe quedar en 3.9.15 |

## Pendiente

- Enviar las credenciales por correo. Hoy el administrador las copia de la pantalla y las marca como enviadas.
