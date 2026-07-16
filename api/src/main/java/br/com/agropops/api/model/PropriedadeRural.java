package br.com.agropops.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "propriedades_rurais")
public class PropriedadeRural {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column
    private String inscricaoEstadual;

    @Column
    private String caepf;

    // Percentual de ganho/responsabilidade sobre a fazenda
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal percentualParticipacao;

    // A qual produtor essa propriedade pertence
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produtor_id", nullable = false)
    @JsonIgnore
    private Produtor produtor;
}