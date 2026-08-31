package com.joaoalcantara.reservas.seguranca;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.joaoalcantara.reservas.usuario.dominio.Usuario;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Emite e valida os tokens de acesso.
 *
 * <p>Token opaco de sessao em banco seria mais simples de revogar, mas exigiria
 * uma consulta por requisicao. JWT assinado (HS256) foi escolhido por ser
 * stateless: o servidor valida a assinatura sem tocar no banco, o que mantem a
 * API escalavel horizontalmente. O preco e a revogacao — um token roubado vale
 * ate expirar, por isso a expiracao e curta e configuravel.</p>
 *
 * <p>O papel viaja como claim para que a autorizacao tambem dispense consulta;
 * mudanca de papel so vale no proximo login, o que e aceitavel aqui.</p>
 */
@Service
public class ServicoDeToken {

    private static final String CLAIM_PAPEL = "papel";
    private static final String CLAIM_NOME = "nome";

    private final SecretKey chave;
    private final Duration expiracao;
    private final String emissor;
    private final Clock relogio;

    public ServicoDeToken(PropriedadesJwt propriedades, Clock relogio) {
        this.chave = Keys.hmacShaKeyFor(propriedades.segredo().getBytes(StandardCharsets.UTF_8));
        this.expiracao = Duration.ofMinutes(propriedades.expiracaoMinutos());
        this.emissor = propriedades.emissor();
        this.relogio = relogio;
    }

    public TokenEmitido emitirPara(Usuario usuario) {
        Instant agora = relogio.instant();
        Instant expiraEm = agora.plus(expiracao);

        String token = Jwts.builder()
                .issuer(emissor)
                .subject(String.valueOf(usuario.getId()))
                .claim(CLAIM_PAPEL, usuario.getPapel().name())
                .claim(CLAIM_NOME, usuario.getNome())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiraEm))
                .signWith(chave)
                .compact();

        return new TokenEmitido(token, expiraEm, expiracao.toSeconds());
    }

    /**
     * Valida assinatura, emissor e expiracao. Devolve as claims ou lanca
     * {@link JwtException} — o filtro trata como "nao autenticado".
     */
    public Claims validar(String token) {
        return Jwts.parser()
                .verifyWith(chave)
                .requireIssuer(emissor)
                .clock(() -> Date.from(relogio.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static Long idDoUsuario(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public static String papel(Claims claims) {
        return claims.get(CLAIM_PAPEL, String.class);
    }

    public record TokenEmitido(String token, Instant expiraEm, long expiraEmSegundos) {
    }
}
