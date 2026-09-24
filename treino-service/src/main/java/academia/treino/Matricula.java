package academia.treino;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "matriculas")
public class Matricula {

    @Id
    @Column(name = "id_aluno", nullable = false, updatable = false)
    private String idAluno;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusMatricula status;

    protected Matricula() {
    }

    public Matricula(String idAluno, StatusMatricula status) {
        this.idAluno = idAluno;
        this.status = status;
    }

    public String getIdAluno() {
        return idAluno;
    }

    public StatusMatricula getStatus() {
        return status;
    }
}