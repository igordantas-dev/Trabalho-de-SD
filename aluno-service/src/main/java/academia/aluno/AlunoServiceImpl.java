package academia.aluno;

import academia.aluno.entidade.Aluno;
import academia.aluno.repositorio.AlunoRepositorio;
import academia.grpc.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação do serviço gRPC AlunoService.
 * Recebe as chamadas remotas do API Gateway, persiste e consulta dados
 * no PostgreSQL via JPA/Hibernate.
 */
public class AlunoServiceImpl extends AlunoServiceGrpc.AlunoServiceImplBase {

    private final EntityManager em;
    private final AlunoRepositorio repositorio;

    public AlunoServiceImpl(EntityManager em) {
        this.em          = em;
        this.repositorio = new AlunoRepositorio(em);
    }

    // ─── CadastrarAluno ───────────────────────────────────────────────────

    @Override
    public void cadastrarAluno(CadastrarAlunoRequest request,
                               StreamObserver<AlunoResponse> responseObserver) {
        EntityTransaction tx = em.getTransaction();
        try {
            // Verifica duplicidade de CPF antes de persistir
            if (repositorio.buscarPorCpf(request.getCpf()).isPresent()) {
                responseObserver.onError(
                    Status.ALREADY_EXISTS
                        .withDescription("CPF já cadastrado: " + request.getCpf())
                        .asRuntimeException()
                );
                return;
            }

            Aluno aluno = new Aluno(
                request.getNome(),
                request.getCpf(),
                request.getEmail(),
                request.getTelefone(),
                request.getPeso(),
                request.getAltura()
            );

            tx.begin();
            repositorio.salvar(aluno);
            tx.commit();

            System.out.println("[AlunoService] Aluno cadastrado: id=" + aluno.getId()
                + ", nome=" + aluno.getNome());

            responseObserver.onNext(toResponse(aluno, "Aluno cadastrado com sucesso"));
            responseObserver.onCompleted();

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            System.err.println("[AlunoService] Erro ao cadastrar aluno: " + e.getMessage());
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Erro interno ao cadastrar aluno: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    // ─── BuscarAluno ──────────────────────────────────────────────────────

    @Override
    public void buscarAluno(BuscarAlunoRequest request,
                            StreamObserver<AlunoResponse> responseObserver) {
        try {
            UUID id = UUID.fromString(request.getId());
            Optional<Aluno> opcional = repositorio.buscarPorId(id);

            if (opcional.isEmpty()) {
                responseObserver.onError(
                    Status.NOT_FOUND
                        .withDescription("Aluno não encontrado: " + request.getId())
                        .asRuntimeException()
                );
                return;
            }

            responseObserver.onNext(toResponse(opcional.get(), "Aluno encontrado"));
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription("ID inválido: " + request.getId())
                    .asRuntimeException()
            );
        }
    }

    // ─── ListarAlunos ─────────────────────────────────────────────────────

    @Override
    public void listarAlunos(ListarAlunosRequest request,
                             StreamObserver<ListaAlunosResponse> responseObserver) {
        List<Aluno> alunos = repositorio.listarTodos();

        ListaAlunosResponse.Builder builder = ListaAlunosResponse.newBuilder();
        for (Aluno aluno : alunos) {
            builder.addAlunos(toResponse(aluno, ""));
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    // ─── AtualizarStatusAluno ─────────────────────────────────────────────

    @Override
    public void atualizarStatusAluno(AtualizarStatusAlunoRequest request,
                                     StreamObserver<AlunoResponse> responseObserver) {
        EntityTransaction tx = em.getTransaction();
        try {
            UUID id = UUID.fromString(request.getId());
            Optional<Aluno> opcional = repositorio.buscarPorId(id);

            if (opcional.isEmpty()) {
                responseObserver.onError(
                    Status.NOT_FOUND
                        .withDescription("Aluno não encontrado: " + request.getId())
                        .asRuntimeException()
                );
                return;
            }

            tx.begin();
            Aluno atualizado = repositorio.atualizarStatus(opcional.get(), request.getStatus());
            tx.commit();

            System.out.println("[AlunoService] Status atualizado: id=" + atualizado.getId()
                + ", status=" + atualizado.getStatus());

            responseObserver.onNext(toResponse(atualizado,
                "Status atualizado para " + request.getStatus()));
            responseObserver.onCompleted();

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Erro ao atualizar status: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    // ─── Helper: converte entidade → mensagem Protobuf ────────────────────

    private AlunoResponse toResponse(Aluno aluno, String mensagem) {
        return AlunoResponse.newBuilder()
            .setId(aluno.getId().toString())
            .setNome(aluno.getNome())
            .setCpf(aluno.getCpf())
            .setEmail(aluno.getEmail() != null ? aluno.getEmail() : "")
            .setTelefone(aluno.getTelefone() != null ? aluno.getTelefone() : "")
            .setPeso(aluno.getPeso() != null ? aluno.getPeso() : 0.0)
            .setAltura(aluno.getAltura() != null ? aluno.getAltura() : 0.0)
            .setStatus(aluno.getStatus())
            .setMensagem(mensagem)
            .build();
    }
}
