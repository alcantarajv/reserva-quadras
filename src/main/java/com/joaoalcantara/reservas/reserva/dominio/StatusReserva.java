package com.joaoalcantara.reservas.reserva.dominio;

/**
 * Ciclo de vida da reserva: CONFIRMADA -> CONCLUIDA ou CANCELADA.
 *
 * <p>Nao existe estado "PENDENTE": sem pagamento no fluxo, a reserva nasce
 * confirmada. Um estado a mais que nada distingue so acrescenta transicoes
 * para testar.</p>
 */
public enum StatusReserva {
    CONFIRMADA,
    CONCLUIDA,
    CANCELADA;

    public boolean ocupaAgenda() {
        return this != CANCELADA;
    }
}
