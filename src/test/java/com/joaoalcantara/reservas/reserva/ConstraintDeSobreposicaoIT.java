package com.joaoalcantara.reservas.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.joaoalcantara.reservas.comum.TesteDeIntegracao;
import com.joaoalcantara.reservas.comum.config.PropriedadesDeReserva;
import com.joaoalcantara.reservas.quadra.dominio.Modalidade;
import com.joaoalcantara.reservas.quadra.dominio.Quadra;
import com.joaoalcantara.reservas.quadra.dominio.QuadraRepositorio;
import com.joaoalcantara.reservas.reserva.aplicacao.ReservaServico;
import com.joaoalcantara.reservas.reserva.dominio.HorarioIndisponivelException;
import com.joaoalcantara.reservas.reserva.dominio.Reserva;
import com.joaoalcantara.reservas.seguranca.UsuarioAutenticado;
import com.joaoalcantara.reservas.usuario.dominio.Papel;
import com.joaoalcantara.reservas.usuario.dominio.Usuario;
import com.joaoalcantara.reservas.usuario.dominio.UsuarioRepositorio;

/**
 * Bordas da constraint de exclusao, verificadas sem concorrencia.
 *
 * <p>O teste multi-thread prova que a constraint resolve a corrida; este prova que
 * ela decide corretamente <em>o que</em> e sobreposicao. Sao perguntas diferentes,
 * e a segunda e onde mora a maioria dos bugs sutis de agenda.</p>
 */
@TesteDeIntegracao
class ConstraintDeSobreposicaoIT {

    @Autowired
    private ReservaServico reservaServico;

    @Autowired
    private QuadraRepositorio quadras;

    @Autowired
    private UsuarioRepositorio usuarios;

    @Autowired
    private PropriedadesDeReserva propriedades;

    @Autowired
    private JdbcTemplate jdbc;

    private Long quadraId;
    private Long clienteId;
    private UsuarioAutenticado cliente;
    private Instant dezHoras;

    @BeforeEach
    void preparar() {
        jdbc.execute("TRUNCATE TABLE reservas, horarios_funcionamento, quadras, usuarios RESTART IDENTITY CASCADE");

        Quadra quadra = new Quadra("Quadra unica", Modalidade.SOCIETY, new BigDecimal("100.00"));
        for (DayOfWeek dia : DayOfWeek.values()) {
            quadra.adicionarHorario(dia, LocalTime.of(6, 0), LocalTime.of(23, 0));
        }
        quadraId = quadras.salvar(quadra).getId();

        Usuario usuario = new Usuario("Cliente", "cliente@teste.dev",
                "$2a$10$hashfalsoparateste000000000000000000000000000000000000", Papel.CLIENTE, Instant.now());
        clienteId = usuarios.salvar(usuario).getId();
        cliente = new UsuarioAutenticado(clienteId, "Cliente", Papel.CLIENTE);

        ZoneId fuso = propriedades.fusoHorario();
        dezHoras = LocalDate.now(fuso).plusDays(1).atTime(10, 0).atZone(fuso).toInstant();
    }

    private Reserva reservar(int horaInicio, int minutoInicio, int duracaoMinutos) {
        Instant inicio = dezHoras.plus(Duration.ofHours(horaInicio - 10)).plus(Duration.ofMinutes(minutoInicio));
        return reservaServico.criar(clienteId, quadraId, inicio, inicio.plus(Duration.ofMinutes(duracaoMinutos)));
    }

    @Test
    @DisplayName("blocos encostados nao conflitam: 10-11 e 11-12 convivem")
    void bordasNaoColidem() {
        reservar(10, 0, 60);

        assertThatCode(() -> reservar(11, 0, 60)).doesNotThrowAnyException();

        assertThat(contarAtivas()).isEqualTo(2);
    }

    @Test
    @DisplayName("sobreposicao parcial e rejeitada: 10-11 e 10:30-11:30")
    void sobreposicaoParcial() {
        reservar(10, 0, 60);

        assertThatThrownBy(() -> reservar(10, 30, 60))
                .isInstanceOf(HorarioIndisponivelException.class);

        assertThat(contarAtivas()).isEqualTo(1);
    }

    @Test
    @DisplayName("intervalo contido em outro e rejeitado")
    void intervaloContido() {
        reservar(10, 0, 120);

        assertThatThrownBy(() -> reservar(10, 30, 60))
                .isInstanceOf(HorarioIndisponivelException.class);

        assertThat(contarAtivas()).isEqualTo(1);
    }

    @Test
    @DisplayName("cancelar libera o horario para uma nova reserva")
    void cancelamentoLiberaOHorario() {
        Reserva primeira = reservar(10, 0, 60);

        reservaServico.cancelar(primeira.getId(), cliente);

        // Se o WHERE (status <> 'CANCELADA') da constraint estivesse ausente, este
        // horario ficaria bloqueado para sempre depois de um cancelamento.
        assertThatCode(() -> reservar(10, 0, 60)).doesNotThrowAnyException();

        assertThat(contarAtivas()).isEqualTo(1);
    }

    @Test
    @DisplayName("a mesma sobreposicao em quadras diferentes e permitida")
    void quadrasDiferentesNaoConflitam() {
        reservar(10, 0, 60);

        Quadra outra = new Quadra("Segunda quadra", Modalidade.VOLEI, new BigDecimal("90.00"));
        for (DayOfWeek dia : DayOfWeek.values()) {
            outra.adicionarHorario(dia, LocalTime.of(6, 0), LocalTime.of(23, 0));
        }
        Long outraId = quadras.salvar(outra).getId();

        assertThatCode(() -> reservaServico.criar(clienteId, outraId, dezHoras, dezHoras.plus(Duration.ofHours(1))))
                .doesNotThrowAnyException();
    }

    private Integer contarAtivas() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM reservas WHERE quadra_id = ? AND status <> 'CANCELADA'",
                Integer.class, quadraId);
    }
}
