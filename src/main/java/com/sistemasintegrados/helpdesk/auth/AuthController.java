package com.sistemasintegrados.helpdesk.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sistemasintegrados.helpdesk.clientes.RegistroClienteService;
import com.sistemasintegrados.helpdesk.clientes.RegistroRequest;
import com.sistemasintegrados.helpdesk.usuarios.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService auth;

	private final RegistroClienteService registro;

	public AuthController(AuthService auth, RegistroClienteService registro) {
		this.auth = auth;
		this.registro = registro;
	}

	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest datos, HttpServletRequest peticion) {
		return auth.login(datos, peticion.getRemoteAddr(), peticion.getHeader(HttpHeaders.USER_AGENT));
	}

	/** Crea el cliente y su usuario en una transacción y deja la sesión iniciada. */
	@PostMapping("/registro")
	@ResponseStatus(HttpStatus.CREATED)
	public LoginResponse registro(@Valid @RequestBody RegistroRequest datos, HttpServletRequest peticion) {
		Usuario usuario = registro.registrar(datos);
		return auth.abrirSesion(usuario, "cliente", peticion.getRemoteAddr(), peticion.getHeader(HttpHeaders.USER_AGENT));
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		auth.cerrarSesion(usuario.sesionId());
	}

	@GetMapping("/me")
	public UsuarioActual yo(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return auth.usuarioActual(usuario);
	}

}
