package com.sistemasintegrados.helpdesk.comun;

import java.security.SecureRandom;

/**
 * Contraseña inicial de las cuentas que crea el administrador (empleados y
 * clientes registrados desde el panel). El sistema la entrega una sola vez para
 * enviarla al correo del usuario; en la base solo queda el hash.
 */
public final class ClaveTemporal {

	// Sin letras ni números que se confundan entre sí (l, I, 1, O, 0)
	private static final String LETRAS = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";

	private static final String NUMEROS = "23456789";

	private static final SecureRandom AZAR = new SecureRandom();

	private ClaveTemporal() {
	}

	/** Diez caracteres con letras y números, como pide la regla de contraseñas del registro. */
	public static String generar() {
		StringBuilder clave = new StringBuilder();
		for (int i = 0; i < 7; i++) {
			clave.append(LETRAS.charAt(AZAR.nextInt(LETRAS.length())));
		}
		for (int i = 0; i < 2; i++) {
			clave.append(NUMEROS.charAt(AZAR.nextInt(NUMEROS.length())));
		}
		return clave.append('*').toString();
	}

}
