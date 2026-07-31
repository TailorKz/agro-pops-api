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
@Table(name = "parcelas_nota")
public class ParcelaNota {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "parcela_seq")
    @SequenceGenerator(name = "parcela_seq", sequenceName = "parcela_seq", allocationSize = 50)
    private Long id;

    private String numeroParcela; // Ex: 001, 002, 1/3, etc.

    @Column(nullable = false)
    private LocalDate dataVencimento;

    @Column(nullable = false)
    private BigDecimal valor;

    @ManyToOne
    @JoinColumn(name = "nota_fiscal_id", nullable = false)
    @JsonIgnore
    private NotaFiscal notaFiscal;
}