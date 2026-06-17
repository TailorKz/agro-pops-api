package br.com.agropops.api.service;

import org.springframework.stereotype.Service;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

@Service
public class CertificadoDigitalService {

    public void testarCertificado(String caminhoArquivo, String senha) {
        try (InputStream entrada = new FileInputStream(caminhoArquivo)) {

            // ormato padrão do certificado A1 no Windows/Brasil
            KeyStore keyStore = KeyStore.getInstance("PKCS12");

            // Carrega o arquivo e descriptografa usando a senha
            keyStore.load(entrada, senha.toCharArray());

            // Varre os "Alias"
            Enumeration<String> aliases = keyStore.aliases();

            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();

                System.out.println("\n=========================================");
                System.out.println("Lendo Cofre Digital...");
                System.out.println("Alias encontrado: " + alias);

                // Verifica se a chave privada atrelada a este alias existe
                if (keyStore.isKeyEntry(alias)) {
                    X509Certificate certificado = (X509Certificate) keyStore.getCertificate(alias);

                    System.out.println("Titular do Certificado: " + certificado.getSubjectX500Principal().getName());
                    System.out.println("Emissor: " + certificado.getIssuerX500Principal().getName());
                    System.out.println("Válido a partir de: " + certificado.getNotBefore());
                    System.out.println("Válido até: " + certificado.getNotAfter());
                    System.out.println("STATUS: Certificado lido com sucesso e pronto para assinar XMLs!");
                }
            }
            System.out.println("=========================================\n");

        } catch (Exception e) {
            System.err.println("\n[ERRO] Não foi possível ler o certificado.");
            System.err.println("Motivo: " + e.getMessage());
            e.printStackTrace();
        }
    }
}