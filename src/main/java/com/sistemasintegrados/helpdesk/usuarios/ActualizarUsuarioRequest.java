package com.sistemasintegrados.helpdesk.usuarios;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** RF-07: correo y rol de una cuenta existente. */
public record ActualizarUsuarioRequest(
		@NotBlank(message = "Escriba el correo")
		@Pattern(regexp = "[A-Za-z0-9._-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", message = "Correo no válido")
		String correo,

		@NotBlank(message = "Seleccione el rol")
		@Pattern(regexp = "administrador|auditor|agente_soporte|cliente", message = "Rol no válido")
		String rol) {
}
