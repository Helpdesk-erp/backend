package com.sistemasintegrados.helpdesk.pagos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * RF-34 y RF-35. El valor no se envía: es el precio mensual con el que el
 * cliente contrató el paquete.
 */
public record PagoRequest(
		@NotBlank(message = "Seleccione el cliente")
		@Pattern(regexp = "CLI-[0-9]{3,}", message = "Código de cliente no válido")
		String clienteId,

		@NotBlank(message = "Seleccione el paquete")
		@Pattern(regexp = "VENTAS_ONLINE|INVENTARIOS|COMPRAS|PRODUCCION|POS", message = "Paquete no válido")
		String paqueteId,

		@NotBlank(message = "Seleccione el mes")
		@Pattern(regexp = "[0-9]{4}-(0[1-9]|1[0-2])", message = "Formato 2026-10")
		String periodo,

		@NotBlank(message = "Seleccione el método de pago")
		@Pattern(regexp = "PSE|tarjeta_credito|tarjeta_debito|transferencia|efectivo", message = "Método no válido")
		String metodo,

		@NotBlank(message = "Seleccione el resultado")
		@Pattern(regexp = "aprobado|rechazado", message = "El pago queda aprobado o rechazado")
		String estado,

		@Size(max = 60, message = "Máximo 60 caracteres")
		String referencia,

		@Size(max = 200, message = "Máximo 200 caracteres")
		String motivoRechazo) {
}
