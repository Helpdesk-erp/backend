package com.sistemasintegrados.helpdesk.usuarios;

/**
 * Respuesta al crear una cuenta desde el panel del administrador.
 *
 * @param registro      lo que se creó: la fila del usuario (RF-06) o el cliente (RF-11)
 * @param usuario       nombre de usuario para iniciar sesión
 * @param claveTemporal contraseña inicial; solo se entrega en esta respuesta
 */
public record CuentaCreada(Object registro, String usuario, String claveTemporal) {
}
