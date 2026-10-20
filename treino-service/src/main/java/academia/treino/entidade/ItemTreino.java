package academia.treino.entidade;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Entidade JPA que representa um exercício dentro de uma ficha de treino.
 *
 * Armazena tanto a carga sugerida (prescrita pelo instrutor ou pela IA)
 * quanto a carga_usada (informada pelo aluno após realizar o treino).
 * Essa distinção é o coração da funcionalidade de progressão de carga:
 * na próxima ficha do mesmo exercício, carga_usada vira a nova carga_sugerida.
 */
@Entity
@Table(name = "itens_treino")
public class ItemTreino {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Relacionamento com a ficha pai. ManyToOne — muitos itens por ficha.
     * FetchType.LAZY evita carregar a ficha inteira ao buscar apenas o item.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ficha_treino_id", nullable = false)
    private FichaTreino fichaTreino;

    @Column(name = "nome_exercicio", nullable = false)
    private String nomeExercicio;

    @Column(name = "grupo_muscular")
    private String grupoMuscular;

    @Column(name = "series", nullable = false)
    private Integer series;

    @Column(name = "repeticoes", nullable = false)
    private Integer repeticoes;

    /**
     * Carga prescrita (kg). Sugerida pelo instrutor ou pela última execução.
     * Preenchida ao criar a ficha.
     */
    @Column(name = "carga_sugerida")
    private Double cargaSugerida;

    /**
     * Carga real utilizada pelo aluno (kg).
     * null enquanto o treino não for realizado (status = PENDENTE).
     * Preenchida via RegistrarExecucao após o aluno concluir o exercício.
     */
    @Column(name = "carga_usada")
    private Double cargaUsada;

    /**
     * Indica se o aluno realizou este exercício na sessão.
     * false por padrão; true após RegistrarExecucao.
     */
    @Column(name = "concluido", nullable = false)
    private Boolean concluido;

    /** Construtor sem args exigido pelo JPA */
    public ItemTreino() {}

    public ItemTreino(String nomeExercicio, String grupoMuscular,
                      Integer series, Integer repeticoes, Double cargaSugerida) {
        this.nomeExercicio = nomeExercicio;
        this.grupoMuscular = grupoMuscular;
        this.series        = series;
        this.repeticoes    = repeticoes;
        this.cargaSugerida = cargaSugerida;
        this.cargaUsada    = 0.0;
        this.concluido     = false;
    }

    // ─── Getters e Setters ────────────────────────────────────────────────

    public UUID getId()                        { return id; }

    public FichaTreino getFichaTreino()        { return fichaTreino; }
    public void setFichaTreino(FichaTreino f)  { this.fichaTreino = f; }

    public String getNomeExercicio()           { return nomeExercicio; }
    public void setNomeExercicio(String n)     { this.nomeExercicio = n; }

    public String getGrupoMuscular()           { return grupoMuscular; }
    public void setGrupoMuscular(String g)     { this.grupoMuscular = g; }

    public Integer getSeries()                 { return series; }
    public void setSeries(Integer s)           { this.series = s; }

    public Integer getRepeticoes()             { return repeticoes; }
    public void setRepeticoes(Integer r)       { this.repeticoes = r; }

    public Double getCargaSugerida()           { return cargaSugerida; }
    public void setCargaSugerida(Double c)     { this.cargaSugerida = c; }

    public Double getCargaUsada()              { return cargaUsada; }
    public void setCargaUsada(Double c)        { this.cargaUsada = c; }

    public Boolean getConcluido()              { return concluido; }
    public void setConcluido(Boolean c)        { this.concluido = c; }
}
