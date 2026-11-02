package com.sistemasintegrados.helpdesk.comun;

public final class Textos {

	private Textos() {
	}

	/**
	 * Texto vacío o con solo espacios pasa a null. Spring no escribe los campos
	 * null, así que el campo opcional no queda en el documento (un "" no
	 * cumpliría los pattern del $jsonSchema).
	 */
	public static String limpio(String texto) {
		return texto == null || texto.isBlank() ? null : texto.trim();
	}

	/** Correos en minúsculas, como exige el pattern de 01_esquema.js. */
	public static String correo(String correo) {
		return correo == null ? null : correo.trim().toLowerCase();
	}

}
