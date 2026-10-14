package com.sistemasintegrados.helpdesk.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades propias de la aplicación (prefijo "app" en application.properties).
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors) {

	/**
	 * @param secreto       clave HMAC para firmar los tokens, mínimo 32 caracteres
	 * @param duracionHoras horas que dura una sesión antes de expirar
	 */
	public record Jwt(String secreto, int duracionHoras) {
	}

	/**
	 * @param origenes direcciones desde las que el navegador puede llamar a la API
	 */
	public record Cors(List<String> origenes) {
	}

}
