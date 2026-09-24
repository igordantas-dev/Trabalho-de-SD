package academia.financeiro.entidade;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade JPA que representa uma mensalidade gerada para um aluno.
 *
 * Máquina de estados do status:
 *   PENDENTE → PAGO   (quando RegistrarPagamento é chamado)
 *   PENDENTE → ATRASADO (quando data_vencimento < hoje e ainda não pago)
 *
 * aluno_id é referência lógica — sem FK cruzada entre serviços.
 */
@Entity
@Table(name = "mensalidades")
public class Mensalidade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Referência lógica ao aluno no aluno-service.
     * UUID armazenado como String — sem FK de banco entre microsserviços.
     */
    @Column(name = "aluno_id", nullable = false)
    private String alunoId;

    /** Valor em reais. Ex: 99.90 */
    @Column(name = "valor", nullable = false)
    private Double valor;

    /** Data limite para pagamento. */
    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    /** Preenchida apenas quando o pagamento é registrado. */
    @Column(name = "data_pagamento")
    private LocalDateTime dataPagamento;

    /**
     * Status da mensalidade: PENDENTE | PAGO | ATRASADO
     * Padrão ao gerar: PENDENTE
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * Forma de pagamento informada ao registrar.
     * PIX | CARTAO | DINHEIRO | BOLETO
     * null enquanto não pago.
     */
    @Column(name = "forma_pagamento", length = 20)
    private String formaPagamento;

    /** Descrição livre. Ex: "Mensalidade outubro/2025" */
    @Column(name = "descricao")
    private String descricao;

    /** Construtor sem args exigido pelo JPA */
    public Mensalidade() {}

    public Mensalidade(String alunoId, Double valor,
                       LocalDate dataVencimento, String descricao) {
        this.alunoId        = alunoId;
        this.valor          = valor;
        this.dataVencimento = dataVencimento;
        this.descricao      = descricao;
        this.status         = "PENDENTE";
    }

    // ─── Getters e Setters ────────────────────────────────────────────────

    public UUID getId()                          { return id; }

    public String getAlunoId()                   { return alunoId; }

    public Double getValor()                     { return valor; }
    public void   setValor(Double valor)         { this.valor = valor; }

    public LocalDate getDataVencimento()                           { return dataVencimento; }
    public void      setDataVencimento(LocalDate dataVencimento)   { this.dataVencimento = dataVencimento; }

    public LocalDateTime getDataPagamento()                             { return dataPagamento; }
    public void          setDataPagamento(LocalDateTime dataPagamento)  { this.dataPagamento = dataPagamento; }

    public String getStatus()                    { return status; }
    public void   setStatus(String status)       { this.status = status; }

    public String getFormaPagamento()                        { return formaPagamento; }
    public void   setFormaPagamento(String formaPagamento)   { this.formaPagamento = formaPagamento; }

    public String getDescricao()                 { return descricao; }
    public void   setDescricao(String descricao) { this.descricao = descricao; }
}
