package com.joaoalcantara.reservas.comum.config;

import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Expoe o relogio da aplicacao como bean.
 *
 * <p>Nenhuma regra de negocio deve chamar {@code Instant.now()} diretamente: toda
 * decisao que depende de tempo (reserva no passado, antecedencia de cancelamento)
 * recebe este {@link Clock} injetado. Nos testes ele e substituido por
 * {@code Clock.fixed(...)}, o que torna essas regras deterministicas.</p>
 */
@Configuration
public class RelogioConfig {

    @Bean
    public Clock relogio() {
        return Clock.system(ZoneOffset.UTC);
    }
}
