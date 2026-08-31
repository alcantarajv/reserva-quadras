package com.joaoalcantara.reservas.quadra.api;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import com.joaoalcantara.reservas.quadra.dominio.HorarioFuncionamento;
import com.joaoalcantara.reservas.quadra.dominio.Modalidade;
import com.joaoalcantara.reservas.quadra.dominio.Quadra;

public record QuadraResposta(
        Long id,
        String nome,
        Modalidade modalidade,
        BigDecimal precoHora,
        boolean ativa,
        List<HorarioFuncionamentoResposta> horarios) {

    public static QuadraResposta de(Quadra quadra) {
        List<HorarioFuncionamentoResposta> horarios = quadra.getHorarios().stream()
                .sorted(Comparator.comparing(HorarioFuncionamento::getDiaSemana).thenComparing(HorarioFuncionamento::getAbertura))
                .map(HorarioFuncionamentoResposta::de)
                .toList();
        return new QuadraResposta(quadra.getId(), quadra.getNome(), quadra.getModalidade(),
                quadra.getPrecoHora(), quadra.isAtiva(), horarios);
    }

    /** Versao sem horarios, para listagens — evita carregar a colecao. */
    public static QuadraResposta resumida(Quadra quadra) {
        return new QuadraResposta(quadra.getId(), quadra.getNome(), quadra.getModalidade(),
                quadra.getPrecoHora(), quadra.isAtiva(), List.of());
    }
}
