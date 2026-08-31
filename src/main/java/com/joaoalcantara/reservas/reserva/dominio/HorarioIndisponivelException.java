package com.joaoalcantara.reservas.reserva.dominio;

import com.joaoalcantara.reservas.comum.erro.ConflitoException;

/**
 * Outra reserva ja ocupa aquele intervalo naquela quadra.
 *
 * <p>Esta excecao nasce da traducao de uma violacao de constraint do banco, e nao
 * de uma consulta previa: e o unico ponto do sistema em que se sabe, sem janela
 * de corrida, que o horario foi perdido para outra transacao.</p>
 */
public class HorarioIndisponivelException extends ConflitoException {

    public HorarioIndisponivelException() {
        super("horario-indisponivel",
                "Este horario ja foi reservado para esta quadra. Escolha outro.");
    }
}
