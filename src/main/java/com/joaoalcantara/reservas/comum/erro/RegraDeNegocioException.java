package com.joaoalcantara.reservas.comum.erro;

/**
 * Uma regra de negocio foi violada — o pedido e sintaticamente valido, mas o
 * dominio nao o aceita (horario no passado, fora do funcionamento, etc.).
 * Traduzida para 422 Unprocessable Entity.
 */
public class RegraDeNegocioException extends RuntimeException {

    private final String codigo;

    public RegraDeNegocioException(String codigo, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }
}
