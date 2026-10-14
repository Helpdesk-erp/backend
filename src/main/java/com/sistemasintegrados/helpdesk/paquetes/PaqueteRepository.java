package com.sistemasintegrados.helpdesk.paquetes;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaqueteRepository extends MongoRepository<Paquete, String> {

	List<Paquete> findByActivoTrueOrderByNombre();

}
