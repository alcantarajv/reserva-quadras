package com.joaoalcantara.reservas.seguranca;

import com.joaoalcantara.reservas.usuario.dominio.Papel;

/**
 * Identidade extraida do token e colocada no contexto de seguranca.
 *
 * <p>Carregar o id junto do papel e o que permite a regra "um CLIENTE so ve as
 * proprias reservas" ser aplicada no servico sem uma consulta extra por usuario.</p>
 */
public record UsuarioAutenticado(Long id, String nome, Papel papel) {

    public boolean ehAdmin() {
        return papel == Papel.ADMIN;
    }
}
