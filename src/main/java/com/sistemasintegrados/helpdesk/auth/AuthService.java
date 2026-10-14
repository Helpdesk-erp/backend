package com.sistemasintegrados.helpdesk.auth;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Duration;
import java.time.Instant;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.sistemasintegrados.helpdesk.clientes.Cliente;
import com.sistemasintegrados.helpdesk.clientes.ClienteRepository;
import com.sistemasintegrados.helpdesk.comun.ReglaNegocioException;
import com.sistemasintegrados.helpdesk.config.AppProperties;
import com.sistemasintegrados.helpdesk.roles.RolService;
import com.sistemasintegrados.helpdesk.usuarios.Usuario;
import com.sistemasintegrados.helpdesk.usuarios.UsuarioRepository;

@Service
public class AuthService {

	private final UsuarioRepository usuarios;

	private final ClienteRepository clientes;

	private final RolService roles;

	private final MongoTemplate mongo;

	private final PasswordEncoder encoder;

	private final JwtEncoder jwt;

	private final Duration duracionSesion;

	/** Se compara contra este hash cuando el usuario no existe, para que la respuesta tarde lo mismo. */
	private final String hashFalso;

	public AuthService(UsuarioRepository usuarios, ClienteRepository clientes, RolService roles, MongoTemplate mongo,
			PasswordEncoder encoder, JwtEncoder jwt, AppProperties propiedades) {
		this.usuarios = usuarios;
		this.clientes = clientes;
		this.roles = roles;
		this.mongo = mongo;
		this.encoder = encoder;
		this.jwt = jwt;
		this.duracionSesion = Duration.ofHours(propiedades.jwt().duracionHoras());
		this.hashFalso = encoder.encode("usuario-inexistente");
	}

	/** En el campo usuario se acepta el nombre de usuario o el correo. */
	public LoginResponse login(LoginRequest datos, String ip, String agenteNavegador) {
		String usuarioOCorreo = datos.usuario().trim().toLowerCase();
		Usuario usuario = (usuarioOCorreo.contains("@")
				? usuarios.findByCorreo(usuarioOCorreo)
				: usuarios.findByUsuario(usuarioOCorreo)).orElse(null);

		boolean claveCorrecta = encoder.matches(datos.password(), usuario != null ? usuario.passwordHash() : hashFalso);
		if (usuario == null || !claveCorrecta) {
			throw new ReglaNegocioException(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos");
		}
		if ("bloqueado".equals(usuario.estado())) {
			throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "La cuenta está bloqueada. Comuníquese con el administrador");
		}
		if (!usuario.activo()) {
			throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "La cuenta está inactiva");
		}

		// Un usuario puede tener varios roles; por ahora entra con el primero
		return abrirSesion(usuario, usuario.roles().getFirst(), ip, agenteNavegador);
	}

	/**
	 * Registra la sesión en MongoDB y firma el token con el _id de la sesión como "jti".
	 */
	public LoginResponse abrirSesion(Usuario usuario, String rol, String ip, String agenteNavegador) {
		Instant ahora = Instant.now();
		Instant expira = ahora.plus(duracionSesion);

		// Las sesiones que vencieron sin cerrar quedan como expiradas
		mongo.updateMulti(
				query(where("usuario_id").is(usuario.id()).and("estado").is("activa").and("fecha_expiracion").lt(ahora)),
				Update.update("estado", "expirada"), "sesiones");

		Sesion sesion = new Sesion(new ObjectId().toHexString(), usuario.id(), rol, ip, dispositivo(agenteNavegador),
				ahora, expira, null, "activa");
		mongo.insert(sesion);
		mongo.updateFirst(query(where("_id").is(usuario.id())), Update.update("ultimo_acceso", ahora), "usuarios");

		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer("helpdesk-erp")
			.subject(usuario.id())
			.id(sesion.id())
			.issuedAt(ahora)
			.expiresAt(expira)
			.claim("rol", rol)
			.build();
		String token = jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();

		return new LoginResponse(token, expira, usuarioActual(usuario, rol));
	}

	public void cerrarSesion(String sesionId) {
		mongo.updateFirst(
				query(where("_id").is(new ObjectId(sesionId)).and("estado").is("activa")),
				Update.update("estado", "cerrada").set("fecha_cierre", Instant.now()), "sesiones");
	}

	public UsuarioActual usuarioActual(UsuarioAutenticado autenticado) {
		Usuario usuario = usuarios.findById(autenticado.id())
			.orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "El usuario ya no existe"));
		return usuarioActual(usuario, autenticado.rol());
	}

	private UsuarioActual usuarioActual(Usuario usuario, String rol) {
		String nombre = usuario.empleadoId() != null ? nombreEmpleado(usuario.empleadoId())
				: clientes.findById(usuario.clienteId()).map(Cliente::nombre).orElse(usuario.usuario());
		return new UsuarioActual(usuario.id(), usuario.usuario(), usuario.correo(), nombre, rol, usuario.roles(),
				roles.permisos(rol), usuario.clienteId(), usuario.empleadoId());
	}

	private String nombreEmpleado(String empleadoId) {
		Query consulta = query(where("_id").is(empleadoId));
		consulta.fields().include("nombres", "apellidos");
		Document empleado = mongo.findOne(consulta, Document.class, "empleados");
		return empleado == null ? empleadoId : empleado.getString("nombres") + " " + empleado.getString("apellidos");
	}

	/** "Chrome en Windows", "Firefox en Android"... a partir del encabezado User-Agent. */
	static String dispositivo(String agente) {
		if (agente == null || agente.isBlank()) {
			return "Desconocido";
		}
		String navegador = agente.contains("Edg/") ? "Edge"
				: agente.contains("OPR/") ? "Opera"
				: agente.contains("Firefox/") ? "Firefox"
				: agente.contains("Chrome/") ? "Chrome"
				: agente.contains("Safari/") ? "Safari"
				: agente.split("/")[0];
		String sistema = agente.contains("Windows") ? "Windows"
				: agente.contains("Android") ? "Android"
				: agente.contains("iPhone") || agente.contains("iPad") ? "iOS"
				: agente.contains("Mac OS") ? "macOS"
				: agente.contains("Linux") ? "Linux"
				: null;
		return sistema == null ? navegador : navegador + " en " + sistema;
	}

}
