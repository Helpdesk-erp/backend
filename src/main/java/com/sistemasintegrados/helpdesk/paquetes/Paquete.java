package com.sistemasintegrados.helpdesk.paquetes;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Paquete del ERP. camposTicket dice qué datos técnicos pide el formulario de
 * un ticket de este paquete; el frontend lo arma a partir de esta lista.
 */
@Document("paquetes")
public record Paquete(
		@Id String id,
		String nombre,
		String descripcion,
		Long precioMensual,
		Boolean activo,
		List<CampoTicket> camposTicket) {

	/**
	 * @param tipo texto, texto_largo, numero, lista o url
	 */
	public record CampoTicket(
			String campo,
			String etiqueta,
			String tipo,
			List<String> opciones,
			Boolean requerido,
			String ayuda) {
	}

}
