package com.sistemasintegrados.helpdesk.usuarios;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** RF-08: bloquear o inactivar una cuenta (o volver a activarla). */
public record EstadoUsuarioRequest(
		@NotBlank(message = "Seleccione el estado")
		@Pattern(regexp = "activo|inactivo|bloqueado", message = "Estado no válido")
		String estado) {
}
