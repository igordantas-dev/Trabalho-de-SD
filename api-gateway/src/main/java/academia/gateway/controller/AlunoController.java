package academia.gateway.controller;

import academia.gateway.dto.AlunoResponseDto;
import academia.gateway.dto.AtualizarStatusAlunoRequest;
import academia.gateway.dto.CadastrarAlunoRequest;
import academia.grpc.AlunoServiceGrpc;
import academia.grpc.BuscarAlunoRequest;
import academia.grpc.ListarAlunosRequest;
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
 * Controller REST do Gateway para gerenciamento de alunos.
 * Todas as rotas iniciam por verbos de ação para maior clareza.
 * Aplica deadline de 5s em todas as chamadas gRPC conforme especificação.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Alunos", description = "Cadastro, consulta e atualização de alunos")
public class AlunoController {

    private static final long DEADLINE_SEGUNDOS = 5;
    private final AlunoServiceGrpc.AlunoServiceBlockingStub alunoStub;

    public AlunoController(AlunoServiceGrpc.AlunoServiceBlockingStub alunoStub) {
        this.alunoStub = alunoStub;
    }

    /**
     * Cadastra um novo aluno no sistema.
     * POST /api/cadastrar-aluno
     */
    @PostMapping("/cadastrarAluno")
    @Operation(summary = "Cadastrar aluno", description = "Cadastra um novo aluno com validação dos dados de entrada.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Aluno cadastrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado / token ausente"),
            @ApiResponse(responseCode = "409", description = "CPF já existente no banco de dados"),
            @ApiResponse(responseCode = "504", description = "Prazo gRPC esgotado (Deadline Exceeded)")
    })
    public ResponseEntity<AlunoResponseDto> cadastrarAluno(@Valid @RequestBody CadastrarAlunoRequest dto) {
        var protoReq = academia.grpc.CadastrarAlunoRequest.newBuilder()
                .setNome(dto.nome())
                .setCpf(dto.cpf())
                .setEmail(dto.email());

        if (dto.telefone() != null) protoReq.setTelefone(dto.telefone());
        if (dto.peso() != null) protoReq.setPeso(dto.peso());
        if (dto.altura() != null) protoReq.setAltura(dto.altura());

        var stubComPrazo = alunoStub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS);
        var protoResp = stubComPrazo.cadastrarAluno(protoReq.build());
        return ResponseEntity.status(HttpStatus.CREATED).body(AlunoResponseDto.fromProto(protoResp));
    }

    /**
     * Lista todos os alunos cadastrados.
     * GET /api/listar-alunos
     */
    @GetMapping("/listarAlunos")
    @Operation(summary = "Listar alunos", description = "Retorna a lista completa de alunos cadastrados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista recuperada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<List<AlunoResponseDto>> listarAlunos() {
        var stubComPrazo = alunoStub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS);
        var protoResp = stubComPrazo.listarAlunos(ListarAlunosRequest.getDefaultInstance());
        List<AlunoResponseDto> lista = protoResp.getAlunosList()
                .stream()
                .map(AlunoResponseDto::fromProto)
                .toList();
        return ResponseEntity.ok(lista);
    }

    /**
     * Busca um aluno pelo ID (UUID).
     * GET /api/buscar-aluno/{id}
     */
    @GetMapping("/buscarAluno/{id}")
    @Operation(summary = "Buscar aluno por ID", description = "Retorna os dados de um aluno pelo seu identificador.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aluno encontrado"),
            @ApiResponse(responseCode = "404", description = "Aluno não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<AlunoResponseDto> buscarAluno(@PathVariable("id") String id) {
        var protoReq = BuscarAlunoRequest.newBuilder().setId(id).build();
        var stubComPrazo = alunoStub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS);
        var protoResp = stubComPrazo.buscarAluno(protoReq);
        return ResponseEntity.ok(AlunoResponseDto.fromProto(protoResp));
    }

    /**
     * Atualiza o status de matrícula de um aluno (ATIVO | INATIVO | INADIMPLENTE).
     * POST /api/atualizarStatusAluno/{id}
     */
    @RequestMapping(value = "/atualizarStatusAluno/{id}", method = {RequestMethod.POST})
    @Operation(summary = "Atualizar status do aluno", description = "Altera o status de matrícula do aluno para ATIVO, INATIVO ou INADIMPLENTE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Status inválido"),
            @ApiResponse(responseCode = "404", description = "Aluno não encontrado"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<AlunoResponseDto> atualizarStatusAluno(
            @PathVariable("id") String id,
            @Valid @RequestBody AtualizarStatusAlunoRequest dto) {

        var protoReq = academia.grpc.AtualizarStatusAlunoRequest.newBuilder()
                .setId(id)
                .setStatus(dto.status())
                .build();

        var stubComPrazo = alunoStub.withDeadlineAfter(DEADLINE_SEGUNDOS, TimeUnit.SECONDS);
        var protoResp = stubComPrazo.atualizarStatusAluno(protoReq);
        return ResponseEntity.ok(AlunoResponseDto.fromProto(protoResp));
    }
}
