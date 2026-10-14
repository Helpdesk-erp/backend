package com.sistemasintegrados.helpdesk.auth;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Un inicio de sesión. Su _id (ObjectId) viaja en el token como claim "jti".
 */
@Document("sesiones")
public record Sesion(
		@Id String id,
		String usuarioId,
		String rol,
		String ip,
		String dispositivo,
		Instant fechaInicio,
		Instant fechaExpiracion,
		Instant fechaCierre,
		String estado) {
}
