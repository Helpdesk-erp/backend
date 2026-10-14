package com.sistemasintegrados.helpdesk.roles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * Los permisos de cada rol viven en la colección roles. Se guardan en memoria
 * un minuto: un cambio hecho en la base se aplica sin reiniciar el backend.
 */
@Service
public class RolService {

	private static final Duration VIGENCIA = Duration.ofMinutes(1);

	private final RolRepository roles;

	private volatile Map<String, List<String>> permisosPorRol = Map.of();

	private volatile Instant cargado = Instant.EPOCH;

	public RolService(RolRepository roles) {
		this.roles = roles;
	}

	public List<String> permisos(String rol) {
		if (Instant.now().isAfter(cargado.plus(VIGENCIA))) {
			permisosPorRol = roles.findAll().stream().collect(Collectors.toMap(Rol::id, Rol::permisos));
			cargado = Instant.now();
		}
		return permisosPorRol.getOrDefault(rol, List.of());
	}

}
