package com.joaoalcantara.reservas.comum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Marca um teste de integracao: sobe a aplicacao inteira contra um PostgreSQL real.
 *
 * <p>Anotacao composta para que nenhuma classe de teste precise repetir a
 * configuracao do container — e para que trocar essa configuracao seja uma
 * edicao em um lugar so.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresDeTeste.class)
public @interface TesteDeIntegracao {
}
