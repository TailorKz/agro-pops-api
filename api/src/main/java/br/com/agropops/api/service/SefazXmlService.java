package br.com.agropops.api.service;

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
import java.util.List;
import java.util.Optional;

@Service
public class SefazXmlService {

    @Autowired
    private NotaFiscalRepository notaRepository;

    @Autowired
    private ProdutorRepository produtorRepository;

    @Autowired
    private RegraNCMRepository regraRepository;

    // ==============================================================
    // MÉTODO 1: Para o Upload Manual (Vem do React)
    // ==============================================================
    @Transactional
    public int importarNotas(Long produtorId, List<MultipartFile> arquivos) {
        Produtor produtor = produtorRepository.findById(produtorId).orElseThrow();
        int notasImportadas = 0;

        for (MultipartFile arquivo : arquivos) {
            try {
                // Abre o ficheiro recebido pelo navegador
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(arquivo.getInputStream());

                // Manda para o cérebro processar
                boolean sucesso = processarESalvarNota(doc, produtor);
                if (sucesso) notasImportadas++;
            } catch (Exception e) {
                System.out.println("❌ Ficheiro ignorado (Inválido): " + arquivo.getOriginalFilename());
            }
        }
        return notasImportadas;
    }

    // ==============================================================
    // MÉTODO 2: Para o Robô Automático (Virá da SEFAZ)
    // ==============================================================
    @Transactional
    public boolean sincronizarNotaAutomatica(Produtor produtor, String xmlContent) {
        try {
            // Transforma o texto puro num ficheiro de memória e abre
            InputStream targetStream = new ByteArrayInputStream(xmlContent.getBytes());
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(targetStream);

            // Manda para o MESMO cérebro processar!
            return processarESalvarNota(doc, produtor);
        } catch (Exception e) {
            System.out.println("❌ Falha ao ler XML automático: " + e.getMessage());
            return false;
        }
    }

    // ==============================================================
    // O CÉREBRO CENTRAL: Extração, Deduplicação e Inteligência NCM
    // ==============================================================
    private boolean processarESalvarNota(Document doc, Produtor produtor) {
        try {
            String idAtributo = doc.getElementsByTagName("infNFe").item(0).getAttributes().getNamedItem("Id").getNodeValue();
            String chaveAcesso = idAtributo.replace("NFe", "");

            // 1. DEDUPLICAÇÃO
            if (notaRepository.existsByChaveAcesso(chaveAcesso)) {
                return false; // Aborta e não guarda se a nota já existir
            }

            // 2. EXTRAÇÃO DE DADOS BÁSICOS
            String numero = doc.getElementsByTagName("nNF").item(0).getTextContent();

            String dataTexto = doc.getElementsByTagName("dhEmi").getLength() > 0
                    ? doc.getElementsByTagName("dhEmi").item(0).getTextContent()
                    : doc.getElementsByTagName("dEmi").item(0).getTextContent();
            LocalDate dataEmissao = LocalDate.parse(dataTexto.substring(0, 10));

            String tpNF = doc.getElementsByTagName("tpNF").item(0).getTextContent();
            String tipo = tpNF.equals("0") ? "ENTRADA" : "SAIDA";

            String valorStr = doc.getElementsByTagName("vNF").item(0).getTextContent();
            BigDecimal valor = new BigDecimal(valorStr);

            String descricao = "Diversos";
            if (doc.getElementsByTagName("xProd").getLength() > 0) {
                descricao = doc.getElementsByTagName("xProd").item(0).getTextContent();
            }

            // 3. INTELIGÊNCIA FISCAL (NCM)
            String ncm = "N/A";
            if (doc.getElementsByTagName("NCM").getLength() > 0) {
                ncm = doc.getElementsByTagName("NCM").item(0).getTextContent();
            }

            boolean isDedutivel = false;
            Long contadorId = produtor.getContador().getId();
            Optional<RegraNCM> regra = regraRepository.findByNcmAndContadorId(ncm, contadorId);
            if (regra.isPresent()) {
                isDedutivel = regra.get().getIsDedutivel();
            }

            // 4. GUARDA NA BASE DE DADOS
            NotaFiscal nota = new NotaFiscal();
            nota.setChaveAcesso(chaveAcesso);
            nota.setNumero(numero);
            nota.setDataEmissao(dataEmissao);
            nota.setTipo(tipo);
            nota.setValor(valor);
            nota.setDescricao(descricao);
            nota.setIsDedutivel(isDedutivel);
            nota.setProdutor(produtor);

            notaRepository.save(nota);
            return true; // Sucesso!

        } catch (Exception e) {
            return false; // Falhou a leitura deste documento específico
        }
    }
}