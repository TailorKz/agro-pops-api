package br.com.agropops.api.service;

import br.com.agropops.api.model.NotaFiscal;
import br.com.agropops.api.model.Produtor;
import br.com.agropops.api.model.RegraNCM; // NOVO
import br.com.agropops.api.repository.NotaFiscalRepository;
import br.com.agropops.api.repository.ProdutorRepository;
import br.com.agropops.api.repository.RegraNCMRepository; // NOVO
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
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
    private RegraNCMRepository regraRepository; // INJETAMOS O REPOSITÓRIO DE REGRAS

    @Transactional
    public int importarNotas(Long produtorId, List<MultipartFile> arquivos) {
        Produtor produtor = produtorRepository.findById(produtorId).orElseThrow();
        Long contadorId = produtor.getContador().getId(); // Pega o ID do contador dono do produtor

        int notasImportadas = 0;

        for (MultipartFile arquivo : arquivos) {
            try {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(arquivo.getInputStream());

                String idAtributo = doc.getElementsByTagName("infNFe").item(0).getAttributes().getNamedItem("Id").getNodeValue();
                String chaveAcesso = idAtributo.replace("NFe", "");

                if (notaRepository.existsByChaveAcesso(chaveAcesso)) {
                    continue; // Pula se for duplicada
                }

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

                // INTELIGÊNCIA FISCAL (BUSCA O NCM)
                String ncm = "N/A";
                if (doc.getElementsByTagName("NCM").getLength() > 0) {
                    ncm = doc.getElementsByTagName("NCM").item(0).getTextContent();
                }

                boolean isDedutivel = false; // Por padrão, não abate imposto

                Optional<RegraNCM> regra = regraRepository.findByNcmAndContadorId(ncm, contadorId);
                if (regra.isPresent()) {
                    isDedutivel = regra.get().getIsDedutivel(); // Aplica a regra do contador!
                }
                // ==========================================

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
                notasImportadas++;

            } catch (Exception e) {
                System.out.println("❌ Ficheiro ignorado (Inválido): " + arquivo.getOriginalFilename());
            }
        }
        return notasImportadas;
    }
}