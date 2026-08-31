package com.joaoalcantara.reservas.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Configuracao de assinatura dos tokens.
 *
 * <p>O segredo vem de variavel de ambiente e nunca do repositorio. A anotacao
 * {@code @Size(min = 32)} faz a aplicacao falhar no boot se alguem apontar para
 * um segredo fraco — HS256 exige chave de pelo menos 256 bits, e e melhor nao
 * subir do que subir com token forjavel.</p>
 */
@ConfigurationProperties(prefix = "reservas.jwt")
public record PropriedadesJwt(
        @NotBlank(message = "defina a variavel de ambiente JWT_SECRET")
        @Size(min = 32, message = "precisa de no minimo 32 caracteres (256 bits) para HS256")
        String segredo,
        @Positive long expiracaoMinutos,
        @NotBlank String emissor) {
}
