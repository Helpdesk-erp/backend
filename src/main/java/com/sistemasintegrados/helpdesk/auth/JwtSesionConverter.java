package com.sistemasintegrados.helpdesk.auth;

import java.util.ArrayList;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import com.sistemasintegrados.helpdesk.roles.RolService;
import com.sistemasintegrados.helpdesk.usuarios.Usuario;
import com.sistemasintegrados.helpdesk.usuarios.UsuarioRepository;

/**
 * Se ejecuta en cada petición, después de que Spring verifica la firma y la
 * fecha del token. Revisa en MongoDB que la sesión siga activa y la cuenta no
 * esté bloqueada. Así, cerrar sesión o bloquear un usuario tiene efecto
 * inmediato, aunque el token todavía no haya expirado.
 */
@Component
public class JwtSesionConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	private final SesionRepository sesiones;

	private final UsuarioRepository usuarios;

	private final RolService roles;

	public JwtSesionConverter(SesionRepository sesiones, UsuarioRepository usuarios, RolService roles) {
		this.sesiones = sesiones;
		this.usuarios = usuarios;
		this.roles = roles;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		Sesion sesion = jwt.getId() == null ? null : sesiones.findById(jwt.getId()).orElse(null);
		if (sesion == null || !"activa".equals(sesion.estado()) || !sesion.usuarioId().equals(jwt.getSubject())) {
			throw new SesionNoValidaException("La sesión fue cerrada. Inicie sesión de nuevo");
		}

		Usuario usuario = usuarios.findById(jwt.getSubject()).orElse(null);
		if (usuario == null || !usuario.activo()) {
			throw new SesionNoValidaException("La cuenta está bloqueada o inactiva");
		}

		// Autoridades: ROLE_<rol> para hasRole(...) y cada permiso de la colección roles para hasAuthority(...)
		List<GrantedAuthority> autoridades = new ArrayList<>();
		autoridades.add(new SimpleGrantedAuthority("ROLE_" + sesion.rol()));
		roles.permisos(sesion.rol()).forEach(permiso -> autoridades.add(new SimpleGrantedAuthority(permiso)));

		UsuarioAutenticado principal = new UsuarioAutenticado(usuario.id(), usuario.usuario(), sesion.rol(),
				usuario.clienteId(), usuario.empleadoId(), sesion.id());
		return new SesionAutenticada(principal, jwt, autoridades);
	}

}
