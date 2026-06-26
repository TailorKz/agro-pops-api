package br.com.agropops.api.service;

import br.com.agropops.api.model.Produtor;
import br.com.agropops.api.repository.ProdutorRepository;
import com.fincatto.documentofiscal.DFAmbiente;
import com.fincatto.documentofiscal.DFUnidadeFederativa;
import com.fincatto.documentofiscal.nfe.NFeConfig;
import com.fincatto.documentofiscal.nfe400.webservices.WSFacade;
import com.fincatto.documentofiscal.nfe.classes.distribuicao.NFDistribuicaoIntRetorno;
import com.fincatto.documentofiscal.nfe.classes.distribuicao.NFDistribuicaoDocumentoZip;
import com.fincatto.documentofiscal.nfe.webservices.distribuicao.WSDistribuicaoNFe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SefazSyncService {

    @Autowired
    private ProdutorRepository produtorRepository;

    @Autowired
    private SefazXmlService sefazXmlService;

    // initialDelay de 5 minutos (300000 ms) para não bater na SEFAZ assim que o servidor liga.
    // E fixedDelay de 4 horas (14400000 ms).
    @Scheduled(initialDelay = 30000, fixedDelay = 14400000)
    @Transactional
    public void sincronizarNotasDaSefaz() {
        System.out.println("⏳ [CRON-SEFAZ] Iniciando varredura às " + LocalDateTime.now());

        List<Produtor> produtores = produtorRepository.findAll();

        for (Produtor produtor : produtores) {
            if (produtor.getCertificadoPfx() == null || produtor.getSenhaCertificado() == null) {
                continue;
            }

            // =========================================================
            // CORREÇÃO 2: Rate-limit por produtor (Cooldown de 1 hora)
            // =========================================================
            if (produtor.getUltimaSincronizacaoSefaz() != null) {
                LocalDateTime proximaPermitida = produtor.getUltimaSincronizacaoSefaz().plusHours(1);
                if (LocalDateTime.now().isBefore(proximaPermitida)) {
                    System.out.println("⏭️ [" + produtor.getNome() + "] Cooldown ativo. Próxima tentativa permitida após: " + proximaPermitida);
                    continue;
                }
            }

            try {
                System.out.println("-------------------------------------------------");
                System.out.println("🔍 Preparando SEFAZ para: " + produtor.getNome());

                String cpfCnpjLimpo = produtor.getCpfCnpj() != null ? produtor.getCpfCnpj().replaceAll("\\D", "") : "";

                if (cpfCnpjLimpo.length() != 11 && cpfCnpjLimpo.length() != 14) {
                    System.out.println("⚠️ CPF/CNPJ inválido para: " + produtor.getNome() + ". Pulando.");
                    continue;
                }

                NFeConfig config = criarConfig(produtor);
                WSFacade ws = new WSFacade(config);

                String rawNsu = produtor.getUltimoNsu() != null ? produtor.getUltimoNsu() : "0";
                String ultimoNsu;
                try {
                    ultimoNsu = String.format("%015d", Long.parseLong(rawNsu));
                } catch (NumberFormatException e) {
                    ultimoNsu = "000000000000000";
                }

                System.out.println("📡 Enviando requisição | CPF: " + cpfCnpjLimpo + " | NSU: " + ultimoNsu);

                // Chamada oficial da versão 4.x com os 5 argumentos perfeitos
                NFDistribuicaoIntRetorno retorno = ws.consultarDistribuicaoDFe(
                        cpfCnpjLimpo,
                        DFUnidadeFederativa.SC,
                        null,
                        null,
                        ultimoNsu
                );

                // =========================================================
                // CORREÇÃO 3: Sempre registrar o momento da chamada
                // =========================================================
                produtor.setUltimaSincronizacaoSefaz(LocalDateTime.now());

                String cStat = retorno.getCodigoStatusReposta() != null ? retorno.getCodigoStatusReposta() : "";
                String motivo = retorno.getMotivo() != null ? retorno.getMotivo() : "(sem motivo)";

                // LOTE ENCONTRADO (Sucesso - Documentos baixados)
                if (retorno.getLote() != null && retorno.getLote().getDocZip() != null) {
                    int notasLidas = 0;
                    int resumosEncontrados = 0;

                    for (NFDistribuicaoDocumentoZip docZip : retorno.getLote().getDocZip()) {
                        try {
                            String xmlDescompactado = WSDistribuicaoNFe.decodeGZipToXml(docZip.getValue());

                            // 1. NOTAS COMPLETAS (Já manifestadas ou estaduais)
                            if (docZip.getSchema().startsWith("procNFe")) {
                                boolean sucesso = sefazXmlService.sincronizarNotaAutomatica(produtor, xmlDescompactado);
                                if (sucesso) notasLidas++;
                            }
                            // 2. RESUMOS DE NOTAS (Falta Manifestar para a SEFAZ liberar o XML completo)
                            else if (docZip.getSchema().startsWith("resNFe")) {
                                resumosEncontrados++;
                                // Aqui no futuro, criaremos o sefazXmlService.salvarResumo(produtor, xmlDescompactado);
                                System.out.println("⚠️ RESUMO CAPTURADO: Nota detectada na SEFAZ, mas aguardando Manifestação do Destinatário para baixar o XML completo.");
                            }
                            // 3. EVENTOS (Cancelamentos, Cartas de Correção, etc)
                            else if (docZip.getSchema().startsWith("procEventoNFe")) {
                                System.out.println("ℹ️ EVENTO CAPTURADO: Carta de Correção ou Cancelamento detectado.");
                            }
                        } catch (Exception ex) {
                            System.out.println("⚠️ Falha ao ler documento ZIP individual: " + ex.getMessage());
                        }
                    }

                    if (retorno.getUltimoNSU() != null && !retorno.getUltimoNSU().isBlank()) {
                        produtor.setUltimoNsu(retorno.getUltimoNSU());
                        System.out.println("🔖 Novo NSU salvo com sucesso: " + retorno.getUltimoNSU());
                    }
                    produtorRepository.save(produtor);
                    System.out.println("✅ " + notasLidas + " notas completas e " + resumosEncontrados + " resumos sincronizados.");

                } else {
                    // CSTAT 137 (Sem documentos) OU 656 (Consumo Indevido)
                    System.out.println("ℹ️ Resposta da SEFAZ [cStat=" + cStat + "]: " + motivo);

                    if (cStat.equals("656")) {
                        // CORREÇÃO 5: Consumo Indevido. O timestamp foi salvo, bloqueando a próxima 1 hora.
                        System.out.println("⛔ Consumo Indevido detectado para " + produtor.getNome() + ". Próxima tentativa liberada em 1 hora.");
                    }

                    // =========================================================
                    // CORREÇÃO 6: A Obra-Prima. Avançar o NSU mesmo sem documentos!
                    // =========================================================
                    if (retorno.getUltimoNSU() != null && !retorno.getUltimoNSU().isBlank()) {
                        // Só avança se o NSU devolvido for diferente do que enviamos
                        if (!retorno.getUltimoNSU().equals(ultimoNsu)) {
                            produtor.setUltimoNsu(retorno.getUltimoNSU());
                            System.out.println("🔖 NSU atualizado pelo governo (sem docs novos): " + retorno.getUltimoNSU());
                        }
                    }

                    produtorRepository.save(produtor);
                }

            } catch (Exception e) {
                System.out.println("❌ Erro de comunicação com a SEFAZ: " + e.getMessage());
                // Forçar o registro do timestamp para evitar bombardeio em caso de queda da SEFAZ
                try {
                    produtor.setUltimaSincronizacaoSefaz(LocalDateTime.now());
                    produtorRepository.save(produtor);
                } catch (Exception saveEx) {
                    System.out.println("⚠️ Não foi possível salvar o controle de tempo: " + saveEx.getMessage());
                }
            }
        }
        System.out.println("-------------------------------------------------");
        System.out.println("🏁 [CRON-SEFAZ] Varredura finalizada!");
    }

    // Método extraído para deixar o código elegante e limpo
    private NFeConfig criarConfig(Produtor produtor) {
        return new NFeConfig() {
            @Override
            public DFUnidadeFederativa getCUF() {
                return DFUnidadeFederativa.SC;
            }

            @Override
            public DFAmbiente getAmbiente() {
                return DFAmbiente.PRODUCAO;
            }

            @Override
            public String getCertificadoSenha() {
                return produtor.getSenhaCertificado();
            }

            @Override
            public KeyStore getCertificadoKeyStore() throws KeyStoreException {
                try {
                    KeyStore keyStore = KeyStore.getInstance("PKCS12");
                    try (InputStream is = new ByteArrayInputStream(produtor.getCertificadoPfx())) {
                        keyStore.load(is, getCertificadoSenha().toCharArray());
                    }
                    return keyStore;
                } catch (Exception e) {
                    throw new KeyStoreException("Falha ao montar KeyStore do certificado", e);
                }
            }

            @Override
            public KeyStore getCadeiaCertificadosKeyStore() throws KeyStoreException {
                return null;
            }

            @Override
            public String getCadeiaCertificadosSenha() {
                return "changeit";
            }
        };
    }
}