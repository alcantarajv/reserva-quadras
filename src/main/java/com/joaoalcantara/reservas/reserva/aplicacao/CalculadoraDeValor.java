package com.joaoalcantara.reservas.reserva.aplicacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.joaoalcantara.reservas.quadra.dominio.Quadra;

/**
 * Calcula o valor de uma reserva a partir do preco/hora da quadra.
 *
 * <p>Tudo em {@link BigDecimal}: dinheiro nunca em {@code double}, porque
 * 0.1 + 0.2 nao da 0.3 em ponto flutuante binario e o erro se acumula na fatura.</p>
 */
@Component
public class CalculadoraDeValor {

    private static final int ESCALA_MONETARIA = 2;
    private static final BigDecimal MINUTOS_POR_HORA = BigDecimal.valueOf(60);

    public BigDecimal calcular(Quadra quadra, Instant inicio, Instant fim) {
        BigDecimal minutos = BigDecimal.valueOf(Duration.between(inicio, fim).toMinutes());
        BigDecimal horas = minutos.divide(MINUTOS_POR_HORA, 10, RoundingMode.HALF_UP);
        // Arredondamento so no fim, sobre o total: arredondar as horas antes
        // distorceria reservas de 90 minutos.
        return quadra.getPrecoHora().multiply(horas).setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }
}
