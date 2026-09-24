package academia.treino.entidade;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entidade JPA que representa uma ficha de treino de um aluno.
 * Contém a lista de exercícios (ItemTreino) como relacionamento OneToMany.
 *
 * IMPORTANTE: aluno_id é apenas uma referência lógica (UUID como String).
 * Não existe FK de banco cruzando para o aluno-service — isso é o princípio
 * fundamental de desacoplamento entre microsserviços.
 */
@Entity
@Table(name = "fichas_treino")
public class FichaTreino {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Referência lógica ao aluno. UUID armazenado como String.
     * Não há FK para a tabela alunos — cada serviço é independente.
     */
    @Column(name = "aluno_id", nullable = false)
    private String alunoId;

    @Column(name = "titulo", nullable = false)
    private String titulo;

    /**
     * Objetivo do treino: HIPERTROFIA | EMAGRECIMENTO | CONDICIONAMENTO
     */
    @Column(name = "objetivo", nullable = false, length = 30)
    private String objetivo;

    /**
     * Status da ficha: PENDENTE (não realizado) | CONCLUIDO (realizado)
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    /**
     * Preenchido apenas quando o aluno registra a execução do treino.
     */
    @Column(name = "data_conclusao")
    private LocalDateTime dataConclusao;

    /**
     * Lista de exercícios da ficha. CascadeType.ALL garante que os itens
     * sejam salvos/removidos junto com a ficha.
     */
    @OneToMany(mappedBy = "fichaTreino", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemTreino> itens = new ArrayList<>();

    /** Construtor sem args exigido pelo JPA */
    public FichaTreino() {}

    public FichaTreino(String alunoId, String titulo, String objetivo) {
        this.alunoId      = alunoId;
        this.titulo       = titulo;
        this.objetivo     = objetivo;
        this.status       = "PENDENTE";
        this.dataCriacao  = LocalDateTime.now();
    }

    // ─── Getters e Setters ────────────────────────────────────────────────

    public UUID getId()                        { return id; }

    public String getAlunoId()                 { return alunoId; }

    public String getTitulo()                  { return titulo; }
    public void   setTitulo(String titulo)     { this.titulo = titulo; }

    public String getObjetivo()                { return objetivo; }
    public void   setObjetivo(String objetivo) { this.objetivo = objetivo; }

    public String getStatus()                  { return status; }
    public void   setStatus(String status)     { this.status = status; }

    public LocalDateTime getDataCriacao()      { return dataCriacao; }

    public LocalDateTime getDataConclusao()                         { return dataConclusao; }
    public void          setDataConclusao(LocalDateTime dataConclusao) { this.dataConclusao = dataConclusao; }

    public List<ItemTreino> getItens()         { return itens; }
    public void addItem(ItemTreino item) {
        item.setFichaTreino(this);
        this.itens.add(item);
    }
}
