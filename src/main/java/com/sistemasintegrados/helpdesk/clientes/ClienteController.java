package com.sistemasintegrados.helpdesk.clientes;

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
import com.sistemasintegrados.helpdesk.usuarios.CuentaCreada;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/clientes")
public class ClienteController {

	private final ClienteService clientes;

	public ClienteController(ClienteService clientes) {
		this.clientes = clientes;
	}

	@GetMapping
	@PreAuthorize("hasAnyAuthority('clientes:gestionar', 'clientes:ver')")
	public List<Document> listar() {
		return clientes.listar();
	}

	/** RF-13 desde el lado del cliente: sus datos y sus paquetes con el estado de cada uno. */
	@GetMapping("/mio")
	@PreAuthorize("hasRole('cliente')")
	public Cliente mio(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return clientes.obtener(usuario.clienteId());
	}

	@GetMapping("/{id}")
	@PreAuthorize("hasAnyAuthority('clientes:gestionar', 'clientes:ver')")
	public Map<String, Object> detalle(@PathVariable String id) {
		return clientes.detalle(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('clientes:gestionar')")
	public CuentaCreada registrar(@Valid @RequestBody ClienteRequest datos) {
		return clientes.registrar(datos);
	}

	@PutMapping("/{id}")
	@PreAuthorize("hasAuthority('clientes:gestionar')")
	public Cliente actualizar(@PathVariable String id, @Valid @RequestBody ActualizarClienteRequest datos) {
		return clientes.actualizar(id, datos);
	}

	/** Eliminación lógica (estado inactivo). */
	@DeleteMapping("/{id}")
	@PreAuthorize("hasAuthority('clientes:gestionar')")
	public Cliente inactivar(@PathVariable String id) {
		return clientes.inactivar(id);
	}

	@PostMapping("/{id}/reactivacion")
	@PreAuthorize("hasAuthority('clientes:gestionar')")
	public Cliente reactivar(@PathVariable String id) {
		return clientes.reactivar(id);
	}

	@PostMapping("/{id}/paquetes")
	@PreAuthorize("hasAuthority('clientes:gestionar')")
	public Cliente contratarPaquete(@PathVariable String id, @Valid @RequestBody ContratoPaqueteRequest datos) {
		return clientes.contratarPaquete(id, datos);
	}

}
