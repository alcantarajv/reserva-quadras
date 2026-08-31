package com.joaoalcantara.reservas.reserva.infra;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.joaoalcantara.reservas.reserva.dominio.Reserva;

interface ReservaSpringDataRepository extends JpaRepository<Reserva, Long> {

    @Query("""
            select r from Reserva r
              join fetch r.quadra
              join fetch r.usuario
             where r.id = :id
            """)
    Optional<Reserva> buscarCompleta(@Param("id") Long id);

    @Query("""
            select r from Reserva r
              join fetch r.quadra
              join fetch r.usuario
             where r.usuario.id = :usuarioId
             order by r.inicio desc
            """)
    List<Reserva> doUsuario(@Param("usuarioId") Long usuarioId);

    @Query("""
            select r from Reserva r
              join fetch r.quadra
              join fetch r.usuario
             order by r.inicio desc
            """)
    List<Reserva> todasComRelacoes();

    /**
     * Reservas que ocupam agenda e cruzam a janela pedida.
     *
     * <p>A condicao de sobreposicao de intervalos semiabertos e
     * {@code inicio < fimDaJanela AND fim > inicioDaJanela} — a mesma logica do
     * operador {@code &&} usado pela constraint de exclusao.</p>
     */
    @Query("""
            select r from Reserva r
             where r.quadra.id = :quadraId
               and r.status <> com.joaoalcantara.reservas.reserva.dominio.StatusReserva.CANCELADA
               and r.inicio < :fim
               and r.fim > :inicio
             order by r.inicio asc
            """)
    List<Reserva> ativasNoPeriodo(@Param("quadraId") Long quadraId,
                                  @Param("inicio") Instant inicio,
                                  @Param("fim") Instant fim);
}
