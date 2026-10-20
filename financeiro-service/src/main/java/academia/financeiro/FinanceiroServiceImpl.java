package academia.financeiro;

import academia.financeiro.entidade.Mensalidade;
import academia.financeiro.repositorio.FinanceiroRepositorio;
import academia.grpc.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação do serviço gRPC FinanceiroService.
 * Gerencia emissão e pagamento de mensalidades no PostgreSQL via JPA.
 * Suporta isolamento de transação por chamada via EntityManagerFactory.
 */
public class FinanceiroServiceImpl extends FinanceiroServiceGrpc.FinanceiroServiceImplBase {

    private final EntityManagerFactory emf;
    private final EntityManager singleEm;

    public FinanceiroServiceImpl(EntityManagerFactory emf) {
        this.emf = emf;
        this.singleEm = null;
    }

    public FinanceiroServiceImpl(EntityManager em) {
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

    // ─── GerarMensalidade ─────────────────────────────────────────────────

    @Override
    public void gerarMensalidade(GerarMensalidadeRequest request,
                                 StreamObserver<MensalidadeResponse> responseObserver) {
        EntityManager em = createEntityManager();
        FinanceiroRepositorio repositorio = new FinanceiroRepositorio(em);
        EntityTransaction tx = em.getTransaction();
        try {
            if (request.getAlunoId() == null || request.getAlunoId().isBlank()) {
                responseObserver.onError(
                    Status.INVALID_ARGUMENT
                        .withDescription("O ID do aluno (alunoId) é obrigatório para gerar mensalidade.")
                        .asRuntimeException()
                );
                return;
            }

            LocalDate dataVencimento;
            try {
                dataVencimento = LocalDate.parse(request.getDataVencimento().trim());
            } catch (Exception ex) {
                responseObserver.onError(
                    Status.INVALID_ARGUMENT
                        .withDescription("Data de vencimento inválida. Use o formato YYYY-MM-DD: " + request.getDataVencimento())
                        .asRuntimeException()
                );
                return;
            }

            Mensalidade mensalidade = new Mensalidade(
                request.getAlunoId().trim(),
                request.getValor(),
                dataVencimento,
                request.getDescricao() != null ? request.getDescricao().trim() : ""
            );

            tx.begin();
            repositorio.salvar(mensalidade);
            tx.commit();

            System.out.println("[FinanceiroService] Mensalidade gerada com sucesso: id=" + mensalidade.getId()
                + ", aluno=" + mensalidade.getAlunoId()
                + ", valor=R$" + mensalidade.getValor());

            responseObserver.onNext(toResponse(mensalidade, "Mensalidade gerada com sucesso"));
            responseObserver.onCompleted();

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            Throwable root = getRootCause(e);
            System.err.println("[FinanceiroService] Erro ao gerar mensalidade: " + root.getMessage());
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Erro ao gerar mensalidade: " + (root.getMessage() != null ? root.getMessage() : e.getMessage()))
                    .asRuntimeException()
            );
        } finally {
            closeEntityManager(em);
        }
    }

    // ─── RegistrarPagamento ───────────────────────────────────────────────

    @Override
    public void registrarPagamento(RegistrarPagamentoRequest request,
                                   StreamObserver<MensalidadeResponse> responseObserver) {
        EntityManager em = createEntityManager();
        FinanceiroRepositorio repositorio = new FinanceiroRepositorio(em);
        EntityTransaction tx = em.getTransaction();
        try {
            UUID id;
            try {
                id = UUID.fromString(request.getMensalidadeId().trim());
            } catch (Exception ex) {
                responseObserver.onError(
                    Status.INVALID_ARGUMENT
                        .withDescription("ID informado possui formato UUID inválido: " + request.getMensalidadeId())
                        .asRuntimeException()
                );
                return;
            }

            Optional<Mensalidade> opcional = repositorio.buscarPorId(id);

            if (opcional.isEmpty()) {
                responseObserver.onError(
                    Status.NOT_FOUND
                        .withDescription("Mensalidade não encontrada para o ID: " + request.getMensalidadeId())
                        .asRuntimeException()
                );
                return;
            }

            Mensalidade mensalidade = opcional.get();

            if ("PAGO".equalsIgnoreCase(mensalidade.getStatus())) {
                responseObserver.onError(
                    Status.FAILED_PRECONDITION
                        .withDescription("Esta mensalidade já foi quitada anteriormente e não pode ser paga novamente.")
                        .asRuntimeException()
                );
                return;
            }

            tx.begin();
            mensalidade.setStatus("PAGO");
            mensalidade.setDataPagamento(LocalDateTime.now());
            mensalidade.setFormaPagamento(request.getFormaPagamento().trim().toUpperCase());
            Mensalidade atualizada = repositorio.atualizar(mensalidade);
            tx.commit();

            System.out.println("[FinanceiroService] Pagamento registrado com sucesso: id=" + id
                + ", forma=" + request.getFormaPagamento());

            responseObserver.onNext(toResponse(atualizada,
                "Pagamento registrado com sucesso via " + request.getFormaPagamento()));
            responseObserver.onCompleted();

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            Throwable root = getRootCause(e);
            System.err.println("[FinanceiroService] Erro ao registrar pagamento: " + root.getMessage());
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Erro ao registrar pagamento: " + (root.getMessage() != null ? root.getMessage() : e.getMessage()))
                    .asRuntimeException()
            );
        } finally {
            closeEntityManager(em);
        }
    }

    // ─── BuscarMensalidade ────────────────────────────────────────────────

    @Override
    public void buscarMensalidade(BuscarMensalidadeRequest request,
                                  StreamObserver<MensalidadeResponse> responseObserver) {
        EntityManager em = createEntityManager();
        FinanceiroRepositorio repositorio = new FinanceiroRepositorio(em);
        try {
            UUID id = UUID.fromString(request.getId().trim());
            Optional<Mensalidade> opcional = repositorio.buscarPorId(id);

            if (opcional.isEmpty()) {
                responseObserver.onError(
                    Status.NOT_FOUND
                        .withDescription("Mensalidade não encontrada para o ID: " + request.getId())
                        .asRuntimeException()
                );
                return;
            }

            responseObserver.onNext(toResponse(opcional.get(), "Mensalidade encontrada"));
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
                    .withDescription("Erro ao buscar mensalidade: " + root.getMessage())
                    .asRuntimeException()
            );
        } finally {
            closeEntityManager(em);
        }
    }

    // ─── ListarMensalidadesPorAluno ───────────────────────────────────────

    @Override
    public void listarMensalidadesPorAluno(ListarMensalidadesPorAlunoRequest request,
                                           StreamObserver<ListaMensalidadesResponse> responseObserver) {
        EntityManager em = createEntityManager();
        FinanceiroRepositorio repositorio = new FinanceiroRepositorio(em);
        try {
            String alunoId = request.getAlunoId() != null ? request.getAlunoId().trim() : "";
            if (alunoId.isBlank()) {
                responseObserver.onError(
                    Status.INVALID_ARGUMENT
                        .withDescription("O ID do aluno (alunoId) é obrigatório para listar as mensalidades.")
                        .asRuntimeException()
                );
                return;
            }

            // Atualiza status de mensalidades vencidas de forma segura e não bloqueante
            EntityTransaction tx = em.getTransaction();
            try {
                tx.begin();
                int atualizadas = repositorio.marcarAtrasadas();
                tx.commit();
                if (atualizadas > 0) {
                    System.out.println("[FinanceiroService] " + atualizadas + " mensalidade(s) marcadas como ATRASADO");
                }
            } catch (Exception ex) {
                if (tx.isActive()) tx.rollback();
                System.err.println("[FinanceiroService] Aviso ao atualizar mensalidades atrasadas: " + ex.getMessage());
            }

            List<Mensalidade> lista = repositorio.listarPorAluno(alunoId);

            ListaMensalidadesResponse.Builder builder = ListaMensalidadesResponse.newBuilder();
            for (Mensalidade m : lista) {
                builder.addMensalidades(toResponse(m, ""));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            Throwable root = getRootCause(e);
            System.err.println("[FinanceiroService] Erro ao listar mensalidades: " + root.getMessage());
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Erro ao listar mensalidades do aluno: " + root.getMessage())
                    .asRuntimeException()
            );
        } finally {
            closeEntityManager(em);
        }
    }

    // ─── Helper: entidade → mensagem Protobuf ────────────────────────────

    private MensalidadeResponse toResponse(Mensalidade m, String mensagem) {
        return MensalidadeResponse.newBuilder()
            .setId(m.getId().toString())
            .setAlunoId(m.getAlunoId())
            .setValor(m.getValor())
            .setDataVencimento(m.getDataVencimento().toString())
            .setDataPagamento(m.getDataPagamento() != null
                ? m.getDataPagamento().toString() : "")
            .setStatus(m.getStatus())
            .setFormaPagamento(m.getFormaPagamento() != null
                ? m.getFormaPagamento() : "")
            .setDescricao(m.getDescricao() != null ? m.getDescricao() : "")
            .setMensagem(mensagem)
            .build();
    }
}
