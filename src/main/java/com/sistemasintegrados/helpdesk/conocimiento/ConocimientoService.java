package com.sistemasintegrados.helpdesk.conocimiento;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.sistemasintegrados.helpdesk.auth.UsuarioAutenticado;
import com.sistemasintegrados.helpdesk.comun.ContadorService;
import com.sistemasintegrados.helpdesk.comun.ReglaNegocioException;
import com.sistemasintegrados.helpdesk.comun.Textos;

@Service
public class ConocimientoService {

	private final MongoTemplate mongo;

	private final ContadorService contadores;

	public ConocimientoService(MongoTemplate mongo, ContadorService contadores) {
		this.mongo = mongo;
		this.contadores = contadores;
	}

	/**
	 * RF-31. El agente documenta un ticket que él mismo resolvió. Cada ticket da
	 * origen a un solo artículo, y el artículo hereda el paquete y el cliente del
	 * ticket para que la trazabilidad (RF-33) cuadre.
	 */
	public Articulo crear(ArticuloRequest datos, UsuarioAutenticado autor) {
		Document ticket = mongo.findOne(query(where("_id").is(datos.ticketId())), Document.class, "tickets");
		if (ticket == null) {
			throw new ReglaNegocioException(HttpStatus.NOT_FOUND, "No existe el ticket " + datos.ticketId());
		}
		if (!List.of("resuelto", "cerrado").contains(ticket.getString("estado"))) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "Solo se documentan tickets resueltos o cerrados");
		}
		if (!autor.id().equals(ticket.getString("agente_id"))) {
			throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "Solo el agente que resolvió el ticket puede documentarlo");
		}
		if (mongo.exists(query(where("ticket_id").is(datos.ticketId())), "base_conocimiento")) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "Ese ticket ya tiene un artículo en la base de conocimiento");
		}

		String contacto = Textos.limpio(datos.contacto());
		if (contacto == null) {
			Document cliente = mongo.findOne(query(where("_id").is(ticket.getString("cliente_id"))), Document.class, "clientes");
			if (cliente != null) {
				contacto = cliente.getString("representante") != null ? cliente.getString("representante")
						: cliente.getString("nombre");
			}
		}

		// Palabras clave en minúsculas y sin repetir
		LinkedHashSet<String> palabras = new LinkedHashSet<>();
		if (datos.palabrasClave() != null) {
			datos.palabrasClave().stream().map(Textos::limpio).filter(p -> p != null).map(String::toLowerCase)
				.forEach(palabras::add);
		}

		Articulo articulo = new Articulo(
				contadores.siguiente("base_conocimiento", "KB", 3),
				datos.ticketId(),
				ticket.getString("cliente_id"),
				contacto,
				ticket.getString("paquete"),
				datos.titulo().trim(),
				datos.problema().trim(),
				datos.pasos().stream().map(String::trim).toList(),
				datos.solucion().trim(),
				List.copyOf(palabras),
				autor.id(),
				Instant.now());
		return mongo.insert(articulo);
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
