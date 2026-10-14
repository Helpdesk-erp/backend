package com.sistemasintegrados.helpdesk.auth;

import java.util.List;

/**
 * Datos del usuario que necesita el frontend para armar el menú y el dashboard.
 *
 * @param nombre   nombre del empleado o del cliente
 * @param rol      rol con el que inició sesión
 * @param permisos permisos de ese rol, tomados de la colección roles
 */
public record UsuarioActual(
		String id,
		String usuario,
		String correo,
		String nombre,
		String rol,
		List<String> roles,
		List<String> permisos,
		String clienteId,
		String empleadoId) {
}
