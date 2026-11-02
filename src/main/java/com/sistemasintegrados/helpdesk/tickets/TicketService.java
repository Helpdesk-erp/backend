package com.sistemasintegrados.helpdesk.tickets;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import com.sistemasintegrados.helpdesk.auth.UsuarioAutenticado;
import com.sistemasintegrados.helpdesk.clientes.Cliente;
import com.sistemasintegrados.helpdesk.clientes.ClienteRepository;
import com.sistemasintegrados.helpdesk.comun.ContadorService;
import com.sistemasintegrados.helpdesk.comun.ReglaNegocioException;
import com.sistemasintegrados.helpdesk.comun.Textos;
import com.sistemasintegrados.helpdesk.paquetes.Paquete;
import com.sistemasintegrados.helpdesk.paquetes.PaqueteRepository;
import com.sistemasintegrados.helpdesk.reportes.ReportesService;
import com.sistemasintegrados.helpdesk.usuarios.UsuarioService;

@Service
public class TicketService {

	/**
	 * Reglas del oneOf de tickets en 01_esquema.js que campos_ticket no
	 * describe. Si no se revisan aquí, el validador de MongoDB rechaza el ticket
	 * sin decir en qué campo está el error.
	 */
	private static final Map<String, Regla> REGLAS_DETALLE = Map.of(
			"codigo_barras", new Regla("[0-9]{8,14}", "Entre 8 y 14 dígitos"),
			"orden_compra", new Regla("OC-[0-9]+", "Formato OC-004512"),
			"nit_proveedor", new Regla("[0-9]{6,10}", "Solo números, entre 6 y 10 dígitos"));

	private static final Set<String> NO_NEGATIVOS = Set.of("cantidad_sistema", "cantidad_fisica");

	/** Cambios de estado que puede hacer el agente (RF-25). */
	private static final Map<String, List<String>> TRANSICIONES_AGENTE = Map.of(
			"asignado", List.of("en_proceso", "resuelto"),
			"en_proceso", List.of("resuelto"));

	private record Regla(String patron, String mensaje) {
	}

	private final MongoTemplate mongo;

	private final ContadorService contadores;

	private final ClienteRepository clientes;

	private final PaqueteRepository paquetes;

	private final UsuarioService usuarios;

	private final ReportesService reportes;

	public TicketService(MongoTemplate mongo, ContadorService contadores, ClienteRepository clientes,
			PaqueteRepository paquetes, UsuarioService usuarios, ReportesService reportes) {
		this.mongo = mongo;
		this.contadores = contadores;
		this.clientes = clientes;
		this.paquetes = paquetes;
		this.usuarios = usuarios;
		this.reportes = reportes;
	}

	/**
	 * Tickets del cliente que soporte ya marcó como resueltos y que esperan su
	 * confirmación para cerrarse. Agregación con $match; el índice
	 * tickets_por_cliente cubre el filtro por cliente_id.
	 */
	public List<Document> pendientesDeConfirmar(String clienteId) {
		List<Document> etapas = List.of(
				new Document("$match", new Document("cliente_id", clienteId).append("estado", "resuelto")));

		List<Document> tickets = mongo.getCollection("tickets").aggregate(etapas).into(new ArrayList<>());
		tickets.forEach(ticket -> ticket.put("id", ticket.remove("_id")));
		return tickets;
	}

	/**
	 * Lista según quién pregunta: el cliente ve los suyos (RF-21), el agente los
	 * que tiene asignados y el administrador y el auditor todos. Del más
	 * reciente al más antiguo.
	 */
	public List<Document> listar(UsuarioAutenticado usuario, String estado, String paquete) {
		Document filtro = switch (usuario.rol()) {
			case "cliente" -> new Document("cliente_id", usuario.clienteId());
			case "agente_soporte" -> new Document("agente_id", usuario.id());
			default -> new Document();
		};
		if (estado != null && ReportesService.ESTADOS_TICKET.contains(estado)) {
			filtro.append("estado", estado);
		}
		if (paquete != null && !paquete.isBlank()) {
			filtro.append("paquete", paquete);
		}
		return reportes.listarTickets(filtro, new Document("fecha_creacion", -1), 500);
	}

	/**
	 * El ticket completo: detalle técnico con las etiquetas del formulario,
	 * historial con el nombre de quien hizo cada cambio, cliente, agente y el
	 * artículo de la base de conocimiento que salió de él, si existe.
	 */
	public Document detalle(String id, UsuarioAutenticado usuario) {
		cargar(id, usuario);
		Document ticket = mongo.findOne(query(where("_id").is(id)), Document.class, "tickets");
		ticket.put("id", ticket.remove("_id"));

		Query consultaCliente = query(where("_id").is(ticket.getString("cliente_id")));
		consultaCliente.fields().include("nombre", "representante", "telefono", "correo");
		Document cliente = mongo.findOne(consultaCliente, Document.class, "clientes");
		if (cliente != null) {
			cliente.put("id", cliente.remove("_id"));
		}
		ticket.put("cliente", cliente);

		List<Document> historial = ticket.getList("historial", Document.class, List.of());
		Set<String> ids = new LinkedHashSet<>();
		historial.forEach(evento -> ids.add(evento.getString("usuario_id")));
		ids.add(ticket.getString("reportado_por"));
		if (ticket.getString("agente_id") != null) {
			ids.add(ticket.getString("agente_id"));
		}
		Map<String, Document> personas = usuarios.listar(new Document("_id", new Document("$in", ids))).stream()
			.collect(Collectors.toMap(p -> p.getString("id"), Function.identity()));
		historial.forEach(evento -> {
			Document persona = personas.get(evento.getString("usuario_id"));
			if (persona != null) {
				evento.put("usuario", persona.getString("usuario"));
				evento.put("nombre", persona.getString("nombre"));
				evento.put("rol", persona.getString("rol"));
			}
		});
		Document agente = personas.get(ticket.getString("agente_id"));
		ticket.put("agente", agente == null ? null : new Document("id", agente.getString("id")).append("nombre", agente.getString("nombre")));
		Document reporto = personas.get(ticket.getString("reportado_por"));
		ticket.put("reportado_por_usuario", reporto == null ? null : reporto.getString("usuario"));

		paquetes.findById(ticket.getString("paquete")).ifPresent(p -> {
			ticket.put("paquete_nombre", p.nombre());
			ticket.put("campos", p.camposTicket());
		});

		Query consultaArticulo = query(where("ticket_id").is(id));
		consultaArticulo.fields().include("_id");
		Document articulo = mongo.findOne(consultaArticulo, Document.class, "base_conocimiento");
		ticket.put("articulo_id", articulo == null ? null : articulo.getString("_id"));
		return ticket;
	}

	/**
	 * RF-20. Solo sobre un paquete que el cliente tiene contratado y activo
	 * (un pago rechazado lo suspende). detalle se revisa campo por campo contra
	 * paquetes.campos_ticket (RF-19).
	 */
	public Document crear(CrearTicketRequest datos, UsuarioAutenticado usuario) {
		Cliente cliente = clientes.findById(usuario.clienteId())
			.orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "No se encontró el cliente de este usuario"));
		Paquete paquete = paquetes.findById(datos.paquete())
			.orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "No existe el paquete " + datos.paquete()));

		Cliente.PaqueteContratado contratado = cliente.paquetesContratados() == null ? null
				: cliente.paquetesContratados().stream()
					.filter(p -> p.paqueteId().equals(paquete.id()))
					.findFirst()
					.orElse(null);
		if (contratado == null) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "No tiene contratado el paquete " + paquete.nombre(),
					Map.of("paquete", "No tiene contratado este paquete"));
		}
		if (!"activo".equals(contratado.estado())) {
			String motivo = "suspendido".equals(contratado.estado()) ? "está suspendido por un pago rechazado" : "está vencido";
			throw new ReglaNegocioException(HttpStatus.CONFLICT,
					"El paquete " + paquete.nombre() + " " + motivo + ". No puede abrir tickets de ese paquete",
					Map.of("paquete", "El paquete " + motivo));
		}

		Map<String, Object> detalle = validarDetalle(paquete, datos.detalle());
		Instant ahora = Instant.now();
		Ticket ticket = new Ticket(
				contadores.siguiente("tickets", "TCK", 4),
				cliente.id(),
				usuario.id(),
				paquete.id(),
				datos.tipo(),
				datos.prioridad(),
				datos.asunto().trim(),
				datos.descripcion().trim(),
				detalle,
				"sin_atender",
				null,
				null,
				null,
				List.of(new Ticket.Evento(ahora, "sin_atender", usuario.id(), "Ticket creado por el cliente")),
				ahora,
				null);
		mongo.insert(ticket);
		return detalle(ticket.id(), usuario);
	}

	/** RF-22. La condición de estado va en el filtro: si soporte ya lo tomó, no se modifica. */
	public Document actualizar(String id, ActualizarTicketRequest datos, UsuarioAutenticado usuario) {
		UpdateResult resultado = mongo.updateFirst(
				query(where("_id").is(id).and("cliente_id").is(usuario.clienteId()).and("estado").is("sin_atender")),
				Update.update("asunto", datos.asunto().trim())
					.set("descripcion", datos.descripcion().trim())
					.set("prioridad", datos.prioridad())
					.set("fecha_actualizacion", Instant.now()),
				"tickets");
		if (resultado.getMatchedCount() == 0) {
			cargar(id, usuario);
			throw new ReglaNegocioException(HttpStatus.CONFLICT,
					"Solo puede editar el ticket mientras siga sin atender. Soporte ya lo está revisando");
		}
		return detalle(id, usuario);
	}

	/** RF-23. Solo mientras no tenga agente; después se conserva por trazabilidad. */
	public void eliminar(String id, UsuarioAutenticado usuario) {
		DeleteResult resultado = mongo.remove(
				query(where("_id").is(id).and("cliente_id").is(usuario.clienteId()).and("estado").is("sin_atender")
					.and("agente_id").exists(false)),
				"tickets");
		if (resultado.getDeletedCount() == 0) {
			cargar(id, usuario);
			throw new ReglaNegocioException(HttpStatus.CONFLICT,
					"Solo se puede eliminar un ticket que todavía no tiene agente asignado");
		}
	}

	/**
	 * RF-24. También sirve para reasignar un ticket asignado o en proceso; en ese
	 * caso vuelve a "asignado" porque el agente nuevo no lo ha empezado.
	 */
	public Document asignar(String id, AsignarTicketRequest datos, UsuarioAutenticado administrador) {
		Ticket ticket = cargar(id, administrador);
		if (List.of("resuelto", "cerrado").contains(ticket.estado())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "El ticket ya está " + ticket.estado() + " y no se puede asignar");
		}
		if (datos.agenteId().equals(ticket.agenteId())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "El ticket ya está asignado a ese agente",
					Map.of("agente_id", "Ya está asignado a este agente"));
		}
		List<Document> agente = usuarios.listar(new Document("_id", datos.agenteId())
			.append("roles", "agente_soporte").append("estado", "activo"));
		if (agente.isEmpty()) {
			throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Revise los datos del formulario",
					Map.of("agente_id", "Debe ser un agente de soporte activo"));
		}

		String comentario = Textos.limpio(datos.comentario());
		if (comentario == null) {
			comentario = (ticket.agenteId() == null ? "Asignado a " : "Reasignado a ") + agente.getFirst().getString("nombre");
		}
		UpdateResult resultado = mongo.updateFirst(
				query(where("_id").is(id).and("estado").is(ticket.estado())),
				Update.update("estado", "asignado")
					.set("agente_id", datos.agenteId())
					.set("asignado_por", administrador.id())
					.set("fecha_actualizacion", Instant.now())
					.push("historial", evento("asignado", administrador.id(), comentario)),
				"tickets");
		comprobarCambio(resultado);
		return detalle(id, administrador);
	}

	/**
	 * RF-25. El agente pasa su ticket a en proceso o a resuelto. Para resolverlo
	 * hace falta la solución (RF-26), que puede venir en la misma petición.
	 */
	public Document cambiarEstado(String id, CambioEstadoRequest datos, UsuarioAutenticado agente) {
		Ticket ticket = cargar(id, agente);
		List<String> permitidos = TRANSICIONES_AGENTE.getOrDefault(ticket.estado(), List.of());
		if (!permitidos.contains(datos.estado())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT,
					"Un ticket " + nombreEstado(ticket.estado()) + " no puede pasar a " + nombreEstado(datos.estado()));
		}

		Update cambio = Update.update("estado", datos.estado()).set("fecha_actualizacion", Instant.now());
		if ("resuelto".equals(datos.estado())) {
			String solucion = Textos.limpio(datos.solucion()) != null ? datos.solucion().trim() : ticket.solucion();
			if (solucion == null) {
				throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Escriba la solución antes de marcar el ticket como resuelto",
						Map.of("solucion", "La solución es obligatoria para resolver el ticket"));
			}
			cambio.set("solucion", solucion);
		}
		cambio.push("historial", evento(datos.estado(), agente.id(), Textos.limpio(datos.comentario())));

		UpdateResult resultado = mongo.updateFirst(
				query(where("_id").is(id).and("agente_id").is(agente.id()).and("estado").is(ticket.estado())),
				cambio, "tickets");
		comprobarCambio(resultado);
		return detalle(id, agente);
	}

	/**
	 * RF-26. El agente escribe o corrige la solución mientras atiende el ticket.
	 * Un ticket cerrado ya no se modifica.
	 */
	public Document registrarSolucion(String id, SolucionRequest datos, UsuarioAutenticado agente) {
		Ticket ticket = cargar(id, agente);
		if (!List.of("asignado", "en_proceso", "resuelto").contains(ticket.estado())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "El ticket está " + nombreEstado(ticket.estado())
					+ " y ya no se puede modificar la solución");
		}
		UpdateResult resultado = mongo.updateFirst(
				query(where("_id").is(id).and("agente_id").is(agente.id()).and("estado").is(ticket.estado())),
				Update.update("solucion", datos.solucion().trim()).set("fecha_actualizacion", Instant.now()),
				"tickets");
		comprobarCambio(resultado);
		return detalle(id, agente);
	}

	/** RF-28. El cliente confirma que la solución le sirvió y el ticket se cierra. */
	public Document cerrar(String id, String comentario, UsuarioAutenticado cliente) {
		String texto = Textos.limpio(comentario);
		UpdateResult resultado = mongo.updateFirst(
				query(where("_id").is(id).and("cliente_id").is(cliente.clienteId()).and("estado").is("resuelto")),
				Update.update("estado", "cerrado")
					.set("fecha_actualizacion", Instant.now())
					.push("historial", evento("cerrado", cliente.id(), texto != null ? texto : "Solución confirmada por el cliente")),
				"tickets");
		if (resultado.getMatchedCount() == 0) {
			cargar(id, cliente);
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "Solo se puede cerrar un ticket que soporte marcó como resuelto");
		}
		return detalle(id, cliente);
	}

	/**
	 * Trae el ticket si quien pregunta lo puede ver: el cliente los suyos, el
	 * agente los que tiene asignados, el administrador y el auditor todos. A un
	 * cliente no se le dice si existe un ticket de otro.
	 */
	Ticket cargar(String id, UsuarioAutenticado usuario) {
		Ticket ticket = mongo.findById(id, Ticket.class);
		boolean ajeno = ticket != null && "cliente".equals(usuario.rol()) && !ticket.clienteId().equals(usuario.clienteId());
		if (ticket == null || ajeno) {
			throw new ReglaNegocioException(HttpStatus.NOT_FOUND, "No existe el ticket " + id);
		}
		if ("agente_soporte".equals(usuario.rol()) && !usuario.id().equals(ticket.agenteId())) {
			throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "Este ticket no está asignado a usted");
		}
		return ticket;
	}

	/**
	 * Arma tickets.detalle con los campos del paquete: revisa obligatorios,
	 * convierte los números, valida listas, URL y los formatos de REGLAS_DETALLE.
	 * Los campos que el paquete no pide se descartan.
	 */
	private static Map<String, Object> validarDetalle(Paquete paquete, Map<String, Object> valores) {
		Map<String, Object> detalle = new LinkedHashMap<>();
		Map<String, String> errores = new LinkedHashMap<>();

		for (Paquete.CampoTicket campo : paquete.camposTicket()) {
			Object crudo = valores == null ? null : valores.get(campo.campo());
			String texto = crudo == null ? null : Textos.limpio(String.valueOf(crudo));
			String clave = "detalle." + campo.campo();

			if (texto == null) {
				if (Boolean.TRUE.equals(campo.requerido())) {
					errores.put(clave, "Este campo es obligatorio");
				}
				continue;
			}

			Object valor = texto;
			String error = null;
			switch (campo.tipo()) {
				case "numero" -> {
					Number numero = numero(texto);
					if (numero == null) {
						error = "Escriba un número";
					}
					else if (NO_NEGATIVOS.contains(campo.campo()) && numero.doubleValue() < 0) {
						error = "No puede ser negativo";
					}
					valor = numero;
				}
				case "url" -> {
					if (!texto.matches("https?://\\S+")) {
						error = "Debe empezar por http:// o https://";
					}
				}
				case "lista" -> {
					if (campo.opciones() != null && !campo.opciones().contains(texto)) {
						error = "Seleccione una de las opciones";
					}
				}
				default -> {
				}
			}
			Regla regla = REGLAS_DETALLE.get(campo.campo());
			if (error == null && regla != null && !texto.matches(regla.patron())) {
				error = regla.mensaje();
			}

			if (error != null) {
				errores.put(clave, error);
			}
			else {
				detalle.put(campo.campo(), valor);
			}
		}

		if (!errores.isEmpty()) {
			throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Revise los datos técnicos del formulario", errores);
		}
		return detalle;
	}

	/** "92,5" o "92.5" -> 92.5 (double); "120" -> 120 (long). null si no es un número. */
	private static Number numero(String texto) {
		try {
			BigDecimal numero = new BigDecimal(texto.replace(',', '.'));
			// Con un ternario, Java pasaría el long a double
			if (numero.stripTrailingZeros().scale() <= 0) {
				return numero.longValueExact();
			}
			return numero.doubleValue();
		}
		catch (NumberFormatException | ArithmeticException e) {
			return null;
		}
	}

	/** Elemento del historial. Se arma como Document con Date para que $push lo guarde tal cual. */
	private static Document evento(String estado, String usuarioId, String comentario) {
		Document evento = new Document("fecha", Date.from(Instant.now())).append("estado", estado).append("usuario_id", usuarioId);
		if (comentario != null) {
			evento.append("comentario", comentario);
		}
		return evento;
	}

	/** Otro usuario cambió el ticket entre la lectura y la escritura. */
	private static void comprobarCambio(UpdateResult resultado) {
		if (resultado.getMatchedCount() == 0) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "El ticket cambió mientras lo editaba. Recargue la página");
		}
	}

	private static String nombreEstado(String estado) {
		return switch (estado) {
			case "sin_atender" -> "sin atender";
			case "en_proceso" -> "en proceso";
			default -> estado;
		};
	}

}
