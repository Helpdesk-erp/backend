package com.sistemasintegrados.helpdesk.pagos;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class PagoService {

	private final MongoTemplate mongo;

	public PagoService(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	/**
	 * Recaudo por método de pago (PSE, transferencia, tarjeta...). Agregación con
	 * $group: suma el valor de los pagos aprobados y cuenta los pagos y los
	 * rechazos de cada método.
	 */
	public List<Document> recaudoPorMetodo() {
		List<Document> etapas = List.of(Document.parse("""
				{ $group: {
				    _id: "$metodo",
				    recaudado: { $sum: { $cond: [ { $eq: [ "$estado", "aprobado" ] }, "$valor", 0 ] } },
				    pagos: { $sum: 1 },
				    rechazados: { $sum: { $cond: [ { $eq: [ "$estado", "rechazado" ] }, 1, 0 ] } }
				} }
				"""));

		List<Document> filas = mongo.getCollection("pagos").aggregate(etapas).into(new ArrayList<>());
		filas.forEach(fila -> fila.put("metodo", fila.remove("_id")));
		return filas;
	}

}
