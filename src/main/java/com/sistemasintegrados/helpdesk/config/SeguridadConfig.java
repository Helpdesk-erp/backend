package com.sistemasintegrados.helpdesk.config;

import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sistemasintegrados.helpdesk.auth.JwtSesionConverter;
import com.sistemasintegrados.helpdesk.auth.RespuestaNoAutenticado;

@Configuration
@EnableMethodSecurity
public class SeguridadConfig {

	@Bean
	SecurityFilterChain filtros(HttpSecurity http, JwtSesionConverter convertidor, RespuestaNoAutenticado noAutenticado)
			throws Exception {
		http.csrf(csrf -> csrf.disable())
			.cors(Customizer.withDefaults())
			.sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(rutas -> rutas
				.requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/registro").permitAll()
				.anyRequest().authenticated())
			.oauth2ResourceServer(recurso -> recurso
				.jwt(jwt -> jwt.jwtAuthenticationConverter(convertidor))
				.authenticationEntryPoint(noAutenticado))
			.exceptionHandling(errores -> errores.authenticationEntryPoint(noAutenticado));
		return http.build();
	}

	@Bean
	SecretKey claveJwt(AppProperties propiedades) {
		String secreto = propiedades.jwt().secreto();
		if (secreto == null || secreto.length() < 32) {
			throw new IllegalStateException("Falta JWT_SECRET o tiene menos de 32 caracteres. Revise el archivo .env del backend");
		}
		return new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey clave) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(clave));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey clave) {
		return NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
	}

	/** Lee los hash $2b$ que vienen en 02_datos_semilla.js y genera $2a$ para los nuevos. */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(AppProperties propiedades) {
		CorsConfiguration cors = new CorsConfiguration();
		cors.setAllowedOrigins(propiedades.cors().origenes());
		cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
		UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
		fuente.registerCorsConfiguration("/api/**", cors);
		return fuente;
	}

}
