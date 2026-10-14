package com.sistemasintegrados.helpdesk;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Comprueba que el contexto de Spring arranca. No necesita una base de datos
 * real porque el cliente de MongoDB solo se conecta en la primera consulta.
 */
@SpringBootTest(properties = {
		"spring.mongodb.uri=mongodb://127.0.0.1:27017/?serverSelectionTimeoutMS=1000",
		"app.jwt.secreto=clave-solo-para-pruebas-con-mas-de-32-caracteres" })
class HelpdeskApplicationTests {

	@Test
	void contextLoads() {
	}

}
