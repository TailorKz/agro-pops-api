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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LivroCaixaService {

    @Autowired
    private NotaFiscalRepository notaRepository;

    @Autowired
    private LancamentoAvulsoRepository avulsoRepository;

    // --- LÓGICA DE PREJUÍZO DO ANO ANTERIOR ---
    private BigDecimal calcularPrejuizoAnoAnterior(Long produtorId, int anoAtual) {
        int anoAnterior = anoAtual - 1;

        BigDecimal receitasNfe = notaRepository.sumReceitasByProdutorAndAno(produtorId, anoAnterior);
        BigDecimal receitasAvulso = avulsoRepository.sumReceitasByProdutorAndAno(produtorId, anoAnterior);
        BigDecimal totalReceitas = receitasNfe.add(receitasAvulso);

        BigDecimal dedutivelNfe = notaRepository.sumDespesasDedutiveisNfeByProdutorAndAno(produtorId, anoAnterior);
        BigDecimal dedutivelAvulso = avulsoRepository.sumDespesasDedutiveisAvulsoByProdutorAndAno(produtorId, anoAnterior);
        BigDecimal totalDedutivel = dedutivelNfe.add(dedutivelAvulso);

        BigDecimal saldo = totalReceitas.subtract(totalDedutivel);

        // Se saldo < 0, a fazenda deu prejuízo fiscal no ano anterior.
        if (saldo.compareTo(BigDecimal.ZERO) < 0) {
            return saldo.abs(); // Retorna o valor positivo do prejuízo para ser somado às despesas do ano atual
        }
        return BigDecimal.ZERO;
    }

    public List<LancamentoDTO> buscarLivroCaixa(Long produtorId, int ano) {
        List<LancamentoDTO> livroCaixa = new ArrayList<>();

        // 1. Extrair Itens das Notas Fiscais
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

        // 2. Extrair Lançamentos Avulsos
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

        // 3. INTELIGÊNCIA FISCAL: Injetar Prejuízo Compensável do Ano Anterior
        BigDecimal prejuizoAnterior = calcularPrejuizoAnoAnterior(produtorId, ano);
        if (prejuizoAnterior.compareTo(BigDecimal.ZERO) > 0) {
            livroCaixa.add(new LancamentoDTO(
                    "SISTEMA-PREJ",
                    LocalDate.of(ano, 1, 1), // Entra sempre no dia 1º de Janeiro do ano corrente
                    "LCDPR " + (ano - 1),
                    "Prejuízo Fiscal Compensável do Exercício Anterior",
                    "SISTEMA", // <-- Nova Origem para identificar no Frontend
                    "SAIDA",
                    prejuizoAnterior,
                    true // O prejuízo é 100% dedutível por lei
            ));
        }

        // 4. Ordenar tudo por data (do mais antigo para o mais novo)
        livroCaixa.sort(Comparator.comparing(LancamentoDTO::data));

        return livroCaixa;
    }

    public TotaisLivroCaixaDTO calcularTotais(Long produtorId, int ano) {
        BigDecimal receitasNfe = notaRepository.sumReceitasByProdutorAndAno(produtorId, ano);
        BigDecimal receitasAvulso = avulsoRepository.sumReceitasByProdutorAndAno(produtorId, ano);
        BigDecimal totalReceitas = receitasNfe.add(receitasAvulso);

        BigDecimal dedutivelNfe = notaRepository.sumDespesasDedutiveisNfeByProdutorAndAno(produtorId, ano);
        BigDecimal dedutivelAvulso = avulsoRepository.sumDespesasDedutiveisAvulsoByProdutorAndAno(produtorId, ano);
        BigDecimal totalDedutivelCalculado = dedutivelNfe.add(dedutivelAvulso);

        BigDecimal saidasNfe = notaRepository.sumTotalSaidasNfeByProdutorAndAno(produtorId, ano);
        BigDecimal saidasAvulso = avulsoRepository.sumTotalSaidasAvulsoByProdutorAndAno(produtorId, ano);
        BigDecimal totalSaidasReais = saidasNfe.add(saidasAvulso);

        // INTELIGÊNCIA FISCAL: Aplicar Prejuízo Anterior nas contas
        BigDecimal prejuizoAnterior = calcularPrejuizoAnoAnterior(produtorId, ano);
        totalDedutivelCalculado = totalDedutivelCalculado.add(prejuizoAnterior);
        totalSaidasReais = totalSaidasReais.add(prejuizoAnterior); // Entra na trava de saídas também

        // Regra de Negócio: O total dedutível nunca pode ser maior que o total de saídas
        BigDecimal despesasEfetivas = totalDedutivelCalculado.min(totalSaidasReais);

        return new TotaisLivroCaixaDTO(totalReceitas, despesasEfetivas);
    }
}