package br.com.agropops.api.service;

import br.com.agropops.api.model.*;
import br.com.agropops.api.repository.NotaFiscalRepository;
import br.com.agropops.api.repository.ProdutorRepository;
import br.com.agropops.api.repository.RegraNCMRepository;
import br.com.agropops.api.repository.RegraGlobalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
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

    @Autowired
    private RegraGlobalRepository regraGlobalRepository;

    private static final Set<String> CFOPS_DEVOLUCAO_VENDA = Set.of(
            "1201", "1202", "1410", "1411", "2201", "2202", "2410", "2411"
    );

    private static final Set<String> CFOPS_DEVOLUCAO_COMPRA = Set.of(
            "5201", "5202", "5410", "5411", "6201", "6202", "6410", "6411"
    );

    private static final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    // ========================================================
    // MOTOR DE SEGURANÇA XXE (XML External Entity)
    // ========================================================
    private DocumentBuilderFactory getSecureDocumentBuilderFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Blindagem contra XXE (Billion Laughs e leitura de arquivos do SO)
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    @Transactional
    public br.com.agropops.api.dto.ResultadoImportacaoDTO importarNotas(Long produtorId, Long propriedadeFallbackId, List<MultipartFile> arquivos, boolean forcarDivergentes, boolean ignorarParcelas) {
        Produtor produtor = produtorRepository.findById(produtorId).orElseThrow();

        List<RegraNCM> regras = regraRepository.findByContadorId(produtor.getContador().getId());
        Map<String, Boolean> mapaRegras = regras.stream()
                .collect(Collectors.toMap(RegraNCM::getNcm, RegraNCM::getIsDedutivel, (r1, r2) -> r1));

        List<RegraGlobal> globais = regraGlobalRepository.findAll();
        Map<String, RegraGlobal> mapaGlobaisNcm = globais.stream()
                .filter(r -> r.getTipo().equals("NCM"))
                .collect(Collectors.toMap(RegraGlobal::getCodigo, r -> r, (r1, r2) -> r1));
        Map<String, RegraGlobal> mapaGlobaisCfop = globais.stream()
                .filter(r -> r.getTipo().equals("CFOP"))
                .collect(Collectors.toMap(RegraGlobal::getCodigo, r -> r, (r1, r2) -> r1));

        Set<String> chavesExistentes = notaRepository.findChavesAcessoByProdutorId(produtorId);
        List<NotaFiscal> notasParaSalvar = new ArrayList<>();

        br.com.agropops.api.dto.ResultadoImportacaoDTO resultado = new br.com.agropops.api.dto.ResultadoImportacaoDTO();

        // 1. Montar lista de Documentos Válidos (Produtor + Propriedades)
        List<String> documentosValidos = new ArrayList<>();
        if (produtor.getCpfCnpj() != null) documentosValidos.add(produtor.getCpfCnpj().replaceAll("\\D", ""));
        if (produtor.getCnpj() != null) documentosValidos.add(produtor.getCnpj().replaceAll("\\D", ""));
        for (PropriedadeRural p : produtor.getPropriedades()) {
            if (p.getCpfCnpj() != null && !p.getCpfCnpj().isEmpty()) {
                documentosValidos.add(p.getCpfCnpj().replaceAll("\\D", ""));
            }
        }

        try {
            javax.xml.parsers.DocumentBuilder builder = getSecureDocumentBuilderFactory().newDocumentBuilder();

            for (MultipartFile arquivo : arquivos) {
                try {
                    Document doc = builder.parse(arquivo.getInputStream());

                    // --- VERIFICAÇÃO DE DIVERGÊNCIA (CPF/CNPJ) ---
                    String docEmitente = "";
                    String nomeEmitente = "";
                    if (doc.getElementsByTagName("emit").getLength() > 0) {
                        org.w3c.dom.Element emit = (org.w3c.dom.Element) doc.getElementsByTagName("emit").item(0);
                        if(emit.getElementsByTagName("CNPJ").getLength() > 0) docEmitente = emit.getElementsByTagName("CNPJ").item(0).getTextContent();
                        else if(emit.getElementsByTagName("CPF").getLength() > 0) docEmitente = emit.getElementsByTagName("CPF").item(0).getTextContent();
                        if(emit.getElementsByTagName("xNome").getLength() > 0) nomeEmitente = emit.getElementsByTagName("xNome").item(0).getTextContent();
                    }

                    String docDestinatario = "";
                    String nomeDestinatario = "";
                    if (doc.getElementsByTagName("dest").getLength() > 0) {
                        org.w3c.dom.Element dest = (org.w3c.dom.Element) doc.getElementsByTagName("dest").item(0);
                        if(dest.getElementsByTagName("CNPJ").getLength() > 0) docDestinatario = dest.getElementsByTagName("CNPJ").item(0).getTextContent();
                        else if(dest.getElementsByTagName("CPF").getLength() > 0) docDestinatario = dest.getElementsByTagName("CPF").item(0).getTextContent();
                        if(dest.getElementsByTagName("xNome").getLength() > 0) nomeDestinatario = dest.getElementsByTagName("xNome").item(0).getTextContent();
                    }

                    boolean pertenceAoProdutor = documentosValidos.contains(docEmitente) || documentosValidos.contains(docDestinatario);

                    if (!pertenceAoProdutor && !forcarDivergentes) {
                        // Se não pertence ao produtor e não foi forçado, adiciona à lista de divergentes e pula
                        br.com.agropops.api.dto.NotaDivergenteDTO div = new br.com.agropops.api.dto.NotaDivergenteDTO();
                        div.setNomeArquivo(arquivo.getOriginalFilename());
                        div.setEmitente(nomeEmitente);
                        div.setDestinatario(nomeDestinatario);
                        div.setDocumentoEncontrado(docEmitente + " / " + docDestinatario);
                        resultado.getDivergentes().add(div);
                        continue;
                    }

                    NotaFiscal notaProcessada = processarNotaNaMemoria(doc, produtor, mapaRegras, mapaGlobaisNcm, mapaGlobaisCfop, chavesExistentes, propriedadeFallbackId, ignorarParcelas);

                    if (notaProcessada != null) {
                        notasParaSalvar.add(notaProcessada);
                        chavesExistentes.add(notaProcessada.getChaveAcesso());
                    } else {
                        resultado.setIgnoradas(resultado.getIgnoradas() + 1);
                    }
                } catch (Exception e) {
                    System.out.println("Ficheiro ignorado (Inválido): " + arquivo.getOriginalFilename());
                    resultado.setFalhas(resultado.getFalhas() + 1);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro crítico ao inicializar o leitor de XML: " + e.getMessage());
        }

        if (!notasParaSalvar.isEmpty()) {
            notaRepository.saveAll(notasParaSalvar);
            resultado.setImportadas(notasParaSalvar.size());
        }

        return resultado;
    }

    @Transactional
    public boolean sincronizarNotaAutomatica(Produtor produtor, String xmlContent) {
        try {
            List<RegraNCM> regras = regraRepository.findByContadorId(produtor.getContador().getId());
            Map<String, Boolean> mapaRegras = regras.stream()
                    .collect(Collectors.toMap(RegraNCM::getNcm, RegraNCM::getIsDedutivel, (r1, r2) -> r1));

            List<RegraGlobal> globais = regraGlobalRepository.findAll();
            Map<String, RegraGlobal> mapaGlobaisNcm = globais.stream().filter(r -> r.getTipo().equals("NCM")).collect(Collectors.toMap(RegraGlobal::getCodigo, r -> r, (r1, r2) -> r1));
            Map<String, RegraGlobal> mapaGlobaisCfop = globais.stream().filter(r -> r.getTipo().equals("CFOP")).collect(Collectors.toMap(RegraGlobal::getCodigo, r -> r, (r1, r2) -> r1));
            Set<String> chavesExistentes = notaRepository.findChavesAcessoByProdutorId(produtor.getId());

            InputStream targetStream = new ByteArrayInputStream(xmlContent.getBytes());
            Document doc = getSecureDocumentBuilderFactory().newDocumentBuilder().parse(targetStream);

            NotaFiscal notaProcessada = processarNotaNaMemoria(doc, produtor, mapaRegras, mapaGlobaisNcm, mapaGlobaisCfop, chavesExistentes, null, false);

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

    private NotaFiscal processarNotaNaMemoria(Document doc, Produtor produtor, Map<String, Boolean> mapaRegras, Map<String, RegraGlobal> mapaGlobaisNcm, Map<String, RegraGlobal> mapaGlobaisCfop, Set<String> chavesExistentes, Long propriedadeFallbackId, boolean ignorarParcelas) {
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

            // --- EXTRAÇÃO DA NATUREZA DA OPERAÇÃO ---
            String naturezaOperacao = "Não informada";
            if (doc.getElementsByTagName("natOp").getLength() > 0) {
                naturezaOperacao = doc.getElementsByTagName("natOp").item(0).getTextContent().toUpperCase();
            }

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

            // ========================================================
            // A MÁGICA DO LCDPR: ESPELHAMENTO CONTÁBIL E DEVOLUÇÕES
            // ========================================================

            // 1. Descobrir se o Produtor é o Emitente cruzando a IE
            boolean isEmitenteProdutor = false;
            for (PropriedadeRural prop : produtor.getPropriedades()) {
                String ieFazenda = prop.getInscricaoEstadual();
                if (ieFazenda != null && !ieFazenda.isEmpty()) {
                    if (ieFazenda.replaceAll("\\D", "").equals(ieEmitente)) {
                        isEmitenteProdutor = true;
                        break;
                    }
                }
            }

            boolean isEntradaFisica = tpNF.equals("0");
            boolean isDevolucao = naturezaOperacao.contains("DEVOLUCAO") ||
                    naturezaOperacao.contains("DEVOLUÇÃO") ||
                    naturezaOperacao.contains("RETORNO");

            // 2. Definir o Tipo Financeiro (O que vai pro Livro Caixa)
            String tipo; // ENTRADA (Receita) ou SAIDA (Despesa)

            if (isDevolucao) {
                if (isEmitenteProdutor) {
                    // Produtor recebendo devolução de venda -> Anula Receita (ENTRADA)
                    // Produtor enviando devolução de compra -> Anula Despesa (SAIDA)
                    tipo = isEntradaFisica ? "ENTRADA" : "SAIDA";
                } else {
                    // Loja recebendo devolução de compra do produtor -> Anula Despesa (SAIDA)
                    // Loja enviando devolução de venda pro produtor -> Anula Receita (ENTRADA)
                    tipo = isEntradaFisica ? "SAIDA" : "ENTRADA";
                }
            } else {
                if (isEmitenteProdutor) {
                    // Produtor vendendo (Saída) -> Receita (ENTRADA)
                    // Produtor comprando de CPF (Entrada) -> Despesa (SAIDA)
                    tipo = isEntradaFisica ? "SAIDA" : "ENTRADA";
                } else {
                    // Loja vendendo pro Produtor (Saída) -> Despesa (SAIDA)
                    // Loja comprando do Produtor (Entrada) -> Receita (ENTRADA)
                    tipo = isEntradaFisica ? "ENTRADA" : "SAIDA";
                }
            }

            String empresaEnvolvida = isEmitenteProdutor ? destinatarioNome : emitenteNome;

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
            nota.setNaturezaOperacao(naturezaOperacao);
            nota.setNomeEmitente(emitenteNome);
            nota.setNomeDestinatario(destinatarioNome);
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
                        System.out.println("  MATCH DE IE ENCONTRADO! Nota vinculada à: " + prop.getNome());
                        break;
                    }
                }
            }

            if (propriedadeDestino == null && propriedadeFallbackId != null) {
                propriedadeDestino = produtor.getPropriedades().stream()
                        .filter(p -> p.getId().equals(propriedadeFallbackId))
                        .findFirst()
                        .orElse(null);
            }

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

                // 1. Extrai o valor bruto do produto
                String valorProdutoStr = prod.getElementsByTagName("vProd").item(0).getTextContent();
                BigDecimal valorBruto = new BigDecimal(valorProdutoStr);

                // 2. Extrai os modificadores do valor (Descontos e Acréscimos)
                BigDecimal vDesc = BigDecimal.ZERO;
                if (prod.getElementsByTagName("vDesc").getLength() > 0) {
                    vDesc = new BigDecimal(prod.getElementsByTagName("vDesc").item(0).getTextContent());
                }

                BigDecimal vFrete = BigDecimal.ZERO;
                if (prod.getElementsByTagName("vFrete").getLength() > 0) {
                    vFrete = new BigDecimal(prod.getElementsByTagName("vFrete").item(0).getTextContent());
                }

                BigDecimal vSeg = BigDecimal.ZERO;
                if (prod.getElementsByTagName("vSeg").getLength() > 0) {
                    vSeg = new BigDecimal(prod.getElementsByTagName("vSeg").item(0).getTextContent());
                }

                BigDecimal vOutro = BigDecimal.ZERO;
                if (prod.getElementsByTagName("vOutro").getLength() > 0) {
                    vOutro = new BigDecimal(prod.getElementsByTagName("vOutro").item(0).getTextContent());
                }

                BigDecimal vIPI = BigDecimal.ZERO;
                if (det.getElementsByTagName("vIPI").getLength() > 0) {
                    vIPI = new BigDecimal(det.getElementsByTagName("vIPI").item(0).getTextContent());
                }

                // 3. Aplica a inteligência de mercado: Valor Líquido Real do Item
                BigDecimal valorProduto = valorBruto
                        .subtract(vDesc)
                        .add(vFrete)
                        .add(vSeg)
                        .add(vOutro)
                        .add(vIPI);

                // INTELIGÊNCIA TRIBUTÁRIA GLOBAL E DO CONTADOR
                boolean isDedutivel = tipo.equals("ENTRADA");

                if (tipo.equals("SAIDA")) {
                    // 1. Regra específica do Contador (prioridade máxima) - Cascata (8, 4 ou 2 dígitos)
                    if (ncmProduto != null && mapaRegras.containsKey(ncmProduto)) {
                        isDedutivel = mapaRegras.get(ncmProduto);
                    } else if (ncmProduto != null && ncmProduto.length() >= 4 && mapaRegras.containsKey(ncmProduto.substring(0, 4))) {
                        isDedutivel = mapaRegras.get(ncmProduto.substring(0, 4));
                    } else if (ncmProduto != null && ncmProduto.length() >= 2 && mapaRegras.containsKey(ncmProduto.substring(0, 2))) {
                        isDedutivel = mapaRegras.get(ncmProduto.substring(0, 2));
                    } else {
                        // 2. Regras Globais (SaaS)
                        RegraGlobal regraCfop = null;
                        if (cfop != null && !cfop.isEmpty()) {
                            regraCfop = mapaGlobaisCfop.get(cfop);
                            if (regraCfop == null && cfop.length() >= 3) {
                                regraCfop = mapaGlobaisCfop.get(cfop.substring(cfop.length() - 3));
                            }
                        }

                        RegraGlobal regraNcm = null;
                        if (ncmProduto != null && !ncmProduto.isEmpty()) {
                            regraNcm = mapaGlobaisNcm.get(ncmProduto);
                            if (regraNcm == null && ncmProduto.length() >= 4) {
                                regraNcm = mapaGlobaisNcm.get(ncmProduto.substring(0, 4));
                            }
                            if (regraNcm == null && ncmProduto.length() >= 2) {
                                regraNcm = mapaGlobaisNcm.get(ncmProduto.substring(0, 2));
                            }
                        }

                        // 3. Tomada de Decisão (Com correção de Simples Faturamento)
                        if (regraCfop != null && !regraCfop.getIsDedutivel()) {
                            // EXCEÇÃO: Se for Simples Faturamento (922) e o NCM for dedutível, o NCM tem prioridade!
                            if (cfop != null && cfop.endsWith("922") && regraNcm != null && regraNcm.getIsDedutivel()) {
                                isDedutivel = true;
                            } else {
                                // Para os demais bloqueios (Conserto, Armazém), o CFOP bloqueia a nota.
                                isDedutivel = false;
                            }
                        } else if (regraNcm != null) {
                            isDedutivel = regraNcm.getIsDedutivel();
                        } else if (regraCfop != null && regraCfop.getIsDedutivel()) {
                            isDedutivel = true;
                        }
                    }
                }

                if (CFOPS_DEVOLUCAO_VENDA.contains(cfop) || CFOPS_DEVOLUCAO_COMPRA.contains(cfop)) {
                    valorProduto = valorProduto.negate();
                    nomeProduto = "[DEVOLUÇÃO] " + nomeProduto;
                    isDedutivel = true; // A devolução sempre entra no Livro para estornar o saldo!
                }

                ItemNota item = new ItemNota();
                item.setDescricao(nomeProduto);
                item.setNcm(ncmProduto);
                item.setCfop(cfop);
                item.setValor(valorProduto);
                item.setIsDedutivel(isDedutivel);
                item.setNotaFiscal(nota);
                nota.getItens().add(item);

                valorTotalAjustado = valorTotalAjustado.add(valorProduto);
            }

            nota.setValorTotal(valorTotalAjustado);

            org.w3c.dom.NodeList dupNodes = doc.getElementsByTagName("dup");

            // trava do ignorarParcelas + regra de resíduo
            if (!ignorarParcelas && dupNodes.getLength() > 0) {
                // 1. Variável para somar tudo que foi encontrado nas faturas/duplicatas
                BigDecimal somaParcelas = BigDecimal.ZERO;

                for (int k = 0; k < dupNodes.getLength(); k++) {
                    org.w3c.dom.Element dup = (org.w3c.dom.Element) dupNodes.item(k);
                    String nDup = dup.getElementsByTagName("nDup").item(0).getTextContent();
                    String dVencStr = dup.getElementsByTagName("dVenc").item(0).getTextContent();
                    String vDupStr = dup.getElementsByTagName("vDup").item(0).getTextContent();

                    ParcelaNota parcela = new ParcelaNota();
                    parcela.setNumeroParcela(nDup);
                    parcela.setDataVencimento(LocalDate.parse(dVencStr));

                    BigDecimal vDup = new BigDecimal(vDupStr);
                    if (valorTotalAjustado.compareTo(BigDecimal.ZERO) < 0) {
                        vDup = vDup.negate();
                    }
                    parcela.setValor(vDup);
                    parcela.setNotaFiscal(nota);
                    nota.getParcelas().add(parcela);

                    // Acumula o valor absoluto para não ter problemas com devoluções
                    somaParcelas = somaParcelas.add(vDup.abs());
                }

                // CÁLCULO DO RESÍDUO (FUNRURAL / PAGAMENTO À VISTA)
                BigDecimal diferenca = valorTotalAjustado.abs().subtract(somaParcelas).setScale(2, java.math.RoundingMode.HALF_UP);

                // Se houver uma diferença financeira na nota (margem de erro de 5 centavos para arredondamento SEFAZ)
                if (diferenca.compareTo(new BigDecimal("0.05")) > 0) {
                    ParcelaNota parcelaResiduo = new ParcelaNota();
                    // "RET/AV" = Retenção (impostos) ou À Vista (parte paga fora do boleto)
                    parcelaResiduo.setNumeroParcela("RET/AV");
                    parcelaResiduo.setDataVencimento(dataEmissao); // Lançado na mesma data da nota

                    BigDecimal vResiduo = diferenca;
                    if (valorTotalAjustado.compareTo(BigDecimal.ZERO) < 0) {
                        vResiduo = vResiduo.negate(); // Mantém o sinal correto caso seja devolução
                    }

                    parcelaResiduo.setValor(vResiduo);
                    parcelaResiduo.setNotaFiscal(nota);
                    nota.getParcelas().add(parcelaResiduo);
                }

            } else {
                // Caso a nota não tenha aba de cobrança/fatura OU o contador tenha desmarcado o checkbox
                ParcelaNota parcela = new ParcelaNota();
                parcela.setNumeroParcela("001");
                parcela.setDataVencimento(dataEmissao);
                parcela.setValor(valorTotalAjustado);
                parcela.setNotaFiscal(nota);
                nota.getParcelas().add(parcela);
            }

            // CRIAR UM ESPELHO (JSON) DO XML ORIGINAL
            try {

                java.util.Map<String, Object> dadosOriginais = new java.util.HashMap<>();

                // Salva o estado puro dos itens
                java.util.List<java.util.Map<String, Object>> origItens = new java.util.ArrayList<>();
                for (ItemNota i : nota.getItens()) {
                    java.util.Map<String, Object> mapI = new java.util.HashMap<>();
                    mapI.put("descricao", i.getDescricao());
                    mapI.put("ncm", i.getNcm());
                    mapI.put("cfop", i.getCfop());
                    mapI.put("valor", i.getValor().toString());
                    mapI.put("isDedutivel", i.getIsDedutivel());
                    origItens.add(mapI);
                }
                dadosOriginais.put("itens", origItens);

                // Salva o estado puro das parcelas financeiras
                java.util.List<java.util.Map<String, Object>> origParcelas = new java.util.ArrayList<>();
                for (ParcelaNota p : nota.getParcelas()) {
                    java.util.Map<String, Object> mapP = new java.util.HashMap<>();
                    mapP.put("numeroParcela", p.getNumeroParcela());
                    mapP.put("dataVencimento", p.getDataVencimento().toString());
                    mapP.put("valor", p.getValor().toString());
                    origParcelas.add(mapP);
                }
                dadosOriginais.put("parcelas", origParcelas);

                nota.setJsonOriginal(mapper.writeValueAsString(dadosOriginais));
            } catch (Exception ex) {
                System.out.println("Aviso: Falha ao gerar JSON Original da nota: " + ex.getMessage());
            }

            return nota;
        } catch (Exception e) {
            return null;
        }
    }
    // ========================================================
    // IMPORTAÇÃO AUTOMATIZADA VIA CHAVE DE ACESSO (LEITOR)
    // ========================================================
    @Transactional
    public NotaFiscal importarNotaPorChave(Long produtorId, String chave, Long propriedadeId) {
        if (chave == null || chave.length() != 44) {
            throw new RuntimeException("Chave de acesso inválida. Deve conter exatamente 44 dígitos.");
        }

        if (notaRepository.findByChaveAcesso(chave).isPresent()) {
            throw new RuntimeException("Esta nota fiscal já foi importada anteriormente.");
        }

        Produtor produtor = produtorRepository.findById(produtorId)
                .orElseThrow(() -> new RuntimeException("Produtor não encontrado."));

        // Extrai dados básicos diretamente da estrutura padrão de 44 dígitos da chave NF-e
        // [UF(2)][AAMM(4)][CNPJ(14)][Mod(2)][Serie(3)][Nº(9)][TpEmis(4)][cDV(1)]
        String cnpjEmitente = chave.substring(6, 20);
        String numeroNota = String.valueOf(Integer.parseInt(chave.substring(25, 34)));
        String anoMes = "20" + chave.substring(2, 4) + "-" + chave.substring(4, 6) + "-01";

        NotaFiscal nota = new NotaFiscal();
        nota.setProdutor(produtor);
        nota.setChaveAcesso(chave);
        nota.setNumero(numeroNota);
        nota.setTipo("SAIDA");
        nota.setDataEmissao(LocalDate.parse(anoMes));
        nota.setEmpresaEnvolvida("Fornecedor CNPJ: " + cnpjEmitente);
        nota.setValorTotal(BigDecimal.ZERO); // O contador ajustará o valor posteriormente se necessário

        // Vincula a propriedade rural
        if (propriedadeId != null) {
            produtor.getPropriedades().stream()
                    .filter(p -> p.getId().equals(propriedadeId))
                    .findFirst()
                    .ifPresent(nota::setPropriedadeRural);
        } else if (!produtor.getPropriedades().isEmpty()) {
            nota.setPropriedadeRural(produtor.getPropriedades().get(0));
        }

        // Adiciona um item padrão para a nota não ficar vazia
        ItemNota item = new ItemNota();
        item.setDescricao("Importado via Leitor de Chave (Aguardando Detalhes)");
        item.setNcm("00000000");
        item.setCfop("1102");
        item.setValor(BigDecimal.ZERO);
        item.setIsDedutivel(true);
        item.setNotaFiscal(nota);
        nota.getItens().add(item);

        // Adiciona parcela padrão
        ParcelaNota parcela = new ParcelaNota();
        parcela.setNumeroParcela("001");
        parcela.setDataVencimento(nota.getDataEmissao());
        parcela.setValor(BigDecimal.ZERO);
        parcela.setNotaFiscal(nota);
        nota.getParcelas().add(parcela);

        return notaRepository.save(nota);
    }
}