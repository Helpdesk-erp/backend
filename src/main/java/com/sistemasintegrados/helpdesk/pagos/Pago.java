package com.sistemasintegrados.helpdesk.pagos;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Pago mensual de un paquete contratado.
 *
 * @param periodo       mes que paga, formato 2026-10
 * @param motivoRechazo obligatorio cuando el estado es rechazado
 * @param registradoPor administrador que lo registró
 */
@Document("pagos")
public record Pago(
		@Id String id,
		String clienteId,
		String paqueteId,
		String periodo,
		Long valor,
		String metodo,
		String estado,
		String referencia,
		String motivoRechazo,
		String registradoPor,
		Instant fecha) {
}
