package com.sistemasintegrados.helpdesk.capacitaciones;

import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sistemasintegrados.helpdesk.auth.UsuarioAutenticado;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/capacitaciones")
public class CapacitacionController {

	private final CapacitacionService capacitaciones;

	public CapacitacionController(CapacitacionService capacitaciones) {
		this.capacitaciones = capacitaciones;
	}

	@GetMapping
	@PreAuthorize("hasAnyAuthority('capacitaciones:gestionar', 'capacitaciones:dictar', 'capacitaciones:inscribirse')")
	public List<Document> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return capacitaciones.listar(usuario);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyAuthority('capacitaciones:gestionar', 'capacitaciones:dictar')")
	public Map<String, Object> detalle(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
		return capacitaciones.detalle(id, usuario);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('capacitaciones:gestionar')")
	public Capacitacion programar(@Valid @RequestBody CapacitacionRequest datos) {
		return capacitaciones.programar(datos);
	}

	@PostMapping("/{id}/cancelacion")
	@PreAuthorize("hasAuthority('capacitaciones:gestionar')")
	public Capacitacion cancelar(@PathVariable String id) {
		return capacitaciones.cancelar(id);
	}

	@PostMapping("/{id}/inscripcion")
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('capacitaciones:inscribirse')")
	public Document inscribir(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
		return capacitaciones.inscribir(id, usuario);
	}

	@DeleteMapping("/{id}/inscripcion")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAuthority('capacitaciones:inscribirse')")
	public void cancelarInscripcion(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
		capacitaciones.cancelarInscripcion(id, usuario);
	}

	@PutMapping("/{id}/asistencia")
	@PreAuthorize("hasAuthority('capacitaciones:dictar')")
	public Map<String, Object> registrarAsistencia(@PathVariable String id, @Valid @RequestBody AsistenciaRequest datos,
			@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return capacitaciones.registrarAsistencia(id, datos, usuario);
	}

}
