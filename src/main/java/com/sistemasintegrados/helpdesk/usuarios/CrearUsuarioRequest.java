package com.sistemasintegrados.helpdesk.usuarios;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * RF-06: cuenta de un empleado con un rol interno. La contraseña la genera el
 * sistema (ver ClaveTemporal).
 */
public record CrearUsuarioRequest(
		@NotBlank(message = "Seleccione el empleado")
		@Pattern(regexp = "EMP-[0-9]{3,}", message = "Código de empleado no válido")
		String empleadoId,

		@NotBlank(message = "Escriba el nombre de usuario")
		@Pattern(regexp = "[a-z0-9._]{4,30}", message = "Entre 4 y 30 caracteres: minúsculas, números, punto o guion bajo")
		String usuario,

		@NotBlank(message = "Escriba el correo")
		@Pattern(regexp = "[A-Za-z0-9._-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", message = "Correo no válido")
		String correo,

		@NotBlank(message = "Seleccione el rol")
		@Pattern(regexp = "administrador|auditor|agente_soporte", message = "Un empleado solo puede tener un rol interno")
		String rol) {
}
