package com.sistemasintegrados.helpdesk.empleados;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface EmpleadoRepository extends MongoRepository<Empleado, String> {
}
