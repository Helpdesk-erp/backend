package com.sistemasintegrados.helpdesk.clientes;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.sistemasintegrados.helpdesk.comun.ReglaNegocioException;

/**
 * Reglas del anyOf de clientes en 01_esquema.js: persona natural con CC o CE;
 * empresa o persona jurídica con NIT y representante. Las usan el registro
 * del propio cliente (RF-05) y el del administrador (RF-11).
 */
final class ReglasCliente {

	private ReglasCliente() {
	}

	static void validar(String tipoCliente, String tipoDocumento, String representante) {
		boolean personaNatural = "persona_natural".equals(tipoCliente);
		Map<String, String> errores = new LinkedHashMap<>();
		if (personaNatural && "NIT".equals(tipoDocumento)) {
			errores.put("tipo_documento", "Una persona natural se registra con CC o CE");
		}
		if (!personaNatural && !"NIT".equals(tipoDocumento)) {
			errores.put("tipo_documento", "Una empresa o persona jurídica se registra con NIT");
		}
		if (!personaNatural && representante == null) {
			errores.put("representante", "Escriba el nombre del propietario o representante legal");
		}
		if (!errores.isEmpty()) {
			throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Revise los datos del formulario", errores);
		}
	}

}
