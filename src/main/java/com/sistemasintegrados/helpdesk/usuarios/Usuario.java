package com.sistemasintegrados.helpdesk.usuarios;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Cuenta de acceso. Pertenece a un cliente (clienteId) o a un empleado
 * (empleadoId), nunca a los dos. Los nombres de campo pasan a snake_case en
 * MongoDB (passwordHash -> password_hash).
 */
@Document("usuarios")
public record Usuario(
		@Id String id,
		String usuario,
		String correo,
		String passwordHash,
		List<String> roles,
		String clienteId,
		String empleadoId,
		String estado,
		Boolean credencialesEnviadas,
		Instant ultimoAcceso,
		Instant fechaCreacion) {

	public boolean activo() {
		return "activo".equals(estado);
	}

}
