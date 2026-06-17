package br.com.agropops.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    @JsonIgnore // Protege a senha do certificado
    private String senhaCertificado;

    @ManyToOne
    @JoinColumn(name = "contador_id", nullable = false)
    @JsonIgnore // Corta o Loop Infinito
    private Contador contador;
}