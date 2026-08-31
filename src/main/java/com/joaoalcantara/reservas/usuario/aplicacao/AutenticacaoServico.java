package com.joaoalcantara.reservas.usuario.aplicacao;

import java.time.Clock;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.joaoalcantara.reservas.comum.erro.ConflitoException;
import com.joaoalcantara.reservas.comum.erro.RecursoNaoEncontradoException;
import com.joaoalcantara.reservas.seguranca.ServicoDeToken;
import com.joaoalcantara.reservas.usuario.api.LoginRequisicao;
import com.joaoalcantara.reservas.usuario.api.RegistroRequisicao;
import com.joaoalcantara.reservas.usuario.dominio.Papel;
import com.joaoalcantara.reservas.usuario.dominio.Usuario;
import com.joaoalcantara.reservas.usuario.dominio.UsuarioRepositorio;

/** Registro e login. */
@Service
public class AutenticacaoServico {

    private final UsuarioRepositorio repositorio;
    private final PasswordEncoder codificador;
    private final ServicoDeToken servicoDeToken;
    private final Clock relogio;

    public AutenticacaoServico(UsuarioRepositorio repositorio, PasswordEncoder codificador,
                               ServicoDeToken servicoDeToken, Clock relogio) {
        this.repositorio = repositorio;
        this.codificador = codificador;
        this.servicoDeToken = servicoDeToken;
        this.relogio = relogio;
    }

    /**
     * O registro publico sempre cria CLIENTE. Promover alguem a ADMIN e operacao
     * administrativa deliberada — se o papel viesse no corpo da requisicao,
     * qualquer um se autopromoveria.
     */
    @Transactional
    public Usuario registrar(RegistroRequisicao requisicao) {
        if (repositorio.existeComEmail(requisicao.email())) {
            throw new ConflitoException("email-ja-cadastrado", "Ja existe uma conta com este e-mail");
        }
        Usuario usuario = new Usuario(
                requisicao.nome(),
                requisicao.email(),
                codificador.encode(requisicao.senha()),
                Papel.CLIENTE,
                relogio.instant());
        return repositorio.salvar(usuario);
    }

    /**
     * Autentica e devolve o token.
     *
     * <p>E-mail inexistente e senha errada dao exatamente o mesmo erro: distinguir
     * os dois casos permitiria enumerar quais e-mails tem conta no sistema.</p>
     */
    @Transactional(readOnly = true)
    public ServicoDeToken.TokenEmitido autenticar(LoginRequisicao requisicao) {
        Usuario usuario = repositorio.porEmail(requisicao.email())
                .orElseThrow(() -> new BadCredentialsException("Credenciais invalidas"));

        if (!codificador.matches(requisicao.senha(), usuario.getSenhaHash())) {
            throw new BadCredentialsException("Credenciais invalidas");
        }
        return servicoDeToken.emitirPara(usuario);
    }

    @Transactional(readOnly = true)
    public Usuario buscarPorId(Long id) {
        return repositorio.porId(id)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuario", id));
    }
}
