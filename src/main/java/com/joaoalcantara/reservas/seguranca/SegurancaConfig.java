package com.joaoalcantara.reservas.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.joaoalcantara.reservas.usuario.dominio.Papel;

/**
 * Configuracao de autenticacao e autorizacao.
 *
 * <p>Todas as regras de acesso por rota ficam declaradas aqui, num unico bloco
 * legivel, em vez de espalhadas em {@code @PreAuthorize} pelos controllers.</p>
 */
@Configuration
public class SegurancaConfig {

    private final FiltroDeAutenticacaoJwt filtroJwt;
    private final PontoDeEntradaNaoAutorizado pontoDeEntrada;
    private final ManipuladorDeAcessoNegado acessoNegado;

    public SegurancaConfig(FiltroDeAutenticacaoJwt filtroJwt,
                           PontoDeEntradaNaoAutorizado pontoDeEntrada,
                           ManipuladorDeAcessoNegado acessoNegado) {
        this.filtroJwt = filtroJwt;
        this.pontoDeEntrada = pontoDeEntrada;
        this.acessoNegado = acessoNegado;
    }

    @Bean
    public SecurityFilterChain cadeiaDeFiltros(HttpSecurity http) throws Exception {
        return http
                // API stateless com token no header: nao ha cookie de sessao para
                // um site terceiro reaproveitar, entao CSRF nao se aplica.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers(HttpMethod.POST, "/api/auth/registrar", "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()

                        // Documentacao publica: o objetivo dela e justamente ser acessivel
                        // antes de ter um token. Em producao com dados sensiveis, valeria
                        // restringir por rede ou papel.
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/quadras", "/api/quadras/*").permitAll()
                        .requestMatchers("/api/quadras/**").hasRole(Papel.ADMIN.name())
                        .requestMatchers("/api/bloqueios/**").hasRole(Papel.ADMIN.name())
                        .anyRequest().authenticated())
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint(pontoDeEntrada)
                        .accessDeniedHandler(acessoNegado))
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder codificadorDeSenha() {
        return new BCryptPasswordEncoder();
    }
}
