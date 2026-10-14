package com.sistemasintegrados.helpdesk.auth;

import org.springframework.security.core.AuthenticationException;

/**
 * El token es válido pero la sesión o la cuenta ya no lo son.
 */
public class SesionNoValidaException extends AuthenticationException {

	public SesionNoValidaException(String mensaje) {
		super(mensaje);
	}

}
