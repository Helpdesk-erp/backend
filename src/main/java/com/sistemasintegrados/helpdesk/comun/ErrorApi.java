package com.sistemasintegrados.helpdesk.comun;

import java.util.Map;

/**
 * Cuerpo de todas las respuestas de error.
 *
 * @param mensaje texto para mostrar al usuario
 * @param errores mensaje por campo del formulario, cuando aplica
 * @param detalle información técnica (por ejemplo, la regla del $jsonSchema que falló)
 */
public record ErrorApi(String mensaje, Map<String, String> errores, Object detalle) {

	public static ErrorApi de(String mensaje) {
		return new ErrorApi(mensaje, null, null);
	}

}
