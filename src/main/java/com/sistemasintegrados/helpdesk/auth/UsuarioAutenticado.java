package com.sistemasintegrados.helpdesk.auth;

/**
 * Quién hace la petición. Los controladores lo reciben con @AuthenticationPrincipal.
 *
 * @param rol       rol con el que inició sesión
 * @param clienteId solo en usuarios de clientes
 * @param empleadoId solo en usuarios de empleados
 */
public record UsuarioAutenticado(
		String id,
		String usuario,
		String rol,
		String clienteId,
		String empleadoId,
		String sesionId) {
}
