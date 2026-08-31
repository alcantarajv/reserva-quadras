package com.joaoalcantara.reservas.comum.erro;

/** Recurso pedido pelo cliente nao existe. Traduzida para 404. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }

    public static RecursoNaoEncontradoException de(String recurso, Object id) {
        return new RecursoNaoEncontradoException("%s de id %s nao encontrada(o)".formatted(recurso, id));
    }
}
