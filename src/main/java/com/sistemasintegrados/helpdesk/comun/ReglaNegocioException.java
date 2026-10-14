package com.sistemasintegrados.helpdesk.comun;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Error que el usuario puede corregir: datos que no cuadran, recurso que no
 * existe, cuenta bloqueada. El manejador global lo convierte en JSON.
 */
public class ReglaNegocioException extends RuntimeException {

	private final HttpStatus estado;

	private final Map<String, String> errores;

	public ReglaNegocioException(HttpStatus estado, String mensaje) {
		this(estado, mensaje, null);
	}

	/**
	 * @param errores mensaje por campo del formulario (clave en snake_case)
	 */
	public ReglaNegocioException(HttpStatus estado, String mensaje, Map<String, String> errores) {
		super(mensaje);
		this.estado = estado;
		this.errores = errores;
	}

	public HttpStatus getEstado() {
		return estado;
	}

	public Map<String, String> getErrores() {
		return errores;
	}

}
