package br.com.agropops.api.service;

import br.com.agropops.api.dto.LancamentoDTO;
import br.com.agropops.api.dto.TotaisLivroCaixaDTO;
import br.com.agropops.api.model.ItemNota;
import br.com.agropops.api.model.LancamentoAvulso;
import br.com.agropops.api.model.NotaFiscal;
import br.com.agropops.api.model.ParcelaNota;
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

        // 1. Extrair Parcelas das Notas Fiscais (Regime de Caixa)
        List<NotaFiscal> notas = notaRepository.findByProdutorIdAndAnoVencimento(produtorId, ano);

        for (NotaFiscal nota : notas) {
            // Verifica se a nota possui pelo menos um item dedutível para colorir a linha de verde no Livro Caixa
            boolean temItemDedutivel = nota.getItens().stream().anyMatch(ItemNota::getIsDedutivel);

            for (ParcelaNota parcela : nota.getParcelas()) {
                // Filtra para garantir que a parcela realmente pertence ao ano pesquisado
                if (parcela.getDataVencimento().getYear() == ano) {
                    livroCaixa.add(new LancamentoDTO(
                            "NFE-" + parcela.getId(), // Usa o ID da Parcela
                            parcela.getDataVencimento(), // Usa a data do pagamento
                            "NF " + nota.getNumero(),
                            "NF " + nota.getNumero() + " - Parc " + parcela.getNumeroParcela() + " - " + nota.getEmpresaEnvolvida(), // Histórico limpo
                            "NFE",
                            nota.getTipo(),
                            parcela.getValor(),
                            temItemDedutivel,
                            nota.getId() // ID da nota para o Modal abrir
                    ));
                }
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
                    avulso.getIsDedutivel(),
                    null
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
                    true, // O prejuízo é 100% dedutível por lei
                    null
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