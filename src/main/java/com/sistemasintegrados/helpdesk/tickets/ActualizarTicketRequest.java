package com.sistemasintegrados.helpdesk.tickets;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** RF-22: lo que el cliente puede corregir mientras el ticket sigue sin atender. */
public record ActualizarTicketRequest(
		@NotBlank(message = "Escriba el asunto")
		@Size(max = 150, message = "Máximo 150 caracteres")
		String asunto,

		@NotBlank(message = "Describa el problema")
		@Size(max = 3000, message = "Máximo 3000 caracteres")
		String descripcion,

		@NotBlank(message = "Seleccione la prioridad")
		@Pattern(regexp = "baja|media|alta", message = "Prioridad no válida")
		String prioridad) {
}
