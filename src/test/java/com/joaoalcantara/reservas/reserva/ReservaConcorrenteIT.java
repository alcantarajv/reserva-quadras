package com.joaoalcantara.reservas.reserva;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
import com.joaoalcantara.reservas.usuario.dominio.Papel;
import com.joaoalcantara.reservas.usuario.dominio.Usuario;
import com.joaoalcantara.reservas.usuario.dominio.UsuarioRepositorio;

/**
 * O teste central do projeto.
 *
 * <p>Um sistema de reservas parece um CRUD ate o momento em que duas pessoas
 * clicam em "reservar" na mesma quadra, no mesmo horario, no mesmo instante.
 * Validar em Java com um {@code SELECT} antes do {@code INSERT} nao resolve:
 * entre a consulta e a gravacao existe uma janela em que a outra transacao passa,
 * e as duas veem o horario livre.</p>
 *
 * <p>Este teste dispara N threads simultaneas contra o mesmo horario e verifica
 * que <strong>exatamente uma</strong> vence — nao por acaso, mas porque a
 * constraint de exclusao do PostgreSQL garante isso no unico ponto sem janela de
 * corrida.</p>
 */
@TesteDeIntegracao
class ReservaConcorrenteIT {

    private static final int THREADS = 16;

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
    private List<Long> usuarioIds;
    private Instant inicio;
    private Instant fim;

    @BeforeEach
    void preparar() {
        // Ordem importa: reservas referenciam usuarios e quadras.
        jdbc.execute("TRUNCATE TABLE reservas, horarios_funcionamento, quadras, usuarios RESTART IDENTITY CASCADE");

        Quadra quadra = new Quadra("Quadra da disputa", Modalidade.FUTSAL, new BigDecimal("120.00"));
        for (DayOfWeek dia : DayOfWeek.values()) {
            quadra.adicionarHorario(dia, LocalTime.of(6, 0), LocalTime.of(23, 0));
        }
        quadraId = quadras.salvar(quadra).getId();

        usuarioIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Usuario usuario = new Usuario("Cliente " + i, "cliente%d@teste.dev".formatted(i),
                    "$2a$10$hashfalsoparateste000000000000000000000000000000000000", Papel.CLIENTE, Instant.now());
            usuarioIds.add(usuarios.salvar(usuario).getId());
        }

        // Amanha as 10:00 no fuso do negocio: futuro, alinhado a grade de 30min e
        // dentro do horario de funcionamento configurado acima.
        ZoneId fuso = propriedades.fusoHorario();
        inicio = LocalDate.now(fuso).plusDays(1).atTime(10, 0).atZone(fuso).toInstant();
        fim = inicio.plus(Duration.ofHours(1));
    }

    @Test
    @DisplayName("com N threads no mesmo horario, exatamente uma reserva e criada")
    void apenasUmaReservaVence() throws InterruptedException {
        AtomicInteger sucessos = new AtomicInteger();
        AtomicInteger conflitos = new AtomicInteger();
        List<Throwable> inesperados = java.util.Collections.synchronizedList(new ArrayList<>());

        // A largada libera todas as threads ao mesmo tempo. Sem ela, as threads
        // iniciariam em sequencia e o teste passaria sem nunca ter havido corrida.
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch chegada = new CountDownLatch(THREADS);

        try (ExecutorService executor = Executors.newFixedThreadPool(THREADS)) {
            for (Long usuarioId : usuarioIds) {
                executor.submit(() -> {
                    try {
                        largada.await();
                        reservaServico.criar(usuarioId, quadraId, inicio, fim);
                        sucessos.incrementAndGet();
                    } catch (Throwable e) {
                        if (ehConflitoDeHorario(e)) {
                            conflitos.incrementAndGet();
                        } else {
                            inesperados.add(e);
                        }
                    } finally {
                        chegada.countDown();
                    }
                });
            }

            largada.countDown();
            assertThat(chegada.await(60, TimeUnit.SECONDS))
                    .as("todas as threads terminaram dentro do tempo")
                    .isTrue();
        }

        assertThat(inesperados)
                .as("nenhuma thread deve falhar por motivo diferente de conflito de horario")
                .isEmpty();

        assertThat(sucessos.get())
                .as("exatamente uma reserva deve vencer a disputa")
                .isEqualTo(1);

        assertThat(conflitos.get())
                .as("todas as outras devem receber conflito de horario")
                .isEqualTo(THREADS - 1);

        // A verificacao que realmente importa: o estado final do banco. Contadores
        // em memoria poderiam mentir; a tabela nao.
        Integer gravadas = jdbc.queryForObject(
                "SELECT count(*) FROM reservas WHERE quadra_id = ? AND status <> 'CANCELADA'",
                Integer.class, quadraId);

        assertThat(gravadas)
                .as("o banco deve conter uma unica reserva ativa para esse horario")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("horarios que nao colidem sao criados em paralelo, sem serializar a quadra")
    void horariosDistintosNaoConflitam() throws InterruptedException {
        AtomicInteger sucessos = new AtomicInteger();
        List<Throwable> falhas = java.util.Collections.synchronizedList(new ArrayList<>());

        int quantidade = 6;
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch chegada = new CountDownLatch(quantidade);

        try (ExecutorService executor = Executors.newFixedThreadPool(quantidade)) {
            for (int i = 0; i < quantidade; i++) {
                // Blocos consecutivos de uma hora: 10-11, 11-12, 12-13...
                // Com intervalo semiaberto [inicio, fim), as bordas nao colidem.
                Instant inicioDaVez = inicio.plus(Duration.ofHours(i));
                Instant fimDaVez = inicioDaVez.plus(Duration.ofHours(1));
                Long usuarioId = usuarioIds.get(i);

                executor.submit(() -> {
                    try {
                        largada.await();
                        reservaServico.criar(usuarioId, quadraId, inicioDaVez, fimDaVez);
                        sucessos.incrementAndGet();
                    } catch (Throwable e) {
                        falhas.add(e);
                    } finally {
                        chegada.countDown();
                    }
                });
            }

            largada.countDown();
            assertThat(chegada.await(60, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(falhas)
                .as("reservas em horarios distintos nao devem conflitar entre si")
                .isEmpty();
        assertThat(sucessos.get()).isEqualTo(quantidade);
    }

    /**
     * O conflito pode chegar embrulhado por camadas do Spring, dependendo de onde
     * a violacao estourou. Procurar na cadeia de causas evita um teste fragil que
     * depende do embrulho exato.
     */
    private boolean ehConflitoDeHorario(Throwable erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof HorarioIndisponivelException) {
                return true;
            }
            if (causa == causa.getCause()) {
                break;
            }
        }
        return false;
    }

}
