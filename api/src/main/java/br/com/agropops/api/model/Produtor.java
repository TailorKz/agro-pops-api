package br.com.agropops.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Date;

@Getter
@Setter
@Entity
@Table(name = "produtores")
public class Produtor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nome;
    private String cpfCnpj;
    private String inscricaoEstadual;

    @Lob
    @JsonIgnore // Protege o ficheiro binário pesado de ser carregado na lista
    private byte[] certificadoPfx;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String senhaCertificado;

    // Guardará a data em que o certificado expira
    @Temporal(TemporalType.DATE)
    private Date validadeCertificado;


    @ManyToOne
    @JoinColumn(name = "contador_id", nullable = false)
    @JsonIgnore // Corta o Loop Infinito
    private Contador contador;
}