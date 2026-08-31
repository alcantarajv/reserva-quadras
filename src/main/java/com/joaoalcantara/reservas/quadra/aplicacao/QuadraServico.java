package com.joaoalcantara.reservas.quadra.aplicacao;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.joaoalcantara.reservas.comum.erro.ConflitoException;
import com.joaoalcantara.reservas.comum.erro.RecursoNaoEncontradoException;
import com.joaoalcantara.reservas.comum.erro.RegraDeNegocioException;
import com.joaoalcantara.reservas.quadra.api.HorarioFuncionamentoRequisicao;
import com.joaoalcantara.reservas.quadra.api.QuadraRequisicao;
import com.joaoalcantara.reservas.quadra.dominio.HorarioFuncionamento;
import com.joaoalcantara.reservas.quadra.dominio.Quadra;
import com.joaoalcantara.reservas.quadra.dominio.QuadraRepositorio;

/** Casos de uso administrativos sobre quadras. */
@Service
public class QuadraServico {

    private final QuadraRepositorio repositorio;

    public QuadraServico(QuadraRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional
    public Quadra criar(QuadraRequisicao requisicao) {
        if (repositorio.existeComNome(requisicao.nome())) {
            throw new ConflitoException("quadra-duplicada",
                    "Ja existe uma quadra chamada '%s'".formatted(requisicao.nome()));
        }
        Quadra quadra = new Quadra(requisicao.nome(), requisicao.modalidade(), requisicao.precoHora());
        aplicarHorarios(quadra, requisicao.horarios());
        return repositorio.salvar(quadra);
    }

    @Transactional
    public Quadra atualizar(Long id, QuadraRequisicao requisicao) {
        Quadra quadra = buscarComHorarios(id);
        boolean trocouDeNome = !quadra.getNome().equalsIgnoreCase(requisicao.nome());
        if (trocouDeNome && repositorio.existeComNome(requisicao.nome())) {
            throw new ConflitoException("quadra-duplicada",
                    "Ja existe uma quadra chamada '%s'".formatted(requisicao.nome()));
        }
        quadra.atualizarDados(requisicao.nome(), requisicao.modalidade(), requisicao.precoHora());
        aplicarHorarios(quadra, requisicao.horarios());
        return repositorio.salvar(quadra);
    }

    @Transactional
    public Quadra alterarSituacao(Long id, boolean ativa) {
        Quadra quadra = buscarComHorarios(id);
        if (ativa) {
            quadra.ativar();
        } else {
            quadra.desativar();
        }
        return repositorio.salvar(quadra);
    }

    @Transactional(readOnly = true)
    public Quadra buscarComHorarios(Long id) {
        return repositorio.porIdComHorarios(id)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Quadra", id));
    }

    @Transactional(readOnly = true)
    public List<Quadra> listar(boolean apenasAtivas) {
        return repositorio.listar(apenasAtivas);
    }

    /**
     * Substitui a grade de horarios validando o que Bean Validation nao alcanca:
     * coerencia entre abertura e fechamento, e ausencia de sobreposicao entre as
     * janelas do mesmo dia. Sem isso, "08:00-12:00" e "10:00-14:00" na segunda
     * conviveriam e a checagem de funcionamento ficaria ambigua.
     */
    private void aplicarHorarios(Quadra quadra, List<HorarioFuncionamentoRequisicao> requisicoes) {
        if (requisicoes == null || requisicoes.isEmpty()) {
            quadra.definirHorarios(List.of());
            return;
        }

        for (HorarioFuncionamentoRequisicao horario : requisicoes) {
            if (!horario.fechamento().isAfter(horario.abertura())) {
                throw new RegraDeNegocioException("horario-invalido",
                        "Em %s, o fechamento (%s) deve ser posterior a abertura (%s)"
                                .formatted(horario.diaSemana(), horario.fechamento(), horario.abertura()));
            }
        }

        for (DayOfWeek dia : DayOfWeek.values()) {
            List<HorarioFuncionamentoRequisicao> doDia = requisicoes.stream()
                    .filter(horario -> horario.diaSemana() == dia)
                    .sorted(Comparator.comparing(HorarioFuncionamentoRequisicao::abertura))
                    .toList();

            for (int i = 1; i < doDia.size(); i++) {
                if (doDia.get(i).abertura().isBefore(doDia.get(i - 1).fechamento())) {
                    throw new RegraDeNegocioException("horarios-sobrepostos",
                            "As janelas de funcionamento de %s se sobrepoem".formatted(dia));
                }
            }
        }

        List<HorarioFuncionamento> horarios = new ArrayList<>();
        for (HorarioFuncionamentoRequisicao horario : requisicoes) {
            horarios.add(new HorarioFuncionamento(quadra, horario.diaSemana(), horario.abertura(), horario.fechamento()));
        }
        quadra.definirHorarios(horarios);
    }
}
