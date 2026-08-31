package com.joaoalcantara.reservas;

import org.springframework.boot.SpringApplication;

import com.joaoalcantara.reservas.comum.PostgresDeTeste;

/**
 * Sobe a aplicacao localmente com um PostgreSQL descartavel.
 *
 * <p>Atalho de desenvolvimento: rodar esta classe pela IDE dispensa
 * {@code docker compose up} e a configuracao manual de conexao. O banco morre com
 * o processo, entao cada execucao comeca limpa.</p>
 */
public class TestReservasApplication {

    public static void main(String[] args) {
        SpringApplication.from(ReservasApplication::main).with(PostgresDeTeste.class).run(args);
    }
}
