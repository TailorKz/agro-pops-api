package br.com.agropops.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "regras_ncm")
public class RegraNCM {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ncm;

    private String descricao;

    @Column(nullable = false)
    private Boolean isDedutivel;

    // Cada contador tem as suas próprias regras
    @ManyToOne
    @JoinColumn(name = "contador_id", nullable = false)
    @JsonIgnore
    private Contador contador;
}