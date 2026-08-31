package com.joaoalcantara.reservas.quadra.api;

import jakarta.validation.constraints.NotNull;

public record SituacaoRequisicao(@NotNull(message = "e obrigatorio") Boolean ativa) {
}
