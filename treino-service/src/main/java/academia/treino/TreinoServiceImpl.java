package academia.treino;

import academia.grpc.*;
import academia.treino.entidade.FichaTreino;
import academia.treino.entidade.ItemTreino;
import academia.treino.repositorio.TreinoRepositorio;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementação do serviço gRPC TreinoService.
 * Gerencia fichas de treino e catálogo de exercícios no PostgreSQL via JPA.
 * Suporta isolamento de transação por chamada via EntityManagerFactory.
 */
public class TreinoServiceImpl extends TreinoServiceGrpc.TreinoServiceImplBase {

    private final EntityManagerFactory emf;
    private final EntityManager singleEm;

    public TreinoServiceImpl(EntityManagerFactory emf) {
        this.emf = emf;
        this.singleEm = null;
    }

    public TreinoServiceImpl(EntityManager em) {
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

    // ─── CriarFichaTreino ─────────────────────────────────────────────────

    @Override
    public void criarFichaTreino(CriarFichaTreinoRequest request,
                                 StreamObserver<FichaTreinoResponse> responseObserver) {
        EntityManager em = createEntityManager();
        TreinoRepositorio repositorio = new TreinoRepositorio(em);
        EntityTransaction tx = em.getTransaction();
        try {
            if (request.getAlunoId() == null || request.getAlunoId().isBlank()) {
                responseObserver.onError(
                    Status.INVALID_ARGUMENT
                        .withDescription("O ID do aluno (alunoId) é obrigatório para criar a ficha de treino.")
                        .asRuntimeException()
                );
                return;
            }

            FichaTreino ficha = new FichaTreino(
                request.getAlunoId().trim(),
                request.getTitulo().trim(),
                request.getObjetivo().trim()
            );

            for (ItemTreinoRequest itemReq : request.getItensList()) {
                // Consulta se o aluno já realizou este exercício antes
                // para sugerir automaticamente a última carga utilizada
                double ultimaCarga = repositorio.ultimaCargaUsada(
                    request.getAlunoId().trim(), itemReq.getNomeExercicio().trim());

                double cargaSugerida = itemReq.getCargaSugerida() > 0
                    ? itemReq.getCargaSugerida()
                    : ultimaCarga; // usa a última carga real se não informada

                ItemTreino item = new ItemTreino(
                    itemReq.getNomeExercicio().trim(),
                    itemReq.getGrupoMuscular() != null ? itemReq.getGrupoMuscular().trim() : "",
                    itemReq.getSeries() > 0 ? itemReq.getSeries() : 3,
                    itemReq.getRepeticoes() > 0 ? itemReq.getRepeticoes() : 10,
                    cargaSugerida
                );
                ficha.addItem(item);
            }

            tx.begin();
            repositorio.salvar(ficha);
            tx.commit();

            System.out.println("[TreinoService] Ficha criada com sucesso: id=" + ficha.getId()
                + ", aluno=" + ficha.getAlunoId() + ", itens=" + ficha.getItens().size());

            responseObserver.onNext(toResponse(ficha, "Ficha de treino criada com sucesso"));
            responseObserver.onCompleted();

        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            Throwable root = getRootCause(e);
            System.err.println("[TreinoService] Erro ao criar ficha: " + root.getMessage());
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Erro ao criar ficha de treino: " + (root.getMessage() != null ? root.getMessage() : e.getMessage()))
                    .asRuntimeException()
            );
        } finally {
            closeEntityManager(em);
        }
    }

    // ─── BuscarFichaTreino ────────────────────────────────────────────────

    @Override
    public void buscarFichaTreino(BuscarFichaTreinoRequest request,
                                  StreamObserver<FichaTreinoResponse> responseObserver) {
        EntityManager em = createEntityManager();
        TreinoRepositorio repositorio = new TreinoRepositorio(em);
        try {
            UUID id = UUID.fromString(request.getId().trim());
            Optional<FichaTreino> opcional = repositorio.buscarPorId(id);

            if (opcional.isEmpty()) {
                responseObserver.onError(
                    Status.NOT_FOUND
                        .withDescription("Ficha de treino não encontrada para o ID: " + request.getId())
                        .asRuntimeException()
                );
                return;
            }

            responseObserver.onNext(toResponse(opcional.get(), "Ficha encontrada"));
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
                    .withDescription("Erro ao buscar ficha de treino: " + root.getMessage())
                    .asRuntimeException()
            );
        } finally {
            closeEntityManager(em);
        }
    }

    // ─── ListarFichasPorAluno ─────────────────────────────────────────────

    @Override
    public void listarFichasPorAluno(ListarFichasPorAlunoRequest request,
                                     StreamObserver<ListaFichasTreinoResponse> responseObserver) {
        EntityManager em = createEntityManager();
        TreinoRepositorio repositorio = new TreinoRepositorio(em);
        try {
            String alunoId = request.getAlunoId() != null ? request.getAlunoId().trim() : "";
            if (alunoId.isBlank()) {
                responseObserver.onError(
                    Status.INVALID_ARGUMENT
                        .withDescription("O ID do aluno (alunoId) é obrigatório para listar as fichas.")
                        .asRuntimeException()
                );
                return;
            }

            List<FichaTreino> fichas = repositorio.listarPorAluno(alunoId);

            ListaFichasTreinoResponse.Builder builder = ListaFichasTreinoResponse.newBuilder();
            for (FichaTreino ficha : fichas) {
                builder.addFichas(toResponse(ficha, ""));
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            Throwable root = getRootCause(e);
            System.err.println("[TreinoService] Erro ao listar fichas: " + root.getMessage());
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Erro ao listar fichas do aluno: " + root.getMessage())
                    .asRuntimeException()
            );
        } finally {
            closeEntityManager(em);
        }
    }

    // ─── RegistrarExecucao ────────────────────────────────────────────────

    @Override
    public void registrarExecucao(RegistrarExecucaoRequest request,
                                  StreamObserver<FichaTreinoResponse> responseObserver) {
        EntityManager em = createEntityManager();
        TreinoRepositorio repositorio = new TreinoRepositorio(em);
        EntityTransaction tx = em.getTransaction();
        try {
            UUID fichaId = UUID.fromString(request.getFichaId().trim());
            Optional<FichaTreino> opcional = repositorio.buscarPorId(fichaId);

            if (opcional.isEmpty()) {
                responseObserver.onError(
                    Status.NOT_FOUND
                        .withDescription("Ficha de treino não encontrada: " + request.getFichaId())
                        .asRuntimeException()
                );
                return;
            }

            FichaTreino ficha = opcional.get();

            tx.begin();

            // Atualiza cada item com a carga real utilizada pelo aluno
            for (ExecucaoItemRequest execItem : request.getItensList()) {
                UUID itemId = UUID.fromString(execItem.getItemId().trim());
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

            System.out.println("[TreinoService] Execução registrada com sucesso: ficha=" + fichaId
                + ", itens atualizados=" + request.getItensCount());

            responseObserver.onNext(toResponse(atualizada, "Treino registrado com sucesso!"));
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            responseObserver.onError(
                Status.INVALID_ARGUMENT
                    .withDescription("ID informado possui formato UUID inválido: " + e.getMessage())
                    .asRuntimeException()
            );
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            Throwable root = getRootCause(e);
            System.err.println("[TreinoService] Erro ao registrar execução: " + root.getMessage());
            responseObserver.onError(
                Status.INTERNAL
                    .withDescription("Erro ao registrar execução: " + root.getMessage())
                    .asRuntimeException()
            );
        } finally {
            closeEntityManager(em);
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

        if (ficha.getItens() != null) {
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
        }

        return builder.build();
    }
}