package com.sistemasintegrados.helpdesk.paquetes;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaqueteService {

	private final MongoTemplate mongo;

	public PaqueteService(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	/**
	 * Catálogo de paquetes del ERP, del más costoso al más económico y, a igual
	 * precio, por nombre. Agregación con $sort.
	 */
	public List<Document> catalogoPorPrecio() {
		List<Document> etapas = List.of(Document.parse("{ $sort: { precio_mensual: -1, nombre: 1 } }"));

		List<Document> paquetes = mongo.getCollection("paquetes").aggregate(etapas).into(new ArrayList<>());
		paquetes.forEach(paquete -> paquete.put("id", paquete.remove("_id")));
		return paquetes;
	}

}
