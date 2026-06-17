package br.com.agropops.api.service;

import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;

@Service
public class CertificadoDigitalService {

    public Date extrairValidade(byte[] certificadoPfx, String senha) throws Exception {

        // 1. Instancia um "Cofre Digital" no formato padrão do Windows/Serasa (PKCS12)
        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        // 2. Tenta abrir o ficheiro usando a palavra-passe digitada no React
        try (ByteArrayInputStream bis = new ByteArrayInputStream(certificadoPfx)) {
            // Se a senha estiver errada, esta linha "explode" e cai no catch abaixo!
            keyStore.load(bis, senha.toCharArray());
        } catch (Exception e) {
            throw new RuntimeException("Palavra-passe incorreta ou ficheiro de certificado inválido.");
        }

        // 3. Procura o certificado lá dentro (geralmente existe apenas um "Alias" por PFX)
        Enumeration<String> aliases = keyStore.aliases();
        if (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();

            // 4. Extrai o certificado e pega a Data Final de Validade (NotAfter)
            X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
            return cert.getNotAfter();
        }

        throw new RuntimeException("Nenhum certificado encontrado dentro do ficheiro.");
    }
}