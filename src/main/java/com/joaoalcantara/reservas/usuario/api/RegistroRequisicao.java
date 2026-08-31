package com.joaoalcantara.reservas.usuario.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequisicao(
        @NotBlank(message = "e obrigatorio") @Size(max = 120, message = "deve ter no maximo 120 caracteres") String nome,
        @NotBlank(message = "e obrigatorio") @Email(message = "formato invalido")
        @Size(max = 180, message = "deve ter no maximo 180 caracteres") String email,
        @NotBlank(message = "e obrigatoria")
        @Size(min = 8, max = 72, message = "deve ter entre 8 e 72 caracteres") String senha) {
}
