package com.joaoalcantara.reservas.reserva.aplicacao;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.joaoalcantara.reservas.comum.config.PropriedadesDeReserva;
import com.joaoalcantara.reservas.comum.erro.RegraDeNegocioException;
import com.joaoalcantara.reservas.quadra.dominio.Quadra;

/**
 * Regras de negocio da criacao de reserva.
 *
 * <p>Isolado do servico de propósito: aqui nao ha banco, transacao nem HTTP —
 * so {@link Clock}, configuracao e a quadra. Isso torna cada regra testavel por
 * teste unitario puro, com relogio fixo, sem subir contexto Spring.</p>
 *
 * <p>Note o que este validador <em>nao</em> faz: verificar se o horario ja esta
 * ocupado. Essa checagem nao pode ser feita aqui de forma confiavel, porque entre
 * a consulta e o INSERT existe uma janela em que outra transacao passa. A garantia
 * de nao-sobreposicao fica no banco, via constraint de exclusao.</p>
 */
@Component
public class ValidadorDeReserva {

    private final PropriedadesDeReserva propriedades;
    private final Clock relogio;

    public ValidadorDeReserva(PropriedadesDeReserva propriedades, Clock relogio) {
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    public void validar(Quadra quadra, Instant inicio, Instant fim) {
        validarOrdem(inicio, fim);
        validarQuadraAtiva(quadra);
        validarNaoEstaNoPassado(inicio);
        validarAntecedenciaMaxima(inicio);
        validarGranularidade(inicio, fim);
        validarDuracao(inicio, fim);
        validarHorarioDeFuncionamento(quadra, inicio, fim);
    }

    private void validarOrdem(Instant inicio, Instant fim) {
        if (!fim.isAfter(inicio)) {
            throw new RegraDeNegocioException("intervalo-invalido",
                    "O fim da reserva deve ser posterior ao inicio");
        }
    }

    private void validarQuadraAtiva(Quadra quadra) {
        if (!quadra.isAtiva()) {
            throw new RegraDeNegocioException("quadra-inativa",
                    "A quadra '%s' nao esta disponivel para reservas".formatted(quadra.getNome()));
        }
    }

    private void validarNaoEstaNoPassado(Instant inicio) {
        if (inicio.isBefore(relogio.instant())) {
            throw new RegraDeNegocioException("reserva-no-passado",
                    "Nao e possivel reservar um horario que ja passou");
        }
    }

    /**
     * Impede reservas absurdamente distantes, que travariam a agenda por meses.
     * O limite e configuravel porque e politica comercial, nao lei da natureza.
     */
    private void validarAntecedenciaMaxima(Instant inicio) {
        Instant limite = relogio.instant().plus(propriedades.antecedenciaMaximaDias(), ChronoUnit.DAYS);
        if (inicio.isAfter(limite)) {
            throw new RegraDeNegocioException("antecedencia-excedida",
                    "Reservas so podem ser feitas com ate %d dias de antecedencia"
                            .formatted(propriedades.antecedenciaMaximaDias()));
        }
    }

    /**
     * Alinha inicio e duracao a grade de horarios.
     *
     * <p>Sem isso, uma reserva das 09:07 as 10:07 fragmentaria a agenda e deixaria
     * buracos curtos demais para vender.</p>
     */
    private void validarGranularidade(Instant inicio, Instant fim) {
        int granularidade = propriedades.granularidadeMinutos();
        LocalDateTime inicioLocal = LocalDateTime.ofInstant(inicio, fusoDoNegocio());

        boolean inicioAlinhado = inicioLocal.getSecond() == 0
                && inicioLocal.getNano() == 0
                && inicioLocal.getMinute() % granularidade == 0;

        if (!inicioAlinhado) {
            throw new RegraDeNegocioException("inicio-desalinhado",
                    "O inicio da reserva deve cair em multiplos de %d minutos".formatted(granularidade));
        }

        long duracaoMinutos = Duration.between(inicio, fim).toMinutes();
        if (duracaoMinutos % granularidade != 0) {
            throw new RegraDeNegocioException("duracao-desalinhada",
                    "A duracao deve ser multipla de %d minutos".formatted(granularidade));
        }
    }

    private void validarDuracao(Instant inicio, Instant fim) {
        long minutos = Duration.between(inicio, fim).toMinutes();
        if (minutos < propriedades.duracaoMinimaMinutos()) {
            throw new RegraDeNegocioException("duracao-abaixo-do-minimo",
                    "A reserva deve durar no minimo %d minutos".formatted(propriedades.duracaoMinimaMinutos()));
        }
        if (minutos > propriedades.duracaoMaximaMinutos()) {
            throw new RegraDeNegocioException("duracao-acima-do-maximo",
                    "A reserva deve durar no maximo %d minutos".formatted(propriedades.duracaoMaximaMinutos()));
        }
    }

    /**
     * Compara o intervalo com a grade de funcionamento da quadra.
     *
     * <p>A conversao para o fuso do negocio e o ponto delicado: os instantes estao
     * em UTC, mas "abre as 8h" e hora local.</p>
     *
     * <p>Limitacao conhecida e deliberada: reservas que atravessam a meia-noite
     * local sao rejeitadas. O horario de funcionamento e modelado como um par de
     * {@code LocalTime} dentro de um dia da semana, e um intervalo que cruza a
     * virada cairia em dois dias diferentes. Suportar isso exigiria representar
     * janelas que "vazam" para o dia seguinte — complexidade que uma quadra
     * fechando as 23h nao justifica. Um clube 24h precisaria de outro modelo.</p>
     */
    private void validarHorarioDeFuncionamento(Quadra quadra, Instant inicio, Instant fim) {
        ZoneId fuso = fusoDoNegocio();
        LocalDateTime inicioLocal = LocalDateTime.ofInstant(inicio, fuso);
        LocalDateTime fimLocal = LocalDateTime.ofInstant(fim, fuso);

        if (!inicioLocal.toLocalDate().equals(fimLocal.toLocalDate())) {
            throw new RegraDeNegocioException("reserva-atravessa-o-dia",
                    "A reserva nao pode atravessar a virada do dia");
        }

        if (!quadra.funcionaEm(inicioLocal.getDayOfWeek(), inicioLocal.toLocalTime(), fimLocal.toLocalTime())) {
            throw new RegraDeNegocioException("fora-do-horario-de-funcionamento",
                    "A quadra %s nao funciona nesse horario".formatted(quadra.getNome()));
        }
    }

    private ZoneId fusoDoNegocio() {
        return propriedades.fusoHorario();
    }
}
