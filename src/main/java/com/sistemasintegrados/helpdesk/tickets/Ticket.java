package com.sistemasintegrados.helpdesk.tickets;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Caso reportado por un cliente. detalle cambia según el paquete (documento
 * polimórfico) y historial guarda cada cambio de estado.
 *
 * @param detalle     datos técnicos que pide paquetes.campos_ticket
 * @param agenteId    usuario de soporte que lo atiende
 * @param asignadoPor administrador que lo asignó
 */
@Document("tickets")
public record Ticket(
		@Id String id,
		String clienteId,
		String reportadoPor,
		String paquete,
		String tipo,
		String prioridad,
		String asunto,
		String descripcion,
		Map<String, Object> detalle,
		String estado,
		String agenteId,
		String asignadoPor,
		String solucion,
		List<Evento> historial,
		Instant fechaCreacion,
		Instant fechaActualizacion) {

	public record Evento(Instant fecha, String estado, String usuarioId, String comentario) {
	}

}
