package com.sistemasintegrados.helpdesk.tickets;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * RF-20. Los campos de detalle dependen del paquete: TicketService los revisa
 * contra paquetes.campos_ticket.
 */
public record CrearTicketRequest(
		@NotBlank(message = "Seleccione el paquete")
		@Pattern(regexp = "VENTAS_ONLINE|INVENTARIOS|COMPRAS|PRODUCCION|POS", message = "Paquete no válido")
		String paquete,

		@NotBlank(message = "Seleccione el tipo")
		@Pattern(regexp = "incidente|consulta|mejora", message = "Tipo no válido")
		String tipo,

		@NotBlank(message = "Seleccione la prioridad")
		@Pattern(regexp = "baja|media|alta", message = "Prioridad no válida")
		String prioridad,

		@NotBlank(message = "Escriba el asunto")
		@Size(max = 150, message = "Máximo 150 caracteres")
		String asunto,

		@NotBlank(message = "Describa el problema")
		@Size(max = 3000, message = "Máximo 3000 caracteres")
		String descripcion,

		Map<String, Object> detalle) {
}
