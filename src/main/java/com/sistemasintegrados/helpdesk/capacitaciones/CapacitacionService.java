package com.sistemasintegrados.helpdesk.capacitaciones;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mongodb.client.result.UpdateResult;
import com.sistemasintegrados.helpdesk.auth.UsuarioAutenticado;
import com.sistemasintegrados.helpdesk.clientes.Cliente;
import com.sistemasintegrados.helpdesk.clientes.ClienteRepository;
import com.sistemasintegrados.helpdesk.comun.ContadorService;
import com.sistemasintegrados.helpdesk.comun.ReglaNegocioException;
import com.sistemasintegrados.helpdesk.comun.Textos;
import com.sistemasintegrados.helpdesk.usuarios.UsuarioService;

@Service
public class CapacitacionService {

	/** Inscritos (sin los cancelados), nombre del instructor y del cliente de las personalizadas. */
	private static final String LOOKUPS = """
			{ etapas: [
			  { $lookup: {
			      from: "asistencias", localField: "_id", foreignField: "capacitacion_id", as: "inscripciones",
			      pipeline: [ { $match: { estado: { $ne: "cancelado" } } }, { $project: { _id: 0, usuario_id: 1, estado: 1 } } ]
			  } },
			  { $lookup: {
			      from: "usuarios", localField: "instructor_id", foreignField: "_id", as: "instructor",
			      pipeline: [
			        { $lookup: { from: "empleados", localField: "empleado_id", foreignField: "_id", as: "empleado" } },
			        { $project: { nombre: { $concat: [ { $first: "$empleado.nombres" }, " ", { $first: "$empleado.apellidos" } ] } } }
			      ]
			  } },
			  { $lookup: { from: "clientes", localField: "cliente_id", foreignField: "_id", as: "cliente",
			      pipeline: [ { $project: { nombre: 1 } } ] } },
			  { $project: {
			      _id: 0, id: "$_id", titulo: 1, descripcion: 1, paquete: 1, tipo: 1, cliente_id: 1, modalidad: 1, enlace: 1,
			      lugar: 1, instructor_id: 1, cupo_maximo: 1, sesiones: 1, fecha_inicio: 1, fecha_fin: 1, estado: 1,
			      inscripciones: 1, instructor: { $first: "$instructor.nombre" }, cliente: { $first: "$cliente.nombre" }
			  } }
			] }
			""";

	private final MongoTemplate mongo;

	private final ContadorService contadores;

	private final ClienteRepository clientes;

	private final UsuarioService usuarios;

	public CapacitacionService(MongoTemplate mongo, ContadorService contadores, ClienteRepository clientes,
			UsuarioService usuarios) {
		this.mongo = mongo;
		this.contadores = contadores;
		this.clientes = clientes;
		this.usuarios = usuarios;
	}

	/**
	 * Lo que ve cada rol: el administrador todas, el agente las que dicta y el
	 * cliente las masivas de sus paquetes activos, las personalizadas para él y
	 * las que ya tiene inscritas. Las más próximas primero.
	 */
	public List<Document> listar(UsuarioAutenticado usuario) {
		Document filtro = switch (usuario.rol()) {
			case "agente_soporte" -> new Document("instructor_id", usuario.id());
			case "cliente" -> filtroCliente(usuario);
			default -> new Document();
		};
		List<Document> etapas = new ArrayList<>();
		etapas.add(new Document("$match", filtro));
		etapas.add(new Document("$sort", new Document("fecha_inicio", -1)));
		etapas.addAll(Document.parse(LOOKUPS).getList("etapas", Document.class));

		List<Document> capacitaciones = mongo.getCollection("capacitaciones").aggregate(etapas).into(new ArrayList<>());
		capacitaciones.forEach(capacitacion -> resumirInscripciones(capacitacion, usuario.id()));
		return capacitaciones;
	}

	/** La capacitación con la lista de inscritos, para el instructor y el administrador. */
	public Map<String, Object> detalle(String id, UsuarioAutenticado usuario) {
		Capacitacion capacitacion = obtener(id);
		if ("agente_soporte".equals(usuario.rol()) && !usuario.id().equals(capacitacion.instructorId())) {
			throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "Usted no es el instructor de esta capacitación");
		}
		List<Document> etapas = new ArrayList<>();
		etapas.add(new Document("$match", new Document("_id", id)));
		etapas.addAll(Document.parse(LOOKUPS).getList("etapas", Document.class));
		Document resumen = mongo.getCollection("capacitaciones").aggregate(etapas).first();
		resumirInscripciones(resumen, null);

		List<Document> etapasAsistentes = new ArrayList<>();
		etapasAsistentes.add(new Document("$match", new Document("capacitacion_id", id)));
		etapasAsistentes.addAll(Document.parse("""
				{ etapas: [
				  { $lookup: { from: "usuarios", localField: "usuario_id", foreignField: "_id", as: "usuario",
				      pipeline: [ { $project: { usuario: 1, correo: 1 } } ] } },
				  { $lookup: { from: "clientes", localField: "cliente_id", foreignField: "_id", as: "cliente",
				      pipeline: [ { $project: { nombre: 1 } } ] } },
				  { $project: {
				      _id: 0, usuario_id: 1, cliente_id: 1, estado: 1, calificacion: 1, comentario: 1, fecha_inscripcion: 1,
				      usuario: { $first: "$usuario.usuario" }, correo: { $first: "$usuario.correo" },
				      cliente: { $first: "$cliente.nombre" }
				  } },
				  { $sort: { fecha_inscripcion: 1 } }
				] }
				""").getList("etapas", Document.class));
		List<Document> asistentes = mongo.getCollection("asistencias").aggregate(etapasAsistentes).into(new ArrayList<>());

		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("capacitacion", resumen);
		datos.put("asistentes", asistentes);
		return datos;
	}

	/**
	 * RF-38. Una personalizada es para un cliente que tenga el paquete; una
	 * virtual lleva enlace y una presencial lugar (allOf del $jsonSchema). El
	 * instructor debe ser un agente de soporte activo.
	 */
	public Capacitacion programar(CapacitacionRequest datos) {
		Map<String, String> errores = new LinkedHashMap<>();
		boolean personalizada = "personalizada".equals(datos.tipo());
		boolean virtual = "virtual".equals(datos.modalidad());
		String clienteId = personalizada ? Textos.limpio(datos.clienteId()) : null;
		String enlace = virtual ? Textos.limpio(datos.enlace()) : null;
		String lugar = virtual ? null : Textos.limpio(datos.lugar());

		if (personalizada) {
			Cliente cliente = clienteId == null ? null : clientes.findById(clienteId).orElse(null);
			if (cliente == null) {
				errores.put("cliente_id", "Seleccione el cliente de la capacitación personalizada");
			}
			else if ("inactivo".equals(cliente.estado())) {
				errores.put("cliente_id", "El cliente está inactivo");
			}
			else if (cliente.paquetesContratados() == null
					|| cliente.paquetesContratados().stream().noneMatch(p -> p.paqueteId().equals(datos.paquete()))) {
				errores.put("cliente_id", "El cliente no tiene contratado ese paquete");
			}
		}
		if (virtual && (enlace == null || !enlace.matches("https?://\\S+"))) {
			errores.put("enlace", "Escriba el enlace de la reunión, empezando por https://");
		}
		if (!virtual && lugar == null) {
			errores.put("lugar", "Escriba el lugar de la capacitación presencial");
		}
		if (usuarios.listar(new Document("_id", datos.instructorId()).append("roles", "agente_soporte")
			.append("estado", "activo")).isEmpty()) {
			errores.put("instructor_id", "El instructor debe ser un agente de soporte activo");
		}

		Instant ahora = Instant.now();
		for (int i = 0; i < datos.sesiones().size(); i++) {
			CapacitacionRequest.Sesion sesion = datos.sesiones().get(i);
			if (!sesion.fechaInicio().isAfter(ahora)) {
				errores.put("sesiones[" + i + "].fecha_inicio", "La sesión debe ser en el futuro");
			}
			if (!sesion.fechaFin().isAfter(sesion.fechaInicio())) {
				errores.put("sesiones[" + i + "].fecha_fin", "Debe terminar después de empezar");
			}
		}
		if (!errores.isEmpty()) {
			throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Revise los datos del formulario", errores);
		}

		List<Capacitacion.Sesion> sesiones = datos.sesiones().stream()
			.map(s -> new Capacitacion.Sesion(s.tema().trim(), s.fechaInicio(), s.fechaFin()))
			.sorted(Comparator.comparing(Capacitacion.Sesion::fechaInicio))
			.toList();
		Instant fin = sesiones.stream().map(Capacitacion.Sesion::fechaFin).max(Comparator.naturalOrder()).orElseThrow();

		return mongo.insert(new Capacitacion(
				contadores.siguiente("capacitaciones", "CAP", 3),
				datos.titulo().trim(),
				datos.descripcion().trim(),
				datos.paquete(),
				datos.tipo(),
				clienteId,
				datos.modalidad(),
				enlace,
				lugar,
				datos.instructorId(),
				datos.cupoMaximo(),
				sesiones,
				sesiones.getFirst().fechaInicio(),
				fin,
				"programada",
				ahora));
	}

	public Capacitacion cancelar(String id) {
		UpdateResult resultado = mongo.updateFirst(
				query(where("_id").is(id).and("estado").in("programada", "en_curso")),
				Update.update("estado", "cancelada"), "capacitaciones");
		if (resultado.getMatchedCount() == 0) {
			obtener(id);
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "Solo se cancela una capacitación programada o en curso");
		}
		return obtener(id);
	}

	/**
	 * RF-39. El índice único una_inscripcion_por_usuario impide inscribir dos
	 * veces al mismo usuario; si había cancelado, la inscripción se reactiva.
	 */
	public Document inscribir(String id, UsuarioAutenticado usuario) {
		Capacitacion capacitacion = obtener(id);
		if (!"programada".equals(capacitacion.estado()) || !capacitacion.fechaInicio().isAfter(Instant.now())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "La capacitación ya empezó o no está abierta a inscripciones");
		}
		if (!mongo.exists(query(new Criteria().andOperator(
				where("_id").is(id), filtroDisponible(usuario))), "capacitaciones")) {
			throw new ReglaNegocioException(HttpStatus.FORBIDDEN,
					"Esta capacitación no está disponible para sus paquetes activos");
		}
		long inscritos = mongo.count(query(where("capacitacion_id").is(id).and("estado").ne("cancelado")), "asistencias");
		if (inscritos >= capacitacion.cupoMaximo()) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "No quedan cupos en esta capacitación");
		}

		Document existente = mongo.findOne(query(where("capacitacion_id").is(id).and("usuario_id").is(usuario.id())),
				Document.class, "asistencias");
		Date ahora = new Date();
		if (existente != null && "cancelado".equals(existente.getString("estado"))) {
			mongo.updateFirst(query(where("_id").is(existente.getObjectId("_id"))),
					Update.update("estado", "inscrito").set("fecha_inscripcion", ahora), "asistencias");
		}
		else {
			// Si ya existe, el índice único responde 409 "Ya está inscrito en esa capacitación"
			mongo.insert(new Document("capacitacion_id", id).append("usuario_id", usuario.id())
				.append("cliente_id", usuario.clienteId()).append("estado", "inscrito").append("fecha_inscripcion", ahora),
					"asistencias");
		}
		return new Document("capacitacion_id", id).append("estado", "inscrito").append("fecha_inscripcion", ahora);
	}

	/** El cliente cancela su inscripción mientras la capacitación no haya empezado. */
	public void cancelarInscripcion(String id, UsuarioAutenticado usuario) {
		Capacitacion capacitacion = obtener(id);
		if (!capacitacion.fechaInicio().isAfter(Instant.now())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "La capacitación ya empezó");
		}
		UpdateResult resultado = mongo.updateFirst(
				query(where("capacitacion_id").is(id).and("usuario_id").is(usuario.id()).and("estado").is("inscrito")),
				Update.update("estado", "cancelado"), "asistencias");
		if (resultado.getMatchedCount() == 0) {
			throw new ReglaNegocioException(HttpStatus.NOT_FOUND, "No está inscrito en esta capacitación");
		}
	}

	/**
	 * RF-40. El instructor marca quién asistió y la calificación de 1 a 5 que
	 * dio cada asistente; quien no asistió no califica (anyOf de asistencias).
	 * Todo va en una transacción: o se guarda la lista completa o nada. Al
	 * terminar, la capacitación pasa a en curso o a finalizada según la fecha.
	 */
	@Transactional
	public Map<String, Object> registrarAsistencia(String id, AsistenciaRequest datos, UsuarioAutenticado instructor) {
		Capacitacion capacitacion = obtener(id);
		if (!instructor.id().equals(capacitacion.instructorId())) {
			throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "Solo el instructor registra la asistencia");
		}
		if ("cancelada".equals(capacitacion.estado())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "La capacitación está cancelada");
		}
		Instant ahora = Instant.now();
		if (capacitacion.fechaInicio().isAfter(ahora)) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "La capacitación todavía no ha empezado");
		}

		Map<String, String> errores = new LinkedHashMap<>();
		for (int i = 0; i < datos.asistencias().size(); i++) {
			AsistenciaRequest.Registro registro = datos.asistencias().get(i);
			if (!"asistio".equals(registro.estado()) && registro.calificacion() != null) {
				errores.put("asistencias[" + i + "].calificacion", "Solo califica quien asistió");
			}
		}
		if (!errores.isEmpty()) {
			throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Revise la lista de asistencia", errores);
		}

		for (AsistenciaRequest.Registro registro : datos.asistencias()) {
			Update cambio = Update.update("estado", registro.estado());
			if (registro.calificacion() != null) {
				cambio.set("calificacion", registro.calificacion());
			}
			else {
				cambio.unset("calificacion");
			}
			String comentario = Textos.limpio(registro.comentario());
			if (comentario != null) {
				cambio.set("comentario", comentario);
			}
			else {
				cambio.unset("comentario");
			}
			UpdateResult resultado = mongo.updateFirst(query(where("capacitacion_id").is(id)
				.and("usuario_id").is(registro.usuarioId()).and("estado").ne("cancelado")), cambio, "asistencias");
			if (resultado.getMatchedCount() == 0) {
				throw new ReglaNegocioException(HttpStatus.BAD_REQUEST,
						"El usuario " + registro.usuarioId() + " no está inscrito en esta capacitación");
			}
		}

		String estado = capacitacion.fechaFin().isBefore(ahora) ? "finalizada" : "en_curso";
		mongo.updateFirst(query(where("_id").is(id)), Update.update("estado", estado), "capacitaciones");
		return detalle(id, instructor);
	}

	private Capacitacion obtener(String id) {
		Capacitacion capacitacion = mongo.findById(id, Capacitacion.class);
		if (capacitacion == null) {
			throw new ReglaNegocioException(HttpStatus.NOT_FOUND, "No existe la capacitación " + id);
		}
		return capacitacion;
	}

	/** Masivas de los paquetes activos del cliente o personalizadas para él. */
	private Criteria filtroDisponible(UsuarioAutenticado usuario) {
		Cliente cliente = clientes.findById(usuario.clienteId()).orElse(null);
		List<String> activos = cliente == null || cliente.paquetesContratados() == null ? List.of()
				: cliente.paquetesContratados().stream()
					.filter(p -> "activo".equals(p.estado()))
					.map(Cliente.PaqueteContratado::paqueteId)
					.toList();
		return new Criteria().orOperator(
				where("tipo").is("masiva").and("paquete").in(activos),
				where("tipo").is("personalizada").and("cliente_id").is(usuario.clienteId()));
	}

	/** Lo disponible más lo que el cliente ya tiene inscrito (aunque el paquete se haya suspendido después). */
	private Document filtroCliente(UsuarioAutenticado usuario) {
		List<String> inscritas = mongo.findDistinct(query(where("usuario_id").is(usuario.id())), "capacitacion_id",
				"asistencias", String.class);
		Document disponibles = query(filtroDisponible(usuario)).getQueryObject();
		return new Document("$or", List.of(disponibles, new Document("_id", new Document("$in", inscritas))));
	}

	/** Cambia la lista de inscripciones por el conteo y, si se pide, el estado de ese usuario. */
	private static void resumirInscripciones(Document capacitacion, String usuarioId) {
		List<Document> inscripciones = capacitacion.getList("inscripciones", Document.class, List.of());
		capacitacion.remove("inscripciones");
		capacitacion.put("inscritos", inscripciones.size());
		if (usuarioId != null) {
			capacitacion.put("mi_inscripcion", inscripciones.stream()
				.filter(i -> usuarioId.equals(i.getString("usuario_id")))
				.map(i -> i.getString("estado"))
				.findFirst()
				.orElse(null));
		}
	}

}
