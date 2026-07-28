package br.com.agropops.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "fornecedores")
public class Fornecedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome; // Razão Social ou Nome

    @Column
    private String fantasia;

    @Column
    private String email;

    @Column
    private String cpfCnpj;

    @Column
    private String inscricaoEstadual;

    @Column
    private String endereco;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contador_id", nullable = false)
    @JsonIgnore
    private Contador contador;
}