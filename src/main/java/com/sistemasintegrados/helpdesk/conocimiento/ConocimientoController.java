package com.sistemasintegrados.helpdesk.conocimiento;

import java.util.List;

import org.bson.Document;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

}
