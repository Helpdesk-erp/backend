package com.sistemasintegrados.helpdesk.empleados;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemasintegrados.helpdesk.auth.AuthService;
import com.sistemasintegrados.helpdesk.auth.UsuarioAutenticado;
import com.sistemasintegrados.helpdesk.comun.ContadorService;
import com.sistemasintegrados.helpdesk.comun.Fechas;
import com.sistemasintegrados.helpdesk.comun.ReglaNegocioException;
import com.sistemasintegrados.helpdesk.comun.Textos;
import com.sistemasintegrados.helpdesk.usuarios.Usuario;
import com.sistemasintegrados.helpdesk.usuarios.UsuarioRepository;

@Service
public class EmpleadoService {

	private final EmpleadoRepository empleados;

	private final UsuarioRepository usuarios;

	private final MongoTemplate mongo;

	private final ContadorService contadores;

	private final AuthService auth;

	public EmpleadoService(EmpleadoRepository empleados, UsuarioRepository usuarios, MongoTemplate mongo,
			ContadorService contadores, AuthService auth) {
		this.empleados = empleados;
		this.usuarios = usuarios;
		this.mongo = mongo;
		this.contadores = contadores;
		this.auth = auth;
	}

	/** Empleados con su cuenta de usuario (si tienen), los activos primero. */
	public List<Document> listar() {
		List<Document> etapas = Document.parse("""
				{ etapas: [
				  { $lookup: {
				      from: "usuarios", localField: "_id", foreignField: "empleado_id", as: "usuario",
				      pipeline: [ { $project: { _id: 0, id: "$_id", usuario: 1, rol: { $first: "$roles" }, estado: 1 } } ]
				  } },
				  { $project: {
				      _id: 0, id: "$_id", nombres: 1, apellidos: 1, numero_documento: 1, correo_personal: 1, telefono_movil: 1,
				      cargo: "$contrato.cargo", area: "$contrato.area", tipo_contrato: "$contrato.tipo_contrato",
				      fecha_fin: "$contrato.fecha_fin", estado: 1, usuario: { $first: "$usuario" }
				  } },
				  { $sort: { estado: 1, apellidos: 1 } }
				] }
				""").getList("etapas", Document.class);
		return mongo.getCollection("empleados").aggregate(etapas).into(new ArrayList<>());
	}

	public Empleado obtener(String id) {
		return empleados.findById(id)
			.orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "No existe el empleado " + id));
	}

	/** RF-15. El empleado empieza activo y sin usuario; la cuenta se crea aparte (RF-06). */
	public Empleado registrar(EmpleadoRequest datos) {
		validar(datos);
		Instant ahora = Instant.now();
		return mongo.insert(aEmpleado(contadores.siguiente("empleados", "EMP", 3), datos, "activo", ahora, ahora));
	}

	/** RF-16. Reemplaza los datos personales y el contrato; conserva el estado y la fecha de creación. */
	public Empleado actualizar(String id, EmpleadoRequest datos) {
		Empleado actual = obtener(id);
		if ("retirado".equals(actual.estado())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "El empleado está retirado y ya no se puede editar");
		}
		validar(datos);
		return mongo.save(aEmpleado(id, datos, actual.estado(), actual.fechaCreacion(), Instant.now()));
	}

	/**
	 * RF-17. Marca al empleado como retirado e inactiva su usuario en una misma
	 * transacción, y cierra las sesiones que tenga abiertas. Si es un agente con
	 * tickets o capacitaciones pendientes, primero hay que reasignarlos.
	 */
	@Transactional
	public Empleado retirar(String id, UsuarioAutenticado quien) {
		Empleado empleado = obtener(id);
		if ("retirado".equals(empleado.estado())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "El empleado ya está retirado");
		}
		if (id.equals(quien.empleadoId())) {
			throw new ReglaNegocioException(HttpStatus.CONFLICT, "No puede retirarse a sí mismo");
		}

		Usuario usuario = usuarios.findByEmpleadoId(id).orElse(null);
		if (usuario != null) {
			long ticketsAbiertos = mongo.count(query(where("agente_id").is(usuario.id())
				.and("estado").in("asignado", "en_proceso")), "tickets");
			if (ticketsAbiertos > 0) {
				throw new ReglaNegocioException(HttpStatus.CONFLICT, "El empleado tiene " + ticketsAbiertos
						+ " tickets abiertos. Reasígnelos a otro agente antes de retirarlo");
			}
			long capacitaciones = mongo.count(query(where("instructor_id").is(usuario.id())
				.and("estado").in("programada", "en_curso")), "capacitaciones");
			if (capacitaciones > 0) {
				throw new ReglaNegocioException(HttpStatus.CONFLICT, "El empleado es instructor de " + capacitaciones
						+ " capacitaciones programadas. Cancélelas o cambie el instructor antes de retirarlo");
			}
		}

		Instant ahora = Instant.now();
		Update retiro = Update.update("estado", "retirado").set("fecha_actualizacion", ahora);
		if (empleado.contrato().fechaFin() == null) {
			retiro.set("contrato.fecha_fin", Fechas.aInstante(LocalDate.now(ZoneOffset.UTC)));
		}
		mongo.updateFirst(query(where("_id").is(id)), retiro, "empleados");

		if (usuario != null) {
			mongo.updateFirst(query(where("_id").is(usuario.id())), Update.update("estado", "inactivo"), "usuarios");
			auth.cerrarSesionesDe(usuario.id());
		}
		return obtener(id);
	}

	/** Reglas que dependen de varios campos: los anyOf del $jsonSchema de empleados. */
	private static void validar(EmpleadoRequest datos) {
		Map<String, String> errores = new LinkedHashMap<>();
		EmpleadoRequest.Contrato contrato = datos.contrato();
		if (!"indefinido".equals(contrato.tipoContrato()) && contrato.fechaFin() == null) {
			errores.put("contrato.fecha_fin", "Un contrato que no es indefinido debe tener fecha de terminación");
		}
		if (contrato.fechaFin() != null && !contrato.fechaFin().isAfter(contrato.fechaInicio())) {
			errores.put("contrato.fecha_fin", "Debe ser posterior a la fecha de inicio");
		}

		EmpleadoRequest.InformacionBancaria banco = datos.informacionBancaria();
		if (banco != null && !vacia(banco)) {
			if (Textos.limpio(banco.banco()) == null) {
				errores.put("informacion_bancaria.banco", "Escriba el banco");
			}
			if (Textos.limpio(banco.tipoCuenta()) == null) {
				errores.put("informacion_bancaria.tipo_cuenta", "Seleccione el tipo de cuenta");
			}
			if (Textos.limpio(banco.numeroCuenta()) == null) {
				errores.put("informacion_bancaria.numero_cuenta", "Escriba el número de cuenta");
			}
		}
		if (!errores.isEmpty()) {
			throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Revise los datos del formulario", errores);
		}
	}

	private static boolean vacia(EmpleadoRequest.InformacionBancaria banco) {
		return Textos.limpio(banco.banco()) == null && Textos.limpio(banco.tipoCuenta()) == null
				&& Textos.limpio(banco.numeroCuenta()) == null;
	}

	private static Empleado aEmpleado(String id, EmpleadoRequest datos, String estado, Instant creacion,
			Instant actualizacion) {
		EmpleadoRequest.Direccion d = datos.direccion();
		EmpleadoRequest.Contrato c = datos.contrato();
		EmpleadoRequest.SeguridadSocial s = datos.seguridadSocial();
		EmpleadoRequest.InformacionBancaria b = datos.informacionBancaria();

		// La fecha de terminación de un contrato indefinido solo se guarda al retirar al empleado
		Instant fechaFin = "indefinido".equals(c.tipoContrato()) ? null : Fechas.aInstante(c.fechaFin());

		return new Empleado(
				id,
				datos.numeroDocumento(),
				datos.nombres().trim(),
				datos.apellidos().trim(),
				datos.sexo(),
				datos.estadoCivil(),
				datos.profesion().trim(),
				Textos.correo(datos.correoPersonal()),
				datos.telefonoMovil(),
				new Empleado.Direccion(d.direccion().trim(), Textos.limpio(d.barrio()), d.ciudad().trim(),
						d.departamento().trim(), Textos.limpio(d.codigoPostal())),
				new Empleado.Contrato(c.cargo().trim(), c.area(), c.tipoContrato(), Fechas.aInstante(c.fechaInicio()),
						fechaFin, c.salario(), Textos.limpio(c.jornada()), Textos.limpio(c.modalidad())),
				new Empleado.SeguridadSocial(s.eps().trim(), s.fondoPensiones().trim(), s.arl().trim(),
						Textos.limpio(s.cajaCompensacion()), Textos.limpio(s.fondoCesantias())),
				b == null || vacia(b) ? null
						: new Empleado.InformacionBancaria(b.banco().trim(), b.tipoCuenta(), b.numeroCuenta().trim()),
				estado,
				creacion,
				actualizacion);
	}

}
