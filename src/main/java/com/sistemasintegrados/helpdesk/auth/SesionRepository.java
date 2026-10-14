package com.sistemasintegrados.helpdesk.auth;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SesionRepository extends MongoRepository<Sesion, String> {
}
