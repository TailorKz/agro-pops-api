package br.com.agropops.api.service;

import br.com.agropops.api.model.NotaFiscal;
import br.com.agropops.api.model.Produtor;
import br.com.agropops.api.repository.NotaFiscalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class MockDataService {

    @Autowired
    private NotaFiscalRepository notaFiscalRepository;

    public void gerarNotasFalsasParaProdutor(Produtor produtor) {
        List<NotaFiscal> notas = new ArrayList<>();
        Random random = new Random();

        // 1. Gerar 10 notas de ENTRADA (Receitas/Vendas)
        for (int i = 1; i <= 10; i++) {
            NotaFiscal nota = new NotaFiscal();
            nota.setNumero("ENT-" + (1000 + i));
            // Gera datas nos últimos 60 dias
            nota.setDataEmissao(LocalDate.now().minusDays(random.nextInt(60)));
            nota.setTipo("ENTRADA");
            // Valores entre 5.000 e 20.000
            nota.setValor(BigDecimal.valueOf(5000 + random.nextInt(15000)));
            nota.setIsDedutivel(false);
            nota.setDescricao("Venda de Produção Agrícola (Lote " + i + ")");
            nota.setProdutor(produtor);
            notas.add(nota);
        }

        // 2. Gerar 10 notas de SAIDA (Despesas/Insumos)
        for (int i = 1; i <= 10; i++) {
            NotaFiscal nota = new NotaFiscal();
            nota.setNumero("SAI-" + (2000 + i));
            nota.setDataEmissao(LocalDate.now().minusDays(random.nextInt(60)));
            nota.setTipo("SAIDA");
            // Valores entre 1.000 e 6.000
            nota.setValor(BigDecimal.valueOf(1000 + random.nextInt(5000)));
            // Mistura aleatoriamente o que é dedutível no Livro Caixa e o que não é
            nota.setIsDedutivel(random.nextBoolean());
            nota.setDescricao("Compra de Insumos/Sementes/Defensivos " + i);
            nota.setProdutor(produtor);
            notas.add(nota);
        }

        // Grava as 20 notas na base de dados de uma só vez para ser extremamente rápido!
        notaFiscalRepository.saveAll(notas);
        System.out.println("🚜 20 Notas Fiscais Mockadas geradas para o produtor: " + produtor.getNome());
    }
}