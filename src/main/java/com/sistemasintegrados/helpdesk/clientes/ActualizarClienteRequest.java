package com.sistemasintegrados.helpdesk.clientes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** RF-12: datos de contacto y dirección. El tipo, el documento y el nombre no cambian. */
public record ActualizarClienteRequest(
		@Pattern(regexp = "[A-Za-zÁÉÍÓÚáéíóúÑñ ]*", message = "El nombre del representante solo puede tener letras")
		String representante,

		@NotBlank(message = "Escriba el correo")
		@Pattern(regexp = "[A-Za-z0-9._-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", message = "Correo no válido")
		String correo,

		@NotBlank(message = "Escriba el teléfono")
		@Pattern(regexp = "[0-9]{10}", message = "El teléfono debe tener 10 dígitos")
		String telefono,

		@NotNull(message = "Escriba la dirección")
		@Valid
		RegistroRequest.Direccion direccion) {
}
