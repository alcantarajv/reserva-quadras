package com.joaoalcantara.reservas.quadra.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.joaoalcantara.reservas.quadra.aplicacao.QuadraServico;
import com.joaoalcantara.reservas.quadra.dominio.Quadra;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Endpoints de quadras.
 *
 * <p>Leitura e publica; escrita exige ADMIN. A autorizacao propriamente dita e
 * declarada na configuracao do Spring Security por padrao de rota, para ficar
 * num lugar so e ser auditavel de relance.</p>
 */
@Tag(name = "Quadras", description = "Consulta publica e gestao administrativa de quadras")
@RestController
@RequestMapping("/api/quadras")
public class QuadraController {

    private final QuadraServico servico;

    public QuadraController(QuadraServico servico) {
        this.servico = servico;
    }

    @Operation(summary = "Lista quadras", description = "Publico. Nao traz a grade de horarios; use a busca por id.")
    @SecurityRequirements
    @GetMapping
    public List<QuadraResposta> listar(@RequestParam(defaultValue = "true") boolean apenasAtivas) {
        return servico.listar(apenasAtivas).stream().map(QuadraResposta::resumida).toList();
    }

    @Operation(summary = "Busca uma quadra com sua grade de funcionamento", description = "Publico.")
    @SecurityRequirements
    @GetMapping("/{id}")
    public QuadraResposta buscar(@PathVariable Long id) {
        return QuadraResposta.de(servico.buscarComHorarios(id));
    }

    @Operation(summary = "Cria uma quadra", description = "Exige papel ADMIN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Quadra criada"),
            @ApiResponse(responseCode = "409", description = "Ja existe quadra com esse nome", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "422", description = "Grade de horarios incoerente: fechamento antes da abertura, ou janelas sobrepostas no mesmo dia", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @PostMapping
    public ResponseEntity<QuadraResposta> criar(@Valid @RequestBody QuadraRequisicao requisicao) {
        Quadra quadra = servico.criar(requisicao);
        return ResponseEntity
                .created(URI.create("/api/quadras/" + quadra.getId()))
                .body(QuadraResposta.de(quadra));
    }

    @Operation(summary = "Atualiza uma quadra e sua grade", description = "Exige papel ADMIN. A grade enviada substitui a anterior por completo.")
    @PutMapping("/{id}")
    public QuadraResposta atualizar(@PathVariable Long id, @Valid @RequestBody QuadraRequisicao requisicao) {
        return QuadraResposta.de(servico.atualizar(id, requisicao));
    }

    /**
     * Ativacao/desativacao e endpoint proprio, e nao um campo do PUT: e uma
     * transicao de estado com significado de negocio (parar de aceitar reservas),
     * nao apenas a edicao de um atributo.
     */
    @Operation(summary = "Ativa ou desativa a quadra",
            description = "Exige papel ADMIN. Desativar impede NOVAS reservas; as ja confirmadas "
                    + "continuam valendo. Cancelar em massa e uma decisao administrativa separada.")
    @PutMapping("/{id}/situacao")
    public QuadraResposta alterarSituacao(@PathVariable Long id, @Valid @RequestBody SituacaoRequisicao requisicao) {
        return QuadraResposta.de(servico.alterarSituacao(id, requisicao.ativa()));
    }
}
