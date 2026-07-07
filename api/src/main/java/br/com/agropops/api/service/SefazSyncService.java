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
import com.fincatto.documentofiscal.nfe400.classes.evento.manifestacaodestinatario.NFTipoEventoManifestacaoDestinatario;

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
                            // 2. RESUMOS DE NOTAS: MANIFESTAÇÃO AUTOMÁTICA
                            else if (docZip.getSchema().startsWith("resNFe")) {
                                resumosEncontrados++;

                                // Extrai a chave de acesso (44 dígitos) diretamente do XML do Resumo
                                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<chNFe>(.*?)</chNFe>").matcher(xmlDescompactado);

                                if (matcher.find()) {
                                    String chaveAcesso = matcher.group(1);
                                    System.out.println("⚠️ RESUMO CAPTURADO: Chave " + chaveAcesso + ". Disparando Ciência da Operação Automática...");

                                    try {
                                        // Dispara o evento oficial para o Governo Federal
                                        ws.manifestaDestinatarioNota(
                                                chaveAcesso,
                                                NFTipoEventoManifestacaoDestinatario.CIENCIA_DA_EMISSAO,
                                                "", // Justificativa não é necessária para este evento
                                                cpfCnpjLimpo
                                        );
                                        System.out.println("✅ Ciência da Operação registrada com sucesso na SEFAZ!");
                                    } catch (Exception ex) {
                                        System.out.println("❌ Erro ao manifestar automaticamente a nota: " + ex.getMessage());
                                    }
                                }
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

                    // CORREÇÃO 6: Avançar o NSU mesmo sem documentos!
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

    // =======================================================================
    // NOVO: Método para o Frontend disparar a Manifestação Final (Confirmação/Desconhecimento)
    // =======================================================================
    public String manifestarNotaManualmente(Produtor produtor, String chaveAcesso, com.fincatto.documentofiscal.nfe400.classes.evento.manifestacaodestinatario.NFTipoEventoManifestacaoDestinatario tipoEvento) {
        try {
            String cpfCnpjLimpo = produtor.getCpfCnpj().replaceAll("\\D", "");
            NFeConfig config = criarConfig(produtor);
            WSFacade ws = new WSFacade(config);

            String justificativa = "";
            // Se for Operação não Realizada, a SEFAZ exige uma justificativa
            if (tipoEvento.equals(com.fincatto.documentofiscal.nfe400.classes.evento.manifestacaodestinatario.NFTipoEventoManifestacaoDestinatario.OPERACAO_NAO_REALIZADA)) {
                justificativa = "Mercadoria nao entregue ou devolvida";
            }

            // Dispara o evento para a SEFAZ
            var retorno = ws.manifestaDestinatarioNota(
                    chaveAcesso,
                    tipoEvento,
                    justificativa,
                    cpfCnpjLimpo
            );

            // A SEFAZ responde a eventos dentro de uma lista (Lote)
            if (retorno.getEventoRetorno() != null && !retorno.getEventoRetorno().isEmpty()) {
                // Pegamos a resposta do nosso evento específico (que é o primeiro e único do lote)
                var infoRetorno = retorno.getEventoRetorno().get(0).getInfoEventoRetorno();

                // 135 significa "Evento registrado e vinculado a NF-e"
                if (infoRetorno.getCodigoStatus().equals("135")) {
                    return "Sucesso: Evento registrado e vinculado a NF-e.";
                } else {
                    return "Erro da SEFAZ: " + infoRetorno.getMotivo();
                }
            } else {
                // Caso o lote inteiro tenha sido rejeitado antes de processar o evento
                return "Erro da SEFAZ: Lote rejeitado - " + retorno.getMotivo();
            }

        } catch (Exception e) {
            return "Erro de comunicação: " + e.getMessage();
        }
    }
}