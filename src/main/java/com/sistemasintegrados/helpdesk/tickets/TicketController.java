package com.sistemasintegrados.helpdesk.tickets;

import java.util.List;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sistemasintegrados.helpdesk.auth.UsuarioAutenticado;

import jakarta.validation.Valid;

/**
 * El permiso decide qué operación se puede pedir; TicketService revisa además
 * que el ticket sea del cliente o esté asignado al agente que lo pide.
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

	private final TicketService tickets;

	public TicketController(TicketService tickets) {
		this.tickets = tickets;
	}

	@GetMapping
	@PreAuthorize("hasAnyAuthority('tickets:ver_todos', 'tickets:ver_asignados', 'tickets:ver_propios')")
	public List<Document> listar(@AuthenticationPrincipal UsuarioAutenticado usuario,
			@RequestParam(required = false) String estado, @RequestParam(required = false) String paquete) {
		return tickets.listar(usuario, estado, paquete);
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyAuthority('tickets:ver_todos', 'tickets:ver_asignados', 'tickets:ver_propios')")
	public Document detalle(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
		return tickets.detalle(id, usuario);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('tickets:crear')")
	public Document crear(@Valid @RequestBody CrearTicketRequest datos, @AuthenticationPrincipal UsuarioAutenticado usuario) {
		return tickets.crear(datos, usuario);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('tickets:crear')")
	public Document actualizar(@PathVariable String id, @Valid @RequestBody ActualizarTicketRequest datos,
			@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return tickets.actualizar(id, datos, usuario);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@PreAuthorize("hasAuthority('tickets:crear')")
	public void eliminar(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
		tickets.eliminar(id, usuario);
	}

	@PostMapping("/{id}/asignacion")
	@PreAuthorize("hasAuthority('tickets:asignar')")
	public Document asignar(@PathVariable String id, @Valid @RequestBody AsignarTicketRequest datos,
			@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return tickets.asignar(id, datos, usuario);
	}

	@PostMapping("/{id}/estado")
	@PreAuthorize("hasAuthority('tickets:cambiar_estado')")
	public Document cambiarEstado(@PathVariable String id, @Valid @RequestBody CambioEstadoRequest datos,
			@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return tickets.cambiarEstado(id, datos, usuario);
	}

	@PutMapping("/{id}/solucion")
	@PreAuthorize("hasAuthority('tickets:cambiar_estado')")
	public Document registrarSolucion(@PathVariable String id, @Valid @RequestBody SolucionRequest datos,
			@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return tickets.registrarSolucion(id, datos, usuario);
	}

	@PostMapping("/{id}/cierre")
	@PreAuthorize("hasRole('cliente')")
	public Document cerrar(@PathVariable String id, @Valid @RequestBody(required = false) ComentarioRequest datos,
			@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return tickets.cerrar(id, datos == null ? null : datos.comentario(), usuario);
	}

}
