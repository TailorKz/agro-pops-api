package br.com.agropops.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime; // <-- ADICIONE ESTA IMPORTAÇÃO
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "produtores")
public class Produtor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;

    @Column(nullable = false)
    private String cpfCnpj;

    @Column
    private String cnpj;

    @Column
    private String telefone;

    @Column(length = 50)
    private String ultimoNsu;

    @Lob
    @JsonIgnore
    private byte[] certificadoPfx;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String senhaCertificado;

    // CAMPO DE CONTROLE DE TEMPO DA SEFAZ
    @Column
    private LocalDateTime ultimaSincronizacaoSefaz;

    @Temporal(TemporalType.DATE)
    private Date validadeCertificado;

    @ManyToOne
    @JoinColumn(name = "contador_id", nullable = false)
    @JsonIgnore
    private Contador contador;


    @OneToMany(mappedBy = "produtor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PropriedadeRural> propriedades = new ArrayList<>();
}