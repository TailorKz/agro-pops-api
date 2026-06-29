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
import br.com.agropops.api.model.ItemNota;

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
    // ==============================================================
    // O CÉREBRO CENTRAL: Extração, Deduplicação e Inteligência NCM
    // ==============================================================
    private boolean processarESalvarNota(Document doc, Produtor produtor) {
        try {
            String idAtributo = doc.getElementsByTagName("infNFe").item(0).getAttributes().getNamedItem("Id").getNodeValue();
            String chaveAcesso = idAtributo.replace("NFe", "");

            // 1. DEDUPLICAÇÃO
            if (notaRepository.existsByChaveAcesso(chaveAcesso)) {
                return false;
            }

            // 2. EXTRAÇÃO DE DADOS BÁSICOS DA NOTA
            String numero = doc.getElementsByTagName("nNF").item(0).getTextContent();
            String dataTexto = doc.getElementsByTagName("dhEmi").getLength() > 0
                    ? doc.getElementsByTagName("dhEmi").item(0).getTextContent()
                    : doc.getElementsByTagName("dEmi").item(0).getTextContent();
            LocalDate dataEmissao = LocalDate.parse(dataTexto.substring(0, 10));

            String tpNF = doc.getElementsByTagName("tpNF").item(0).getTextContent();
            String tipo = tpNF.equals("0") ? "ENTRADA" : "SAIDA";

            String valorStr = doc.getElementsByTagName("vNF").item(0).getTextContent();
            BigDecimal valorTotal = new BigDecimal(valorStr);

            // 3. DESCOBRIR A EMPRESA ENVOLVIDA (Razão Social)
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

            // Se for despesa (SAIDA), a empresa é quem emitiu. Se for receita (ENTRADA), a empresa é quem comprou (destinatário).
            String empresaEnvolvida = tipo.equals("SAIDA") ? emitenteNome : destinatarioNome;

            // 4. PREPARA A NOTA
            NotaFiscal nota = new NotaFiscal();
            nota.setChaveAcesso(chaveAcesso);
            nota.setNumero(numero);
            nota.setDataEmissao(dataEmissao);
            nota.setTipo(tipo);
            nota.setValorTotal(valorTotal);
            nota.setEmpresaEnvolvida(empresaEnvolvida);
            nota.setProdutor(produtor);

            // 5. INTELIGÊNCIA FISCAL: VARRER TODOS OS ITENS
            org.w3c.dom.NodeList detNodes = doc.getElementsByTagName("det");
            Long contadorId = produtor.getContador().getId();

            for (int i = 0; i < detNodes.getLength(); i++) {
                org.w3c.dom.Element det = (org.w3c.dom.Element) detNodes.item(i);
                org.w3c.dom.Element prod = (org.w3c.dom.Element) det.getElementsByTagName("prod").item(0);

                String nomeProduto = prod.getElementsByTagName("xProd").item(0).getTextContent();
                String ncmProduto = prod.getElementsByTagName("NCM").item(0).getTextContent();
                String valorProdutoStr = prod.getElementsByTagName("vProd").item(0).getTextContent();
                BigDecimal valorProduto = new BigDecimal(valorProdutoStr);

                boolean isDedutivel = false;
                // O LCDPR só deduz despesas (SAÍDA). Entradas são sempre receitas brutas.
                if (tipo.equals("SAIDA")) {
                    Optional<RegraNCM> regra = regraRepository.findByNcmAndContadorId(ncmProduto, contadorId);
                    if (regra.isPresent()) {
                        isDedutivel = regra.get().getIsDedutivel();
                    }
                }

                ItemNota item = new ItemNota();
                item.setDescricao(nomeProduto);
                item.setNcm(ncmProduto);
                item.setValor(valorProduto);
                item.setIsDedutivel(isDedutivel);
                item.setNotaFiscal(nota);

                // Adiciona o item à nota (o Hibernate vai salvar tudo junto!)
                nota.getItens().add(item);
            }

            notaRepository.save(nota);
            return true;
        } catch (Exception e) {
            System.out.println("⚠️ Erro ao processar XML: " + e.getMessage());
            return false;
        }
    }
}