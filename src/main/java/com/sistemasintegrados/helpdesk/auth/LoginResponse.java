package com.sistemasintegrados.helpdesk.auth;

import java.time.Instant;

public record LoginResponse(String token, Instant expira, UsuarioActual usuario) {
}
