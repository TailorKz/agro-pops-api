package br.com.agropops.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Getter
@Setter
@Entity
@Table(name = "contadores")
public class Contador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nomeEscritorio;
    private String crc;
    private String estado;
    private String nomeResponsavel;
    private String telefone;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    // Apenas permite a ESCRITA (no cadastro), mas proíbe a LEITURA (para o frontend)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String senha;

    @JsonIgnore
    @OneToMany(mappedBy = "contador", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Produtor> produtores = new ArrayList<>();

}