package com.sistemasintegrados.helpdesk.pagos;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemasintegrados.helpdesk.auth.UsuarioAutenticado;
import com.sistemasintegrados.helpdesk.clientes.Cliente;
import com.sistemasintegrados.helpdesk.clientes.ClienteService;
import com.sistemasintegrados.helpdesk.comun.ContadorService;
import com.sistemasintegrados.helpdesk.comun.ReglaNegocioException;
import com.sistemasintegrados.helpdesk.comun.Textos;
import com.sistemasintegrados.helpdesk.reportes.ReportesService;

@Service
public class PagoService {

	private final MongoTemplate mongo;

	private final ContadorService contadores;

	private final ClienteService clientes;

	private final ReportesService reportes;

	public PagoService(MongoTemplate mongo, ContadorService contadores, ClienteService clientes, ReportesService reportes) {
		this.mongo = mongo;
		this.contadores = contadores;
		this.clientes = clientes;
		this.reportes = reportes;
	}

	/**
	 * Recaudo por método de pago (PSE, transferencia, tarjeta...). Agregación con
	 * $group: suma el valor de los pagos aprobados y cuenta los pagos y los
	 * rechazos de cada método.
	 */
	public List<Document> recaudoPorMetodo() {
		List<Document> etapas = List.of(Document.parse("""
				{ $group: {
				    _id: "$metodo",
				    recaudado: { $sum: { $cond: [ { $eq: [ "$estado", "aprobado" ] }, "$valor", 0 ] } },
				    pagos: { $sum: 1 },
				    rechazados: { $sum: { $cond: [ { $eq: [ "$estado", "rechazado" ] }, 1, 0 ] } }
				} }
				"""));

		List<Document> filas = mongo.getCollection("pagos").aggregate(etapas).into(new ArrayList<>());
		filas.forEach(fila -> fila.put("metodo", fila.remove("_id")));
		return filas;
	}

	/** Pagos más recientes primero, con el nombre del cliente. */
	public List<Document> listar(String clienteId, String estado) {
		Document filtro = new Document();
		if (clienteId != null && !clienteId.isBlank()) {
			filtro.append("cliente_id", clienteId);
		}
		if (estado != null && !estado.isBlank()) {
			filtro.append("estado", estado);
		}
		return reportes.pagosRecientes(filtro, 300);
	}

	/**
	 * RF-34 y RF-35 en una transacción multi-documento. Se inserta el pago y,
	 * según el resultado, se cambia el paquete en el cliente:
	 * <ul>
	 * <li>rechazado: el paquete queda suspendido (RF-35);</li>
	 * <li>aprobado sobre un paquete suspendido: el paquete se reactiva.</li>
	 * </ul>
	 * Si cualquiera de las dos escrituras falla, ninguna queda guardada. Solo
	 * puede haber un pago aprobado por paquete y por mes.
	 */
	@Transactional
	public Map<String, Object> registrar(PagoRequest datos, UsuarioAutenticado administrador) {
		Cliente cliente = clientes.obtener(datos.clienteId());
		if ("inactivo".equals(cliente.estado())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "El cliente está inactivo");
		}
		Cliente.PaqueteContratado contratado = cliente.paquetesContratados() == null ? null
				: cliente.paquetesContratados().stream()
					.filter(p -> p.paqueteId().equals(datos.paqueteId()))
					.findFirst()
					.orElse(null);
		if (contratado == null) {
			throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Revise los datos del formulario",
					Map.of("paquete_id", "El cliente no tiene contratado este paquete"));
		}
		if ("vencido".equals(contratado.estado())) {
			throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Revise los datos del formulario",
					Map.of("paquete_id", "El contrato de este paquete está vencido"));
		}

		boolean rechazado = "rechazado".equals(datos.estado());
		String motivo = Textos.limpio(datos.motivoRechazo());
		if (rechazado && motivo == null) {
			throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Revise los datos del formulario",
					Map.of("motivo_rechazo", "Escriba por qué se rechazó el pago"));
		}
		boolean yaPagado = mongo.exists(query(where("cliente_id").is(cliente.id()).and("paquete_id").is(datos.paqueteId())
			.and("periodo").is(datos.periodo()).and("estado").is("aprobado")), "pagos");
		if (yaPagado) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "Ese mes ya está pagado",
					Map.of("periodo", "Ya hay un pago aprobado de " + contratado.nombre() + " para este mes"));
		}

		Pago pago = new Pago(
				contadores.siguiente("pagos", "PAG", 4),
				cliente.id(),
				datos.paqueteId(),
				datos.periodo(),
				contratado.precioMensual(),
				datos.metodo(),
				datos.estado(),
				Textos.limpio(datos.referencia()),
				rechazado ? motivo : null,
				administrador.id(),
				Instant.now());
		mongo.insert(pago);

		String estadoPaquete = contratado.estado();
		if (rechazado && "activo".equals(estadoPaquete)) {
			estadoPaquete = "suspendido";
		}
		else if (!rechazado && "suspendido".equals(estadoPaquete)) {
			estadoPaquete = "activo";
		}
		if (!estadoPaquete.equals(contratado.estado())) {
			// El $ apunta al elemento de paquetes_contratados que cumplió el filtro
			mongo.updateFirst(
					query(where("_id").is(cliente.id()).and("paquetes_contratados.paquete_id").is(datos.paqueteId())),
					Update.update("paquetes_contratados.$.estado", estadoPaquete), "clientes");
			clientes.ajustarEstado(cliente.id());
		}

		Map<String, Object> respuesta = new LinkedHashMap<>();
		respuesta.put("pago", pago);
		respuesta.put("estado_paquete", estadoPaquete);
		respuesta.put("estado_cliente", clientes.obtener(cliente.id()).estado());
		return respuesta;
	}

}
