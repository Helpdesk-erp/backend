package com.sistemasintegrados.helpdesk.empleados;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Formulario de registro y edición de un empleado. Las reglas son las del
 * $jsonSchema de empleados en 01_esquema.js.
 */
public record EmpleadoRequest(
		@NotBlank(message = "Escriba el número de documento")
		@Pattern(regexp = "[0-9]{6,10}", message = "Solo números, entre 6 y 10 dígitos")
		String numeroDocumento,

		@NotBlank(message = "Escriba los nombres")
		@Pattern(regexp = "[A-Za-zÁÉÍÓÚáéíóúÑñ ]+", message = "Solo letras")
		@Size(max = 60, message = "Máximo 60 caracteres")
		String nombres,

		@NotBlank(message = "Escriba los apellidos")
		@Pattern(regexp = "[A-Za-zÁÉÍÓÚáéíóúÑñ ]+", message = "Solo letras")
		@Size(max = 60, message = "Máximo 60 caracteres")
		String apellidos,

		@NotBlank(message = "Seleccione el sexo")
		@Pattern(regexp = "masculino|femenino|otro", message = "Valor no válido")
		String sexo,

		@NotBlank(message = "Seleccione el estado civil")
		@Pattern(regexp = "soltero|casado|union_libre|divorciado|viudo", message = "Valor no válido")
		String estadoCivil,

		@NotBlank(message = "Escriba la profesión")
		String profesion,

		@NotBlank(message = "Escriba el correo personal")
		@Pattern(regexp = "[A-Za-z0-9._-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", message = "Correo no válido")
		String correoPersonal,

		@NotBlank(message = "Escriba el celular")
		@Pattern(regexp = "[0-9]{10}", message = "El celular debe tener 10 dígitos")
		String telefonoMovil,

		@NotNull(message = "Escriba la dirección") @Valid Direccion direccion,

		@NotNull(message = "Complete el contrato") @Valid Contrato contrato,

		@NotNull(message = "Complete la seguridad social") @Valid SeguridadSocial seguridadSocial,

		@Valid InformacionBancaria informacionBancaria) {

	public record Direccion(
			@NotBlank(message = "Escriba la dirección") String direccion,
			String barrio,
			@NotBlank(message = "Escriba la ciudad") String ciudad,
			@NotBlank(message = "Seleccione el departamento") String departamento,
			@Pattern(regexp = "([0-9]{6})?", message = "El código postal tiene 6 dígitos") String codigoPostal) {
	}

	public record Contrato(
			@NotBlank(message = "Escriba el cargo") String cargo,

			@NotBlank(message = "Seleccione el área")
			@Pattern(regexp = "administracion|soporte|auditoria", message = "Área no válida")
			String area,

			@NotBlank(message = "Seleccione el tipo de contrato")
			@Pattern(regexp = "indefinido|termino_fijo|prestacion_servicios", message = "Tipo de contrato no válido")
			String tipoContrato,

			@NotNull(message = "Escriba la fecha de inicio") LocalDate fechaInicio,

			LocalDate fechaFin,

			@NotNull(message = "Escriba el salario") @PositiveOrZero(message = "El salario no puede ser negativo")
			Long salario,

			@Pattern(regexp = "(tiempo_completo|medio_tiempo)?", message = "Jornada no válida") String jornada,

			@Pattern(regexp = "(presencial|remoto|hibrido)?", message = "Modalidad no válida") String modalidad) {
	}

	public record SeguridadSocial(
			@NotBlank(message = "Escriba la EPS") String eps,
			@NotBlank(message = "Escriba el fondo de pensiones") String fondoPensiones,
			@NotBlank(message = "Escriba la ARL") String arl,
			String cajaCompensacion,
			String fondoCesantias) {
	}

	/** Opcional, pero si se llena va completa. */
	public record InformacionBancaria(
			String banco,
			@Pattern(regexp = "(ahorros|corriente)?", message = "Tipo de cuenta no válido") String tipoCuenta,
			@Pattern(regexp = "([0-9]{6,20})?", message = "Solo números, entre 6 y 20 dígitos") String numeroCuenta) {
	}

}
