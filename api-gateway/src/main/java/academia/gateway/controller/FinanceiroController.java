package academia.gateway.controller;

import academia.gateway.dto.GerarMensalidadeRequest;
import academia.gateway.dto.MensalidadeResponseDto;
import academia.gateway.dto.RegistrarPagamentoRequest;
import academia.grpc.BuscarMensalidadeRequest;
import academia.grpc.FinanceiroServiceGrpc;
import academia.grpc.ListarMensalidadesPorAlunoRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST do Gateway para gestão financeira (mensalidades e pagamentos).
 * Traduz chamadas HTTP/JSON para RPCs do microsserviço financeiro-service via gRPC.
 */
@RestController
@RequestMapping("/api/mensalidades")
public class FinanceiroController {

    private final FinanceiroServiceGrpc.FinanceiroServiceBlockingStub financeiroStub;

    public FinanceiroController(FinanceiroServiceGrpc.FinanceiroServiceBlockingStub financeiroStub) {
        this.financeiroStub = financeiroStub;
    }

    /**
     * Gera uma nova cobrança/mensalidade para um aluno.
     * POST /api/mensalidades
     */
    @PostMapping
    public ResponseEntity<MensalidadeResponseDto> gerar(@Valid @RequestBody GerarMensalidadeRequest dto) {
        var protoReq = academia.grpc.GerarMensalidadeRequest.newBuilder()
                .setAlunoId(dto.alunoId())
                .setValor(dto.valor())
                .setDataVencimento(dto.dataVencimento());

        if (dto.descricao() != null) {
            protoReq.setDescricao(dto.descricao());
        }

        var protoResp = financeiroStub.gerarMensalidade(protoReq.build());
        return ResponseEntity.status(HttpStatus.CREATED).body(MensalidadeResponseDto.fromProto(protoResp));
    }

    /**
     * Busca os detalhes de uma mensalidade pelo ID (UUID).
     * GET /api/mensalidades/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<MensalidadeResponseDto> buscarPorId(@PathVariable String id) {
        var protoReq = BuscarMensalidadeRequest.newBuilder().setId(id).build();
        var protoResp = financeiroStub.buscarMensalidade(protoReq);
        return ResponseEntity.ok(MensalidadeResponseDto.fromProto(protoResp));
    }

    /**
     * Lista todas as mensalidades vinculadas a um aluno.
     * GET /api/mensalidades/aluno/{alunoId}
     */
    @GetMapping("/aluno/{alunoId}")
    public ResponseEntity<List<MensalidadeResponseDto>> listarPorAluno(@PathVariable String alunoId) {
        var protoReq = ListarMensalidadesPorAlunoRequest.newBuilder().setAlunoId(alunoId).build();
        var protoResp = financeiroStub.listarMensalidadesPorAluno(protoReq);
        List<MensalidadeResponseDto> lista = protoResp.getMensalidadesList()
                .stream()
                .map(MensalidadeResponseDto::fromProto)
                .toList();
        return ResponseEntity.ok(lista);
    }

    /**
     * Registra a quitação de uma mensalidade (PIX, CARTAO, DINHEIRO, BOLETO).
     * POST /api/mensalidades/{id}/pagamento
     */
    @PostMapping("/{id}/pagamento")
    public ResponseEntity<MensalidadeResponseDto> registrarPagamento(
            @PathVariable String id,
            @Valid @RequestBody RegistrarPagamentoRequest dto) {

        var protoReq = academia.grpc.RegistrarPagamentoRequest.newBuilder()
                .setMensalidadeId(id)
                .setFormaPagamento(dto.formaPagamento())
                .build();

        var protoResp = financeiroStub.registrarPagamento(protoReq);
        return ResponseEntity.ok(MensalidadeResponseDto.fromProto(protoResp));
    }
}
