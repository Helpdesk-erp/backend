package com.sistemasintegrados.helpdesk.conocimiento;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** RF-31. El paquete y el cliente se toman del ticket. */
public record ArticuloRequest(
		@NotBlank(message = "Indique el ticket")
		@Pattern(regexp = "TCK-[0-9]{4,}", message = "Código de ticket no válido")
		String ticketId,

		@NotBlank(message = "Escriba el título")
		@Size(max = 150, message = "Máximo 150 caracteres")
		String titulo,

		@NotBlank(message = "Describa el problema")
		@Size(max = 3000, message = "Máximo 3000 caracteres")
		String problema,

		@NotEmpty(message = "Escriba al menos un paso")
		@Size(max = 30, message = "Máximo 30 pasos")
		List<@NotBlank(message = "El paso no puede quedar vacío") String> pasos,

		@NotBlank(message = "Escriba la solución")
		@Size(max = 3000, message = "Máximo 3000 caracteres")
		String solucion,

		@Size(max = 15, message = "Máximo 15 palabras clave")
		List<String> palabrasClave,

		@Size(max = 120, message = "Máximo 120 caracteres")
		String contacto) {
}
