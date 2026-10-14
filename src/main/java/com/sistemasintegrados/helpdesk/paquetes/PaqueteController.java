package com.sistemasintegrados.helpdesk.paquetes;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sistemasintegrados.helpdesk.comun.ReglaNegocioException;

@RestController
@RequestMapping("/api/paquetes")
public class PaqueteController {

	private final PaqueteRepository paquetes;

	public PaqueteController(PaqueteRepository paquetes) {
		this.paquetes = paquetes;
	}

	@GetMapping
	public List<Paquete> listar() {
		return paquetes.findByActivoTrueOrderByNombre();
	}

	/** Incluye campos_ticket, que es lo que necesita el formulario dinámico. */
	@GetMapping("/{id}")
	public Paquete obtener(@PathVariable String id) {
		return paquetes.findById(id.toUpperCase())
			.orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "No existe el paquete " + id));
	}

}
