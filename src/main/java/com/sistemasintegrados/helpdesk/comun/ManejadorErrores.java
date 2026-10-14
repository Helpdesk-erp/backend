package com.sistemasintegrados.helpdesk.comun;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mongodb.MongoServerException;
import com.mongodb.MongoWriteException;

/**
 * Convierte las excepciones en respuestas JSON con un mensaje entendible.
 * Las validaciones van en tres capas: el formulario de React, las anotaciones
 * de los DTO y, al final, el $jsonSchema de MongoDB.
 */
@RestControllerAdvice
public class ManejadorErrores {

	private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

	private record Duplicado(String campo, String mensaje) {
	}

	/** Un mensaje por cada índice único de 01_esquema.js. */
	private static final Map<String, Duplicado> INDICES_UNICOS = Map.of(
			"cliente_documento_unico", new Duplicado("numero_documento", "Ya existe un cliente con ese número de documento"),
			"cliente_correo_unico", new Duplicado("correo", "Ya existe un cliente registrado con ese correo"),
			"usuario_unico", new Duplicado("usuario", "Ese nombre de usuario ya está en uso"),
			"usuario_correo_unico", new Duplicado("correo", "Ya existe una cuenta con ese correo"),
			"empleado_documento_unico", new Duplicado("numero_documento", "Ya existe un empleado con ese número de documento"),
			"un_usuario_por_empleado", new Duplicado("empleado_id", "Ese empleado ya tiene un usuario"),
			"una_inscripcion_por_usuario", new Duplicado("capacitacion_id", "Ya está inscrito en esa capacitación"));

	@ExceptionHandler(ReglaNegocioException.class)
	ResponseEntity<ErrorApi> reglaNegocio(ReglaNegocioException e) {
		return ResponseEntity.status(e.getEstado()).body(new ErrorApi(e.getMessage(), e.getErrores(), null));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorApi> datosInvalidos(MethodArgumentNotValidException e) {
		Map<String, String> errores = new LinkedHashMap<>();
		e.getBindingResult().getFieldErrors()
			.forEach(error -> errores.putIfAbsent(aSnakeCase(error.getField()), error.getDefaultMessage()));
		return ResponseEntity.badRequest().body(new ErrorApi("Revise los datos del formulario", errores, null));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorApi> jsonInvalido(HttpMessageNotReadableException e) {
		return ResponseEntity.badRequest().body(ErrorApi.de("El cuerpo de la petición no es un JSON válido"));
	}

	/** Código 11000 de MongoDB: un índice único rechazó el documento. */
	@ExceptionHandler(DuplicateKeyException.class)
	ResponseEntity<ErrorApi> duplicado(DuplicateKeyException e) {
		String mensaje = String.valueOf(e.getMessage());
		for (Map.Entry<String, Duplicado> indice : INDICES_UNICOS.entrySet()) {
			if (mensaje.contains("index: " + indice.getKey() + " ")) {
				Duplicado d = indice.getValue();
				return ResponseEntity.status(HttpStatus.CONFLICT)
					.body(new ErrorApi(d.mensaje(), Map.of(d.campo(), d.mensaje()), null));
			}
		}
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorApi.de("Ya existe un registro con esos datos"));
	}

	/** Código 121 de MongoDB: el documento no cumple el $jsonSchema de la colección. */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ErrorApi> integridad(DataIntegrityViolationException e) {
		Throwable causa = e.getCause();
		if (causa instanceof MongoServerException mongo && mongo.getCode() == 121) {
			// errInfo trae qué regla del esquema falló; se pasa a Document para que Jackson lo serialice limpio
			Object detalle = causa instanceof MongoWriteException escritura && escritura.getError().getDetails() != null
					? Document.parse(escritura.getError().getDetails().toJson())
					: null;
			log.warn("Documento rechazado por el validador: {}", detalle);
			return ResponseEntity.badRequest()
				.body(new ErrorApi("Los datos no cumplen las reglas de la base de datos", null, detalle));
		}
		log.error("Error de integridad", e);
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorApi.de("No se pudo guardar el registro"));
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ErrorApi> sinPermiso(AccessDeniedException e) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorApi.de("No tiene permiso para esta operación"));
	}

	/** tipoCliente -> tipo_cliente, direccion.ciudad queda igual. */
	private static String aSnakeCase(String campo) {
		return campo.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
	}

}
