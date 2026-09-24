package academia.gateway.controller;

import academia.gateway.dto.CriarFichaTreinoRequest;
import academia.gateway.dto.FichaTreinoResponseDto;
import academia.gateway.dto.RegistrarExecucaoRequest;
import academia.grpc.BuscarFichaTreinoRequest;
import academia.grpc.ListarFichasPorAlunoRequest;
import academia.grpc.TreinoServiceGrpc;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST do Gateway para gerenciamento de fichas de treino e execução dos exercícios.
 * Traduz chamadas HTTP/JSON para RPCs do microsserviço treino-service via gRPC.
 */
@RestController
@RequestMapping("/api/treinos")
public class TreinoController {

    private final TreinoServiceGrpc.TreinoServiceBlockingStub treinoStub;

    public TreinoController(TreinoServiceGrpc.TreinoServiceBlockingStub treinoStub) {
        this.treinoStub = treinoStub;
    }

    /**
     * Cria uma nova ficha de treino para um aluno.
     * POST /api/treinos
     */
    @PostMapping
    public ResponseEntity<FichaTreinoResponseDto> criar(@Valid @RequestBody CriarFichaTreinoRequest dto) {
        var protoReq = academia.grpc.CriarFichaTreinoRequest.newBuilder()
                .setAlunoId(dto.alunoId())
                .setTitulo(dto.titulo())
                .setObjetivo(dto.objetivo());

        for (var itemDto : dto.itens()) {
            var itemProto = academia.grpc.ItemTreinoRequest.newBuilder()
                    .setNomeExercicio(itemDto.nomeExercicio())
                    .setGrupoMuscular(itemDto.grupoMuscular() != null ? itemDto.grupoMuscular() : "")
                    .setSeries(itemDto.series())
                    .setRepeticoes(itemDto.repeticoes())
                    .setCargaSugerida(itemDto.cargaSugerida() != null ? itemDto.cargaSugerida() : 0.0);
            protoReq.addItens(itemProto);
        }

        var protoResp = treinoStub.criarFichaTreino(protoReq.build());
        return ResponseEntity.status(HttpStatus.CREATED).body(FichaTreinoResponseDto.fromProto(protoResp));
    }

    /**
     * Busca uma ficha de treino pelo ID (UUID).
     * GET /api/treinos/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<FichaTreinoResponseDto> buscarPorId(@PathVariable String id) {
        var protoReq = BuscarFichaTreinoRequest.newBuilder().setId(id).build();
        var protoResp = treinoStub.buscarFichaTreino(protoReq);
        return ResponseEntity.ok(FichaTreinoResponseDto.fromProto(protoResp));
    }

    /**
     * Lista todas as fichas de treino de um aluno.
     * GET /api/treinos/aluno/{alunoId}
     */
    @GetMapping("/aluno/{alunoId}")
    public ResponseEntity<List<FichaTreinoResponseDto>> listarPorAluno(@PathVariable String alunoId) {
        var protoReq = ListarFichasPorAlunoRequest.newBuilder().setAlunoId(alunoId).build();
        var protoResp = treinoStub.listarFichasPorAluno(protoReq);
        List<FichaTreinoResponseDto> lista = protoResp.getFichasList()
                .stream()
                .map(FichaTreinoResponseDto::fromProto)
                .toList();
        return ResponseEntity.ok(lista);
    }

    /**
     * Registra a conclusão do treino e salva as cargas reais utilizadas em cada exercício.
     * POST /api/treinos/{id}/execucao
     */
    @PostMapping("/{id}/execucao")
    public ResponseEntity<FichaTreinoResponseDto> registrarExecucao(
            @PathVariable String id,
            @Valid @RequestBody RegistrarExecucaoRequest dto) {

        var protoReq = academia.grpc.RegistrarExecucaoRequest.newBuilder()
                .setFichaId(id);

        for (var itemDto : dto.itens()) {
            protoReq.addItens(academia.grpc.ExecucaoItemRequest.newBuilder()
                    .setItemId(itemDto.itemId())
                    .setCargaUsada(itemDto.cargaUsada())
                    .setConcluido(itemDto.concluido() != null ? itemDto.concluido() : true)
                    .build());
        }

        var protoResp = treinoStub.registrarExecucao(protoReq.build());
        return ResponseEntity.ok(FichaTreinoResponseDto.fromProto(protoResp));
    }
}
