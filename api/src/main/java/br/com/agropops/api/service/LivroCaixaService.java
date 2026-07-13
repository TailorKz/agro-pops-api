package br.com.agropops.api.service;

import br.com.agropops.api.dto.LancamentoDTO;
import br.com.agropops.api.dto.TotaisLivroCaixaDTO;
import br.com.agropops.api.model.ItemNota;
import br.com.agropops.api.model.LancamentoAvulso;
import br.com.agropops.api.model.NotaFiscal;
import br.com.agropops.api.repository.LancamentoAvulsoRepository;
import br.com.agropops.api.repository.NotaFiscalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LivroCaixaService {

    @Autowired
    private NotaFiscalRepository notaRepository;

    @Autowired
    private LancamentoAvulsoRepository avulsoRepository;

    public List<LancamentoDTO> buscarLivroCaixa(Long produtorId, int ano) {
        List<LancamentoDTO> livroCaixa = new ArrayList<>();

        // 1. Extrair Itens das Notas Fiscais (Fonte 1)
        List<NotaFiscal> notas = notaRepository.findByProdutorIdAndAnoWithItens(produtorId, ano);
        for (NotaFiscal nota : notas) {
            for (ItemNota item : nota.getItens()) {
                livroCaixa.add(new LancamentoDTO(
                        "NFE-" + item.getId(),
                        nota.getDataEmissao(),
                        "NF " + nota.getNumero(),
                        item.getDescricao(),
                        "NFE",
                        nota.getTipo(),
                        item.getValor(),
                        item.getIsDedutivel()
                ));
            }
        }

        // 2. Extrair Lançamentos Avulsos (Fonte 2)
        List<LancamentoAvulso> avulsos = avulsoRepository.findByProdutorIdAndAno(produtorId, ano);
        for (LancamentoAvulso avulso : avulsos) {
            livroCaixa.add(new LancamentoDTO(
                    "AVU-" + avulso.getId(),
                    avulso.getData(),
                    avulso.getDocumento(),
                    avulso.getHistorico(),
                    "AVULSO",
                    avulso.getTipo(),
                    avulso.getValor(),
                    avulso.getIsDedutivel()
            ));
        }

        // 3. Ordenar tudo por data (do mais antigo para o mais novo)
        livroCaixa.sort(Comparator.comparing(LancamentoDTO::data));

        return livroCaixa;
    }

    public TotaisLivroCaixaDTO calcularTotais(Long produtorId, int ano) {
        // 1. Receitas Totais (NFE + Avulso)
        BigDecimal receitasNfe = notaRepository.sumReceitasByProdutorAndAno(produtorId, ano);
        BigDecimal receitasAvulso = avulsoRepository.sumReceitasByProdutorAndAno(produtorId, ano);
        BigDecimal totalReceitas = receitasNfe.add(receitasAvulso);

        // 2. Dedutibilidades Somadas (NFE + Avulso)
        BigDecimal dedutivelNfe = notaRepository.sumDespesasDedutiveisNfeByProdutorAndAno(produtorId, ano);
        BigDecimal dedutivelAvulso = avulsoRepository.sumDespesasDedutiveisAvulsoByProdutorAndAno(produtorId, ano);
        BigDecimal totalDedutivelCalculado = dedutivelNfe.add(dedutivelAvulso);

        // 3. Saídas Totais Reais para Trava Matemática (NFE + Avulso)
        BigDecimal saidasNfe = notaRepository.sumTotalSaidasNfeByProdutorAndAno(produtorId, ano);
        BigDecimal saidasAvulso = avulsoRepository.sumTotalSaidasAvulsoByProdutorAndAno(produtorId, ano);
        BigDecimal totalSaidasReais = saidasNfe.add(saidasAvulso);

        // 4. Regra de Negócio: O total dedutível nunca pode ser maior que o total de saídas (Math.min)
        BigDecimal despesasEfetivas = totalDedutivelCalculado.min(totalSaidasReais);

        return new TotaisLivroCaixaDTO(totalReceitas, despesasEfetivas);
    }
}