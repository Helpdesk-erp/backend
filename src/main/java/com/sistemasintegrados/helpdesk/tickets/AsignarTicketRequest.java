package com.sistemasintegrados.helpdesk.tickets;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** RF-24. Si no se escribe comentario, el historial dice "Asignado a <agente>". */
public record AsignarTicketRequest(
		@NotBlank(message = "Seleccione el agente")
		@Pattern(regexp = "USR-[0-9]{3,}", message = "Código de usuario no válido")
		String agenteId,

		@Size(max = 300, message = "Máximo 300 caracteres")
		String comentario) {
}
