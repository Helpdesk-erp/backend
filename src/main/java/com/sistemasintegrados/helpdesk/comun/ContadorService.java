package com.sistemasintegrados.helpdesk.comun;

import org.bson.Document;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Genera los códigos legibles (CLI-005, TCK-0007...) con la colección contadores.
 */
@Service
public class ContadorService {

	private final MongoTemplate mongo;

	public ContadorService(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	/**
	 * findAndModify con $inc es atómico, así que dos peticiones simultáneas nunca
	 * reciben el mismo número. Corre fuera de la transacción que lo llama: si esa
	 * transacción falla queda un número sin usar (igual que una secuencia en SQL),
	 * pero dos registros al mismo tiempo no chocan por el mismo contador.
	 *
	 * @param coleccion _id del contador ("clientes", "tickets"...)
	 * @param prefijo   CLI, USR, TCK...
	 * @param digitos   mínimo de dígitos con ceros a la izquierda
	 */
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	public String siguiente(String coleccion, String prefijo, int digitos) {
		Document contador = mongo.findAndModify(
				Query.query(Criteria.where("_id").is(coleccion)),
				new Update().inc("secuencia", 1),
				FindAndModifyOptions.options().returnNew(true),
				Document.class,
				"contadores");
		if (contador == null) {
			throw new IllegalStateException("No existe el contador '" + coleccion + "'. Cargue base_datos/02_datos_semilla.js");
		}
		long numero = contador.get("secuencia", Number.class).longValue();
		return prefijo + "-" + String.format("%0" + digitos + "d", numero);
	}

}
