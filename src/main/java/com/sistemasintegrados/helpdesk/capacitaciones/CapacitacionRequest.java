package com.sistemasintegrados.helpdesk.capacitaciones;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * RF-38. Las fechas de inicio y fin de la capacitación se calculan con las
 * sesiones del cronograma.
 */
public record CapacitacionRequest(
		@NotBlank(message = "Escriba el título")
		@Size(max = 150, message = "Máximo 150 caracteres")
		String titulo,

		@NotBlank(message = "Escriba la descripción")
		@Size(max = 1000, message = "Máximo 1000 caracteres")
		String descripcion,

		@NotBlank(message = "Seleccione el paquete")
		@Pattern(regexp = "VENTAS_ONLINE|INVENTARIOS|COMPRAS|PRODUCCION|POS", message = "Paquete no válido")
		String paquete,

		@NotBlank(message = "Seleccione el tipo")
		@Pattern(regexp = "masiva|personalizada", message = "Tipo no válido")
		String tipo,

		@Pattern(regexp = "(CLI-[0-9]{3,})?", message = "Código de cliente no válido")
		String clienteId,

		@NotBlank(message = "Seleccione la modalidad")
		@Pattern(regexp = "virtual|presencial", message = "Modalidad no válida")
		String modalidad,

		String enlace,

		@Size(max = 200, message = "Máximo 200 caracteres")
		String lugar,

		@NotBlank(message = "Seleccione el instructor")
		@Pattern(regexp = "USR-[0-9]{3,}", message = "Código de usuario no válido")
		String instructorId,

		@NotNull(message = "Escriba el cupo")
		@Min(value = 1, message = "Mínimo 1 cupo")
		@Max(value = 500, message = "Máximo 500 cupos")
		Integer cupoMaximo,

		@NotEmpty(message = "Agregue al menos una sesión")
		@Size(max = 20, message = "Máximo 20 sesiones")
		List<@Valid Sesion> sesiones) {

	public record Sesion(
			@NotBlank(message = "Escriba el tema") @Size(max = 150, message = "Máximo 150 caracteres") String tema,
			@NotNull(message = "Escriba el inicio") Instant fechaInicio,
			@NotNull(message = "Escriba el fin") Instant fechaFin) {
	}

}
