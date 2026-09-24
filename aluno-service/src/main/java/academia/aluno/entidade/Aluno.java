package academia.aluno.entidade;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Entidade JPA que representa um aluno cadastrado na academia.
 * Mapeada para a tabela "alunos" no banco de dados do aluno-service.
 *
 * Segue o padrão da Aula 6: UUID gerado pelo Hibernate, colunas obrigatórias
 * marcadas com nullable = false, construtor sem args para o JPA.
 */
@Entity
@Table(name = "alunos")
public class Aluno {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "cpf", nullable = false, unique = true, length = 14)
    private String cpf;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "telefone")
    private String telefone;

    /**
     * Peso em kg. Usado para cálculo de IMC e progressão de carga nos treinos.
     */
    @Column(name = "peso")
    private Double peso;

    /**
     * Altura em metros. Usado em conjunto com o peso para cálculo de IMC.
     */
    @Column(name = "altura")
    private Double altura;

    /**
     * Status de matrícula do aluno.
     * Valores aceitos: ATIVO, INATIVO, INADIMPLENTE
     * Padrão ao cadastrar: ATIVO
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** Construtor sem argumentos exigido pelo JPA/Hibernate */
    public Aluno() {}

    public Aluno(String nome, String cpf, String email, String telefone,
                 Double peso, Double altura) {
        this.nome      = nome;
        this.cpf       = cpf;
        this.email     = email;
        this.telefone  = telefone;
        this.peso      = peso;
        this.altura    = altura;
        this.status    = "ATIVO"; // status inicial ao cadastrar
    }

    // ─── Getters e Setters ────────────────────────────────────────────────

    public UUID getId()                  { return id; }

    public String getNome()              { return nome; }
    public void   setNome(String nome)   { this.nome = nome; }

    public String getCpf()               { return cpf; }
    public void   setCpf(String cpf)     { this.cpf = cpf; }

    public String getEmail()             { return email; }
    public void   setEmail(String email) { this.email = email; }

    public String getTelefone()                   { return telefone; }
    public void   setTelefone(String telefone)    { this.telefone = telefone; }

    public Double getPeso()              { return peso; }
    public void   setPeso(Double peso)   { this.peso = peso; }

    public Double getAltura()               { return altura; }
    public void   setAltura(Double altura)  { this.altura = altura; }

    public String getStatus()                { return status; }
    public void   setStatus(String status)   { this.status = status; }
}
