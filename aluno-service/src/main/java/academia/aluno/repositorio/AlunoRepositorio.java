package academia.aluno.repositorio;

import academia.aluno.entidade.Aluno;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repositório responsável por todas as operações de banco de dados da entidade Aluno.
 * Usa EntityManager do JPA diretamente (sem Spring Data, pois este serviço
 * é um servidor gRPC standalone — sem Spring Boot).
 */
public class AlunoRepositorio {

    private final EntityManager em;

    public AlunoRepositorio(EntityManager em) {
        this.em = em;
    }

    /**
     * Persiste um novo aluno no banco.
     * Executa dentro de uma transação gerenciada pelo chamador.
     */
    public Aluno salvar(Aluno aluno) {
        em.persist(aluno);
        return aluno;
    }

    /**
     * Busca um aluno pelo UUID. Retorna Optional vazio se não encontrado.
     */
    public Optional<Aluno> buscarPorId(UUID id) {
        Aluno aluno = em.find(Aluno.class, id);
        return Optional.ofNullable(aluno);
    }

    /**
     * Busca um aluno pelo CPF. Utilizado para verificar duplicidade no cadastro.
     */
    public Optional<Aluno> buscarPorCpf(String cpf) {
        try {
            TypedQuery<Aluno> query = em.createQuery(
                "SELECT a FROM Aluno a WHERE a.cpf = :cpf", Aluno.class);
            query.setParameter("cpf", cpf);
            return Optional.of(query.getSingleResult());
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Retorna todos os alunos cadastrados.
     */
    public List<Aluno> listarTodos() {
        return em.createQuery("SELECT a FROM Aluno a ORDER BY a.nome", Aluno.class)
                 .getResultList();
    }

    /**
     * Atualiza o status de matrícula de um aluno já persistido.
     */
    public Aluno atualizarStatus(Aluno aluno, String novoStatus) {
        aluno.setStatus(novoStatus);
        return em.merge(aluno);
    }
}
