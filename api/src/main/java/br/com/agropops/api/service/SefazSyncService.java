package br.com.agropops.api.service;

import br.com.agropops.api.dto.ResultadoImportacaoDTO;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.fincatto.documentofiscal.nfe400.classes.evento.manifestacaodestinatario.NFTipoEventoManifestacaoDestinatario;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.time.LocalDateTime;

@Service
public class SefazSyncService {

    @Autowired
    private ProdutorRepository produtorRepository;

    @Autowired
    private SefazXmlService sefazXmlService;

    @Transactional
    public ResultadoImportacaoDTO sincronizarComCertificadoEmMemoria(Produtor produtor, MultipartFile certificado, String senha) throws Exception {

        if (produtor.getUltimaSincronizacaoSefaz() != null) {
            LocalDateTime proximaPermitida = produtor.getUltimaSincronizacaoSefaz().plusHours(1);
            if (LocalDateTime.now().isBefore(proximaPermitida)) {
                throw new RuntimeException("A SEFAZ exige um intervalo de 1 hora entre sincronizações. Próxima tentativa permitida: " + proximaPermitida.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
            }
        }

        String cpfCnpjLimpo = produtor.getCpfCnpj() != null ? produtor.getCpfCnpj().replaceAll("\\D", "") : "";
        if (cpfCnpjLimpo.length() != 11 && cpfCnpjLimpo.length() != 14) {
            throw new RuntimeException("CPF/CNPJ inválido para o produtor.");
        }

        byte[] pfxBytes = certificado.getBytes();
        NFeConfig config = criarConfigEmMemoria(senha, pfxBytes);
        WSFacade ws = new WSFacade(config);

        ResultadoImportacaoDTO resultadoFinal = new ResultadoImportacaoDTO();
        int maxRequisicoes = 10;
        int loops = 0;
        boolean continuarBuscando = true;



        try {
            while (continuarBuscando && loops < maxRequisicoes) {
                loops++;
                String rawNsu = produtor.getUltimoNsu() != null ? produtor.getUltimoNsu() : "0";
                String ultimoNsu = String.format("%015d", Long.parseLong(rawNsu));

                NFDistribuicaoIntRetorno retorno = ws.consultarDistribuicaoDFe(
                        cpfCnpjLimpo, DFUnidadeFederativa.SC, null, null, ultimoNsu
                );

                String cStat = retorno.getCodigoStatusReposta() != null ? retorno.getCodigoStatusReposta() : "";

                if (retorno.getLote() != null && retorno.getLote().getDocZip() != null) {
                    for (NFDistribuicaoDocumentoZip docZip : retorno.getLote().getDocZip()) {
                        try {
                            String xmlDescompactado = WSDistribuicaoNFe.decodeGZipToXml(docZip.getValue());

                            if (docZip.getSchema().startsWith("procNFe")) {
                                boolean sucesso = sefazXmlService.sincronizarNotaAutomatica(produtor, xmlDescompactado);
                                if (sucesso) resultadoFinal.setImportadas(resultadoFinal.getImportadas() + 1);
                                else resultadoFinal.setIgnoradas(resultadoFinal.getIgnoradas() + 1);
                            }
                            else if (docZip.getSchema().startsWith("resNFe")) {
                                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<chNFe>(.*?)</chNFe>").matcher(xmlDescompactado);
                                if (matcher.find()) {
                                    try {
                                        ws.manifestaDestinatarioNota(matcher.group(1), NFTipoEventoManifestacaoDestinatario.CIENCIA_DA_EMISSAO, "", cpfCnpjLimpo);
                                    } catch (Exception ex) { }
                                }
                            }
                        } catch (Exception ex) { }
                    }
                }
                else if (cStat.equals("137")) {
                    continuarBuscando = false; // Nenhum documento novo
                    // REGISTRA A TRAVA: Só trava por 1h quando a SEFAZ diz que a fila acabou!
                    produtor.setUltimaSincronizacaoSefaz(LocalDateTime.now());
                }
                else if (cStat.equals("656")) {
                    continuarBuscando = false;
                    // REGISTRA A TRAVA: Punição da SEFAZ, obriga a esperar 1h.
                    produtor.setUltimaSincronizacaoSefaz(LocalDateTime.now());
                    throw new RuntimeException("Consumo Indevido detectado pela SEFAZ. Aguarde 1 hora.");
                }

                if (retorno.getUltimoNSU() != null && !retorno.getUltimoNSU().isBlank()) {
                    if (!retorno.getUltimoNSU().equals(ultimoNsu)) {
                        produtor.setUltimoNsu(retorno.getUltimoNSU());
                    }

                    // Se o Último NSU lido for igual ao Máximo NSU disponível na SEFAZ, o lote acabou.
                    if (retorno.getUltimoNSU().equals(retorno.getMaximoNSU())) {
                        continuarBuscando = false;
                    }
                }
            }
        } finally {
            produtorRepository.save(produtor);
        }

        return resultadoFinal;
    }

    public String manifestarNotaManualmente(Produtor produtor, String chaveAcesso, NFTipoEventoManifestacaoDestinatario tipoEvento, MultipartFile certificado, String senha) {
        try {
            String cpfCnpjLimpo = produtor.getCpfCnpj().replaceAll("\\D", "");
            byte[] pfxBytes = certificado.getBytes();

            NFeConfig config = criarConfigEmMemoria(senha, pfxBytes);
            WSFacade ws = new WSFacade(config);

            String justificativa = "";
            if (tipoEvento.equals(NFTipoEventoManifestacaoDestinatario.OPERACAO_NAO_REALIZADA)) {
                justificativa = "Mercadoria nao entregue ou devolvida";
            }

            var retorno = ws.manifestaDestinatarioNota(
                    chaveAcesso,
                    tipoEvento,
                    justificativa,
                    cpfCnpjLimpo
            );

            if (retorno.getEventoRetorno() != null && !retorno.getEventoRetorno().isEmpty()) {
                var infoRetorno = retorno.getEventoRetorno().get(0).getInfoEventoRetorno();
                if (infoRetorno.getCodigoStatus().equals("135")) {
                    return "Sucesso: Evento registrado e vinculado a NF-e.";
                } else {
                    return "Erro da SEFAZ: " + infoRetorno.getMotivo();
                }
            } else {
                return "Erro da SEFAZ: Lote rejeitado - " + retorno.getMotivo();
            }

        } catch (Exception e) {
            return "Erro de comunicação: " + e.getMessage();
        }
    }

    private NFeConfig criarConfigEmMemoria(String senha, byte[] pfxBytes) {
        return new NFeConfig() {
            @Override public DFUnidadeFederativa getCUF() { return DFUnidadeFederativa.SC; }
            @Override public DFAmbiente getAmbiente() { return DFAmbiente.PRODUCAO; }
            @Override public String getCertificadoSenha() { return senha; }
            @Override public KeyStore getCadeiaCertificadosKeyStore() throws KeyStoreException { return null; }
            @Override public String getCadeiaCertificadosSenha() { return "changeit"; }
            @Override
            public KeyStore getCertificadoKeyStore() throws KeyStoreException {
                try {
                    KeyStore keyStore = KeyStore.getInstance("PKCS12");
                    try (InputStream is = new ByteArrayInputStream(pfxBytes)) {
                        keyStore.load(is, getCertificadoSenha().toCharArray());
                    }
                    return keyStore;
                } catch (Exception e) {
                    throw new KeyStoreException("Falha ao montar KeyStore: Senha incorreta ou arquivo PFX corrompido.", e);
                }
            }
        };
    }
}