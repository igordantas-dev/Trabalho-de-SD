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
     * Compara tanto o formato exato quanto os números sem pontuação.
     */
    public Optional<Aluno> buscarPorCpf(String cpf) {
        if (cpf == null || cpf.isBlank()) return Optional.empty();
        String cpfLimpo = cpf.trim();
        String apenasNumeros = cpfLimpo.replaceAll("\\D", "");
        try {
            TypedQuery<Aluno> query = em.createQuery(
                "SELECT a FROM Aluno a WHERE a.cpf = :cpf OR REPLACE(REPLACE(REPLACE(a.cpf, '.', ''), '-', ''), ' ', '') = :numeros",
                Aluno.class);
            query.setParameter("cpf", cpfLimpo);
            query.setParameter("numeros", apenasNumeros.isBlank() ? cpfLimpo : apenasNumeros);
            List<Aluno> lista = query.getResultList();
            return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Busca um aluno pelo e-mail (case-insensitive).
     * Utilizado para verificar duplicidade no cadastro.
     */
    public Optional<Aluno> buscarPorEmail(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        try {
            TypedQuery<Aluno> query = em.createQuery(
                "SELECT a FROM Aluno a WHERE LOWER(a.email) = LOWER(:email)", Aluno.class);
            query.setParameter("email", email.trim());
            List<Aluno> lista = query.getResultList();
            return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
        } catch (Exception e) {
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
