package com.joaoalcantara.reservas.reserva.infra;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import com.joaoalcantara.reservas.reserva.dominio.HorarioIndisponivelException;
import com.joaoalcantara.reservas.reserva.dominio.Reserva;
import com.joaoalcantara.reservas.reserva.dominio.ReservaRepositorio;

/**
 * Adaptador de persistencia de reservas.
 *
 * <p>Alem de delegar ao Spring Data, este adaptador tem uma responsabilidade
 * propria: traduzir a violacao da constraint de exclusao
 * {@code reservas_sem_sobreposicao} num erro de dominio. Esse e o unico lugar do
 * sistema que sabe o nome da constraint — dominio e servico continuam falando
 * apenas em {@link HorarioIndisponivelException}.</p>
 */
@Repository
class ReservaRepositorioJpa implements ReservaRepositorio {

    /** Precisa bater exatamente com o nome usado na migration V4. */
    static final String CONSTRAINT_SOBREPOSICAO = "reservas_sem_sobreposicao";

    private final ReservaSpringDataRepository jpa;

    ReservaRepositorioJpa(ReservaSpringDataRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Reserva salvar(Reserva reserva) {
        try {
            // saveAndFlush, e nao save: o INSERT precisa chegar ao banco agora,
            // dentro deste try, para que a violacao seja capturavel aqui.
            return jpa.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException e) {
            if (violouConstraintDeSobreposicao(e)) {
                throw new HorarioIndisponivelException();
            }
            // Qualquer outra violacao de integridade e bug nosso, nao conflito de
            // agenda: deixa subir em vez de mascarar como 409.
            throw e;
        }
    }

    @Override
    public Optional<Reserva> porId(Long id) {
        return jpa.buscarCompleta(id);
    }

    @Override
    public List<Reserva> doUsuario(Long usuarioId) {
        return jpa.doUsuario(usuarioId);
    }

    @Override
    public List<Reserva> todas() {
        return jpa.todasComRelacoes();
    }

    @Override
    public List<Reserva> ativasDaQuadraNoPeriodo(Long quadraId, Instant inicio, Instant fim) {
        return jpa.ativasNoPeriodo(quadraId, inicio, fim);
    }

    /**
     * Identifica a constraint pelo nome.
     *
     * <p>O Hibernate expoe o nome em {@code ConstraintViolationException}, mas nem
     * todo driver o preenche; por isso ha o fallback textual sobre a cadeia de
     * mensagens. Comparar pelo nome — e nao apenas assumir que toda violacao de
     * integridade e sobreposicao — impede que um bug de chave estrangeira seja
     * reportado ao cliente como "horario indisponivel".</p>
     */
    private boolean violouConstraintDeSobreposicao(DataIntegrityViolationException excecao) {
        for (Throwable causa = excecao; causa != null; causa = causa.getCause()) {
            if (causa instanceof org.hibernate.exception.ConstraintViolationException hibernate
                    && CONSTRAINT_SOBREPOSICAO.equalsIgnoreCase(hibernate.getConstraintName())) {
                return true;
            }
            String mensagem = causa.getMessage();
            if (mensagem != null && mensagem.toLowerCase(Locale.ROOT).contains(CONSTRAINT_SOBREPOSICAO)) {
                return true;
            }
        }
        return false;
    }
}
