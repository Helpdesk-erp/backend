package com.sistemasintegrados.helpdesk.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * @param usuario nombre de usuario o correo
 */
public record LoginRequest(
		@NotBlank(message = "Escriba su usuario o correo") String usuario,
		@NotBlank(message = "Escriba la contraseña") String password) {
}
