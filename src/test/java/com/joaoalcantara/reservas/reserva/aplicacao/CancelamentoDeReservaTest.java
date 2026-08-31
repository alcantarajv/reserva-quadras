package com.joaoalcantara.reservas.reserva.aplicacao;

import static com.joaoalcantara.reservas.comum.Fixtures.AGORA;
import static com.joaoalcantara.reservas.comum.Fixtures.instante;
import static com.joaoalcantara.reservas.comum.Fixtures.propriedadesPadrao;
import static com.joaoalcantara.reservas.comum.Fixtures.quadraAberta;
import static com.joaoalcantara.reservas.comum.Fixtures.relogioFixo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.joaoalcantara.reservas.comum.erro.RecursoNaoEncontradoException;
import com.joaoalcantara.reservas.comum.erro.RegraDeNegocioException;
import com.joaoalcantara.reservas.quadra.dominio.QuadraRepositorio;
import com.joaoalcantara.reservas.reserva.dominio.Reserva;
import com.joaoalcantara.reservas.reserva.dominio.ReservaRepositorio;
import com.joaoalcantara.reservas.reserva.dominio.StatusReserva;
import com.joaoalcantara.reservas.seguranca.UsuarioAutenticado;
import com.joaoalcantara.reservas.usuario.dominio.Papel;
import com.joaoalcantara.reservas.usuario.dominio.Usuario;
import com.joaoalcantara.reservas.usuario.dominio.UsuarioRepositorio;

/**
 * Regras de cancelamento e de visibilidade das reservas.
 *
 * <p>Persistencia entra como duble: o que esta sob teste sao as decisoes do
 * servico — quem pode cancelar, com quanta antecedencia, e quem enxerga o que.</p>
 */
class CancelamentoDeReservaTest {

    private static final Long DONO_ID = 10L;
    private static final Long OUTRO_ID = 99L;

    private final ReservaRepositorio reservas = mock(ReservaRepositorio.class);
    private final QuadraRepositorio quadras = mock(QuadraRepositorio.class);
    private final UsuarioRepositorio usuarios = mock(UsuarioRepositorio.class);

    private final ReservaServico servico = new ReservaServico(
            reservas, quadras, usuarios,
            new ValidadorDeReserva(propriedadesPadrao(), relogioFixo()),
            new CalculadoraDeValor(),
            propriedadesPadrao(),
            relogioFixo());

    private final UsuarioAutenticado dono = new UsuarioAutenticado(DONO_ID, "Dono", Papel.CLIENTE);
    private final UsuarioAutenticado intruso = new UsuarioAutenticado(OUTRO_ID, "Intruso", Papel.CLIENTE);
    private final UsuarioAutenticado admin = new UsuarioAutenticado(1L, "Admin", Papel.ADMIN);

    /** Reserva pertencente a DONO_ID comecando no instante informado. */
    private Reserva reservaComecandoEm(Instant inicio) {
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(DONO_ID);

        Reserva reserva = new Reserva(usuario, quadraAberta(), inicio, inicio.plusSeconds(3600),
                new BigDecimal("120.00"), AGORA);

        when(reservas.porId(anyLong())).thenReturn(Optional.of(reserva));
        return reserva;
    }

    @Test
    @DisplayName("o dono cancela quando ha antecedencia suficiente")
    void donoCancelaComAntecedencia() {
        // Relogio fixo as 10:00; reserva as 20:00 do mesmo dia = 10h de antecedencia,
        // acima do minimo de 6h.
        Reserva reserva = reservaComecandoEm(instante(2026, 9, 1, 20, 0));

        Reserva cancelada = servico.cancelar(1L, dono);

        assertThat(cancelada.getStatus()).isEqualTo(StatusReserva.CANCELADA);
        assertThat(cancelada.getCanceladaEm()).isEqualTo(AGORA);
        assertThat(reserva.getStatus()).isEqualTo(StatusReserva.CANCELADA);
    }

    @Test
    @DisplayName("o dono nao cancela dentro do prazo minimo de antecedencia")
    void donoNaoCancelaEmCimaDaHora() {
        // Reserva as 14:00 com o relogio as 10:00 = 4h, abaixo do minimo de 6h.
        reservaComecandoEm(instante(2026, 9, 1, 14, 0));

        assertThatThrownBy(() -> servico.cancelar(1L, dono))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("antecedencia");
    }

    @Test
    @DisplayName("o ADMIN cancela mesmo em cima da hora")
    void adminIgnoraAntecedencia() {
        reservaComecandoEm(instante(2026, 9, 1, 14, 0));

        Reserva cancelada = servico.cancelar(1L, admin);

        assertThat(cancelada.getStatus()).isEqualTo(StatusReserva.CANCELADA);
    }

    @Test
    @DisplayName("cancelar duas vezes e rejeitado")
    void naoCancelaDuasVezes() {
        Reserva reserva = reservaComecandoEm(instante(2026, 9, 1, 20, 0));
        reserva.cancelar(AGORA);

        assertThatThrownBy(() -> servico.cancelar(1L, dono))
                .isInstanceOf(RegraDeNegocioException.class)
                .hasMessageContaining("ja foi cancelada");
    }

    @Test
    @DisplayName("um cliente que nao e o dono recebe 404, nao 403")
    void reservaDeOutroClienteSomeDaVista() {
        reservaComecandoEm(instante(2026, 9, 1, 20, 0));

        // Responder 403 confirmaria que a reserva existe; 404 nao vaza nada.
        assertThatThrownBy(() -> servico.buscar(1L, intruso))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    @DisplayName("o ADMIN enxerga a reserva de qualquer cliente")
    void adminVeTudo() {
        Reserva reserva = reservaComecandoEm(instante(2026, 9, 1, 20, 0));

        assertThat(servico.buscar(1L, admin)).isSameAs(reserva);
    }

    @Test
    @DisplayName("a listagem do cliente traz so as dele; a do admin, todas")
    void listagemRespeitaOPapel() {
        when(reservas.doUsuario(DONO_ID)).thenReturn(List.of());
        when(reservas.todas()).thenReturn(List.of());

        servico.listar(dono);
        servico.listar(admin);

        org.mockito.Mockito.verify(reservas).doUsuario(DONO_ID);
        org.mockito.Mockito.verify(reservas).todas();
    }

    @Test
    @DisplayName("reserva inexistente resulta em 404")
    void reservaInexistente() {
        when(reservas.porId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.cancelar(1L, dono))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
