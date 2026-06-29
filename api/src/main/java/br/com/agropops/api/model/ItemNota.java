package br.com.agropops.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "itens_nota")
public class ItemNota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descricao;

    private String ncm;

    private BigDecimal valor;

    private Boolean isDedutivel; // O controle agora é individual por item!

    @ManyToOne
    @JoinColumn(name = "nota_fiscal_id", nullable = false)
    @JsonIgnore // Evita loop infinito no JSON
    private NotaFiscal notaFiscal;
}