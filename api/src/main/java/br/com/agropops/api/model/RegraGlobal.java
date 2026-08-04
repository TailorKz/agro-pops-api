package br.com.agropops.api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "regras_globais")
public class RegraGlobal {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tipo; // "NCM" ou "CFOP"

    @Column(nullable = false, unique = true)
    private String codigo;

    @Column(length = 1000)
    private String descricao;

    @Column(nullable = false)
    private Boolean isDedutivel;
}