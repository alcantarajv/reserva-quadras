package com.joaoalcantara.reservas.usuario.api;

import jakarta.validation.constraints.NotBlank;

public record LoginRequisicao(
        @NotBlank(message = "e obrigatorio") String email,
        @NotBlank(message = "e obrigatoria") String senha) {
}
