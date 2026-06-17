package br.com.agropops.api.security;

import br.com.agropops.api.model.Contador;
import br.com.agropops.api.model.Produtor;
import br.com.agropops.api.repository.ContadorRepository;
import br.com.agropops.api.repository.ProdutorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioAuthService {

    @Autowired
    private ContadorRepository contadorRepository;

    @Autowired
    private ProdutorRepository produtorRepository;

    // O Spring vai abrir a transação
    @Transactional(readOnly = true)
    public Contador buscarContador(String email) {
        return contadorRepository.findByEmail(email).orElse(null);
    }

    // POSTGRES LER O CERTIFICADO (@LOB)
    @Transactional(readOnly = true)
    public Produtor buscarProdutor(String cpf) {
        return produtorRepository.findFirstByCpfCnpj(cpf).orElse(null);
    }
}