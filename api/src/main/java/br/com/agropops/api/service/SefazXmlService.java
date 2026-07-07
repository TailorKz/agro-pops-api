package br.com.agropops.api.service;

import br.com.agropops.api.model.ItemNota;
import br.com.agropops.api.model.NotaFiscal;
import br.com.agropops.api.model.Produtor;
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

    // MÉTODO 1: Upload Manual pelo React (Otimizado com Lotes e RAM)
    @Transactional
    public int importarNotas(Long produtorId, List<MultipartFile> arquivos) {
        Produtor produtor = produtorRepository.findById(produtorId).orElseThrow();

        // Evita N+1 no NCM
        List<RegraNCM> regras = regraRepository.findByContadorId(produtor.getContador().getId());
        Map<String, Boolean> mapaRegras = regras.stream()
                .collect(Collectors.toMap(RegraNCM::getNcm, RegraNCM::getIsDedutivel, (r1, r2) -> r1));

        // CARREGAR CHAVES EXISTENTES PARA A RAM - elimina o Auto-Flush do Hibernate
        Set<String> chavesExistentes = notaRepository.findChavesAcessoByProdutorId(produtorId);

        // LISTA DE ESPERA
        List<NotaFiscal> notasParaSalvar = new ArrayList<>();

        for (MultipartFile arquivo : arquivos) {
            try {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(arquivo.getInputStream());
                NotaFiscal notaProcessada = processarNotaNaMemoria(doc, produtor, mapaRegras, chavesExistentes);

                if (notaProcessada != null) {
                    notasParaSalvar.add(notaProcessada);
                    chavesExistentes.add(notaProcessada.getChaveAcesso()); // Atualiza RAM para evitar duplicados no lote
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
    // MÉTODO 2: Robô Automático=
    @Transactional
    public boolean sincronizarNotaAutomatica(Produtor produtor, String xmlContent) {
        try {
            // Carrega regras e chaves para a RAM para a nota do Robô
            List<RegraNCM> regras = regraRepository.findByContadorId(produtor.getContador().getId());
            Map<String, Boolean> mapaRegras = regras.stream()
                    .collect(Collectors.toMap(RegraNCM::getNcm, RegraNCM::getIsDedutivel, (r1, r2) -> r1));

            Set<String> chavesExistentes = notaRepository.findChavesAcessoByProdutorId(produtor.getId());

            InputStream targetStream = new ByteArrayInputStream(xmlContent.getBytes());
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(targetStream);

            NotaFiscal notaProcessada = processarNotaNaMemoria(doc, produtor, mapaRegras, chavesExistentes);

            // Robô processa uma a uma, então o save() direto faz sentido aqui
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

    // rocessamento em Memória
    private NotaFiscal processarNotaNaMemoria(Document doc, Produtor produtor, Map<String, Boolean> mapaRegras, Set<String> chavesExistentes) {
        try {
            String idAtributo = doc.getElementsByTagName("infNFe").item(0).getAttributes().getNamedItem("Id").getNodeValue();
            String chaveAcesso = idAtributo.replace("NFe", "");

            // Deduplicação Instantânea em RAM
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

            String valorStr = doc.getElementsByTagName("vNF").item(0).getTextContent();
            BigDecimal valorTotal = new BigDecimal(valorStr);

            String emitenteNome = "Desconhecido";
            if (doc.getElementsByTagName("emit").getLength() > 0) {
                org.w3c.dom.Element emit = (org.w3c.dom.Element) doc.getElementsByTagName("emit").item(0);
                if(emit.getElementsByTagName("xNome").getLength() > 0) emitenteNome = emit.getElementsByTagName("xNome").item(0).getTextContent();
            }

            String destinatarioNome = "Desconhecido";
            if (doc.getElementsByTagName("dest").getLength() > 0) {
                org.w3c.dom.Element dest = (org.w3c.dom.Element) doc.getElementsByTagName("dest").item(0);
                if(dest.getElementsByTagName("xNome").getLength() > 0) destinatarioNome = dest.getElementsByTagName("xNome").item(0).getTextContent();
            }

            String empresaEnvolvida = tipo.equals("SAIDA") ? emitenteNome : destinatarioNome;

            NotaFiscal nota = new NotaFiscal();
            nota.setChaveAcesso(chaveAcesso);
            nota.setNumero(numero);
            nota.setDataEmissao(dataEmissao);
            nota.setTipo(tipo);
            nota.setValorTotal(valorTotal);
            nota.setEmpresaEnvolvida(empresaEnvolvida);
            nota.setProdutor(produtor);

            org.w3c.dom.NodeList detNodes = doc.getElementsByTagName("det");

            for (int i = 0; i < detNodes.getLength(); i++) {
                org.w3c.dom.Element det = (org.w3c.dom.Element) detNodes.item(i);
                org.w3c.dom.Element prod = (org.w3c.dom.Element) det.getElementsByTagName("prod").item(0);

                String nomeProduto = prod.getElementsByTagName("xProd").item(0).getTextContent();
                String ncmProduto = prod.getElementsByTagName("NCM").item(0).getTextContent();
                String valorProdutoStr = prod.getElementsByTagName("vProd").item(0).getTextContent();
                BigDecimal valorProduto = new BigDecimal(valorProdutoStr);

                // Consulta a Memória (RAM) ao invés do Banco de Dados
                boolean isDedutivel = tipo.equals("SAIDA") && mapaRegras.getOrDefault(ncmProduto, false);

                ItemNota item = new ItemNota();
                item.setDescricao(nomeProduto);
                item.setNcm(ncmProduto);
                item.setValor(valorProduto);
                item.setIsDedutivel(isDedutivel);
                item.setNotaFiscal(nota);

                nota.getItens().add(item);
            }

            return nota;

        } catch (Exception e) {
            return null;
        }
    }
}