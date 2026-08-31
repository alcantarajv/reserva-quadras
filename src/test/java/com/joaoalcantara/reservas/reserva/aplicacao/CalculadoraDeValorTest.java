package com.joaoalcantara.reservas.reserva.aplicacao;

import static com.joaoalcantara.reservas.comum.Fixtures.instante;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.joaoalcantara.reservas.quadra.dominio.Modalidade;
import com.joaoalcantara.reservas.quadra.dominio.Quadra;

class CalculadoraDeValorTest {

    private final CalculadoraDeValor calculadora = new CalculadoraDeValor();

    private Quadra quadraDe(String precoHora) {
        return new Quadra("Quadra", Modalidade.FUTSAL, new BigDecimal(precoHora));
    }

    @Test
    @DisplayName("uma hora cheia custa exatamente o preco/hora")
    void horaCheia() {
        BigDecimal valor = calculadora.calcular(quadraDe("120.00"),
                instante(2026, 9, 1, 14, 0),
                instante(2026, 9, 1, 15, 0));

        assertThat(valor).isEqualByComparingTo("120.00");
    }

    @Test
    @DisplayName("meia hora custa metade")
    void meiaHora() {
        BigDecimal valor = calculadora.calcular(quadraDe("120.00"),
                instante(2026, 9, 1, 14, 0),
                instante(2026, 9, 1, 14, 30));

        assertThat(valor).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("uma hora e meia cobra proporcional, sem arredondar a duracao")
    void horaEMeia() {
        BigDecimal valor = calculadora.calcular(quadraDe("100.00"),
                instante(2026, 9, 1, 14, 0),
                instante(2026, 9, 1, 15, 30));

        assertThat(valor).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("preco com dizima e arredondado a dois centavos, meio para cima")
    void arredondamento() {
        // 100.00 / 3 * 1.5h nao fecha em centavos exatos; o resultado precisa vir
        // com duas casas, sem propagar erro de ponto flutuante.
        BigDecimal valor = calculadora.calcular(quadraDe("33.33"),
                instante(2026, 9, 1, 14, 0),
                instante(2026, 9, 1, 15, 30));

        assertThat(valor).isEqualByComparingTo("50.00");
        assertThat(valor.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("o valor sempre tem duas casas decimais")
    void escalaMonetaria() {
        BigDecimal valor = calculadora.calcular(quadraDe("80"),
                instante(2026, 9, 1, 14, 0),
                instante(2026, 9, 1, 16, 0));

        assertThat(valor.scale()).isEqualTo(2);
        assertThat(valor).isEqualByComparingTo("160.00");
    }
}
