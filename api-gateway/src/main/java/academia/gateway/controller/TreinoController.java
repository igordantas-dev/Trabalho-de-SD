package academia.gateway.controller;

import academia.gateway.dto.CriarFichaTreinoRequest;
import academia.gateway.dto.FichaTreinoResponseDto;
import academia.gateway.dto.GerarTreinoIaRequest;
import academia.gateway.dto.RegistrarExecucaoRequest;
import academia.gateway.servico.IaTreinoService;
import academia.grpc.BuscarFichaTreinoRequest;
import academia.grpc.ListarFichasPorAlunoRequest;
import academia.grpc.TreinoServiceGrpc;
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
 * Controller REST do Gateway para gerenciamento de treinos e execução de exercícios.
 * Todas as rotas iniciam por verbos de ação para máxima legibilidade.
 * Aplica deadline de 5s em todas as chamadas gRPC conforme especificação.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "3. Treinos", description = "Operações para criar fichas, gerar treinos com IA, consultar e registrar execução com cargas reais")
public class TreinoController {

    private static final long DEADLINE_SEGUNDOS = 5;

    private final TreinoServiceGrpc.TreinoServiceBlockingStub treinoStub;
    private final IaTreinoService iaTreinoService;

    public TreinoController(
            TreinoServiceGrpc.TreinoServiceBlockingStub treinoStub,
            IaTreinoService iaTreinoService) {
        this.treinoStub = treinoStub;
        this.iaTreinoService = iaTreinoService;
    }

    /**
     * Cria uma nova ficha de treino manualmente para um aluno.
     * POST /api/criar-ficha-treino
     */
    @PostMapping({"/criar-ficha-treino", "/treinos"})
    @Operation(summary = "Criar ficha de treino manual", description = "Cria uma ficha contendo exercícios, repetições, séries e sugestão de carga, persistindo no PostgreSQL via treino-service (gRPC).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ficha de treino criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados da ficha inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "504", description = "Prazo gRPC esgotado")
    })
    public ResponseEntity<FichaTreinoResponseDto> criarFichaTreino(@Valid @RequestBody CriarFichaTreinoRequest dto) {
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

        var stubComPrazo = treinoStub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS);
        var protoResp = stubComPrazo.criarFichaTreino(protoReq.build());
        return ResponseEntity.status(HttpStatus.CREATED).body(FichaTreinoResponseDto.fromProto(protoResp));
    }

    /**
     * Gera um treino inteligente assistido por IA e salva no banco de dados.
     * POST /api/gerar-treino-ia
     */
    @PostMapping({"/gerar-treino-ia", "/treinos/gerar-ia"})
    @Operation(summary = "Gerar treino inteligente com IA", description = "Busca a biometria do aluno no aluno-service, sintetiza exercícios ideais com base no objetivo e foco, e persiste a ficha no treino-service.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Treino gerado pela IA e salvo com sucesso"),
            @ApiResponse(responseCode = "404", description = "Aluno não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<FichaTreinoResponseDto> gerarTreinoComIa(@Valid @RequestBody GerarTreinoIaRequest request) {
        FichaTreinoResponseDto dto = iaTreinoService.gerarTreinoComIa(request);
        return ResponseEntity.ok(dto);
    }

    /**
     * Busca uma ficha de treino pelo ID (UUID).
     * GET /api/buscar-ficha-treino/{id}
     */
    @GetMapping({"/buscar-ficha-treino/{id}", "/treinos/{id}"})
    @Operation(summary = "Buscar ficha de treino por ID", description = "Retorna os detalhes da ficha de treino e todos os exercícios associados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ficha encontrada"),
            @ApiResponse(responseCode = "404", description = "Ficha não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<FichaTreinoResponseDto> buscarFichaTreino(@PathVariable String id) {
        var protoReq = BuscarFichaTreinoRequest.newBuilder().setId(id).build();
        var stubComPrazo = treinoStub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS);
        var protoResp = stubComPrazo.buscarFichaTreino(protoReq);
        return ResponseEntity.ok(FichaTreinoResponseDto.fromProto(protoResp));
    }

    /**
     * Lista todas as fichas de treino vinculadas a um aluno.
     * GET /api/listar-fichas-aluno/{alunoId}
     */
    @GetMapping({"/listar-fichas-aluno/{alunoId}", "/treinos/aluno/{alunoId}"})
    @Operation(summary = "Listar fichas de treino do aluno", description = "Retorna o histórico completo de fichas de treino de um aluno específico.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Fichas listadas com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<List<FichaTreinoResponseDto>> listarFichasPorAluno(@PathVariable String alunoId) {
        var protoReq = ListarFichasPorAlunoRequest.newBuilder().setAlunoId(alunoId).build();
        var stubComPrazo = treinoStub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS);
        var protoResp = stubComPrazo.listarFichasPorAluno(protoReq);
        List<FichaTreinoResponseDto> lista = protoResp.getFichasList()
                .stream()
                .map(FichaTreinoResponseDto::fromProto)
                .toList();
        return ResponseEntity.ok(lista);
    }

    /**
     * Registra a conclusão do treino e salva as cargas reais utilizadas em cada exercício.
     * POST /api/registrar-execucao-treino/{id}
     */
    @PostMapping({"/registrar-execucao-treino/{id}", "/treinos/{id}/execucao"})
    @Operation(summary = "Registrar execução e cargas reais", description = "Conclui a ficha de treino e persiste a carga real (kg) utilizada em cada exercício para progressão de carga futura.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Execução registrada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Ficha não encontrada"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<FichaTreinoResponseDto> registrarExecucaoTreino(
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

        var stubComPrazo = treinoStub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS);
        var protoResp = stubComPrazo.registrarExecucao(protoReq.build());
        return ResponseEntity.ok(FichaTreinoResponseDto.fromProto(protoResp));
    }
}
