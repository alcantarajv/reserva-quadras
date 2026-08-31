package com.joaoalcantara.reservas.quadra.api;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotNull;

public record HorarioFuncionamentoRequisicao(
        @NotNull(message = "e obrigatorio") DayOfWeek diaSemana,
        @NotNull(message = "e obrigatorio") @JsonFormat(pattern = "HH:mm") LocalTime abertura,
        @NotNull(message = "e obrigatorio") @JsonFormat(pattern = "HH:mm") LocalTime fechamento) {
}
