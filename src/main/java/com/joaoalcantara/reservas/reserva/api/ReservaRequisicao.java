package com.joaoalcantara.reservas.reserva.api;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/**
 * Entrada de criacao de reserva.
 *
 * <p>Os instantes chegam em ISO-8601 com fuso ({@code 2026-09-01T14:00:00Z} ou
 * {@code 2026-09-01T11:00:00-03:00}). Exigir o fuso do cliente elimina a
 * ambiguidade de "14h" — a API nunca tem que adivinhar de onde o horario veio.</p>
 */
public record ReservaRequisicao(
        @NotNull(message = "e obrigatoria") Long quadraId,
        @NotNull(message = "e obrigatorio") Instant inicio,
        @NotNull(message = "e obrigatorio") Instant fim) {
}
