package com.sistemasintegrados.helpdesk.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.convert.DefaultDbRefResolver;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * El esquema (colecciones, validadores e índices) lo crea base_datos/01_esquema.js.
 * Spring no crea nada: solo lee y escribe en las colecciones que ya existen.
 */
@Configuration
public class MongoConfig {

	/**
	 * Spring Data agrega por defecto un campo "_class" a cada documento. Con el
	 * type mapper en null los documentos quedan iguales a los que se insertan
	 * desde mongosh o Compass.
	 */
	@Bean
	MappingMongoConverter mappingMongoConverter(MongoDatabaseFactory factory, MongoMappingContext context,
			MongoCustomConversions conversions) {
		MappingMongoConverter converter = new MappingMongoConverter(new DefaultDbRefResolver(factory), context);
		converter.setCustomConversions(conversions);
		converter.setTypeMapper(new DefaultMongoTypeMapper(null));
		return converter;
	}

	/**
	 * Habilita @Transactional para transacciones multi-documento. Requiere que
	 * MongoDB corra como replica set (Atlas siempre lo es).
	 */
	@Bean
	MongoTransactionManager transactionManager(MongoDatabaseFactory factory) {
		return new MongoTransactionManager(factory);
	}

}
