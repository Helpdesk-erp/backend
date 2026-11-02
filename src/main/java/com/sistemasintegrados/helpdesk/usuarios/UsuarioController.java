package com.sistemasintegrados.helpdesk.usuarios;

import java.util.List;

import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sistemasintegrados.helpdesk.auth.UsuarioAutenticado;
import com.sistemasintegrados.helpdesk.reportes.ReportesService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

	private final UsuarioService usuarios;

	private final ReportesService reportes;

	public UsuarioController(UsuarioService usuarios, ReportesService reportes) {
		this.usuarios = usuarios;
		this.reportes = reportes;
	}

	@GetMapping
	@PreAuthorize("hasAuthority('usuarios:gestionar')")
	public List<Document> listar() {
		return usuarios.listar(new Document());
	}

	/** Agentes activos con su carga, para asignar tickets o escoger instructor. */
	@GetMapping("/agentes")
	@PreAuthorize("hasAnyAuthority('tickets:asignar', 'capacitaciones:gestionar')")
	public List<Document> agentes() {
		return reportes.cargaPorAgente();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('usuarios:gestionar')")
	public CuentaCreada crear(@Valid @RequestBody CrearUsuarioRequest datos) {
		return usuarios.crearParaEmpleado(datos);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('usuarios:gestionar')")
	public Document actualizar(@PathVariable String id, @Valid @RequestBody ActualizarUsuarioRequest datos,
			@AuthenticationPrincipal UsuarioAutenticado quien) {
		return usuarios.actualizar(id, datos, quien);
	}

	@PatchMapping("/{id}/estado")
	@PreAuthorize("hasAuthority('usuarios:gestionar')")
	public Document cambiarEstado(@PathVariable String id, @Valid @RequestBody EstadoUsuarioRequest datos,
			@AuthenticationPrincipal UsuarioAutenticado quien) {
		return usuarios.cambiarEstado(id, datos.estado(), quien);
	}

	@PatchMapping("/{id}/credenciales-enviadas")
	@PreAuthorize("hasAuthority('usuarios:gestionar')")
	public Document marcarCredencialesEnviadas(@PathVariable String id) {
		return usuarios.marcarCredencialesEnviadas(id);
	}

}
