package com.joaoalcantara.reservas.quadra.aplicacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.joaoalcantara.reservas.comum.erro.ConflitoException;
import com.joaoalcantara.reservas.comum.erro.RegraDeNegocioException;
import com.joaoalcantara.reservas.quadra.api.HorarioFuncionamentoRequisicao;
import com.joaoalcantara.reservas.quadra.api.QuadraRequisicao;
import com.joaoalcantara.reservas.quadra.dominio.Modalidade;
import com.joaoalcantara.reservas.quadra.dominio.Quadra;
import com.joaoalcantara.reservas.quadra.dominio.QuadraRepositorio;

/**
 * Validacoes de coerencia da grade de funcionamento.
 *
 * <p>Sao regras que Bean Validation nao alcanca, porque dependem da relacao entre
 * varios campos — e nao do formato de cada um isoladamente.</p>
 */
class QuadraServicoTest {

    private final QuadraRepositorio repositorio = mock(QuadraRepositorio.class);
    private final QuadraServico servico = new QuadraServico(repositorio);

    @BeforeEach
    void devolveOQueRecebe() {
        when(repositorio.salvar(any(Quadra.class))).thenAnswer(chamada -> chamada.getArgument(0));
        when(repositorio.existeComNome(anyString())).thenReturn(false);
    }

    private QuadraRequisicao requisicaoCom(HorarioFuncionamentoRequisicao... horarios) {
        return new QuadraRequisicao("Quadra Central", Modalidade.FUTSAL, new BigDecimal("120.00"), List.of(horarios));
    }

    private HorarioFuncionamentoRequisicao horario(DayOfWeek dia, int aberturaHora, int fechamentoHora) {
        return new HorarioFuncionamentoRequisicao(dia, LocalTime.of(aberturaHora, 0), LocalTime.of(fechamentoHora, 0));
    }

    @Test
    @DisplayName("cria a quadra com a grade informada")
    void criaComGrade() {
        Quadra quadra = servico.criar(requisicaoCom(
                horario(DayOfWeek.MONDAY, 8, 22),
                horario(DayOfWeek.TUESDAY, 8, 22)));

        assertThat(quadra.getHorarios()).hasSize(2);
        assertThat(quadra.isAtiva()).isTrue();
        assertThat(quadra.funcionaEm(DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(15, 0))).isTrue();
    }

    @Test
    @DisplayName("rejeita nome ja usado por outra quadra")
    void rejeitaNomeDuplicado() {
        when(repositorio.existeComNome(anyString())).thenReturn(true);

        assertThatThrownBy(() -> servico.criar(requisicaoCom(horario(DayOfWeek.MONDAY, 8, 22))))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("Ja existe");
    }

    @Test
    @DisplayName("rejeita fechamento anterior a abertura")
    void rejeitaJanelaInvertida() {
        assertThatThrownBy(() -> servico.criar(requisicaoCom(horario(DayOfWeek.MONDAY, 22, 8))))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("posterior a abertura");
    }

    @Test
    @DisplayName("rejeita janelas que se sobrepoem no mesmo dia")
    void rejeitaJanelasSobrepostas() {
        assertThatThrownBy(() -> servico.criar(requisicaoCom(
                horario(DayOfWeek.MONDAY, 8, 12),
                horario(DayOfWeek.MONDAY, 10, 14))))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("se sobrepoem");
    }

    @Test
    @DisplayName("aceita duas janelas no mesmo dia quando ha intervalo entre elas")
    void aceitaJanelasComIntervalo() {
        assertThatCode(() -> servico.criar(requisicaoCom(
                horario(DayOfWeek.MONDAY, 8, 12),
                horario(DayOfWeek.MONDAY, 14, 22))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("janelas encostadas no mesmo dia sao aceitas")
    void aceitaJanelasEncostadas() {
        // 08-12 e 12-18: nao ha sobreposicao, so continuidade.
        assertThatCode(() -> servico.criar(requisicaoCom(
                horario(DayOfWeek.MONDAY, 8, 12),
                horario(DayOfWeek.MONDAY, 12, 18))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("mesmo horario em dias diferentes nao e sobreposicao")
    void diasDiferentesNaoSobrepoem() {
        assertThatCode(() -> servico.criar(requisicaoCom(
                horario(DayOfWeek.MONDAY, 8, 22),
                horario(DayOfWeek.TUESDAY, 8, 22))))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("desativar impede novas reservas mas mantem a grade")
    void desativarPreservaGrade() {
        Quadra quadra = servico.criar(requisicaoCom(horario(DayOfWeek.MONDAY, 8, 22)));
        when(repositorio.porIdComHorarios(1L)).thenReturn(java.util.Optional.of(quadra));

        Quadra desativada = servico.alterarSituacao(1L, false);

        assertThat(desativada.isAtiva()).isFalse();
        assertThat(desativada.getHorarios()).hasSize(1);
    }

    @Test
    @DisplayName("quadra sem grade e criada, mas nao funciona em horario nenhum")
    void semGradeNaoFunciona() {
        Quadra quadra = servico.criar(new QuadraRequisicao(
                "Sem grade", Modalidade.TENIS, new BigDecimal("50.00"), List.of()));

        assertThat(quadra.getHorarios()).isEmpty();
        assertThat(quadra.funcionaEm(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0))).isFalse();
    }
}
