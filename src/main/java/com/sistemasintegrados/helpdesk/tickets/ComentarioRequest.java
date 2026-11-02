package com.sistemasintegrados.helpdesk.tickets;

import jakarta.validation.constraints.Size;

/** Comentario opcional que queda en el historial (por ejemplo, al confirmar la solución). */
public record ComentarioRequest(
		@Size(max = 300, message = "Máximo 300 caracteres")
		String comentario) {
}
