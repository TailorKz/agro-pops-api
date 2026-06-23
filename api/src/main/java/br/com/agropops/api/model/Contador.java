package br.com.agropops.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

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

    @OneToMany(mappedBy = "contador", cascade = CascadeType.ALL)
    @JsonIgnore // JsonIgnore continua cortando o loop infinito
    private List<Produtor> produtores;
}