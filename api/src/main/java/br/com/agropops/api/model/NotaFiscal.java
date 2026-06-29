package br.com.agropops.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    private String tipo; // "ENTRADA" ou "SAIDA"

    private BigDecimal valorTotal;

    private String empresaEnvolvida; // Razão Social

    @ManyToOne
    @JoinColumn(name = "produtor_id", nullable = false)
    @JsonIgnore
    private Produtor produtor;

    // Relação vários itens
    @OneToMany(mappedBy = "notaFiscal", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemNota> itens = new ArrayList<>();
}