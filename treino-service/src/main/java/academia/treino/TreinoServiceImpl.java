package academia.treino;

import academia.grpc.*;
import academia.treino.entidade.FichaTreino;
import academia.treino.entidade.ItemTreino;
import academia.treino.repositorio.TreinoRepositorio;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação do serviço gRPC TreinoService.
 * Todos os dados são persistidos no PostgreSQL via JPA.
 * Não há mocks, dados em memória ou simulações.
 */
public class TreinoServiceImpl extends TreinoServiceGrpc.TreinoServiceImplBase {

    private final EntityManager em;
    private final TreinoRepositorio repositorio;

    public TreinoServiceImpl(EntityManager em) {
        this.em          = em;
        this.repositorio = new TreinoRepositorio(em);
    }

    // ─── CriarFichaTreino ─────────────────────────────────────────────────

    @Override
    public void criarFichaTreino(CriarFichaTreinoRequest request,
                                 StreamObserver<FichaTreinoResponse> responseObserver) {
        EntityTransaction tx = em.getTransaction();
        try {
            FichaTreino ficha = new FichaTreino(
                request.getAlunoId(),
                request.getTitulo(),
                request.getObjetivo()
            );

            for (ItemTreinoRequest itemReq : request.getItensList()) {
                // Consulta se o aluno já realizou este exercício antes
                // para sugerir automaticamente a última carga utilizada
                double ultimaCarga = repositorio.ultimaCargaUsada(
                    request.getAlunoId(), itemReq.getNomeExercicio());

                double cargaSugerida = itemReq.getCargaSugerida() > 0
                    ? itemReq.getCargaSugerida()
                    : ultimaCarga; // usa a última carga real se não informada

                ItemTreino item = new ItemTreino(
                    itemReq.getNomeExercicio(),
                    itemReq.getGrupoMuscular(),
                    itemReq.getSeries(),
                    itemReq.getRepeticoes(),
                    cargaSugerida
                );
                ficha.addItem(item);
            }

            tx.begin();
            repositorio.salvar(ficha);
            tx.commit();

            System.out.println("[TreinoService] Ficha criada: id=" + ficha.getId()
                + ", aluno=" + ficha.getAlunoId() + ", itens=" + ficha.getItens().size());

            responseObserver.onNext(toResponse(ficha, "Ficha de treino criada com sucesso"));
            responseObserver.onCompleted();

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            System.err.println("[TreinoService] Erro ao criar ficha: " + e.getMessage());
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Erro ao criar ficha de treino: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    // ─── BuscarFichaTreino ────────────────────────────────────────────────

    @Override
    public void buscarFichaTreino(BuscarFichaTreinoRequest request,
                                  StreamObserver<FichaTreinoResponse> responseObserver) {
        try {
            UUID id = UUID.fromString(request.getId());
            Optional<FichaTreino> opcional = repositorio.buscarPorId(id);

            if (opcional.isEmpty()) {
                responseObserver.onError(
                    Status.NOT_FOUND
                        .withDescription("Ficha de treino não encontrada: " + request.getId())
                        .asRuntimeException()
                );
                return;
            }

            responseObserver.onNext(toResponse(opcional.get(), "Ficha encontrada"));
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription("ID inválido: " + request.getId())
                    .asRuntimeException()
            );
        }
    }

    // ─── ListarFichasPorAluno ─────────────────────────────────────────────

    @Override
    public void listarFichasPorAluno(ListarFichasPorAlunoRequest request,
                                     StreamObserver<ListaFichasTreinoResponse> responseObserver) {
        List<FichaTreino> fichas = repositorio.listarPorAluno(request.getAlunoId());

        ListaFichasTreinoResponse.Builder builder = ListaFichasTreinoResponse.newBuilder();
        for (FichaTreino ficha : fichas) {
            builder.addFichas(toResponse(ficha, ""));
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    // ─── RegistrarExecucao ────────────────────────────────────────────────

    @Override
    public void registrarExecucao(RegistrarExecucaoRequest request,
                                  StreamObserver<FichaTreinoResponse> responseObserver) {
        EntityTransaction tx = em.getTransaction();
        try {
            UUID fichaId = UUID.fromString(request.getFichaId());
            Optional<FichaTreino> opcional = repositorio.buscarPorId(fichaId);

            if (opcional.isEmpty()) {
                responseObserver.onError(
                    Status.NOT_FOUND
                        .withDescription("Ficha não encontrada: " + request.getFichaId())
                        .asRuntimeException()
                );
                return;
            }

            FichaTreino ficha = opcional.get();

            tx.begin();

            // Atualiza cada item com a carga real utilizada pelo aluno
            for (ExecucaoItemRequest execItem : request.getItensList()) {
                UUID itemId = UUID.fromString(execItem.getItemId());
                Optional<ItemTreino> itemOpcional = repositorio.buscarItemPorId(itemId);

                if (itemOpcional.isPresent()) {
                    ItemTreino item = itemOpcional.get();
                    item.setCargaUsada(execItem.getCargaUsada());
                    item.setConcluido(execItem.getConcluido());
                    repositorio.atualizarItem(item);
                }
            }

            // Marca a ficha como concluída
            ficha.setStatus("CONCLUIDO");
            ficha.setDataConclusao(LocalDateTime.now());
            FichaTreino atualizada = repositorio.atualizar(ficha);

            tx.commit();

            System.out.println("[TreinoService] Execução registrada: ficha=" + fichaId
                + ", itens atualizados=" + request.getItensCount());

            responseObserver.onNext(toResponse(atualizada, "Treino registrado com sucesso!"));
            responseObserver.onCompleted();

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            System.err.println("[TreinoService] Erro ao registrar execução: " + e.getMessage());
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Erro ao registrar execução: " + e.getMessage())
                    .asRuntimeException()
            );
        }
    }

    // ─── Helper: entidade → mensagem Protobuf ────────────────────────────

    private FichaTreinoResponse toResponse(FichaTreino ficha, String mensagem) {
        FichaTreinoResponse.Builder builder = FichaTreinoResponse.newBuilder()
            .setId(ficha.getId().toString())
            .setAlunoId(ficha.getAlunoId())
            .setTitulo(ficha.getTitulo())
            .setObjetivo(ficha.getObjetivo())
            .setStatus(ficha.getStatus())
            .setDataCriacao(ficha.getDataCriacao().toString())
            .setDataConclusao(ficha.getDataConclusao() != null
                ? ficha.getDataConclusao().toString() : "")
            .setMensagem(mensagem);

        for (ItemTreino item : ficha.getItens()) {
            builder.addItens(ItemTreinoResponse.newBuilder()
                .setId(item.getId().toString())
                .setNomeExercicio(item.getNomeExercicio())
                .setGrupoMuscular(item.getGrupoMuscular() != null ? item.getGrupoMuscular() : "")
                .setSeries(item.getSeries())
                .setRepeticoes(item.getRepeticoes())
                .setCargaSugerida(item.getCargaSugerida() != null ? item.getCargaSugerida() : 0.0)
                .setCargaUsada(item.getCargaUsada() != null ? item.getCargaUsada() : 0.0)
                .setConcluido(item.getConcluido() != null && item.getConcluido())
                .build());
        }

        return builder.build();
    }
}