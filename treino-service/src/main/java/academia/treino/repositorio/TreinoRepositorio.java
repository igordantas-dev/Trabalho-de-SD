package academia.treino.repositorio;

import academia.treino.entidade.FichaTreino;
import academia.treino.entidade.ItemTreino;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório de acesso a dados do treino-service.
 * Encapsula todas as queries JPQL sobre FichaTreino e ItemTreino.
 */
public class TreinoRepositorio {

    private final EntityManager em;

    public TreinoRepositorio(EntityManager em) {
        this.em = em;
    }

    /** Persiste uma nova ficha (com seus itens via CascadeType.ALL). */
    public FichaTreino salvar(FichaTreino ficha) {
        em.persist(ficha);
        return ficha;
    }

    /** Busca ficha por UUID. */
    public Optional<FichaTreino> buscarPorId(UUID id) {
        FichaTreino ficha = em.find(FichaTreino.class, id);
        return Optional.ofNullable(ficha);
    }

    /** Lista todas as fichas de um aluno, da mais recente para a mais antiga. */
    public List<FichaTreino> listarPorAluno(String alunoId) {
        TypedQuery<FichaTreino> query = em.createQuery(
            "SELECT f FROM FichaTreino f WHERE f.alunoId = :alunoId " +
            "ORDER BY f.dataCriacao DESC", FichaTreino.class);
        query.setParameter("alunoId", alunoId);
        return query.getResultList();
    }

    /**
     * Busca um item de treino pelo UUID.
     * Usado ao registrar a execução para atualizar carga_usada e concluido.
     */
    public Optional<ItemTreino> buscarItemPorId(UUID id) {
        ItemTreino item = em.find(ItemTreino.class, id);
        return Optional.ofNullable(item);
    }

    /**
     * Consulta a última carga real utilizada pelo aluno para um exercício específico.
     * Usado para sugerir a carga na próxima ficha com o mesmo exercício.
     *
     * Retorna 0.0 se o aluno nunca realizou o exercício antes.
     */
    public double ultimaCargaUsada(String alunoId, String nomeExercicio) {
        TypedQuery<Double> query = em.createQuery(
            "SELECT i.cargaUsada FROM ItemTreino i " +
            "JOIN i.fichaTreino f " +
            "WHERE f.alunoId = :alunoId " +
            "  AND i.nomeExercicio = :nomeExercicio " +
            "  AND i.concluido = true " +
            "  AND i.cargaUsada > 0 " +
            "ORDER BY f.dataConclusao DESC", Double.class);
        query.setParameter("alunoId", alunoId);
        query.setParameter("nomeExercicio", nomeExercicio);
        query.setMaxResults(1);

        List<Double> resultados = query.getResultList();
        return resultados.isEmpty() ? 0.0 : resultados.get(0);
    }

    /** Atualiza uma ficha já persistida (status, dataConclusao). */
    public FichaTreino atualizar(FichaTreino ficha) {
        return em.merge(ficha);
    }

    /** Atualiza um item já persistido (cargaUsada, concluido). */
    public ItemTreino atualizarItem(ItemTreino item) {
        return em.merge(item);
    }
}
