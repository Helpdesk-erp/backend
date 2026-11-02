package com.sistemasintegrados.helpdesk.empleados;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Empleado de Sistemas Integrados con sus datos personales, el contrato y la
 * seguridad social embebidos. Su cuenta de acceso está en usuarios (empleado_id).
 */
@Document("empleados")
public record Empleado(
		@Id String id,
		String numeroDocumento,
		String nombres,
		String apellidos,
		String sexo,
		String estadoCivil,
		String profesion,
		String correoPersonal,
		String telefonoMovil,
		Direccion direccion,
		Contrato contrato,
		SeguridadSocial seguridadSocial,
		InformacionBancaria informacionBancaria,
		String estado,
		Instant fechaCreacion,
		Instant fechaActualizacion) {

	public record Direccion(String direccion, String barrio, String ciudad, String departamento, String codigoPostal) {
	}

	/**
	 * @param fechaFin obligatoria si el contrato no es indefinido
	 */
	public record Contrato(
			String cargo,
			String area,
			String tipoContrato,
			Instant fechaInicio,
			Instant fechaFin,
			Long salario,
			String jornada,
			String modalidad) {
	}

	public record SeguridadSocial(String eps, String fondoPensiones, String arl, String cajaCompensacion,
			String fondoCesantias) {
	}

	public record InformacionBancaria(String banco, String tipoCuenta, String numeroCuenta) {
	}

	public String nombreCompleto() {
		return nombres + " " + apellidos;
	}

}
