package com.joaoalcantara.reservas.quadra.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.joaoalcantara.reservas.quadra.dominio.Quadra;
import com.joaoalcantara.reservas.quadra.dominio.QuadraRepositorio;

/** Adaptador que liga a porta do dominio ao Spring Data. */
@Repository
class QuadraRepositorioJpa implements QuadraRepositorio {

    private final QuadraSpringDataRepository jpa;

    QuadraRepositorioJpa(QuadraSpringDataRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Quadra salvar(Quadra quadra) {
        return jpa.save(quadra);
    }

    @Override
    public Optional<Quadra> porId(Long id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<Quadra> porIdComHorarios(Long id) {
        return jpa.buscarComHorarios(id);
    }

    @Override
    public List<Quadra> listar(boolean apenasAtivas) {
        return apenasAtivas ? jpa.findByAtivaTrueOrderByNomeAsc() : jpa.findAllByOrderByNomeAsc();
    }

    @Override
    public boolean existeComNome(String nome) {
        return jpa.existsByNomeIgnoreCase(nome);
    }
}
