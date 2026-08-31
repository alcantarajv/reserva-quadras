package com.joaoalcantara.reservas.quadra.dominio;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Janela em que uma quadra funciona num determinado dia da semana.
 *
 * <p>Uma quadra pode ter mais de uma janela no mesmo dia (ex.: 08:00-12:00 e
 * 14:00-22:00, com intervalo de almoco), por isso a chave nao e apenas o dia.</p>
 */
@Entity
@Table(name = "horarios_funcionamento")
public class HorarioFuncionamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "quadra_id", nullable = false)
    private Quadra quadra;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 20)
    private DayOfWeek diaSemana;

    @Column(name = "abertura", nullable = false)
    private LocalTime abertura;

    @Column(name = "fechamento", nullable = false)
    private LocalTime fechamento;

    protected HorarioFuncionamento() {
        // exigido pelo JPA
    }

    public HorarioFuncionamento(Quadra quadra, DayOfWeek diaSemana, LocalTime abertura, LocalTime fechamento) {
        this.quadra = Objects.requireNonNull(quadra, "quadra");
        this.diaSemana = Objects.requireNonNull(diaSemana, "diaSemana");
        this.abertura = Objects.requireNonNull(abertura, "abertura");
        this.fechamento = Objects.requireNonNull(fechamento, "fechamento");
        if (!fechamento.isAfter(abertura)) {
            throw new IllegalArgumentException("fechamento deve ser posterior a abertura");
        }
    }

    /** Verifica se o intervalo [inicio, fim) cabe inteiro dentro desta janela. */
    public boolean contem(LocalTime inicio, LocalTime fim) {
        return !inicio.isBefore(abertura) && !fim.isAfter(fechamento);
    }

    public Long getId() {
        return id;
    }

    public Quadra getQuadra() {
        return quadra;
    }

    public DayOfWeek getDiaSemana() {
        return diaSemana;
    }

    public LocalTime getAbertura() {
        return abertura;
    }

    public LocalTime getFechamento() {
        return fechamento;
    }
}
