package com.sistemasintegrados.helpdesk.roles;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface RolRepository extends MongoRepository<Rol, String> {
}
