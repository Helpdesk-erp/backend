package com.sistemasintegrados.helpdesk.clientes;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Agrega un paquete a paquetes_contratados. El nombre y el precio se copian del
 * catálogo, así el cliente conserva el precio con el que contrató.
 */
public record ContratoPaqueteRequest(
		@NotBlank(message = "Seleccione el paquete")
		@Pattern(regexp = "VENTAS_ONLINE|INVENTARIOS|COMPRAS|PRODUCCION|POS", message = "Paquete no válido")
		String paqueteId,

		@NotNull(message = "Escriba la fecha de inicio") LocalDate fechaInicio,

		@NotNull(message = "Escriba la fecha de terminación") LocalDate fechaFin) {
}
