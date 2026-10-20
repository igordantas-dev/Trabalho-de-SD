package academia.gateway.controller;

import academia.gateway.dto.GerarMensalidadeRequest;
import academia.gateway.dto.MensalidadeResponseDto;
import academia.gateway.dto.RegistrarPagamentoRequest;
import academia.grpc.BuscarMensalidadeRequest;
import academia.grpc.FinanceiroServiceGrpc;
import academia.grpc.ListarMensalidadesPorAlunoRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Controller REST do Gateway para gestão financeira (mensalidades e pagamentos).
 * Todas as rotas iniciam por verbos de ação para máxima clareza.
 * Aplica deadline de 5s em todas as chamadas gRPC conforme especificação.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Financeiro", description = "Geração e pagamento de mensalidades")
public class FinanceiroController {

    private static final long DEADLINE_SEGUNDOS = 5;

    private final FinanceiroServiceGrpc.FinanceiroServiceBlockingStub financeiroStub;

    public FinanceiroController(FinanceiroServiceGrpc.FinanceiroServiceBlockingStub financeiroStub) {
        this.financeiroStub = financeiroStub;
    }

    /**
     * Gera uma nova cobrança/mensalidade para um aluno.
     * POST /api/gerar-mensalidade
     */
    @PostMapping("/gerarMensalidade")
    @Operation(summary = "Gerar mensalidade", description = "Cria uma cobrança para o aluno com valor e data de vencimento.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mensalidade gerada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados da mensalidade inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "504", description = "Prazo gRPC esgotado")
    })
    public ResponseEntity<MensalidadeResponseDto> gerarMensalidade(@Valid @RequestBody GerarMensalidadeRequest dto) {
        var protoReq = academia.grpc.GerarMensalidadeRequest.newBuilder()
                .setAlunoId(dto.alunoId())
                .setValor(dto.valor())
                .setDataVencimento(dto.dataVencimento());

        if (dto.descricao() != null) {
            protoReq.setDescricao(dto.descricao());
        }

        var stubComPrazo = financeiroStub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS);
        var protoResp = stubComPrazo.gerarMensalidade(protoReq.build());
        return ResponseEntity.status(HttpStatus.CREATED).body(MensalidadeResponseDto.fromProto(protoResp));
    }

    /**
     * Busca os detalhes de uma mensalidade pelo ID (UUID).
     * GET /api/buscar-mensalidade/{id}
     */
    @GetMapping("/buscarMensalidade/{id}")
    @Operation(summary = "Buscar mensalidade por ID", description = "Retorna os detalhes de uma mensalidade pelo seu identificador.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensalidade encontrada"),
            @ApiResponse(responseCode = "404", description = "Mensalidade não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<MensalidadeResponseDto> buscarMensalidade(@PathVariable("id") String id) {
        var protoReq = BuscarMensalidadeRequest.newBuilder().setId(id).build();
        var stubComPrazo = financeiroStub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS);
        var protoResp = stubComPrazo.buscarMensalidade(protoReq);
        return ResponseEntity.ok(MensalidadeResponseDto.fromProto(protoResp));
    }

    /**
     * Lista todas as mensalidades vinculadas a um aluno.
     * GET /api/listarMensalidadesAluno/{alunoId}
     */
    @GetMapping("/listarMensalidadesAluno/{alunoId}")
    @Operation(summary = "Listar mensalidades do aluno", description = "Retorna todas as mensalidades de um aluno com seus respectivos status.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mensalidades listadas com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<List<MensalidadeResponseDto>> listarMensalidadesPorAluno(@PathVariable("alunoId") String alunoId) {
        var protoReq = ListarMensalidadesPorAlunoRequest.newBuilder().setAlunoId(alunoId).build();
        var stubComPrazo = financeiroStub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS);
        var protoResp = stubComPrazo.listarMensalidadesPorAluno(protoReq);
        List<MensalidadeResponseDto> lista = protoResp.getMensalidadesList()
                .stream()
                .map(MensalidadeResponseDto::fromProto)
                .toList();
        return ResponseEntity.ok(lista);
    }

    /**
     * Registra a quitação de uma mensalidade (PIX, CARTAO, DINHEIRO, BOLETO).
     * POST /api/registrarPagamentoMensalidade/{id}
     */
    @PostMapping("/registrarPagamentoMensalidade/{id}")
    @Operation(summary = "Registrar pagamento da mensalidade", description = "Registra o pagamento e atualiza o status da mensalidade para PAGO.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagamento registrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Forma de pagamento inválida"),
            @ApiResponse(responseCode = "422", description = "Mensalidade já foi paga anteriormente (precondição falhou)"),
            @ApiResponse(responseCode = "404", description = "Mensalidade não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<MensalidadeResponseDto> registrarPagamentoMensalidade(
            @PathVariable("id") String id,
            @Valid @RequestBody RegistrarPagamentoRequest dto) {

        var protoReq = academia.grpc.RegistrarPagamentoRequest.newBuilder()
                .setMensalidadeId(id)
                .setFormaPagamento(dto.formaPagamento())
                .build();

        var stubComPrazo = financeiroStub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS);
        var protoResp = stubComPrazo.registrarPagamento(protoReq);
        return ResponseEntity.ok(MensalidadeResponseDto.fromProto(protoResp));
    }
}
