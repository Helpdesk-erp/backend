package com.sistemasintegrados.helpdesk.usuarios;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.sistemasintegrados.helpdesk.auth.AuthService;
import com.sistemasintegrados.helpdesk.auth.UsuarioAutenticado;
import com.sistemasintegrados.helpdesk.comun.ClaveTemporal;
import com.sistemasintegrados.helpdesk.comun.ContadorService;
import com.sistemasintegrados.helpdesk.comun.ReglaNegocioException;
import com.sistemasintegrados.helpdesk.comun.Textos;

@Service
public class UsuarioService {

	private static final List<String> ROLES_INTERNOS = List.of("administrador", "auditor", "agente_soporte");

	private final MongoTemplate mongo;

	private final UsuarioRepository usuarios;

	private final ContadorService contadores;

	private final PasswordEncoder encoder;

	private final AuthService auth;

	public UsuarioService(MongoTemplate mongo, UsuarioRepository usuarios, ContadorService contadores,
			PasswordEncoder encoder, AuthService auth) {
		this.mongo = mongo;
		this.usuarios = usuarios;
		this.contadores = contadores;
		this.encoder = encoder;
		this.auth = auth;
	}

	/**
	 * Usuarios del sistema para la tabla del administrador. Agregación con
	 * $project: deja por fuera password_hash, renombra _id como id y arma tres
	 * campos nuevos (el rol principal, si la cuenta es de un empleado o de un
	 * cliente, y el código de ese empleado o cliente).
	 */
	public List<Document> listarParaAdministrador() {
		List<Document> etapas = List.of(Document.parse("""
				{ $project: {
				    _id: 0, id: "$_id", usuario: 1, correo: 1, estado: 1, ultimo_acceso: 1,
				    rol: { $first: "$roles" },
				    vinculo: { $cond: [ { $ifNull: [ "$empleado_id", false ] }, "empleado", "cliente" ] },
				    vinculo_id: { $ifNull: [ "$empleado_id", "$cliente_id" ] }
				} }
				"""));

		return mongo.getCollection("usuarios").aggregate(etapas).into(new ArrayList<>());
	}

	/**
	 * Cuentas para la pantalla de gestión: además de lo que muestra el dashboard,
	 * trae el nombre del empleado o del cliente y si ya se enviaron las credenciales.
	 */
	public List<Document> listar(Document filtro) {
		List<Document> etapas = new ArrayList<>();
		etapas.add(new Document("$match", filtro));
		etapas.addAll(Document.parse("""
				{ etapas: [
				  { $lookup: { from: "empleados", localField: "empleado_id", foreignField: "_id", as: "empleado",
				      pipeline: [ { $project: { nombre: { $concat: [ "$nombres", " ", "$apellidos" ] }, estado: 1 } } ] } },
				  { $lookup: { from: "clientes", localField: "cliente_id", foreignField: "_id", as: "cliente",
				      pipeline: [ { $project: { nombre: 1, estado: 1 } } ] } },
				  { $project: {
				      _id: 0, id: "$_id", usuario: 1, correo: 1, estado: 1, ultimo_acceso: 1, fecha_creacion: 1,
				      credenciales_enviadas: 1, rol: { $first: "$roles" },
				      vinculo: { $cond: [ { $ifNull: [ "$empleado_id", false ] }, "empleado", "cliente" ] },
				      vinculo_id: { $ifNull: [ "$empleado_id", "$cliente_id" ] },
				      nombre: { $ifNull: [ { $first: "$empleado.nombre" }, { $first: "$cliente.nombre" } ] },
				      estado_vinculo: { $ifNull: [ { $first: "$empleado.estado" }, { $first: "$cliente.estado" } ] }
				  } },
				  { $sort: { vinculo: -1, usuario: 1 } }
				] }
				""").getList("etapas", Document.class));
		return mongo.getCollection("usuarios").aggregate(etapas).into(new ArrayList<>());
	}

	/**
	 * RF-06. Crea la cuenta de un empleado activo con una contraseña temporal.
	 * credenciales_enviadas queda en false hasta que se marquen como enviadas (RF-10).
	 */
	public CuentaCreada crearParaEmpleado(CrearUsuarioRequest datos) {
		Document empleado = mongo.findOne(query(where("_id").is(datos.empleadoId())), Document.class, "empleados");
		if (empleado == null) {
			throw new ReglaNegocioException(HttpStatus.NOT_FOUND, "No existe el empleado " + datos.empleadoId());
		}
		if (!"activo".equals(empleado.getString("estado"))) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "Solo se crean cuentas para empleados activos");
		}
		if (usuarios.findByEmpleadoId(datos.empleadoId()).isPresent()) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "Ese empleado ya tiene un usuario");
		}

		String clave = ClaveTemporal.generar();
		Usuario usuario = new Usuario(
				contadores.siguiente("usuarios", "USR", 3),
				datos.usuario(),
				Textos.correo(datos.correo()),
				encoder.encode(clave),
				List.of(datos.rol()),
				null,
				datos.empleadoId(),
				"activo",
				false,
				null,
				Instant.now());
		mongo.insert(usuario);
		return new CuentaCreada(fila(usuario.id()), usuario.usuario(), clave);
	}

	/**
	 * RF-07. Si el rol cambia se cierran las sesiones del usuario, porque cada
	 * sesión guarda el rol con el que se inició.
	 */
	public Document actualizar(String id, ActualizarUsuarioRequest datos, UsuarioAutenticado quien) {
		Usuario usuario = obtener(id);
		boolean esCliente = usuario.clienteId() != null;
		// Mismas reglas del oneOf de usuarios en 01_esquema.js
		if (esCliente && !"cliente".equals(datos.rol())) {
			throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Revise los datos del formulario",
					Map.of("rol", "La cuenta de un cliente solo puede tener el rol cliente"));
		}
		if (!esCliente && !ROLES_INTERNOS.contains(datos.rol())) {
			throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Revise los datos del formulario",
					Map.of("rol", "La cuenta de un empleado solo puede tener un rol interno"));
		}
		boolean cambiaRol = !usuario.roles().contains(datos.rol()) || usuario.roles().size() > 1;
		if (cambiaRol && id.equals(quien.id())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "No puede cambiar el rol de su propia cuenta");
		}

		mongo.updateFirst(query(where("_id").is(id)),
				Update.update("correo", Textos.correo(datos.correo())).set("roles", List.of(datos.rol())), "usuarios");
		if (cambiaRol) {
			auth.cerrarSesionesDe(id);
		}
		return fila(id);
	}

	/**
	 * RF-08. Bloquear o inactivar no borra la cuenta ni su historial; cierra las
	 * sesiones abiertas y JwtSesionConverter rechaza las peticiones siguientes.
	 */
	public Document cambiarEstado(String id, String estado, UsuarioAutenticado quien) {
		Usuario usuario = obtener(id);
		if (id.equals(quien.id())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "No puede cambiar el estado de su propia cuenta");
		}
		if ("activo".equals(estado)) {
			if (usuario.empleadoId() != null && estadoDe("empleados", usuario.empleadoId()).equals("retirado")) {
				throw new ReglaNegocioException(HttpStatus.CONFLICT, "El empleado de esta cuenta está retirado");
			}
			if (usuario.clienteId() != null && estadoDe("clientes", usuario.clienteId()).equals("inactivo")) {
				throw new ReglaNegocioException(HttpStatus.CONFLICT, "El cliente de esta cuenta está inactivo");
			}
		}

		mongo.updateFirst(query(where("_id").is(id)), Update.update("estado", estado), "usuarios");
		if (!"activo".equals(estado)) {
			auth.cerrarSesionesDe(id);
		}
		return fila(id);
	}

	/** RF-10. El sistema ya envió el usuario y la contraseña temporal al correo. */
	public Document marcarCredencialesEnviadas(String id) {
		obtener(id);
		mongo.updateFirst(query(where("_id").is(id)), Update.update("credenciales_enviadas", true), "usuarios");
		return fila(id);
	}

	private Usuario obtener(String id) {
		return usuarios.findById(id)
			.orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "No existe el usuario " + id));
	}

	private Document fila(String id) {
		return listar(new Document("_id", id)).getFirst();
	}

	private String estadoDe(String coleccion, String id) {
		Document documento = mongo.findOne(query(where("_id").is(id)), Document.class, coleccion);
		return documento == null ? "" : documento.getString("estado");
	}

}
