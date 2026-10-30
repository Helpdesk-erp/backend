package com.sistemasintegrados.helpdesk.conocimiento;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class ConocimientoService {

	private final MongoTemplate mongo;

	public ConocimientoService(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	/**
	 * Búsqueda en lenguaje natural con el índice de texto "busqueda_texto"
	 * (español: ignora tildes y palabras como "el", "de", "no"). Ordena por
	 * relevancia (textScore).
	 *
	 * @param paquete opcional, limita la búsqueda a un paquete
	 */
	public List<Document> buscar(String texto, String paquete) {
		if (texto == null || texto.isBlank() || texto.trim().length() < 2) {
			return List.of();
		}
		Document filtro = new Document("$text", new Document("$search", texto.trim()));
		if (paquete != null && !paquete.isBlank()) {
			filtro.append("paquete", paquete);
		}
		Document relevancia = new Document("$meta", "textScore");

		List<Document> articulos = mongo.getCollection("base_conocimiento")
			.find(filtro)
			.projection(new Document("titulo", 1).append("paquete", 1).append("problema", 1).append("pasos", 1)
				.append("solucion", 1).append("palabras_clave", 1).append("ticket_id", 1).append("relevancia", relevancia))
			.sort(new Document("relevancia", relevancia))
			.limit(10)
			.into(new ArrayList<>());
		articulos.forEach(articulo -> articulo.put("id", articulo.remove("_id")));
		return articulos;
	}

	/**
	 * Cada artículo junto con el ticket del que salió, para que el auditor
	 * compruebe que toda solución documentada viene de un caso real. Agregación
	 * con $lookup: base_conocimiento.ticket_id -> tickets._id.
	 */
	public List<Document> articulosConTicket() {
		List<Document> etapas = List.of(Document.parse("""
				{ $lookup: { from: "tickets", localField: "ticket_id", foreignField: "_id", as: "ticket" } }
				"""));

		List<Document> articulos = mongo.getCollection("base_conocimiento").aggregate(etapas).into(new ArrayList<>());
		articulos.forEach(articulo -> articulo.put("id", articulo.remove("_id")));
		return articulos;
	}

}
