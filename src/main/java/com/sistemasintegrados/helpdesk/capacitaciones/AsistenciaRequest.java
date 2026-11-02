package com.sistemasintegrados.helpdesk.capacitaciones;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** RF-40: el instructor registra quién asistió y la calificación que dio cada asistente. */
public record AsistenciaRequest(
		@NotEmpty(message = "No hay asistentes para registrar")
		List<@Valid Registro> asistencias) {

	/**
	 * @param calificacion de 1 a 5; solo para quien asistió
	 */
	public record Registro(
			@NotBlank(message = "Falta el usuario")
			@Pattern(regexp = "USR-[0-9]{3,}", message = "Código de usuario no válido")
			String usuarioId,

			@NotBlank(message = "Seleccione si asistió")
			@Pattern(regexp = "asistio|no_asistio", message = "Estado no válido")
			String estado,

			@Min(value = 1, message = "La calificación va de 1 a 5")
			@Max(value = 5, message = "La calificación va de 1 a 5")
			Integer calificacion,

			@Size(max = 300, message = "Máximo 300 caracteres")
			String comentario) {
	}

}
