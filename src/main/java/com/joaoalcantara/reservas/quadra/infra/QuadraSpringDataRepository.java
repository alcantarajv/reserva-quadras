package com.joaoalcantara.reservas.quadra.infra;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.joaoalcantara.reservas.quadra.dominio.Quadra;

/** Repositorio Spring Data. Detalhe de infraestrutura: ninguem fora deste pacote o usa. */
interface QuadraSpringDataRepository extends JpaRepository<Quadra, Long> {

    @Query("select distinct q from Quadra q left join fetch q.horarios where q.id = :id")
    Optional<Quadra> buscarComHorarios(Long id);

    List<Quadra> findByAtivaTrueOrderByNomeAsc();

    List<Quadra> findAllByOrderByNomeAsc();

    boolean existsByNomeIgnoreCase(String nome);
}
