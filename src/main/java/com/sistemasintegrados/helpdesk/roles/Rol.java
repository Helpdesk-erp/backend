package com.sistemasintegrados.helpdesk.roles;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("roles")
public record Rol(@Id String id, String descripcion, List<String> permisos) {
}
