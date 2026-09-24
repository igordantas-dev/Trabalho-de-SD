package academia.gateway.controller;

import academia.gateway.dto.AlunoResponseDto;
import academia.gateway.dto.AtualizarStatusAlunoRequest;
import academia.gateway.dto.CadastrarAlunoRequest;
import academia.grpc.AlunoServiceGrpc;
import academia.grpc.BuscarAlunoRequest;
import academia.grpc.ListarAlunosRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST do Gateway para gerenciamento de alunos.
 * Traduz chamadas HTTP/JSON para RPCs do microsserviço aluno-service via gRPC.
 */
@RestController
@RequestMapping("/api/alunos")
public class AlunoController {

    private final AlunoServiceGrpc.AlunoServiceBlockingStub alunoStub;

    public AlunoController(AlunoServiceGrpc.AlunoServiceBlockingStub alunoStub) {
        this.alunoStub = alunoStub;
    }

    /**
     * Cadastra um novo aluno no sistema.
     * POST /api/alunos
     */
    @PostMapping
    public ResponseEntity<AlunoResponseDto> cadastrar(@Valid @RequestBody CadastrarAlunoRequest dto) {
        var protoReq = academia.grpc.CadastrarAlunoRequest.newBuilder()
                .setNome(dto.nome())
                .setCpf(dto.cpf())
                .setEmail(dto.email());

        if (dto.telefone() != null) protoReq.setTelefone(dto.telefone());
        if (dto.peso() != null) protoReq.setPeso(dto.peso());
        if (dto.altura() != null) protoReq.setAltura(dto.altura());

        var protoResp = alunoStub.cadastrarAluno(protoReq.build());
        return ResponseEntity.status(HttpStatus.CREATED).body(AlunoResponseDto.fromProto(protoResp));
    }

    /**
     * Lista todos os alunos cadastrados.
     * GET /api/alunos
     */
    @GetMapping
    public ResponseEntity<List<AlunoResponseDto>> listar() {
        var protoResp = alunoStub.listarAlunos(ListarAlunosRequest.getDefaultInstance());
        List<AlunoResponseDto> lista = protoResp.getAlunosList()
                .stream()
                .map(AlunoResponseDto::fromProto)
                .toList();
        return ResponseEntity.ok(lista);
    }

    /**
     * Busca um aluno pelo ID (UUID).
     * GET /api/alunos/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<AlunoResponseDto> buscarPorId(@PathVariable String id) {
        var protoReq = BuscarAlunoRequest.newBuilder().setId(id).build();
        var protoResp = alunoStub.buscarAluno(protoReq);
        return ResponseEntity.ok(AlunoResponseDto.fromProto(protoResp));
    }

    /**
     * Atualiza o status de matrícula de um aluno (ATIVO | INATIVO | INADIMPLENTE).
     * PATCH /api/alunos/{id}/status
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<AlunoResponseDto> atualizarStatus(
            @PathVariable String id,
            @Valid @RequestBody AtualizarStatusAlunoRequest dto) {

        var protoReq = academia.grpc.AtualizarStatusAlunoRequest.newBuilder()
                .setId(id)
                .setStatus(dto.status())
                .build();

        var protoResp = alunoStub.atualizarStatusAluno(protoReq);
        return ResponseEntity.ok(AlunoResponseDto.fromProto(protoResp));
    }
}
