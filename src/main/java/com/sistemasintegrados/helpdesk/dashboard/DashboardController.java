package com.sistemasintegrados.helpdesk.dashboard;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemasintegrados.helpdesk.auth.UsuarioAutenticado;

/**
 * Un endpoint por rol. hasRole('x') revisa la autoridad ROLE_x, que se arma
 * con el rol de la sesión.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	private final DashboardService dashboards;

	public DashboardController(DashboardService dashboards) {
		this.dashboards = dashboards;
	}

	@GetMapping("/administrador")
	@PreAuthorize("hasRole('administrador')")
	public Map<String, Object> administrador() {
		return dashboards.administrador();
	}

	@GetMapping("/agente")
	@PreAuthorize("hasRole('agente_soporte')")
	public Map<String, Object> agente(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return dashboards.agente(usuario);
	}

	@GetMapping("/cliente")
	@PreAuthorize("hasRole('cliente')")
	public Map<String, Object> cliente(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return dashboards.cliente(usuario);
	}

	@GetMapping("/auditor")
	@PreAuthorize("hasRole('auditor')")
	public Map<String, Object> auditor() {
		return dashboards.auditor();
	}

}
