package com.joaoalcantara.reservas.quadra.dominio;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

/**
 * Quadra disponivel para locacao.
 *
 * <p>A quadra e a raiz do agregado que carrega seus horarios de funcionamento:
 * so faz sentido falar de "abre as 8h na segunda" no contexto de uma quadra, e
 * as duas coisas mudam juntas.</p>
 */
@Entity
@Table(name = "quadras")
public class Quadra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidade", nullable = false, length = 30)
    private Modalidade modalidade;

    @Column(name = "preco_hora", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoHora;

    @Column(name = "ativa", nullable = false)
    private boolean ativa;

    @OneToMany(mappedBy = "quadra", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HorarioFuncionamento> horarios = new ArrayList<>();

    protected Quadra() {
        // exigido pelo JPA
    }

    public Quadra(String nome, Modalidade modalidade, BigDecimal precoHora) {
        this.nome = Objects.requireNonNull(nome, "nome");
        this.modalidade = Objects.requireNonNull(modalidade, "modalidade");
        this.precoHora = Objects.requireNonNull(precoHora, "precoHora");
        this.ativa = true;
    }

    public void atualizarDados(String nome, Modalidade modalidade, BigDecimal precoHora) {
        this.nome = Objects.requireNonNull(nome, "nome");
        this.modalidade = Objects.requireNonNull(modalidade, "modalidade");
        this.precoHora = Objects.requireNonNull(precoHora, "precoHora");
    }

    public void ativar() {
        this.ativa = true;
    }

    /**
     * Desativa a quadra. Reservas ja confirmadas continuam validas — desativar
     * impede novas reservas, nao cancela as existentes; cancelar em massa e uma
     * decisao administrativa separada.
     */
    public void desativar() {
        this.ativa = false;
    }

    /** Substitui a agenda semanal inteira. Simplifica a API: o admin envia a grade completa. */
    public void definirHorarios(List<HorarioFuncionamento> novos) {
        this.horarios.clear();
        this.horarios.addAll(novos);
    }

    public void adicionarHorario(DayOfWeek dia, LocalTime abertura, LocalTime fechamento) {
        this.horarios.add(new HorarioFuncionamento(this, dia, abertura, fechamento));
    }

    /**
     * Diz se o intervalo [inicio, fim) daquele dia da semana cabe em alguma das
     * janelas de funcionamento. Precisa caber inteiro em UMA janela: uma reserva
     * que atravessa o intervalo de almoco nao e valida.
     */
    public boolean funcionaEm(DayOfWeek dia, LocalTime inicio, LocalTime fim) {
        return horarios.stream()
                .filter(horario -> horario.getDiaSemana() == dia)
                .anyMatch(horario -> horario.contem(inicio, fim));
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public Modalidade getModalidade() {
        return modalidade;
    }

    public BigDecimal getPrecoHora() {
        return precoHora;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public List<HorarioFuncionamento> getHorarios() {
        return Collections.unmodifiableList(horarios);
    }
}
