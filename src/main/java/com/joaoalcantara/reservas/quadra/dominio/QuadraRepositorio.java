package com.joaoalcantara.reservas.quadra.dominio;

import java.util.List;
import java.util.Optional;

/**
 * Porta de persistencia do agregado Quadra.
 *
 * <p>O dominio depende desta interface, nao do Spring Data. O adaptador vive em
 * {@code quadra.infra}. Assim a camada de servico e testavel com um duble em
 * memoria, e uma eventual troca de tecnologia de persistencia nao toca o dominio.</p>
 */
public interface QuadraRepositorio {

    Quadra salvar(Quadra quadra);

    Optional<Quadra> porId(Long id);

    /** Carrega a quadra ja com os horarios de funcionamento, evitando N+1. */
    Optional<Quadra> porIdComHorarios(Long id);

    List<Quadra> listar(boolean apenasAtivas);

    boolean existeComNome(String nome);
}
