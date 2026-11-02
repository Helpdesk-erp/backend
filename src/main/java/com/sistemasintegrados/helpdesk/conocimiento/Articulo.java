package com.sistemasintegrados.helpdesk.conocimiento;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Qué hizo soporte para resolver un ticket, paso a paso.
 *
 * @param contacto persona del cliente que reportó el problema
 */
@Document("base_conocimiento")
public record Articulo(
		@Id String id,
		String ticketId,
		String clienteId,
		String contacto,
		String paquete,
		String titulo,
		String problema,
		List<String> pasos,
		String solucion,
		List<String> palabrasClave,
		String autorId,
		Instant fechaCreacion) {
}
