package com.sistemasintegrados.helpdesk.comun;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Las fechas sin hora (inicio de un contrato, vigencia de un paquete) se guardan
 * a medianoche UTC, igual que new Date("2026-09-01") en 02_datos_semilla.js.
 */
public final class Fechas {

	private Fechas() {
	}

	public static Instant aInstante(LocalDate fecha) {
		return fecha == null ? null : fecha.atStartOfDay(ZoneOffset.UTC).toInstant();
	}

}
