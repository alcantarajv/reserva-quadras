package com.joaoalcantara.reservas.comum;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.joaoalcantara.reservas.comum.config.PropriedadesDeReserva;
import com.joaoalcantara.reservas.quadra.dominio.Modalidade;
import com.joaoalcantara.reservas.quadra.dominio.Quadra;

/** Objetos de apoio compartilhados pelos testes. */
public final class Fixtures {

    /** Fuso de negocio usado nos testes; o mesmo do application.properties. */
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    /**
     * Relogio fixo numa terca-feira, 01/09/2026, as 10:00 em Sao Paulo.
     * Toda regra que depende de tempo e avaliada contra este instante, o que
     * torna os testes reprodutiveis em qualquer maquina e em qualquer dia.
     */
    public static final Instant AGORA = instante(2026, 9, 1, 10, 0);

    private Fixtures() {
    }

    public static Clock relogioFixo() {
        return Clock.fixed(AGORA, ZoneOffset.UTC);
    }

    /** Converte uma data/hora local de Sao Paulo no instante correspondente. */
    public static Instant instante(int ano, int mes, int dia, int hora, int minuto) {
        return LocalDateTime.of(ano, mes, dia, hora, minuto).atZone(FUSO).toInstant();
    }

    public static PropriedadesDeReserva propriedadesPadrao() {
        return new PropriedadesDeReserva(FUSO, 60, 180, 30, 6, 30);
    }

    /** Quadra ativa que funciona de segunda a domingo, das 08:00 as 22:00. */
    public static Quadra quadraAberta() {
        Quadra quadra = new Quadra("Quadra Central", Modalidade.FUTSAL, new BigDecimal("120.00"));
        for (DayOfWeek dia : DayOfWeek.values()) {
            quadra.adicionarHorario(dia, LocalTime.of(8, 0), LocalTime.of(22, 0));
        }
        return quadra;
    }
}
