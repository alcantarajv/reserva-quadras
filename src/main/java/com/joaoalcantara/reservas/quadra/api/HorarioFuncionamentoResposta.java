package com.joaoalcantara.reservas.quadra.api;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.joaoalcantara.reservas.quadra.dominio.HorarioFuncionamento;

public record HorarioFuncionamentoResposta(
        DayOfWeek diaSemana,
        @JsonFormat(pattern = "HH:mm") LocalTime abertura,
        @JsonFormat(pattern = "HH:mm") LocalTime fechamento) {

    public static HorarioFuncionamentoResposta de(HorarioFuncionamento horario) {
        return new HorarioFuncionamentoResposta(horario.getDiaSemana(), horario.getAbertura(), horario.getFechamento());
    }
}
