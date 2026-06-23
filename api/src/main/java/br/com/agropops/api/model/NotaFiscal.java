package br.com.agropops.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "notas_fiscais")
public class NotaFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 44)
    private String chaveAcesso;

    private String numero;
    private LocalDate dataEmissao;
    private String tipo; // "ENTRADA" (Vendas do produtor) ou "SAIDA" (Despesas)
    private BigDecimal valor;
    private Boolean isDedutivel; // Crucial para o cálculo do Livro Caixa!
    private String descricao;

    @ManyToOne
    @JoinColumn(name = "produtor_id", nullable = false)
    @JsonIgnore
    private Produtor produtor;
}