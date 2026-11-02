package com.sistemasintegrados.helpdesk.clientes;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemasintegrados.helpdesk.auth.AuthService;
import com.sistemasintegrados.helpdesk.comun.ClaveTemporal;
import com.sistemasintegrados.helpdesk.comun.ContadorService;
import com.sistemasintegrados.helpdesk.comun.Fechas;
import com.sistemasintegrados.helpdesk.comun.ReglaNegocioException;
import com.sistemasintegrados.helpdesk.comun.Textos;
import com.sistemasintegrados.helpdesk.paquetes.Paquete;
import com.sistemasintegrados.helpdesk.paquetes.PaqueteRepository;
import com.sistemasintegrados.helpdesk.reportes.ReportesService;
import com.sistemasintegrados.helpdesk.usuarios.CuentaCreada;
import com.sistemasintegrados.helpdesk.usuarios.Usuario;
import com.sistemasintegrados.helpdesk.usuarios.UsuarioRepository;
import com.sistemasintegrados.helpdesk.usuarios.UsuarioService;

/**
 * Gestión de clientes desde el panel del administrador. El registro propio
 * del cliente (RF-05) está en RegistroClienteService.
 */
@Service
public class ClienteService {

	private final MongoTemplate mongo;

	private final ClienteRepository clientes;

	private final UsuarioRepository cuentas;

	private final PaqueteRepository paquetes;

	private final ContadorService contadores;

	private final PasswordEncoder encoder;

	private final AuthService auth;

	private final UsuarioService usuarios;

	private final ReportesService reportes;

	public ClienteService(MongoTemplate mongo, ClienteRepository clientes, UsuarioRepository cuentas,
			PaqueteRepository paquetes, ContadorService contadores, PasswordEncoder encoder, AuthService auth,
			UsuarioService usuarios, ReportesService reportes) {
		this.mongo = mongo;
		this.clientes = clientes;
		this.cuentas = cuentas;
		this.paquetes = paquetes;
		this.contadores = contadores;
		this.encoder = encoder;
		this.auth = auth;
		this.usuarios = usuarios;
		this.reportes = reportes;
	}

	/** Lista para la tabla de clientes, con el estado de cada paquete contratado. */
	public List<Document> listar() {
		List<Document> etapas = Document.parse("""
				{ etapas: [
				  { $project: {
				      _id: 0, id: "$_id", nombre: 1, tipo_cliente: 1, tipo_documento: 1, numero_documento: 1, correo: 1,
				      telefono: 1, ciudad: "$direccion.ciudad", estado: 1, fecha_registro: 1,
				      paquetes: { $map: {
				          input: { $ifNull: [ "$paquetes_contratados", [] ] },
				          in: { paquete_id: "$$this.paquete_id", estado: "$$this.estado" }
				      } }
				  } },
				  { $sort: { nombre: 1 } }
				] }
				""").getList("etapas", Document.class);
		return mongo.getCollection("clientes").aggregate(etapas).into(new ArrayList<>());
	}

	public Cliente obtener(String id) {
		return clientes.findById(id)
			.orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "No existe el cliente " + id));
	}

	/**
	 * RF-13. El cliente con sus paquetes contratados y el estado de cada uno,
	 * sus cuentas de acceso, sus tickets y sus pagos.
	 */
	public Map<String, Object> detalle(String id) {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("cliente", obtener(id));
		datos.put("usuarios", usuarios.listar(new Document("cliente_id", id)));
		datos.put("tickets", reportes.listarTickets(new Document("cliente_id", id), new Document("fecha_creacion", -1), 50));
		datos.put("pagos", reportes.pagosRecientes(new Document("cliente_id", id), 50));
		return datos;
	}

	/**
	 * RF-11. Inserta el cliente y su cuenta en una transacción, como el registro
	 * propio, pero la contraseña la genera el sistema y credenciales_enviadas
	 * queda en false hasta que se le envíen (RF-10).
	 */
	@Transactional
	public CuentaCreada registrar(ClienteRequest datos) {
		boolean personaNatural = "persona_natural".equals(datos.tipoCliente());
		String representante = Textos.limpio(datos.representante());
		ReglasCliente.validar(datos.tipoCliente(), datos.tipoDocumento(), representante);

		Instant ahora = Instant.now();
		String correo = Textos.correo(datos.correo());
		RegistroRequest.Direccion direccion = datos.direccion();

		Cliente cliente = new Cliente(
				contadores.siguiente("clientes", "CLI", 3),
				datos.tipoCliente(),
				datos.tipoDocumento(),
				datos.numeroDocumento(),
				datos.nombre().trim(),
				personaNatural ? null : representante,
				correo,
				datos.telefono(),
				new Cliente.Direccion(direccion.direccion().trim(), direccion.ciudad().trim(), direccion.departamento().trim()),
				List.of(),
				"activo",
				ahora);

		String clave = ClaveTemporal.generar();
		Usuario usuario = new Usuario(
				contadores.siguiente("usuarios", "USR", 3),
				datos.usuario(),
				correo,
				encoder.encode(clave),
				List.of("cliente"),
				cliente.id(),
				null,
				"activo",
				false,
				null,
				ahora);

		mongo.insert(cliente);
		mongo.insert(usuario);
		return new CuentaCreada(cliente, usuario.usuario(), clave);
	}

	/** RF-12. Contacto y dirección; el representante solo aplica a empresas y personas jurídicas. */
	public Cliente actualizar(String id, ActualizarClienteRequest datos) {
		Cliente cliente = obtener(id);
		if ("inactivo".equals(cliente.estado())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "El cliente está inactivo. Reactívelo antes de editarlo");
		}
		boolean personaNatural = "persona_natural".equals(cliente.tipoCliente());
		String representante = Textos.limpio(datos.representante());
		ReglasCliente.validar(cliente.tipoCliente(), cliente.tipoDocumento(), representante);

		RegistroRequest.Direccion d = datos.direccion();
		Update cambios = Update.update("correo", Textos.correo(datos.correo()))
			.set("telefono", datos.telefono())
			.set("direccion", new Document("direccion", d.direccion().trim()).append("ciudad", d.ciudad().trim())
				.append("departamento", d.departamento().trim()));
		// Un null no cumple el bsonType string del esquema: si no aplica, el campo se quita
		if (personaNatural) {
			cambios.unset("representante");
		}
		else {
			cambios.set("representante", representante);
		}
		mongo.updateFirst(query(where("_id").is(id)), cambios, "clientes");
		return obtener(id);
	}

	/**
	 * RF-14. Eliminación lógica: el cliente y sus cuentas quedan inactivos y se
	 * conservan sus tickets y pagos. Va en transacción porque toca dos colecciones.
	 */
	@Transactional
	public Cliente inactivar(String id) {
		Cliente cliente = obtener(id);
		if ("inactivo".equals(cliente.estado())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "El cliente ya está inactivo");
		}
		mongo.updateFirst(query(where("_id").is(id)), Update.update("estado", "inactivo"), "clientes");
		for (Usuario cuenta : cuentas.findByClienteId(id)) {
			mongo.updateFirst(query(where("_id").is(cuenta.id())), Update.update("estado", "inactivo"), "usuarios");
			auth.cerrarSesionesDe(cuenta.id());
		}
		return obtener(id);
	}

	/** Deshace RF-14. Las cuentas bloqueadas siguen bloqueadas; las inactivas vuelven a activo. */
	@Transactional
	public Cliente reactivar(String id) {
		Cliente cliente = obtener(id);
		if (!"inactivo".equals(cliente.estado())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "El cliente no está inactivo");
		}
		mongo.updateFirst(query(where("_id").is(id)), Update.update("estado", estadoSegunPaquetes(cliente)), "clientes");
		mongo.updateMulti(query(where("cliente_id").is(id).and("estado").is("inactivo")),
				Update.update("estado", "activo"), "usuarios");
		return obtener(id);
	}

	/**
	 * Agrega un paquete del catálogo al cliente. Sin un paquete activo el
	 * cliente no puede crear tickets (RF-20). Un paquete vencido se puede
	 * volver a contratar; uno activo o suspendido no.
	 */
	public Cliente contratarPaquete(String id, ContratoPaqueteRequest datos) {
		Cliente cliente = obtener(id);
		if ("inactivo".equals(cliente.estado())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "El cliente está inactivo");
		}
		if (!datos.fechaFin().isAfter(datos.fechaInicio())) {
			throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Revise los datos del formulario",
					Map.of("fecha_fin", "Debe ser posterior a la fecha de inicio"));
		}
		Paquete paquete = paquetes.findById(datos.paqueteId())
			.filter(p -> Boolean.TRUE.equals(p.activo()))
			.orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "El paquete no existe o no está a la venta"));

		List<Cliente.PaqueteContratado> contratados = new ArrayList<>(
				cliente.paquetesContratados() == null ? List.of() : cliente.paquetesContratados());
		for (Cliente.PaqueteContratado actual : contratados) {
			if (actual.paqueteId().equals(paquete.id()) && !"vencido".equals(actual.estado())) {
				throw new ReglaNegocioException(HttpStatus.CONFLICT, "El cliente ya tiene contratado " + paquete.nombre());
			}
		}
		contratados.removeIf(actual -> actual.paqueteId().equals(paquete.id()));
		contratados.add(new Cliente.PaqueteContratado(paquete.id(), paquete.nombre(), paquete.precioMensual(),
				Fechas.aInstante(datos.fechaInicio()), Fechas.aInstante(datos.fechaFin()), "activo"));

		mongo.updateFirst(query(where("_id").is(id)), Update.update("paquetes_contratados", contratados), "clientes");
		ajustarEstado(id);
		return obtener(id);
	}

	/**
	 * Un cliente queda suspendido cuando todos sus paquetes vigentes están
	 * suspendidos por pagos rechazados, y vuelve a activo en cuanto uno se
	 * reactiva. Un cliente inactivo no cambia. Lo llaman los pagos (RF-34, RF-35)
	 * dentro de su transacción.
	 */
	public void ajustarEstado(String id) {
		Cliente cliente = obtener(id);
		if ("inactivo".equals(cliente.estado())) {
			return;
		}
		String estado = estadoSegunPaquetes(cliente);
		if (!estado.equals(cliente.estado())) {
			mongo.updateFirst(query(where("_id").is(id)), Update.update("estado", estado), "clientes");
		}
	}

	private static String estadoSegunPaquetes(Cliente cliente) {
		List<Cliente.PaqueteContratado> contratados = cliente.paquetesContratados() == null ? List.of()
				: cliente.paquetesContratados();
		boolean algunoActivo = contratados.stream().anyMatch(p -> "activo".equals(p.estado()));
		boolean algunoSuspendido = contratados.stream().anyMatch(p -> "suspendido".equals(p.estado()));
		return algunoSuspendido && !algunoActivo ? "suspendido" : "activo";
	}

}
