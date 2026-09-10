package com.joaoalcantara.reservas.comum.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Parametros de negocio que variam por instalacao, lidos de configuracao.
 *
 * <p>Deixar esses numeros em configuracao (e nao espalhados como literais no codigo)
 * permite que o mesmo binario atenda um clube que aluga em blocos de 1h e outro que
 * aluga em blocos de 30min, sem recompilar.</p>
 *
 * <p>{@code fusoHorario} merece atencao: instantes sao persistidos em UTC, mas
 * "a quadra abre as 8h" e uma afirmacao sobre a hora <em>local</em> do clube.
 * Sem um fuso de negocio explicito, a mesma reserva passaria ou falharia na
 * validacao de funcionamento conforme o fuso do servidor — exatamente o tipo de
 * bug que so aparece em producao.</p>
 */
@Validated
@ConfigurationProperties(prefix = "reservas")
public record PropriedadesDeReserva(
        @NotNull ZoneId fusoHorario,
        @Positive int duracaoMinimaMinutos,
        @Positive int duracaoMaximaMinutos,
        @Positive int granularidadeMinutos,
        @Min(0) int antecedenciaMinimaCancelamentoHoras,
        @Positive int antecedenciaMaximaDias) {
}
