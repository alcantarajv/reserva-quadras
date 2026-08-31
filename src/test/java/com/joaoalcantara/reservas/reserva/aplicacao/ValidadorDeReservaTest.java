package com.joaoalcantara.reservas.reserva.aplicacao;

import static com.joaoalcantara.reservas.comum.Fixtures.instante;
import static com.joaoalcantara.reservas.comum.Fixtures.propriedadesPadrao;
import static com.joaoalcantara.reservas.comum.Fixtures.quadraAberta;
import static com.joaoalcantara.reservas.comum.Fixtures.relogioFixo;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.joaoalcantara.reservas.comum.erro.RegraDeNegocioException;
import com.joaoalcantara.reservas.quadra.dominio.Modalidade;
import com.joaoalcantara.reservas.quadra.dominio.Quadra;

/**
 * Regras de criacao de reserva.
 *
 * <p>Teste unitario puro: sem Spring, sem banco, com relogio fixo. Cada regra e
 * verificada isoladamente, e por isso a suite roda em milissegundos.</p>
 */
class ValidadorDeReservaTest {

    private final ValidadorDeReserva validador = new ValidadorDeReserva(propriedadesPadrao(), relogioFixo());

    private void validar(Quadra quadra, Instant inicio, Instant fim) {
        validador.validar(quadra, inicio, fim);
    }

    @Test
    @DisplayName("aceita uma reserva bem formada dentro do horario de funcionamento")
    void aceitaReservaValida() {
        assertThatCode(() -> validar(quadraAberta(),
                instante(2026, 9, 1, 14, 0),
                instante(2026, 9, 1, 15, 0)))
                .doesNotThrowAnyException();
    }

    @Nested
    @DisplayName("tempo")
    class Tempo {

        @Test
        @DisplayName("rejeita reserva que comeca no passado")
        void rejeitaPassado() {
            assertThatThrownBy(() -> validar(quadraAberta(),
                    instante(2026, 9, 1, 8, 0),
                    instante(2026, 9, 1, 9, 0)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("ja passou");
        }

        @Test
        @DisplayName("rejeita reserva alem da antecedencia maxima")
        void rejeitaAntecedenciaExcessiva() {
            assertThatThrownBy(() -> validar(quadraAberta(),
                    instante(2026, 12, 1, 14, 0),
                    instante(2026, 12, 1, 15, 0)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("antecedencia");
        }

        @Test
        @DisplayName("rejeita fim anterior ou igual ao inicio")
        void rejeitaIntervaloInvertido() {
            assertThatThrownBy(() -> validar(quadraAberta(),
                    instante(2026, 9, 1, 15, 0),
                    instante(2026, 9, 1, 14, 0)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("posterior");
        }
    }

    @Nested
    @DisplayName("grade de horarios")
    class Grade {

        @Test
        @DisplayName("rejeita inicio fora dos multiplos de 30 minutos")
        void rejeitaInicioDesalinhado() {
            assertThatThrownBy(() -> validar(quadraAberta(),
                    instante(2026, 9, 1, 14, 7),
                    instante(2026, 9, 1, 15, 7)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("multiplos");
        }

        @Test
        @DisplayName("aceita inicio na meia hora")
        void aceitaMeiaHora() {
            assertThatCode(() -> validar(quadraAberta(),
                    instante(2026, 9, 1, 14, 30),
                    instante(2026, 9, 1, 16, 0)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rejeita duracao abaixo do minimo")
        void rejeitaDuracaoCurta() {
            assertThatThrownBy(() -> validar(quadraAberta(),
                    instante(2026, 9, 1, 14, 0),
                    instante(2026, 9, 1, 14, 30)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("no minimo");
        }

        @Test
        @DisplayName("rejeita duracao acima do maximo")
        void rejeitaDuracaoLonga() {
            assertThatThrownBy(() -> validar(quadraAberta(),
                    instante(2026, 9, 1, 14, 0),
                    instante(2026, 9, 1, 18, 0)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("no maximo");
        }
    }

    @Nested
    @DisplayName("quadra")
    class QuadraTeste {

        @Test
        @DisplayName("rejeita reserva em quadra inativa")
        void rejeitaQuadraInativa() {
            Quadra quadra = quadraAberta();
            quadra.desativar();

            assertThatThrownBy(() -> validar(quadra,
                    instante(2026, 9, 1, 14, 0),
                    instante(2026, 9, 1, 15, 0)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("nao esta disponivel");
        }

        @Test
        @DisplayName("rejeita reserva antes da abertura")
        void rejeitaAntesDaAbertura() {
            Quadra quadra = new Quadra("Coberta", Modalidade.VOLEI, new BigDecimal("80.00"));
            quadra.adicionarHorario(DayOfWeek.TUESDAY, LocalTime.of(18, 0), LocalTime.of(22, 0));

            assertThatThrownBy(() -> validar(quadra,
                    instante(2026, 9, 1, 14, 0),
                    instante(2026, 9, 1, 15, 0)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("nao funciona");
        }

        @Test
        @DisplayName("rejeita reserva que ultrapassa o fechamento")
        void rejeitaAlemDoFechamento() {
            assertThatThrownBy(() -> validar(quadraAberta(),
                    instante(2026, 9, 1, 21, 30),
                    instante(2026, 9, 1, 22, 30)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("nao funciona");
        }

        @Test
        @DisplayName("rejeita reserva no dia da semana em que a quadra nao abre")
        void rejeitaDiaFechado() {
            Quadra quadra = new Quadra("Quadra de sabado", Modalidade.TENIS, new BigDecimal("90.00"));
            quadra.adicionarHorario(DayOfWeek.SATURDAY, LocalTime.of(8, 0), LocalTime.of(20, 0));

            // 01/09/2026 e uma terca-feira.
            assertThatThrownBy(() -> validar(quadra,
                    instante(2026, 9, 1, 14, 0),
                    instante(2026, 9, 1, 15, 0)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("nao funciona");
        }

        @Test
        @DisplayName("rejeita reserva que atravessa duas janelas do mesmo dia")
        void rejeitaAtravessarIntervaloDeAlmoco() {
            Quadra quadra = new Quadra("Com intervalo", Modalidade.FUTSAL, new BigDecimal("100.00"));
            quadra.adicionarHorario(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
            quadra.adicionarHorario(DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(22, 0));

            // 11:30-13:30 nao cabe inteira em nenhuma das duas janelas.
            assertThatThrownBy(() -> validar(quadra,
                    instante(2026, 9, 1, 11, 30),
                    instante(2026, 9, 1, 13, 30)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("nao funciona");
        }

        @Test
        @DisplayName("rejeita reserva que atravessa a virada do dia")
        void rejeitaViradaDoDia() {
            Quadra quadra = new Quadra("Noturna", Modalidade.SOCIETY, new BigDecimal("100.00"));
            quadra.adicionarHorario(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(23, 59));

            assertThatThrownBy(() -> validar(quadra,
                    instante(2026, 9, 1, 23, 0),
                    instante(2026, 9, 2, 0, 30)))
                    .isInstanceOf(RegraDeNegocioException.class)
                    .hasMessageContaining("virada do dia");
        }
    }
}
