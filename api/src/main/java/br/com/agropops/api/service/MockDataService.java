package br.com.agropops.api.service;

import br.com.agropops.api.model.ItemNota;
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
            nota.setDataEmissao(LocalDate.now().minusDays(random.nextInt(60)));
            nota.setTipo("ENTRADA");

            BigDecimal valorTotal = BigDecimal.valueOf(5000 + random.nextInt(15000));
            nota.setValorTotal(valorTotal);
            nota.setEmpresaEnvolvida("Cooperativa Agro " + i);
            nota.setProdutor(produtor);

            // Criar o item da nota (A nova arquitetura 2.0)
            ItemNota item = new ItemNota();
            item.setDescricao("Venda de Produção Agrícola (Lote " + i + ")");
            item.setNcm("10063021");
            item.setValor(valorTotal);
            item.setIsDedutivel(false);
            item.setNotaFiscal(nota);

            // Adiciona o item dentro da lista de itens da nota
            nota.getItens().add(item);

            notas.add(nota);
        }

        // 2. Gerar 10 notas de SAIDA (Despesas/Insumos)
        for (int i = 1; i <= 10; i++) {
            NotaFiscal nota = new NotaFiscal();
            nota.setNumero("SAI-" + (2000 + i));
            nota.setDataEmissao(LocalDate.now().minusDays(random.nextInt(60)));
            nota.setTipo("SAIDA");

            BigDecimal valorTotal = BigDecimal.valueOf(1000 + random.nextInt(5000));
            nota.setValorTotal(valorTotal);
            nota.setEmpresaEnvolvida("Loja de Insumos " + i);
            nota.setProdutor(produtor);

            // Criar o item da nota
            ItemNota item = new ItemNota();
            item.setDescricao("Compra de Insumos/Sementes/Defensivos " + i);
            item.setNcm("31051000");
            item.setValor(valorTotal);
            item.setIsDedutivel(random.nextBoolean());
            item.setNotaFiscal(nota);

            // Adiciona o item dentro da lista de itens da nota
            nota.getItens().add(item);

            notas.add(nota);
        }

        // Grava as 20 notas (e os seus itens) na base de dados de uma só vez
        notaFiscalRepository.saveAll(notas);
        System.out.println("✅ 20 Notas Fiscais Mockadas geradas para o produtor: " + produtor.getNome());
    }
}