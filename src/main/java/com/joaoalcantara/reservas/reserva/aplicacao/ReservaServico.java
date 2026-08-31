package com.joaoalcantara.reservas.reserva.aplicacao;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.joaoalcantara.reservas.comum.config.PropriedadesDeReserva;
import com.joaoalcantara.reservas.comum.erro.RecursoNaoEncontradoException;
import com.joaoalcantara.reservas.comum.erro.RegraDeNegocioException;
import com.joaoalcantara.reservas.quadra.dominio.Quadra;
import com.joaoalcantara.reservas.quadra.dominio.QuadraRepositorio;
import com.joaoalcantara.reservas.reserva.dominio.Reserva;
import com.joaoalcantara.reservas.reserva.dominio.ReservaRepositorio;
import com.joaoalcantara.reservas.reserva.dominio.StatusReserva;
import com.joaoalcantara.reservas.seguranca.UsuarioAutenticado;
import com.joaoalcantara.reservas.usuario.dominio.Usuario;
import com.joaoalcantara.reservas.usuario.dominio.UsuarioRepositorio;

/** Casos de uso de reserva. */
@Service
public class ReservaServico {

    private final ReservaRepositorio reservas;
    private final QuadraRepositorio quadras;
    private final UsuarioRepositorio usuarios;
    private final ValidadorDeReserva validador;
    private final CalculadoraDeValor calculadora;
    private final PropriedadesDeReserva propriedades;
    private final Clock relogio;

    public ReservaServico(ReservaRepositorio reservas, QuadraRepositorio quadras, UsuarioRepositorio usuarios,
                          ValidadorDeReserva validador, CalculadoraDeValor calculadora,
                          PropriedadesDeReserva propriedades, Clock relogio) {
        this.reservas = reservas;
        this.quadras = quadras;
        this.usuarios = usuarios;
        this.validador = validador;
        this.calculadora = calculadora;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    /**
     * Cria uma reserva.
     *
     * <p>Repare na ausencia de qualquer consulta do tipo "ja existe reserva neste
     * horario?". Ela seria inutil: entre o SELECT e o INSERT existe uma janela em
     * que outra transacao insere a reserva concorrente, e a checagem passaria nas
     * duas. Quem garante a exclusividade e a constraint de exclusao no banco; o
     * repositorio traduz a violacao em 409 Conflict.</p>
     *
     * <p>Consequencia pratica: em condicao de corrida, N-1 requisicoes recebem 409
     * e exatamente uma vence — que e o comportamento correto e o que o teste de
     * concorrencia verifica.</p>
     */
    @Transactional
    public Reserva criar(Long usuarioId, Long quadraId, Instant inicio, Instant fim) {
        Usuario usuario = usuarios.porId(usuarioId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuario", usuarioId));

        Quadra quadra = quadras.porIdComHorarios(quadraId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Quadra", quadraId));

        validador.validar(quadra, inicio, fim);

        BigDecimal valor = calculadora.calcular(quadra, inicio, fim);
        Reserva reserva = new Reserva(usuario, quadra, inicio, fim, valor, relogio.instant());

        return reservas.salvar(reserva);
    }

    /**
     * Cancela uma reserva.
     *
     * <p>A autorizacao e por dado, nao por rota: um CLIENTE so cancela o que e
     * dele. Deixar isso no servico garante que qualquer caminho que chegue aqui
     * — inclusive um endpoint futuro — herde a regra.</p>
     */
    @Transactional
    public Reserva cancelar(Long reservaId, UsuarioAutenticado solicitante) {
        Reserva reserva = buscar(reservaId, solicitante);

        if (reserva.getStatus() == StatusReserva.CANCELADA) {
            throw new RegraDeNegocioException("reserva-ja-cancelada", "Esta reserva ja foi cancelada");
        }
        if (reserva.getStatus() == StatusReserva.CONCLUIDA) {
            throw new RegraDeNegocioException("reserva-ja-concluida",
                    "Uma reserva ja concluida nao pode ser cancelada");
        }

        // O ADMIN ignora a antecedencia minima: ele precisa poder liberar a agenda
        // em caso de imprevisto, e a regra existe para proteger o clube do cliente,
        // nao o clube de si mesmo.
        if (!solicitante.ehAdmin()) {
            validarAntecedenciaDeCancelamento(reserva);
        }

        reserva.cancelar(relogio.instant());
        return reserva;
    }

    private void validarAntecedenciaDeCancelamento(Reserva reserva) {
        Duration antecedencia = Duration.between(relogio.instant(), reserva.getInicio());
        Duration minima = Duration.ofHours(propriedades.antecedenciaMinimaCancelamentoHoras());

        if (antecedencia.compareTo(minima) < 0) {
            throw new RegraDeNegocioException("cancelamento-fora-do-prazo",
                    "O cancelamento exige no minimo %d horas de antecedencia"
                            .formatted(propriedades.antecedenciaMinimaCancelamentoHoras()));
        }
    }

    @Transactional(readOnly = true)
    public Reserva buscar(Long reservaId, UsuarioAutenticado solicitante) {
        Reserva reserva = reservas.porId(reservaId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Reserva", reservaId));

        if (!solicitante.ehAdmin() && !reserva.pertenceA(solicitante.id())) {
            // 404, e nao 403: responder "existe, mas nao e sua" ja vaza a
            // informacao de que aquela reserva existe.
            throw RecursoNaoEncontradoException.de("Reserva", reservaId);
        }
        return reserva;
    }

    @Transactional(readOnly = true)
    public List<Reserva> listar(UsuarioAutenticado solicitante) {
        return solicitante.ehAdmin() ? reservas.todas() : reservas.doUsuario(solicitante.id());
    }
}
