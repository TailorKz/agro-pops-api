package br.com.agropops.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JsonIgnore // Impede que a palavra-passe (mesmo encriptada) vá para o Frontend
    private String senha;

    @OneToMany(mappedBy = "contador", cascade = CascadeType.ALL)
    @JsonIgnore // Corta o Loop Infinito pelo lado do contador
    private List<Produtor> produtores;
}