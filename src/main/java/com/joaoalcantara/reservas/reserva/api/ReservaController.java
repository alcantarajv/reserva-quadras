package com.joaoalcantara.reservas.reserva.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.joaoalcantara.reservas.reserva.aplicacao.ReservaServico;
import com.joaoalcantara.reservas.reserva.dominio.Reserva;
import com.joaoalcantara.reservas.seguranca.UsuarioAutenticado;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Endpoints de reserva. Todos exigem autenticacao.
 *
 * <p>O usuario da reserva vem sempre do token, nunca do corpo da requisicao —
 * caso contrario um cliente reservaria em nome de outro.</p>
 */
@Tag(name = "Reservas", description = "Criacao, consulta e cancelamento de reservas")
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    private final ReservaServico servico;

    public ReservaController(ReservaServico servico) {
        this.servico = servico;
    }

    @Operation(summary = "Cria uma reserva",
            description = "O usuario vem do token, nunca do corpo da requisicao. "
                    + "Nao existe consulta previa de disponibilidade: entre o SELECT e o INSERT "
                    + "haveria uma janela de corrida. A exclusividade e garantida por uma constraint "
                    + "de exclusao no PostgreSQL, e a requisicao perdedora recebe 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Reserva confirmada"),
            @ApiResponse(responseCode = "409", description = "Horario ja reservado para esta quadra", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "422", description = "Regra de negocio violada: horario no passado, fora do funcionamento, quadra inativa, duracao invalida", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @PostMapping
    public ResponseEntity<ReservaResposta> criar(@AuthenticationPrincipal UsuarioAutenticado autenticado,
                                                 @Valid @RequestBody ReservaRequisicao requisicao) {
        Reserva reserva = servico.criar(autenticado.id(), requisicao.quadraId(), requisicao.inicio(), requisicao.fim());
        return ResponseEntity
                .created(URI.create("/api/reservas/" + reserva.getId()))
                .body(ReservaResposta.de(reserva));
    }

    /** CLIENTE ve as proprias; ADMIN ve todas. A distincao fica no servico. */
    @Operation(summary = "Lista reservas",
            description = "Um CLIENTE recebe apenas as proprias reservas; um ADMIN recebe todas.")
    @GetMapping
    public List<ReservaResposta> listar(@AuthenticationPrincipal UsuarioAutenticado autenticado) {
        return servico.listar(autenticado).stream().map(ReservaResposta::de).toList();
    }

    @Operation(summary = "Busca uma reserva",
            description = "Uma reserva de outro cliente responde 404, e nao 403: responder 403 "
                    + "ja confirmaria que aquela reserva existe.")
    @GetMapping("/{id}")
    public ReservaResposta buscar(@AuthenticationPrincipal UsuarioAutenticado autenticado, @PathVariable Long id) {
        return ReservaResposta.de(servico.buscar(id, autenticado));
    }

    /**
     * Cancela a reserva.
     *
     * <p>DELETE, mas o registro nao some: o status vira CANCELADA. Historico de
     * reserva e informacao contabil e operacional — apagar a linha destruiria a
     * trilha de quem reservou o que.</p>
     */
    @Operation(summary = "Cancela uma reserva",
            description = "O registro nao e apagado: o status vira CANCELADA e o horario volta a "
                    + "ficar disponivel. Exige antecedencia minima configuravel; um ADMIN pode "
                    + "cancelar a qualquer momento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reserva cancelada"),
            @ApiResponse(responseCode = "422", description = "Fora do prazo de cancelamento, ou reserva ja cancelada/concluida", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @DeleteMapping("/{id}")
    public ReservaResposta cancelar(@AuthenticationPrincipal UsuarioAutenticado autenticado, @PathVariable Long id) {
        return ReservaResposta.de(servico.cancelar(id, autenticado));
    }
}
