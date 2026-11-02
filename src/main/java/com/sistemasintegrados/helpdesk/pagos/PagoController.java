package com.sistemasintegrados.helpdesk.pagos;

import java.util.List;
import java.util.Map;

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
@RequestMapping("/api/pagos")
public class PagoController {

	private final PagoService pagos;

	public PagoController(PagoService pagos) {
		this.pagos = pagos;
	}

	@GetMapping
	@PreAuthorize("hasAnyAuthority('pagos:registrar', 'pagos:ver')")
	public List<Document> listar(@RequestParam(name = "cliente_id", required = false) String clienteId,
			@RequestParam(required = false) String estado) {
		return pagos.listar(clienteId, estado);
	}

	/** Un pago rechazado suspende el paquete; uno aprobado reactiva el paquete suspendido. */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasAuthority('pagos:registrar')")
	public Map<String, Object> registrar(@Valid @RequestBody PagoRequest datos,
			@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return pagos.registrar(datos, usuario);
	}

}
