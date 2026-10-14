package com.sistemasintegrados.helpdesk.auth;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Respuesta 401 en JSON, con el mismo formato que ErrorApi.
 */
@Component
public class RespuestaNoAutenticado implements AuthenticationEntryPoint {

	@Override
	public void commence(HttpServletRequest peticion, HttpServletResponse respuesta, AuthenticationException error)
			throws IOException {
		String mensaje;
		if (error instanceof SesionNoValidaException) {
			mensaje = error.getMessage();
		}
		else if (peticion.getHeader(HttpHeaders.AUTHORIZATION) == null) {
			mensaje = "Debe iniciar sesión";
		}
		else {
			mensaje = "La sesión expiró o el token no es válido. Inicie sesión de nuevo";
		}
		respuesta.setStatus(HttpStatus.UNAUTHORIZED.value());
		respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
		respuesta.setCharacterEncoding("UTF-8");
		respuesta.getWriter().write("{\"mensaje\":\"" + mensaje.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
	}

}
