package br.com.agropops.api.service;

import br.com.agropops.api.model.ItemNota;
import br.com.agropops.api.model.NotaFiscal;
import br.com.agropops.api.model.Produtor;
import br.com.agropops.api.model.PropriedadeRural;
import br.com.agropops.api.model.RegraNCM;
import br.com.agropops.api.repository.NotaFiscalRepository;
import br.com.agropops.api.repository.ProdutorRepository;
import br.com.agropops.api.repository.RegraNCMRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SefazXmlService {

    @Autowired
    private NotaFiscalRepository notaRepository;

    @Autowired
    private ProdutorRepository produtorRepository;

    @Autowired
    private RegraNCMRepository regraRepository;

    private static final Set<String> CFOPS_DEVOLUCAO_VENDA = Set.of(
            "1201", "1202", "1410", "1411", "2201", "2202", "2410", "2411"
    );

    private static final Set<String> CFOPS_DEVOLUCAO_COMPRA = Set.of(
            "5201", "5202", "5410", "5411", "6201", "6202", "6410", "6411"
    );

    @Transactional
    public int importarNotas(Long produtorId, Long propriedadeFallbackId, List<MultipartFile> arquivos) { // <-- Assinatura corrigida!
        Produtor produtor = produtorRepository.findById(produtorId).orElseThrow();
        List<RegraNCM> regras = regraRepository.findByContadorId(produtor.getContador().getId());
        Map<String, Boolean> mapaRegras = regras.stream()
                .collect(Collectors.toMap(RegraNCM::getNcm, RegraNCM::getIsDedutivel, (r1, r2) -> r1));

        Set<String> chavesExistentes = notaRepository.findChavesAcessoByProdutorId(produtorId);
        List<NotaFiscal> notasParaSalvar = new ArrayList<>();

        for (MultipartFile arquivo : arquivos) {
            try {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(arquivo.getInputStream());
                NotaFiscal notaProcessada = processarNotaNaMemoria(doc, produtor, mapaRegras, chavesExistentes, propriedadeFallbackId);
                if (notaProcessada != null) {
                    notasParaSalvar.add(notaProcessada);
                    chavesExistentes.add(notaProcessada.getChaveAcesso());
                }
            } catch (Exception e) {
                System.out.println("Ficheiro ignorado (Inválido): " + arquivo.getOriginalFilename());
            }
        }

        if (!notasParaSalvar.isEmpty()) {
            notaRepository.saveAll(notasParaSalvar);
        }
        return notasParaSalvar.size();
    }

    @Transactional
    public boolean sincronizarNotaAutomatica(Produtor produtor, String xmlContent) {
        try {
            List<RegraNCM> regras = regraRepository.findByContadorId(produtor.getContador().getId());
            Map<String, Boolean> mapaRegras = regras.stream()
                    .collect(Collectors.toMap(RegraNCM::getNcm, RegraNCM::getIsDedutivel, (r1, r2) -> r1));

            Set<String> chavesExistentes = notaRepository.findChavesAcessoByProdutorId(produtor.getId());

            InputStream targetStream = new ByteArrayInputStream(xmlContent.getBytes());
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(targetStream);

            // Notas do Robô Automático não têm fallback de tela, enviamos 'null'
            NotaFiscal notaProcessada = processarNotaNaMemoria(doc, produtor, mapaRegras, chavesExistentes, null);

            if (notaProcessada != null) {
                notaRepository.save(notaProcessada);
                return true;
            }
            return false;
        } catch (Exception e) {
            System.out.println("Falha ao ler XML automático: " + e.getMessage());
            return false;
        }
    }

    private NotaFiscal processarNotaNaMemoria(Document doc, Produtor produtor, Map<String, Boolean> mapaRegras, Set<String> chavesExistentes, Long propriedadeFallbackId) {
        try {
            String idAtributo = doc.getElementsByTagName("infNFe").item(0).getAttributes().getNamedItem("Id").getNodeValue();
            String chaveAcesso = idAtributo.replace("NFe", "");

            if (chavesExistentes.contains(chaveAcesso)) {
                return null;
            }

            String numero = doc.getElementsByTagName("nNF").item(0).getTextContent();
            String dataTexto = doc.getElementsByTagName("dhEmi").getLength() > 0
                    ? doc.getElementsByTagName("dhEmi").item(0).getTextContent()
                    : doc.getElementsByTagName("dEmi").item(0).getTextContent();
            LocalDate dataEmissao = LocalDate.parse(dataTexto.substring(0, 10));

            String tpNF = doc.getElementsByTagName("tpNF").item(0).getTextContent();
            String tipo = tpNF.equals("0") ? "ENTRADA" : "SAIDA";

            // --- INTELIGÊNCIA: EXTRAÇÃO DA IE E NOME DO EMITENTE/DESTINATÁRIO ---
            String emitenteNome = "Desconhecido";
            String ieEmitente = "";
            if (doc.getElementsByTagName("emit").getLength() > 0) {
                org.w3c.dom.Element emit = (org.w3c.dom.Element) doc.getElementsByTagName("emit").item(0);
                if(emit.getElementsByTagName("xNome").getLength() > 0) emitenteNome = emit.getElementsByTagName("xNome").item(0).getTextContent();
                if(emit.getElementsByTagName("IE").getLength() > 0) ieEmitente = emit.getElementsByTagName("IE").item(0).getTextContent().replaceAll("\\D", "");
            }

            String destinatarioNome = "Desconhecido";
            String ieDestinatario = "";
            if (doc.getElementsByTagName("dest").getLength() > 0) {
                org.w3c.dom.Element dest = (org.w3c.dom.Element) doc.getElementsByTagName("dest").item(0);
                if(dest.getElementsByTagName("xNome").getLength() > 0) destinatarioNome = dest.getElementsByTagName("xNome").item(0).getTextContent();
                if(dest.getElementsByTagName("IE").getLength() > 0) ieDestinatario = dest.getElementsByTagName("IE").item(0).getTextContent().replaceAll("\\D", "");
            }

            String empresaEnvolvida = tipo.equals("SAIDA") ? emitenteNome : destinatarioNome;

            NotaFiscal nota = new NotaFiscal();
            nota.setChaveAcesso(chaveAcesso);
            nota.setNumero(numero);

            String chaveAcessoReferencia = null;
            if (doc.getElementsByTagName("refNFe").getLength() > 0) {
                chaveAcessoReferencia = doc.getElementsByTagName("refNFe").item(0).getTextContent();
            }
            nota.setChaveAcessoReferencia(chaveAcessoReferencia);
            nota.setDataEmissao(dataEmissao);
            nota.setTipo(tipo);
            nota.setEmpresaEnvolvida(empresaEnvolvida);
            nota.setProdutor(produtor);

            // ========================================================
            // MATCH DE FAZENDA PELA INSCRIÇÃO ESTADUAL (IE)
            // ========================================================
            PropriedadeRural propriedadeDestino = null;

            for (PropriedadeRural prop : produtor.getPropriedades()) {
                String ieFazenda = prop.getInscricaoEstadual();
                if (ieFazenda != null && !ieFazenda.isEmpty()) {
                    String ieLimpa = ieFazenda.replaceAll("\\D", "");
                    if (ieLimpa.equals(ieEmitente) || ieLimpa.equals(ieDestinatario)) {
                        propriedadeDestino = prop;
                        System.out.println("✅ MATCH DE IE ENCONTRADO! Nota vinculada à: " + prop.getNome());
                        break;
                    }
                }
            }

            // Se o XML não tiver IE ou não bater, usa o Fallback que o contador escolheu na tela
            if (propriedadeDestino == null && propriedadeFallbackId != null) {
                propriedadeDestino = produtor.getPropriedades().stream()
                        .filter(p -> p.getId().equals(propriedadeFallbackId))
                        .findFirst()
                        .orElse(null);
            }
            // Se tudo falhar, joga na Propriedade Principal
            if (propriedadeDestino == null && !produtor.getPropriedades().isEmpty()) {
                propriedadeDestino = produtor.getPropriedades().get(0);
            }
            nota.setPropriedadeRural(propriedadeDestino);
            // ========================================================

            BigDecimal valorTotalAjustado = BigDecimal.ZERO;
            org.w3c.dom.NodeList detNodes = doc.getElementsByTagName("det");

            for (int i = 0; i < detNodes.getLength(); i++) {
                org.w3c.dom.Element det = (org.w3c.dom.Element) detNodes.item(i);
                org.w3c.dom.Element prod = (org.w3c.dom.Element) det.getElementsByTagName("prod").item(0);

                String nomeProduto = prod.getElementsByTagName("xProd").item(0).getTextContent();
                String ncmProduto = prod.getElementsByTagName("NCM").item(0).getTextContent();

                String cfop = "";
                if (prod.getElementsByTagName("CFOP").getLength() > 0) {
                    cfop = prod.getElementsByTagName("CFOP").item(0).getTextContent();
                }

                String valorProdutoStr = prod.getElementsByTagName("vProd").item(0).getTextContent();
                BigDecimal valorProduto = new BigDecimal(valorProdutoStr);

                boolean isDedutivel = tipo.equals("SAIDA") && mapaRegras.getOrDefault(ncmProduto, false);

                if (CFOPS_DEVOLUCAO_VENDA.contains(cfop) || CFOPS_DEVOLUCAO_COMPRA.contains(cfop)) {
                    valorProduto = valorProduto.negate();
                    nomeProduto = "[DEVOLUÇÃO] " + nomeProduto;
                }

                ItemNota item = new ItemNota();
                item.setDescricao(nomeProduto);
                item.setNcm(ncmProduto);
                item.setValor(valorProduto);
                item.setIsDedutivel(isDedutivel);
                item.setNotaFiscal(nota);

                nota.getItens().add(item);
                valorTotalAjustado = valorTotalAjustado.add(valorProduto);
            }

            nota.setValorTotal(valorTotalAjustado);
            return nota;
        } catch (Exception e) {
            return null;
        }
    }
}