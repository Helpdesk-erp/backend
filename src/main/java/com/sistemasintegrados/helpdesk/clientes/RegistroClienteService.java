package com.sistemasintegrados.helpdesk.clientes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemasintegrados.helpdesk.comun.ContadorService;
import com.sistemasintegrados.helpdesk.comun.ReglaNegocioException;
import com.sistemasintegrados.helpdesk.usuarios.Usuario;

@Service
public class RegistroClienteService {

	private final MongoTemplate mongo;

	private final ContadorService contadores;

	private final PasswordEncoder encoder;

	public RegistroClienteService(MongoTemplate mongo, ContadorService contadores, PasswordEncoder encoder) {
		this.mongo = mongo;
		this.contadores = contadores;
		this.encoder = encoder;
	}

	/**
	 * Inserta el cliente y su usuario en una transacción multi-documento. Si el
	 * usuario falla (por ejemplo, el nombre de usuario ya existe), el cliente
	 * tampoco queda guardado. El cliente empieza sin paquetes contratados.
	 */
	@Transactional
	public Usuario registrar(RegistroRequest datos) {
		boolean personaNatural = "persona_natural".equals(datos.tipoCliente());
		String representante = datos.representante() == null || datos.representante().isBlank() ? null
				: datos.representante().trim();

		// Mismas reglas del anyOf de clientes en 01_esquema.js
		Map<String, String> errores = new LinkedHashMap<>();
		if (personaNatural && "NIT".equals(datos.tipoDocumento())) {
			errores.put("tipo_documento", "Una persona natural se registra con CC o CE");
		}
		if (!personaNatural && !"NIT".equals(datos.tipoDocumento())) {
			errores.put("tipo_documento", "Una empresa o persona jurídica se registra con NIT");
		}
		if (!personaNatural && representante == null) {
			errores.put("representante", "Escriba el nombre del propietario o representante legal");
		}
		if (!errores.isEmpty()) {
			throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Revise los datos del formulario", errores);
		}

		Instant ahora = Instant.now();
		String correo = datos.correo().trim().toLowerCase();
		RegistroRequest.Direccion direccion = datos.direccion();

		Cliente cliente = new Cliente(
				contadores.siguiente("clientes", "CLI", 3),
				datos.tipoCliente(),
				datos.tipoDocumento(),
				datos.numeroDocumento(),
				datos.nombre().trim(),
				personaNatural ? null : representante,
				correo,
				datos.telefono(),
				new Cliente.Direccion(direccion.direccion().trim(), direccion.ciudad().trim(), direccion.departamento().trim()),
				List.of(),
				"activo",
				ahora);

		Usuario usuario = new Usuario(
				contadores.siguiente("usuarios", "USR", 3),
				datos.usuario(),
				correo,
				encoder.encode(datos.password()),
				List.of("cliente"),
				cliente.id(),
				null,
				"activo",
				false,
				null,
				ahora);

		mongo.insert(cliente);
		mongo.insert(usuario);
		return usuario;
	}

}
