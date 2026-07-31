package br.com.agropops.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Getter
@Setter
@Entity
// ADICIONA A REGRA COMPOSTA NA TABELA
@Table(name = "notas_fiscais", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"chave_acesso", "produtor_id"})
})
public class NotaFiscal {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "nota_seq")
    @SequenceGenerator(name = "nota_seq", sequenceName = "nota_seq", allocationSize = 50)
    private Long id;


    @Column(length = 44)
    private String chaveAcesso;

    private String numero;

    private LocalDate dataEmissao;

    private String tipo; // "ENTRADA" ou "SAIDA"

    private BigDecimal valorTotal;

    private String empresaEnvolvida; // Razão Social

    @Column(length = 44)
    private String chaveAcessoReferencia; // Guarda o vínculo da contra-nota

    @ManyToOne
    @JoinColumn(name = "produtor_id", nullable = false)
    @JsonIgnore
    private Produtor produtor;

    // VÍNCULO DA NOTA COM A FAZENDA
    @ManyToOne
    @JoinColumn(name = "propriedade_rural_id")
    private PropriedadeRural propriedadeRural;


    // Relacionamento com os itens da nota (Regime de Competência)
    @OneToMany(mappedBy = "notaFiscal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ItemNota> itens = new HashSet<>();

    // Relacionamento com as parcelas/financeiro (Regime de Caixa)
    @OneToMany(mappedBy = "notaFiscal", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ParcelaNota> parcelas = new HashSet<>();
}