package com.sistemasintegrados.helpdesk.empleados;

import java.util.List;

import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/empleados")
@PreAuthorize("hasAuthority('empleados:gestionar')")
public class EmpleadoController {

	private final EmpleadoService empleados;

	public EmpleadoController(EmpleadoService empleados) {
		this.empleados = empleados;
	}

	@GetMapping
	public List<Document> listar() {
		return empleados.listar();
	}

	@GetMapping("/{id}")
	public Empleado obtener(@PathVariable String id) {
		return empleados.obtener(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Empleado registrar(@Valid @RequestBody EmpleadoRequest datos) {
		return empleados.registrar(datos);
	}

	@PutMapping("/{id}")
	public Empleado actualizar(@PathVariable String id, @Valid @RequestBody EmpleadoRequest datos) {
		return empleados.actualizar(id, datos);
	}

	/** Estado retirado y usuario inactivo. No borra el registro. */
	@PostMapping("/{id}/retiro")
	public Empleado retirar(@PathVariable String id, @AuthenticationPrincipal UsuarioAutenticado usuario) {
		return empleados.retirar(id, usuario);
	}

}
