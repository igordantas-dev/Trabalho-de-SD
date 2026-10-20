package academia.financeiro.repositorio;

import academia.financeiro.entidade.Mensalidade;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório de acesso a dados do financeiro-service.
 */
public class FinanceiroRepositorio {

    private final EntityManager em;

    public FinanceiroRepositorio(EntityManager em) {
        this.em = em;
    }

    /** Persiste uma nova mensalidade. */
    public Mensalidade salvar(Mensalidade mensalidade) {
        em.persist(mensalidade);
        return mensalidade;
    }

    /** Busca mensalidade por UUID. */
    public Optional<Mensalidade> buscarPorId(UUID id) {
        return Optional.ofNullable(em.find(Mensalidade.class, id));
    }

    /** Lista todas as mensalidades de um aluno, da mais recente para a mais antiga. */
    public List<Mensalidade> listarPorAluno(String alunoId) {
        TypedQuery<Mensalidade> query = em.createQuery(
            "SELECT m FROM Mensalidade m WHERE m.alunoId = :alunoId " +
            "ORDER BY m.dataVencimento DESC", Mensalidade.class);
        query.setParameter("alunoId", alunoId);
        return query.getResultList();
    }

    /**
     * Atualiza mensalidades vencidas que ainda estão PENDENTE para ATRASADO.
     * Chamado automaticamente antes de listar, para manter o status consistente.
     */
    public int marcarAtrasadas() {
        return em.createQuery(
            "UPDATE Mensalidade m SET m.status = 'ATRASADO' " +
            "WHERE m.status = 'PENDENTE' AND m.dataVencimento < :hoje")
            .setParameter("hoje", LocalDate.now())
            .executeUpdate();
    }

    /** Atualiza uma mensalidade já persistida. */
    public Mensalidade atualizar(Mensalidade mensalidade) {
        return em.merge(mensalidade);
    }
}
