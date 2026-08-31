package com.joaoalcantara.reservas.quadra.api;

import java.math.BigDecimal;
import java.util.List;

import com.joaoalcantara.reservas.quadra.dominio.Modalidade;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Entrada de criacao/atualizacao de quadra.
 *
 * <p>Primeira camada de validacao (formato). A segunda — coerencia entre campos,
 * como fechamento depois da abertura e janelas que nao se sobrepoem — fica no
 * servico, porque depende de regra de negocio e nao de formato.</p>
 */
public record QuadraRequisicao(
        @NotBlank(message = "e obrigatorio") @Size(max = 120, message = "deve ter no maximo 120 caracteres") String nome,
        @NotNull(message = "e obrigatoria") Modalidade modalidade,
        @NotNull(message = "e obrigatorio")
        @DecimalMin(value = "0.00", message = "nao pode ser negativo")
        @Digits(integer = 8, fraction = 2, message = "formato monetario invalido") BigDecimal precoHora,
        @Valid List<HorarioFuncionamentoRequisicao> horarios) {
}
