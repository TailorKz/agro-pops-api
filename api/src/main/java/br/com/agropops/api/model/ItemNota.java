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
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "item_seq")
    @SequenceGenerator(name = "item_seq", sequenceName = "item_seq", allocationSize = 50)
    private Long id;

    private String descricao;

    private String ncm;

    private BigDecimal valor;

    private Boolean isDedutivel; // O controle individual por item

    @ManyToOne
    @JoinColumn(name = "nota_fiscal_id", nullable = false)
    @JsonIgnore // Evita loop infinito no JSON
    private NotaFiscal notaFiscal;
}