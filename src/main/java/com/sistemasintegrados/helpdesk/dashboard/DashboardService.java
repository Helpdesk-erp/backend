package com.sistemasintegrados.helpdesk.dashboard;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.sistemasintegrados.helpdesk.auth.UsuarioAutenticado;
import com.sistemasintegrados.helpdesk.clientes.Cliente;
import com.sistemasintegrados.helpdesk.clientes.ClienteRepository;
import com.sistemasintegrados.helpdesk.comun.ReglaNegocioException;
import com.sistemasintegrados.helpdesk.conocimiento.ConocimientoService;
import com.sistemasintegrados.helpdesk.pagos.PagoService;
import com.sistemasintegrados.helpdesk.paquetes.PaqueteService;
import com.sistemasintegrados.helpdesk.reportes.ReportesService;
import com.sistemasintegrados.helpdesk.tickets.TicketService;
import com.sistemasintegrados.helpdesk.usuarios.UsuarioService;

/**
 * Arma el contenido del dashboard de cada rol con los reportes de agregación.
 */
@Service
public class DashboardService {

	private final ReportesService reportes;

	private final ClienteRepository clientes;

	private final MongoTemplate mongo;

	private final TicketService tickets;

	private final PagoService pagos;

	private final UsuarioService usuarios;

	private final PaqueteService paquetes;

	private final ConocimientoService conocimiento;

	public DashboardService(ReportesService reportes, ClienteRepository clientes, MongoTemplate mongo,
			TicketService tickets, PagoService pagos, UsuarioService usuarios, PaqueteService paquetes,
			ConocimientoService conocimiento) {
		this.reportes = reportes;
		this.clientes = clientes;
		this.mongo = mongo;
		this.tickets = tickets;
		this.pagos = pagos;
		this.usuarios = usuarios;
		this.paquetes = paquetes;
		this.conocimiento = conocimiento;
	}

	public Map<String, Object> administrador() {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("tickets_por_estado", reportes.ticketsPorEstado(new Document()));
		datos.put("clientes", reportes.resumenClientes());
		datos.put("incidentes_por_paquete", reportes.incidentesPorPaquete());
		datos.put("sin_atender", reportes.listarTickets(
				new Document("estado", "sin_atender"), new Document("fecha_creacion", 1), 10));
		datos.put("carga_agentes", reportes.cargaPorAgente());
		datos.put("pagos_rechazados", reportes.pagosRecientes(new Document("estado", "rechazado"), 5));
		datos.put("proximas_capacitaciones", reportes.proximasCapacitaciones(new Document(), null, 5));
		datos.put("usuarios", usuarios.listarParaAdministrador());
		return datos;
	}

	public Map<String, Object> agente(UsuarioAutenticado agente) {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("tickets_por_estado", reportes.ticketsPorEstado(new Document("agente_id", agente.id())));
		datos.put("mis_tickets", reportes.listarTickets(
				new Document("agente_id", agente.id()).append("estado", new Document("$in", List.of("asignado", "en_proceso"))),
				new Document("peso_prioridad", 1).append("fecha_creacion", 1), 20));
		datos.put("articulos_escritos", mongo.count(query(where("autor_id").is(agente.id())), "base_conocimiento"));
		datos.put("mis_capacitaciones", reportes.proximasCapacitaciones(new Document("instructor_id", agente.id()), null, 5));
		return datos;
	}

	public Map<String, Object> cliente(UsuarioAutenticado usuario) {
		Cliente cliente = clientes.findById(usuario.clienteId())
			.orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "No se encontró el cliente de este usuario"));

		// Las capacitaciones masivas solo se ofrecen para los paquetes con el pago al día
		List<String> paquetesActivos = cliente.paquetesContratados() == null ? List.of()
				: cliente.paquetesContratados().stream()
					.filter(paquete -> "activo".equals(paquete.estado()))
					.map(Cliente.PaqueteContratado::paqueteId)
					.toList();
		Document filtroCapacitaciones = new Document("$or", List.of(
				new Document("tipo", "masiva").append("paquete", new Document("$in", paquetesActivos)),
				new Document("tipo", "personalizada").append("cliente_id", cliente.id())));

		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("cliente", cliente);
		datos.put("tickets_por_estado", reportes.ticketsPorEstado(new Document("cliente_id", cliente.id())));
		datos.put("tickets_recientes", reportes.listarTickets(
				new Document("cliente_id", cliente.id()), new Document("fecha_creacion", -1), 8));
		datos.put("capacitaciones", reportes.proximasCapacitaciones(filtroCapacitaciones, usuario.id(), 6));
		datos.put("pagos_recientes", reportes.pagosRecientes(new Document("cliente_id", cliente.id()), 6));
		datos.put("por_confirmar", tickets.pendientesDeConfirmar(cliente.id()));
		datos.put("catalogo_paquetes", paquetes.catalogoPorPrecio());
		return datos;
	}

	public Map<String, Object> auditor() {
		Map<String, Object> datos = new LinkedHashMap<>();
		datos.put("tickets_por_estado", reportes.ticketsPorEstado(new Document()));
		datos.put("incidentes_por_paquete", reportes.incidentesPorPaquete());
		datos.put("tiempo_resolucion", reportes.tiempoResolucionPorPaquete());
		datos.put("ingresos_por_paquete", reportes.ingresosPorPaquete());
		datos.put("asistencia_capacitaciones", reportes.asistenciaPorCapacitacion());
		datos.put("actividad_reciente", reportes.actividadReciente(12));
		datos.put("recaudo_por_metodo", pagos.recaudoPorMetodo());
		datos.put("trazabilidad_conocimiento", conocimiento.articulosConTicket());
		return datos;
	}

}
