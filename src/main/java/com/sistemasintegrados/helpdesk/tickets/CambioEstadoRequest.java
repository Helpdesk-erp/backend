package com.sistemasintegrados.helpdesk.tickets;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * RF-25. Para pasar a resuelto hace falta la solución: la que viene aquí o la
 * que el agente ya registró (RF-26).
 */
public record CambioEstadoRequest(
		@NotBlank(message = "Seleccione el estado")
		@Pattern(regexp = "en_proceso|resuelto", message = "El agente solo puede pasar el ticket a en proceso o resuelto")
		String estado,

		@Size(max = 300, message = "Máximo 300 caracteres")
		String comentario,

		@Size(max = 3000, message = "Máximo 3000 caracteres")
		String solucion) {
}
