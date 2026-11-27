package academia.aluno;

import academia.aluno.entidade.Aluno;
import academia.aluno.repositorio.AlunoRepositorio;
import academia.grpc.*;
import academia.seguranca.Senhas;
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

    @Override
    public void autenticarAluno(AutenticarAlunoRequest request, StreamObserver<AlunoResponse> observer) {
        EntityManager em = createEntityManager();
        try {
            var aluno = new AlunoRepositorio(em).buscarPorEmail(request.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
            String hash = aluno.map(Aluno::getSenhaHash).orElse(HASH_AUSENTE);
            boolean senhaValida = Senhas.verificar(request.getSenha(), hash == null ? HASH_AUSENTE : hash);
            if (aluno.isEmpty() || aluno.get().getSenhaHash() == null || !senhaValida) {
                observer.onError(Status.UNAUTHENTICATED.withDescription("E-mail ou senha incorretos.").asRuntimeException());
                return;
            }
            observer.onNext(toResponse(aluno.get(), "Acesso autorizado")); observer.onCompleted();
        } catch (Exception e) {
            System.err.println("[AlunoService] Falha de autenticacao: " + e.getClass().getSimpleName());
            observer.onError(Status.INTERNAL.withDescription("Nao foi possivel entrar agora. Tente novamente.").asRuntimeException());
        } finally { closeEntityManager(em); }
    }

    // Hash ficticio valido para reduzir diferenca de tempo quando o email nao existe.
    private static final String HASH_AUSENTE = Senhas.gerar(UUID.randomUUID().toString());

    @Override
    public void definirSenhaAluno(DefinirSenhaAlunoRequest request, StreamObserver<AlunoResponse> observer) {
        EntityManager em = createEntityManager();
        var tx = em.getTransaction();
        try {
            Senhas.validar(request.getSenha());
            var aluno = em.find(Aluno.class, UUID.fromString(request.getAlunoId()));
            if (aluno == null) { observer.onError(Status.NOT_FOUND.withDescription("Aluno nao encontrado.").asRuntimeException()); return; }
            String hash = Senhas.gerar(request.getSenha());
            tx.begin(); aluno.setSenhaHash(hash); tx.commit();
            observer.onNext(toResponse(aluno, "Senha de acesso atualizada.")); observer.onCompleted();
        } catch (IllegalArgumentException e) {
            if (tx.isActive()) tx.rollback();
            observer.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            observer.onError(Status.INTERNAL.withDescription("Nao foi possivel atualizar a senha.").asRuntimeException());
        } finally { closeEntityManager(em); }
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
            Senhas.validar(request.getSenha());
            if (request.getPeso() != 0 && (!Double.isFinite(request.getPeso()) || request.getPeso() < 1 || request.getPeso() > 500))
                throw new IllegalArgumentException("Informe o peso em kg, entre 1 e 500.");
            if (request.getAltura() != 0 && (!Double.isFinite(request.getAltura()) || request.getAltura() < 0.5 || request.getAltura() > 2.5))
                throw new IllegalArgumentException("Informe a altura em metros, entre 0,50 e 2,50. Exemplo: 1,70, nao 170.");
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

            aluno.setSenhaHash(Senhas.gerar(request.getSenha()));
            tx.begin();
            repositorio.salvar(aluno);
            tx.commit();

            System.out.println("[AlunoService] Aluno cadastrado com sucesso: id=" + aluno.getId()
                + ", nome=" + aluno.getNome() + ", email=" + aluno.getEmail());

            responseObserver.onNext(toResponse(aluno, "Aluno cadastrado com sucesso"));
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            if (tx.isActive()) tx.rollback();
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
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
