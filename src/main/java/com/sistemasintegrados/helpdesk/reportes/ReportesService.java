package com.sistemasintegrados.helpdesk.reportes;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

/**
 * Reportes con el Framework de Agregación. Cada pipeline está escrito como
 * texto JSON para que se pueda copiar en mongosh tal cual; la misma versión
 * está en base_datos/04_reportes.js. Las partes que dependen del usuario
 * (filtros, límites) se agregan como Document.
 */
@Service
public class ReportesService {

	public static final List<String> ESTADOS_TICKET = List.of("sin_atender", "asignado", "en_proceso", "resuelto", "cerrado");

	/** Trae el nombre del agente: tickets.agente_id -> usuarios -> empleados. */
	private static final String LOOKUP_NOMBRE_EMPLEADO = """
			[
			  { $lookup: { from: "empleados", localField: "empleado_id", foreignField: "_id", as: "empleado" } },
			  { $project: { nombre: { $concat: [ { $first: "$empleado.nombres" }, " ", { $first: "$empleado.apellidos" } ] } } }
			]
			""";

	private final MongoTemplate mongo;

	public ReportesService(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	/** Cantidad de tickets en cada estado. Con filtro vacío cuenta todos. */
	public Map<String, Integer> ticketsPorEstado(Document filtro) {
		List<Document> etapas = new ArrayList<>();
		etapas.add(new Document("$match", filtro));
		etapas.add(Document.parse("{ $group: { _id: \"$estado\", total: { $sum: 1 } } }"));

		Map<String, Integer> conteo = new LinkedHashMap<>();
		ESTADOS_TICKET.forEach(estado -> conteo.put(estado, 0));
		agregar("tickets", etapas).forEach(fila -> conteo.put(fila.getString("_id"), entero(fila, "total")));
		return conteo;
	}

	/** Qué paquete del ERP falla más: incidentes por paquete ($match, $group, $lookup). */
	public List<Document> incidentesPorPaquete() {
		return agregar("tickets", pipeline("""
				[
				  { $match: { tipo: "incidente" } },
				  { $group: {
				      _id: "$paquete",
				      total: { $sum: 1 },
				      abiertos: { $sum: { $cond: [ { $in: [ "$estado", [ "sin_atender", "asignado", "en_proceso" ] ] }, 1, 0 ] } }
				  } },
				  { $lookup: { from: "paquetes", localField: "_id", foreignField: "_id", as: "paquete" } },
				  { $project: { _id: 0, paquete_id: "$_id", nombre: { $first: "$paquete.nombre" }, total: 1, abiertos: 1 } },
				  { $sort: { total: -1, nombre: 1 } }
				]
				"""));
	}

	/**
	 * Lista de tickets con el nombre del cliente y del agente.
	 *
	 * @param orden si incluye "peso_prioridad", los de prioridad alta salen primero
	 */
	public List<Document> listarTickets(Document filtro, Document orden, int limite) {
		List<Document> etapas = new ArrayList<>();
		etapas.add(new Document("$match", filtro));
		if (orden.containsKey("peso_prioridad")) {
			etapas.add(Document.parse(
					"{ $addFields: { peso_prioridad: { $indexOfArray: [ [ \"alta\", \"media\", \"baja\" ], \"$prioridad\" ] } } }"));
		}
		etapas.add(new Document("$sort", orden));
		etapas.add(new Document("$limit", limite));
		etapas.add(new Document("$lookup", new Document("from", "clientes").append("localField", "cliente_id")
			.append("foreignField", "_id").append("as", "cliente")
			.append("pipeline", pipeline("[ { $project: { nombre: 1 } } ]"))));
		etapas.add(new Document("$lookup", new Document("from", "usuarios").append("localField", "agente_id")
			.append("foreignField", "_id").append("as", "agente")
			.append("pipeline", pipeline(LOOKUP_NOMBRE_EMPLEADO))));
		etapas.addAll(pipeline("""
				[
				  { $project: {
				      _id: 0, id: "$_id", asunto: 1, paquete: 1, tipo: 1, prioridad: 1, estado: 1,
				      fecha_creacion: 1, fecha_actualizacion: 1,
				      cliente: { $first: "$cliente.nombre" }, agente: { $first: "$agente.nombre" }
				  } }
				]
				"""));
		return agregar("tickets", etapas);
	}

	/** Tickets abiertos y resueltos por cada agente activo, incluidos los que no tienen ninguno. */
	public List<Document> cargaPorAgente() {
		return agregar("usuarios", pipeline("""
				[
				  { $match: { roles: "agente_soporte", estado: "activo" } },
				  { $lookup: { from: "empleados", localField: "empleado_id", foreignField: "_id", as: "empleado" } },
				  { $lookup: {
				      from: "tickets", localField: "_id", foreignField: "agente_id", as: "carga",
				      pipeline: [
				        { $group: {
				            _id: null,
				            abiertos: { $sum: { $cond: [ { $in: [ "$estado", [ "asignado", "en_proceso" ] ] }, 1, 0 ] } },
				            resueltos: { $sum: { $cond: [ { $in: [ "$estado", [ "resuelto", "cerrado" ] ] }, 1, 0 ] } }
				        } }
				      ]
				  } },
				  { $project: {
				      _id: 0, agente_id: "$_id",
				      nombre: { $concat: [ { $first: "$empleado.nombres" }, " ", { $first: "$empleado.apellidos" } ] },
				      abiertos: { $ifNull: [ { $first: "$carga.abiertos" }, 0 ] },
				      resueltos: { $ifNull: [ { $first: "$carga.resueltos" }, 0 ] }
				  } },
				  { $sort: { abiertos: -1, nombre: 1 } }
				]
				"""));
	}

	/** Clientes activos, paquetes activos y suspendidos, e ingreso mensual de los paquetes activos. */
	public Document resumenClientes() {
		List<Document> filas = agregar("clientes", pipeline("""
				[
				  { $project: {
				      estado: 1,
				      activos: { $filter: { input: { $ifNull: [ "$paquetes_contratados", [] ] }, cond: { $eq: [ "$$this.estado", "activo" ] } } },
				      suspendidos: { $filter: { input: { $ifNull: [ "$paquetes_contratados", [] ] }, cond: { $eq: [ "$$this.estado", "suspendido" ] } } }
				  } },
				  { $group: {
				      _id: null,
				      total: { $sum: 1 },
				      activos: { $sum: { $cond: [ { $eq: [ "$estado", "activo" ] }, 1, 0 ] } },
				      paquetes_activos: { $sum: { $size: "$activos" } },
				      paquetes_suspendidos: { $sum: { $size: "$suspendidos" } },
				      ingreso_mensual: { $sum: { $sum: "$activos.precio_mensual" } }
				  } },
				  { $project: { _id: 0 } }
				]
				"""));
		return filas.isEmpty() ? new Document("total", 0) : filas.getFirst();
	}

	public List<Document> pagosRecientes(Document filtro, int limite) {
		List<Document> etapas = new ArrayList<>();
		etapas.add(new Document("$match", filtro));
		etapas.add(new Document("$sort", new Document("fecha", -1)));
		etapas.add(new Document("$limit", limite));
		etapas.addAll(pipeline("""
				[
				  { $lookup: { from: "clientes", localField: "cliente_id", foreignField: "_id", as: "cliente",
				      pipeline: [ { $project: { nombre: 1 } } ] } },
				  { $project: {
				      _id: 0, id: "$_id", cliente: { $first: "$cliente.nombre" }, paquete_id: 1, periodo: 1, valor: 1,
				      metodo: 1, estado: 1, motivo_rechazo: 1, fecha: 1
				  } }
				]
				"""));
		return agregar("pagos", etapas);
	}

	/**
	 * Capacitaciones programadas o en curso que todavía no terminan.
	 *
	 * @param filtroExtra condiciones adicionales (paquete, instructor, cliente)
	 * @param usuarioId   si no es null, agrega "mi_inscripcion" con el estado de ese usuario
	 */
	public List<Document> proximasCapacitaciones(Document filtroExtra, String usuarioId, int limite) {
		Document filtro = new Document("estado", new Document("$in", List.of("programada", "en_curso")))
			.append("fecha_fin", new Document("$gte", new Date()));
		filtro.putAll(filtroExtra);

		List<Document> etapas = new ArrayList<>();
		etapas.add(new Document("$match", filtro));
		etapas.add(new Document("$sort", new Document("fecha_inicio", 1)));
		etapas.add(new Document("$limit", limite));
		etapas.add(Document.parse("""
				{ $lookup: {
				    from: "asistencias", localField: "_id", foreignField: "capacitacion_id", as: "inscripciones",
				    pipeline: [ { $match: { estado: { $ne: "cancelado" } } }, { $project: { _id: 0, usuario_id: 1, estado: 1 } } ]
				} }
				"""));
		etapas.add(new Document("$lookup", new Document("from", "usuarios").append("localField", "instructor_id")
			.append("foreignField", "_id").append("as", "instructor")
			.append("pipeline", pipeline(LOOKUP_NOMBRE_EMPLEADO))));
		etapas.addAll(pipeline("""
				[
				  { $project: {
				      _id: 0, id: "$_id", titulo: 1, descripcion: 1, paquete: 1, tipo: 1, modalidad: 1, enlace: 1, lugar: 1,
				      estado: 1, fecha_inicio: 1, fecha_fin: 1, cupo_maximo: 1, sesiones: 1, inscripciones: 1,
				      instructor: { $first: "$instructor.nombre" }
				  } }
				]
				"""));

		List<Document> capacitaciones = agregar("capacitaciones", etapas);
		for (Document capacitacion : capacitaciones) {
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
		return capacitaciones;
	}

	/** Horas promedio entre la creación del ticket y su paso a "resuelto", por paquete. */
	public List<Document> tiempoResolucionPorPaquete() {
		return agregar("tickets", pipeline("""
				[
				  { $match: { estado: { $in: [ "resuelto", "cerrado" ] } } },
				  { $project: {
				      paquete: 1, fecha_creacion: 1,
				      resuelto: { $first: { $filter: { input: "$historial", cond: { $eq: [ "$$this.estado", "resuelto" ] } } } }
				  } },
				  { $group: {
				      _id: "$paquete",
				      tickets: { $sum: 1 },
				      minutos: { $avg: { $dateDiff: { startDate: "$fecha_creacion", endDate: "$resuelto.fecha", unit: "minute" } } }
				  } },
				  { $lookup: { from: "paquetes", localField: "_id", foreignField: "_id", as: "paquete" } },
				  { $project: {
				      _id: 0, paquete_id: "$_id", nombre: { $first: "$paquete.nombre" }, tickets: 1,
				      horas_promedio: { $round: [ { $divide: [ "$minutos", 60 ] }, 1 ] }
				  } },
				  { $sort: { horas_promedio: -1 } }
				]
				"""));
	}

	/** Recaudo de pagos aprobados y cantidad de rechazos por paquete. */
	public List<Document> ingresosPorPaquete() {
		return agregar("pagos", pipeline("""
				[
				  { $group: {
				      _id: "$paquete_id",
				      recaudado: { $sum: { $cond: [ { $eq: [ "$estado", "aprobado" ] }, "$valor", 0 ] } },
				      aprobados: { $sum: { $cond: [ { $eq: [ "$estado", "aprobado" ] }, 1, 0 ] } },
				      rechazados: { $sum: { $cond: [ { $eq: [ "$estado", "rechazado" ] }, 1, 0 ] } }
				  } },
				  { $lookup: { from: "paquetes", localField: "_id", foreignField: "_id", as: "paquete" } },
				  { $project: { _id: 0, paquete_id: "$_id", nombre: { $first: "$paquete.nombre" }, recaudado: 1, aprobados: 1, rechazados: 1 } },
				  { $sort: { recaudado: -1 } }
				]
				"""));
	}

	/** Inscritos, asistentes y calificación promedio de cada capacitación. */
	public List<Document> asistenciaPorCapacitacion() {
		return agregar("asistencias", pipeline("""
				[
				  { $group: {
				      _id: "$capacitacion_id",
				      inscritos: { $sum: { $cond: [ { $ne: [ "$estado", "cancelado" ] }, 1, 0 ] } },
				      asistieron: { $sum: { $cond: [ { $eq: [ "$estado", "asistio" ] }, 1, 0 ] } },
				      no_asistieron: { $sum: { $cond: [ { $eq: [ "$estado", "no_asistio" ] }, 1, 0 ] } },
				      calificacion: { $avg: "$calificacion" }
				  } },
				  { $lookup: { from: "capacitaciones", localField: "_id", foreignField: "_id", as: "capacitacion" } },
				  { $project: {
				      _id: 0, capacitacion_id: "$_id",
				      titulo: { $first: "$capacitacion.titulo" }, paquete: { $first: "$capacitacion.paquete" },
				      estado: { $first: "$capacitacion.estado" }, fecha_inicio: { $first: "$capacitacion.fecha_inicio" },
				      inscritos: 1, asistieron: 1, no_asistieron: 1, calificacion: { $round: [ "$calificacion", 1 ] }
				  } },
				  { $sort: { fecha_inicio: 1 } }
				]
				"""));
	}

	/** Últimos cambios de estado de cualquier ticket, tomados del historial embebido. */
	public List<Document> actividadReciente(int limite) {
		List<Document> etapas = new ArrayList<>();
		etapas.add(Document.parse("{ $unwind: \"$historial\" }"));
		etapas.add(Document.parse("{ $sort: { \"historial.fecha\": -1 } }"));
		etapas.add(new Document("$limit", limite));
		etapas.addAll(pipeline("""
				[
				  { $lookup: { from: "usuarios", localField: "historial.usuario_id", foreignField: "_id", as: "usuario",
				      pipeline: [ { $project: { usuario: 1, roles: 1 } } ] } },
				  { $project: {
				      _id: 0, ticket_id: "$_id", asunto: 1, estado: "$historial.estado", fecha: "$historial.fecha",
				      comentario: "$historial.comentario", usuario: { $first: "$usuario.usuario" },
				      rol: { $first: { $first: "$usuario.roles" } }
				  } }
				]
				"""));
		return agregar("tickets", etapas);
	}

	private List<Document> agregar(String coleccion, List<Document> etapas) {
		return mongo.getCollection(coleccion).aggregate(etapas).into(new ArrayList<>());
	}

	/** Convierte un arreglo JSON de etapas en la lista que recibe aggregate(). */
	private static List<Document> pipeline(String json) {
		return Document.parse("{ etapas: " + json + " }").getList("etapas", Document.class);
	}

	private static int entero(Document fila, String campo) {
		return fila.get(campo, Number.class).intValue();
	}

}
