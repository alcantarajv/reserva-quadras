package com.joaoalcantara.reservas.usuario.api;

import com.joaoalcantara.reservas.usuario.dominio.Papel;
import com.joaoalcantara.reservas.usuario.dominio.Usuario;

public record UsuarioResposta(Long id, String nome, String email, Papel papel) {

    public static UsuarioResposta de(Usuario usuario) {
        return new UsuarioResposta(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPapel());
    }
}
