package com.vidacruz.clinical.application.dto;

/** Error body with {@code code}, {@code message} and {@code correlationId} (API-04, NFR-20). */
public record ErrorResponseDTO(String code, String message, String correlationId) {
}
