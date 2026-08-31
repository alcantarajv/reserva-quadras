package com.joaoalcantara.reservas.reserva.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import com.joaoalcantara.reservas.quadra.dominio.Quadra;
import com.joaoalcantara.reservas.usuario.dominio.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Reserva de uma quadra num intervalo de tempo.
 *
 * <p>O intervalo e semiaberto: {@code [inicio, fim)}. Assim uma reserva das
 * 09:00 as 10:00 e outra das 10:00 as 11:00 nao colidem — o mesmo criterio usado
 * pela constraint de exclusao no banco. Escolher a mesma convencao nos dois
 * lugares evita que aplicacao e banco discordem sobre o que e conflito.</p>
 */
@Entity
@Table(name = "reservas")
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quadra_id", nullable = false)
    private Quadra quadra;

    @Column(name = "inicio", nullable = false)
    private Instant inicio;

    @Column(name = "fim", nullable = false)
    private Instant fim;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusReserva status;

    /**
     * Valor congelado no momento da reserva.
     *
     * <p>Nao e derivado do preco atual da quadra: se o clube reajustar a tabela
     * amanha, quem reservou hoje continua devendo o que combinou.</p>
     */
    @Column(name = "valor", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "cancelada_em")
    private Instant canceladaEm;

    protected Reserva() {
        // exigido pelo JPA
    }

    public Reserva(Usuario usuario, Quadra quadra, Instant inicio, Instant fim, BigDecimal valor, Instant criadaEm) {
        this.usuario = Objects.requireNonNull(usuario, "usuario");
        this.quadra = Objects.requireNonNull(quadra, "quadra");
        this.inicio = Objects.requireNonNull(inicio, "inicio");
        this.fim = Objects.requireNonNull(fim, "fim");
        this.valor = Objects.requireNonNull(valor, "valor");
        this.criadaEm = Objects.requireNonNull(criadaEm, "criadaEm");
        this.status = StatusReserva.CONFIRMADA;
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("fim deve ser posterior a inicio");
        }
    }

    /**
     * Marca a reserva como cancelada.
     *
     * <p>A entidade so garante a transicao de estado. A regra de antecedencia
     * minima vive no servico, porque depende do relogio e de configuracao — coisas
     * que a entidade nao deve conhecer.</p>
     */
    public void cancelar(Instant momento) {
        if (status == StatusReserva.CANCELADA) {
            throw new IllegalStateException("Reserva ja esta cancelada");
        }
        if (status == StatusReserva.CONCLUIDA) {
            throw new IllegalStateException("Reserva ja concluida nao pode ser cancelada");
        }
        this.status = StatusReserva.CANCELADA;
        this.canceladaEm = momento;
    }

    public void concluir() {
        if (status != StatusReserva.CONFIRMADA) {
            throw new IllegalStateException("So uma reserva confirmada pode ser concluida");
        }
        this.status = StatusReserva.CONCLUIDA;
    }

    public boolean pertenceA(Long usuarioId) {
        return usuario.getId().equals(usuarioId);
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public Quadra getQuadra() {
        return quadra;
    }

    public Instant getInicio() {
        return inicio;
    }

    public Instant getFim() {
        return fim;
    }

    public StatusReserva getStatus() {
        return status;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public Instant getCriadaEm() {
        return criadaEm;
    }

    public Instant getCanceladaEm() {
        return canceladaEm;
    }
}
