package com.joaoalcantara.reservas.reserva.dominio;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Porta de persistencia de reservas. */
public interface ReservaRepositorio {

    /**
     * Persiste a reserva, forcando a ida ao banco imediatamente.
     *
     * <p>O flush e obrigatorio aqui: se a escrita so acontecesse no commit, a
     * violacao da constraint de exclusao estouraria depois que o metodo
     * transacional ja retornou, virando um erro 500 opaco em vez de um 409.
     * Fazendo o flush dentro do adaptador, a violacao e capturada e traduzida
     * para {@link HorarioIndisponivelException}.</p>
     *
     * @throws HorarioIndisponivelException se o intervalo colidir com outra reserva ativa
     */
    Reserva salvar(Reserva reserva);

    Optional<Reserva> porId(Long id);

    List<Reserva> doUsuario(Long usuarioId);

    List<Reserva> todas();

    /** Reservas que ocupam a agenda de uma quadra dentro de uma janela — base da consulta de disponibilidade. */
    List<Reserva> ativasDaQuadraNoPeriodo(Long quadraId, Instant inicio, Instant fim);
}
