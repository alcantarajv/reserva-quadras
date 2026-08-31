package com.joaoalcantara.reservas.reserva.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.joaoalcantara.reservas.reserva.dominio.Reserva;
import com.joaoalcantara.reservas.reserva.dominio.StatusReserva;

public record ReservaResposta(
        Long id,
        Long quadraId,
        String quadraNome,
        Long usuarioId,
        Instant inicio,
        Instant fim,
        StatusReserva status,
        BigDecimal valor,
        Instant criadaEm,
        Instant canceladaEm) {

    public static ReservaResposta de(Reserva reserva) {
        return new ReservaResposta(
                reserva.getId(),
                reserva.getQuadra().getId(),
                reserva.getQuadra().getNome(),
                reserva.getUsuario().getId(),
                reserva.getInicio(),
                reserva.getFim(),
                reserva.getStatus(),
                reserva.getValor(),
                reserva.getCriadaEm(),
                reserva.getCanceladaEm());
    }
}
