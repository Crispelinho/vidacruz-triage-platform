package com.vidacruz.triageservice.domain.exception;

/**
 * RF-05 request failure. {@code field} names the offending query parameter under the NFR-20 error body.
 */
public class InvalidReportRequestException extends RuntimeException {

	public static final String RANGE_CODE = "INVALID_REPORT_RANGE";
	public static final String VALIDATION_CODE = "VALIDATION_ERROR";

	private final String code;
	private final String field;

	private InvalidReportRequestException(String code, String field, String message) {
		super(message);
		this.code = code;
		this.field = field;
	}

	public static InvalidReportRequestException missing(String field) {
		return new InvalidReportRequestException(VALIDATION_CODE, field,
				"El parámetro " + field + " es obligatorio");
	}

	public static InvalidReportRequestException malformed(String field) {
		return new InvalidReportRequestException(VALIDATION_CODE, field,
				"El parámetro " + field + " debe ser una fecha y hora ISO 8601 con zona, por ejemplo "
						+ "2026-11-25T08:00:00-05:00 (en la URL, el signo + se escribe %2B)");
	}

	public static InvalidReportRequestException invalidRange(String field, String message) {
		return new InvalidReportRequestException(RANGE_CODE, field, message);
	}

	public String code() {
		return code;
	}

	public String field() {
		return field;
	}
}
