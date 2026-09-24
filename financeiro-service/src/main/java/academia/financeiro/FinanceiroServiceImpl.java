package academia.financeiro;

import academia.financeiro.entidade.Mensalidade;
import academia.financeiro.repositorio.FinanceiroRepositorio;
import academia.grpc.*;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação do serviço gRPC FinanceiroService.
 * Persiste e consulta mensalidades no PostgreSQL via JPA.
 */
public class FinanceiroServiceImpl extends FinanceiroServiceGrpc.FinanceiroServiceImplBase {

    private final EntityManager em;
    private final FinanceiroRepositorio repositorio;

    public FinanceiroServiceImpl(EntityManager em) {
        this.em          = em;
        this.repositorio = new FinanceiroRepositorio(em);
    }

    // ─── GerarMensalidade ─────────────────────────────────────────────────

    @Override
    public void gerarMensalidade(GerarMensalidadeRequest request,
                                 StreamObserver<MensalidadeResponse> responseObserver) {
        EntityTransaction tx = em.getTransaction();
        try {
            LocalDate dataVencimento = LocalDate.parse(request.getDataVencimento());

            Mensalidade mensalidade = new Mensalidade(
                request.getAlunoId(),
                request.getValor(),
                dataVencimento,
                request.getDescricao()
            );

            tx.begin();
            repositorio.salvar(mensalidade);
            tx.commit();

            System.out.println("[FinanceiroService] Mensalidade gerada: id=" + mensalidade.getId()
                + ", aluno=" + mensalidade.getAlunoId()
                + ", valor=R$" + mensalidade.getValor());

            responseObserver.onNext(toResponse(mensalidade, "Mensalidade gerada com sucesso"));
            responseObserver.onCompleted();

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            System.err.println("[FinanceiroService] Erro ao gerar mensalidade: " + e.getMessage());
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Erro ao gerar mensalidade: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    // ─── RegistrarPagamento ───────────────────────────────────────────────

    @Override
    public void registrarPagamento(RegistrarPagamentoRequest request,
                                   StreamObserver<MensalidadeResponse> responseObserver) {
        EntityTransaction tx = em.getTransaction();
        try {
            UUID id = UUID.fromString(request.getMensalidadeId());
            Optional<Mensalidade> opcional = repositorio.buscarPorId(id);

            if (opcional.isEmpty()) {
                responseObserver.onError(
                    Status.NOT_FOUND
                        .withDescription("Mensalidade não encontrada: " + request.getMensalidadeId())
                        .asRuntimeException()
                );
                return;
            }

            Mensalidade mensalidade = opcional.get();

            if ("PAGO".equals(mensalidade.getStatus())) {
                responseObserver.onError(
                    Status.FAILED_PRECONDITION
                        .withDescription("Mensalidade já foi paga anteriormente.")
                        .asRuntimeException()
                );
                return;
            }

            tx.begin();
            mensalidade.setStatus("PAGO");
            mensalidade.setDataPagamento(LocalDateTime.now());
            mensalidade.setFormaPagamento(request.getFormaPagamento());
            Mensalidade atualizada = repositorio.atualizar(mensalidade);
            tx.commit();

            System.out.println("[FinanceiroService] Pagamento registrado: id=" + id
                + ", forma=" + request.getFormaPagamento());

            responseObserver.onNext(toResponse(atualizada,
                "Pagamento registrado com sucesso via " + request.getFormaPagamento()));
            responseObserver.onCompleted();

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Erro ao registrar pagamento: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    // ─── BuscarMensalidade ────────────────────────────────────────────────

    @Override
    public void buscarMensalidade(BuscarMensalidadeRequest request,
                                  StreamObserver<MensalidadeResponse> responseObserver) {
        try {
            UUID id = UUID.fromString(request.getId());
            Optional<Mensalidade> opcional = repositorio.buscarPorId(id);

            if (opcional.isEmpty()) {
                responseObserver.onError(
                    Status.NOT_FOUND
                        .withDescription("Mensalidade não encontrada: " + request.getId())
                        .asRuntimeException()
                );
                return;
            }

            responseObserver.onNext(toResponse(opcional.get(), "Mensalidade encontrada"));
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription("ID inválido: " + request.getId())
                    .asRuntimeException()
            );
        }
    }

    // ─── ListarMensalidadesPorAluno ───────────────────────────────────────

    @Override
    public void listarMensalidadesPorAluno(ListarMensalidadesPorAlunoRequest request,
                                           StreamObserver<ListaMensalidadesResponse> responseObserver) {
        EntityTransaction tx = em.getTransaction();
        try {
            // Atualiza status de mensalidades vencidas antes de listar
            tx.begin();
            int atualizadas = repositorio.marcarAtrasadas();
            tx.commit();

            if (atualizadas > 0) {
                System.out.println("[FinanceiroService] " + atualizadas
                    + " mensalidade(s) marcadas como ATRASADO");
            }

            List<Mensalidade> lista = repositorio.listarPorAluno(request.getAlunoId());

            ListaMensalidadesResponse.Builder builder = ListaMensalidadesResponse.newBuilder();
            for (Mensalidade m : lista) {
                builder.addMensalidades(toResponse(m, ""));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Erro ao listar mensalidades: " + e.getMessage())
                    .asRuntimeException()
            );
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
