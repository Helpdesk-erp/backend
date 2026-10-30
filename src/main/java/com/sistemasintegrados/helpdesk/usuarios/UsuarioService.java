package com.sistemasintegrados.helpdesk.usuarios;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService {

	private final MongoTemplate mongo;

	public UsuarioService(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	/**
	 * Usuarios del sistema para la tabla del administrador. Agregación con
	 * $project: deja por fuera password_hash, renombra _id como id y arma tres
	 * campos nuevos (el rol principal, si la cuenta es de un empleado o de un
	 * cliente, y el código de ese empleado o cliente).
	 */
	public List<Document> listarParaAdministrador() {
		List<Document> etapas = List.of(Document.parse("""
				{ $project: {
				    _id: 0, id: "$_id", usuario: 1, correo: 1, estado: 1, ultimo_acceso: 1,
				    rol: { $first: "$roles" },
				    vinculo: { $cond: [ { $ifNull: [ "$empleado_id", false ] }, "empleado", "cliente" ] },
				    vinculo_id: { $ifNull: [ "$empleado_id", "$cliente_id" ] }
				} }
				"""));

		return mongo.getCollection("usuarios").aggregate(etapas).into(new ArrayList<>());
	}

}
