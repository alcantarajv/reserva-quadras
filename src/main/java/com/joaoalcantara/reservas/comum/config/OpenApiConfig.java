package com.joaoalcantara.reservas.comum.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Documentacao OpenAPI da API.
 *
 * <p>O esquema de seguranca e declarado aqui, e nao anotado endpoint a endpoint:
 * assim o botao "Authorize" do Swagger UI passa a existir, o token e colado uma
 * vez e vale para todas as chamadas subsequentes. Sem isso, cada requisicao
 * protegida no Swagger voltaria 401 e a pagina viraria so uma lista bonita.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_JWT = "bearer-jwt";

    @Bean
    public OpenAPI documentacaoDaApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API de Reserva de Quadras")
                        .version("v1")
                        .description("""
                                API para reserva de quadras esportivas.

                                O ponto tecnico central e o controle de concorrencia: duas reservas \
                                simultaneas para a mesma quadra no mesmo horario nao podem coexistir. \
                                A garantia esta numa constraint de exclusao do PostgreSQL \
                                (EXCLUDE USING gist), e nao numa consulta previa em Java — que teria \
                                uma janela de corrida entre o SELECT e o INSERT. A requisicao perdedora \
                                recebe 409 Conflict.

                                Autenticacao: registre-se em /api/auth/registrar, obtenha o token em \
                                /api/auth/login e informe-o no botao Authorize.

                                Datas e horas em ISO-8601 com fuso (ex.: 2026-09-01T14:00:00-03:00).
                                """)
                        .contact(new Contact()
                                .name("Joao Vitor Alcantara Correa")
                                .url("https://github.com/alcantarajv"))
                        .license(new License().name("MIT")))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token devolvido por /api/auth/login. Cole apenas o token, sem o prefixo Bearer.")))
                // Exigencia global: a maioria das rotas precisa de token. As publicas
                // sao marcadas individualmente com @SecurityRequirements.
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
