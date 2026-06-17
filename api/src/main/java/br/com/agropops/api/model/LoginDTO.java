package br.com.agropops.api.model;

import lombok.Data;

@Data
public class LoginDTO {
    private String email;
    private String senha;
}