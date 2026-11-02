package com.sistemasintegrados.helpdesk.clientes;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * RF-11: el administrador registra un cliente. Son los mismos datos del
 * registro propio (RegistroRequest) sin la contraseña: el sistema genera una
 * temporal para la cuenta del cliente.
 */
public record ClienteRequest(
		@NotBlank(message = "Seleccione el tipo de cliente")
		@Pattern(regexp = "persona_natural|empresa|persona_juridica", message = "Tipo de cliente no válido")
		String tipoCliente,

		@NotBlank(message = "Seleccione el tipo de documento")
		@Pattern(regexp = "CC|CE|NIT", message = "Tipo de documento no válido")
		String tipoDocumento,

		@NotBlank(message = "Escriba el número de documento")
		@Pattern(regexp = "[0-9]{6,10}", message = "Solo números, entre 6 y 10 dígitos")
		String numeroDocumento,

		@NotBlank(message = "Escriba el nombre")
		@Size(max = 120, message = "Máximo 120 caracteres")
		String nombre,

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
		RegistroRequest.Direccion direccion,

		@NotBlank(message = "Escriba el nombre de usuario")
		@Pattern(regexp = "[a-z0-9._]{4,30}", message = "Entre 4 y 30 caracteres: minúsculas, números, punto o guion bajo")
		String usuario) {
}
