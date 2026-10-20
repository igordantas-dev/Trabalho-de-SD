package academia.aluno;

import academia.aluno.entidade.Aluno;
import academia.aluno.repositorio.AlunoRepositorio;
import academia.grpc.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação do serviço gRPC AlunoService.
 * Recebe chamadas do API Gateway e gerencia dados de alunos no PostgreSQL.
 * Suporta isolamento de transação por chamada via EntityManagerFactory.
 */
public class AlunoServiceImpl extends AlunoServiceGrpc.AlunoServiceImplBase {

    private final EntityManagerFactory emf;
    private final EntityManager singleEm;

    public AlunoServiceImpl(EntityManagerFactory emf) {
        this.emf = emf;
        this.singleEm = null;
    }

    public AlunoServiceImpl(EntityManager em) {
        this.emf = null;
        this.singleEm = em;
    }

    private EntityManager createEntityManager() {
        return emf != null ? emf.createEntityManager() : singleEm;
    }

    private void closeEntityManager(EntityManager em) {
        if (emf != null && em != null && em.isOpen()) {
            em.close();
        }
    }

    private Throwable getRootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    // ─── CadastrarAluno ───────────────────────────────────────────────────

    @Override
    public void cadastrarAluno(CadastrarAlunoRequest request,
                               StreamObserver<AlunoResponse> responseObserver) {
        EntityManager em = createEntityManager();
        AlunoRepositorio repositorio = new AlunoRepositorio(em);
        EntityTransaction tx = em.getTransaction();

        String cpfLimpo = request.getCpf() != null ? request.getCpf().trim() : "";
        String emailLimpo = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        String nomeLimpo = request.getNome() != null ? request.getNome().trim() : "";
        String telLimpo = (request.getTelefone() != null && !request.getTelefone().isBlank())
                ? request.getTelefone().trim() : "";
        Double peso = request.getPeso() > 0 ? request.getPeso() : null;
        Double altura = request.getAltura() > 0 ? request.getAltura() : null;

        try {
            // 1. Validação proativa de duplicidade de CPF
            if (repositorio.buscarPorCpf(cpfLimpo).isPresent()) {
                responseObserver.onError(
                    Status.ALREADY_EXISTS
                        .withDescription("CPF já cadastrado: " + cpfLimpo)
                        .asRuntimeException()
                );
                return;
            }

            // 2. Validação proativa de duplicidade de E-mail
            if (repositorio.buscarPorEmail(emailLimpo).isPresent()) {
                responseObserver.onError(
                    Status.ALREADY_EXISTS
                        .withDescription("E-mail já cadastrado: " + emailLimpo)
                        .asRuntimeException()
                );
                return;
            }

            Aluno aluno = new Aluno(
                nomeLimpo,
                cpfLimpo,
                emailLimpo,
                telLimpo,
                peso,
                altura
            );

            tx.begin();
            repositorio.salvar(aluno);
            tx.commit();

            System.out.println("[AlunoService] Aluno cadastrado com sucesso: id=" + aluno.getId()
                + ", nome=" + aluno.getNome() + ", email=" + aluno.getEmail());

            responseObserver.onNext(toResponse(aluno, "Aluno cadastrado com sucesso"));
            responseObserver.onCompleted();

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            Throwable root = getRootCause(e);
            String msg = (root.getMessage() != null ? root.getMessage() : e.getMessage()).toLowerCase();

            System.err.println("[AlunoService] Erro ao cadastrar aluno: " + root.getMessage());

            if (msg.contains("duplicate key") || msg.contains("unique constraint") || msg.contains("violates unique")) {
                if (msg.contains("email") || msg.contains("ukmbvpvjdhy6gsmbw6odlro3ub")) {
                    responseObserver.onError(
                        Status.ALREADY_EXISTS
                            .withDescription("E-mail já cadastrado: " + emailLimpo)
                            .asRuntimeException()
                    );
                } else if (msg.contains("cpf")) {
                    responseObserver.onError(
                        Status.ALREADY_EXISTS
                            .withDescription("CPF já cadastrado: " + cpfLimpo)
                            .asRuntimeException()
                    );
                } else {
                    responseObserver.onError(
                        Status.ALREADY_EXISTS
                            .withDescription("Registro duplicado: CPF ou e-mail já existente no banco de dados.")
                            .asRuntimeException()
                    );
                }
            } else if (msg.contains("value too long") || msg.contains("varying(")) {
                responseObserver.onError(
                    Status.INVALID_ARGUMENT
                        .withDescription("Tamanho de campo excedido: verifique se o CPF ou telefone possui caracteres além do permitido.")
                        .asRuntimeException()
                );
            } else {
                responseObserver.onError(
                    Status.INTERNAL
                        .withDescription("Erro ao cadastrar aluno: " + (root.getMessage() != null ? root.getMessage() : e.getMessage()))
                        .asRuntimeException()
                );
            }
        } finally {
            closeEntityManager(em);
        }
    }

    // ─── BuscarAluno ──────────────────────────────────────────────────────

    @Override
    public void buscarAluno(BuscarAlunoRequest request,
                            StreamObserver<AlunoResponse> responseObserver) {
        EntityManager em = createEntityManager();
        AlunoRepositorio repositorio = new AlunoRepositorio(em);
        try {
            UUID id = UUID.fromString(request.getId().trim());
            Optional<Aluno> opcional = repositorio.buscarPorId(id);

            if (opcional.isEmpty()) {
                responseObserver.onError(
                    Status.NOT_FOUND
                        .withDescription("Aluno não encontrado para o ID: " + request.getId())
                        .asRuntimeException()
                );
                return;
            }

            responseObserver.onNext(toResponse(opcional.get(), "Aluno encontrado"));
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription("ID informado possui formato UUID inválido: " + request.getId())
                    .asRuntimeException()
            );
        } catch (Exception e) {
            Throwable root = getRootCause(e);
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Erro ao buscar aluno: " + root.getMessage())
                    .asRuntimeException()
            );
        } finally {
            closeEntityManager(em);
        }
    }

    // ─── ListarAlunos ─────────────────────────────────────────────────────

    @Override
    public void listarAlunos(ListarAlunosRequest request,
                             StreamObserver<ListaAlunosResponse> responseObserver) {
        EntityManager em = createEntityManager();
        AlunoRepositorio repositorio = new AlunoRepositorio(em);
        try {
            List<Aluno> alunos = repositorio.listarTodos();

            ListaAlunosResponse.Builder builder = ListaAlunosResponse.newBuilder();
            for (Aluno aluno : alunos) {
                builder.addAlunos(toResponse(aluno, ""));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            Throwable root = getRootCause(e);
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Erro ao listar alunos: " + root.getMessage())
                    .asRuntimeException()
            );
        } finally {
            closeEntityManager(em);
        }
    }

    // ─── AtualizarStatusAluno ─────────────────────────────────────────────

    @Override
    public void atualizarStatusAluno(AtualizarStatusAlunoRequest request,
                                     StreamObserver<AlunoResponse> responseObserver) {
        EntityManager em = createEntityManager();
        AlunoRepositorio repositorio = new AlunoRepositorio(em);
        EntityTransaction tx = em.getTransaction();
        try {
            UUID id = UUID.fromString(request.getId().trim());
            Optional<Aluno> opcional = repositorio.buscarPorId(id);

            if (opcional.isEmpty()) {
                responseObserver.onError(
                    Status.NOT_FOUND
                        .withDescription("Aluno não encontrado para atualização de status: " + request.getId())
                        .asRuntimeException()
                );
                return;
            }

            tx.begin();
            Aluno atualizado = repositorio.atualizarStatus(opcional.get(), request.getStatus().trim().toUpperCase());
            tx.commit();

            System.out.println("[AlunoService] Status atualizado: id=" + atualizado.getId()
                + ", status=" + atualizado.getStatus());

            responseObserver.onNext(toResponse(atualizado,
                "Status atualizado para " + request.getStatus()));
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription("ID informado possui formato UUID inválido: " + request.getId())
                    .asRuntimeException()
            );
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            Throwable root = getRootCause(e);
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Erro ao atualizar status: " + root.getMessage())
                    .asRuntimeException()
            );
        } finally {
            closeEntityManager(em);
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
