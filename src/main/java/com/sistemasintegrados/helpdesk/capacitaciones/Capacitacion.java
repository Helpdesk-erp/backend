package com.sistemasintegrados.helpdesk.capacitaciones;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Capacitación masiva (para todos los clientes del paquete) o personalizada
 * (para un cliente). El cronograma va embebido en sesiones.
 *
 * @param clienteId   solo en las personalizadas
 * @param enlace      solo en las virtuales
 * @param lugar       solo en las presenciales
 * @param fechaInicio inicio de la primera sesión
 * @param fechaFin    fin de la última sesión
 */
@Document("capacitaciones")
public record Capacitacion(
		@Id String id,
		String titulo,
		String descripcion,
		String paquete,
		String tipo,
		String clienteId,
		String modalidad,
		String enlace,
		String lugar,
		String instructorId,
		Integer cupoMaximo,
		List<Sesion> sesiones,
		Instant fechaInicio,
		Instant fechaFin,
		String estado,
		Instant fechaCreacion) {

	public record Sesion(String tema, Instant fechaInicio, Instant fechaFin) {
	}

}
