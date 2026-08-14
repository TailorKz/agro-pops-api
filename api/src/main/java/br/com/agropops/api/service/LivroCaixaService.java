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
import java.math.RoundingMode;
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

        // 1. Extrair Parcelas das Notas Fiscais (Regime de Caixa)
        List<NotaFiscal> notas = notaRepository.findByProdutorIdAndAnoVencimento(produtorId, ano);
        for (NotaFiscal nota : notas) {
            boolean temItemDedutivel = nota.getItens().stream().anyMatch(ItemNota::getIsDedutivel);

            // CÁLCULO DA PROPORÇÃO DEDUTÍVEL DA NOTA
            BigDecimal totalItens = nota.getItens().stream().map(ItemNota::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalDedutivel = nota.getItens().stream().filter(ItemNota::getIsDedutivel).map(ItemNota::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal percDedutivel = BigDecimal.ZERO;
            if (totalItens.compareTo(BigDecimal.ZERO) != 0) {
                // Aumentamos a precisão para 10 casas para evitar dízimas periódicas
                percDedutivel = totalDedutivel.divide(totalItens, 10, RoundingMode.HALF_UP);
            }

            // Ordena cronologicamente para a última parcela receber o ajuste de centavos corretamente
            List<ParcelaNota> parcelasOrdenadas = nota.getParcelas().stream()
                    .sorted(Comparator.comparing(ParcelaNota::getDataVencimento))
                    .toList();

            BigDecimal somaParcelas = parcelasOrdenadas.stream().map(ParcelaNota::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalDedutivelEsperado = somaParcelas.multiply(percDedutivel).setScale(2, RoundingMode.HALF_UP);

            BigDecimal somaDedutivelAcumulada = BigDecimal.ZERO;

            for (int i = 0; i < parcelasOrdenadas.size(); i++) {
                ParcelaNota parcela = parcelasOrdenadas.get(i);

                BigDecimal valorDedutivelParcela;
                if (i == parcelasOrdenadas.size() - 1) {
                    // A MÁGICA: A última parcela absorve os centavos (Garante que a soma bata exatamente com o montante esperado)
                    valorDedutivelParcela = totalDedutivelEsperado.subtract(somaDedutivelAcumulada);
                } else {
                    valorDedutivelParcela = parcela.getValor().multiply(percDedutivel).setScale(2, RoundingMode.HALF_UP);
                    somaDedutivelAcumulada = somaDedutivelAcumulada.add(valorDedutivelParcela);
                }

                if (parcela.getDataVencimento().getYear() == ano) {
                    livroCaixa.add(new LancamentoDTO(
                            "NFE-" + parcela.getId(),
                            parcela.getDataVencimento(),
                            "NF " + nota.getNumero(),
                            "NF " + nota.getNumero() + " - Parc " + parcela.getNumeroParcela() + "/" + parcelasOrdenadas.size() + " - " + nota.getEmpresaEnvolvida(),
                            "NFE",
                            nota.getTipo(),
                            parcela.getValor(),
                            temItemDedutivel,
                            nota.getId(),
                            percDedutivel.multiply(new BigDecimal("100")).setScale(1, RoundingMode.HALF_UP),
                            valorDedutivelParcela,
                            nota.getConferida()
                    ));
                }
            }
        }

        // 2. Extrair Lançamentos Manuais (Avulsos)
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
                    null,
                    avulso.getIsDedutivel() ? new BigDecimal("100.0") : BigDecimal.ZERO,
                    avulso.getIsDedutivel() ? avulso.getValor() : BigDecimal.ZERO,
                    true
            ));
        }

        // 3. Ordenar do mais antigo para o mais novo
        livroCaixa.sort(Comparator.comparing(LancamentoDTO::data));

        return livroCaixa;
    }

    public TotaisLivroCaixaDTO calcularTotais(Long produtorId, int ano) {
        List<NotaFiscal> notas = notaRepository.findByProdutorIdAndAnoVencimento(produtorId, ano);
        BigDecimal receitasNfe = BigDecimal.ZERO;
        BigDecimal dedutivelNfe = BigDecimal.ZERO;
        BigDecimal saidasNfe = BigDecimal.ZERO;

        for (NotaFiscal nota : notas) {
            BigDecimal totalItens = nota.getItens().stream().map(ItemNota::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalDedutivel = nota.getItens().stream().filter(ItemNota::getIsDedutivel).map(ItemNota::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal percDedutivel = BigDecimal.ZERO;
            if (totalItens.compareTo(BigDecimal.ZERO) != 0) {
                percDedutivel = totalDedutivel.divide(totalItens, 10, RoundingMode.HALF_UP);
            }

            List<ParcelaNota> parcelasOrdenadas = nota.getParcelas().stream()
                    .sorted(Comparator.comparing(ParcelaNota::getDataVencimento))
                    .toList();

            BigDecimal somaParcelas = parcelasOrdenadas.stream().map(ParcelaNota::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalDedutivelEsperado = somaParcelas.multiply(percDedutivel).setScale(2, RoundingMode.HALF_UP);

            BigDecimal somaDedutivelAcumulada = BigDecimal.ZERO;

            for (int i = 0; i < parcelasOrdenadas.size(); i++) {
                ParcelaNota parcela = parcelasOrdenadas.get(i);

                BigDecimal valorDedutivelParcela;
                if (i == parcelasOrdenadas.size() - 1) {
                    valorDedutivelParcela = totalDedutivelEsperado.subtract(somaDedutivelAcumulada);
                } else {
                    valorDedutivelParcela = parcela.getValor().multiply(percDedutivel).setScale(2, RoundingMode.HALF_UP);
                    somaDedutivelAcumulada = somaDedutivelAcumulada.add(valorDedutivelParcela);
                }

                if (parcela.getDataVencimento().getYear() == ano) {
                    if (nota.getTipo().equals("ENTRADA")) {
                        receitasNfe = receitasNfe.add(parcela.getValor());
                    } else {
                        saidasNfe = saidasNfe.add(parcela.getValor());
                        dedutivelNfe = dedutivelNfe.add(valorDedutivelParcela);
                    }
                }
            }
        }

        List<Object[]> resumoAvulso = avulsoRepository.getResumoFinanceiroAno(produtorId, ano);
        BigDecimal receitasAvulso = (BigDecimal) resumoAvulso.get(0)[0];
        BigDecimal saidasAvulso = (BigDecimal) resumoAvulso.get(0)[1];
        BigDecimal dedutivelAvulso = (BigDecimal) resumoAvulso.get(0)[2];

        BigDecimal totalReceitas = receitasNfe.add(receitasAvulso);
        BigDecimal totalDedutivelCalculado = dedutivelNfe.add(dedutivelAvulso);
        BigDecimal totalSaidasReais = saidasNfe.add(saidasAvulso);

        BigDecimal despesasEfetivas = totalDedutivelCalculado.min(totalSaidasReais);
        return new TotaisLivroCaixaDTO(totalReceitas, despesasEfetivas);
    }
}