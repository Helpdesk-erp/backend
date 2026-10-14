package com.sistemasintegrados.helpdesk.auth;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Autenticación que queda en el contexto de Spring Security después de validar
 * el token contra la colección sesiones.
 */
public class SesionAutenticada extends AbstractAuthenticationToken {

	private final UsuarioAutenticado usuario;

	private final Jwt token;

	public SesionAutenticada(UsuarioAutenticado usuario, Jwt token, Collection<? extends GrantedAuthority> permisos) {
		super(permisos);
		this.usuario = usuario;
		this.token = token;
		setAuthenticated(true);
	}

	@Override
	public Object getCredentials() {
		return token;
	}

	@Override
	public UsuarioAutenticado getPrincipal() {
		return usuario;
	}

	@Override
	public String getName() {
		return usuario.id();
	}

}
