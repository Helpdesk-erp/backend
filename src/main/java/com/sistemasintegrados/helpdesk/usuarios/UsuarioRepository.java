package com.sistemasintegrados.helpdesk.usuarios;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface UsuarioRepository extends MongoRepository<Usuario, String> {

	Optional<Usuario> findByUsuario(String usuario);

	Optional<Usuario> findByCorreo(String correo);

}
