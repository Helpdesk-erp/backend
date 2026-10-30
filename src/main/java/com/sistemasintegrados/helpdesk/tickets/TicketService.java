package com.sistemasintegrados.helpdesk.tickets;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class TicketService {

	private final MongoTemplate mongo;

	public TicketService(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	/**
	 * Tickets del cliente que soporte ya marcó como resueltos y que esperan su
	 * confirmación para cerrarse. Agregación con $match; el índice
	 * tickets_por_cliente cubre el filtro por cliente_id.
	 */
	public List<Document> pendientesDeConfirmar(String clienteId) {
		List<Document> etapas = List.of(
				new Document("$match", new Document("cliente_id", clienteId).append("estado", "resuelto")));

		List<Document> tickets = mongo.getCollection("tickets").aggregate(etapas).into(new ArrayList<>());
		tickets.forEach(ticket -> ticket.put("id", ticket.remove("_id")));
		return tickets;
	}

}
