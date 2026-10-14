package com.sistemasintegrados.helpdesk.clientes;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Cliente con los paquetes que contrató embebidos. Cada paquete conserva el
 * precio con el que se vendió.
 */
@Document("clientes")
public record Cliente(
		@Id String id,
		String tipoCliente,
		String tipoDocumento,
		String numeroDocumento,
		String nombre,
		String representante,
		String correo,
		String telefono,
		Direccion direccion,
		List<PaqueteContratado> paquetesContratados,
		String estado,
		Instant fechaRegistro) {

	public record Direccion(String direccion, String ciudad, String departamento) {
	}

	public record PaqueteContratado(
			String paqueteId,
			String nombre,
			Long precioMensual,
			Instant fechaInicio,
			Instant fechaFin,
			String estado) {
	}

}
