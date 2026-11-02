package com.sistemasintegrados.helpdesk.conocimiento;

import java.util.List;

import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sistemasintegrados.helpdesk.auth.UsuarioAutenticado;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/conocimiento")
public class ConocimientoController {

	private final ConocimientoService conocimiento;

	public ConocimientoController(ConocimientoService conocimiento) {
		this.conocimiento = conocimiento;
	}

	/** GET /api/conocimiento/buscar?q=no puedo ingresar&paquete=POS */
	@GetMapping("/buscar")
	@PreAuthorize("hasAuthority('conocimiento:buscar')")
	public List<Document> buscar(@RequestParam("q") String texto, @RequestParam(required = false) String paquete) {
		return conocimiento.buscar(texto, paquete);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('conocimiento:crear')")
	public Articulo crear(@Valid @RequestBody ArticuloRequest datos, @AuthenticationPrincipal UsuarioAutenticado autor) {
		return conocimiento.crear(datos, autor);
	}

}
