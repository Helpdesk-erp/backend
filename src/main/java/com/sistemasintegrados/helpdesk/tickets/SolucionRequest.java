package com.sistemasintegrados.helpdesk.tickets;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** RF-26. */
public record SolucionRequest(
		@NotBlank(message = "Escriba la solución")
		@Size(max = 3000, message = "Máximo 3000 caracteres")
		String solucion) {
}
