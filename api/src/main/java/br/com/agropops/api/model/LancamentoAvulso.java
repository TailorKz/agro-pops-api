package br.com.agropops.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "lancamentos_avulsos")
public class LancamentoAvulso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate data;
    private String tipoDocumento; // "1"-Nota, "2"-Fatura, "3"-Recibo, "4"-Contrato, "5"-Folha, "6"-Outros
    private String documento;
    private String cpfCnpjParticipante;
    private String historico;
    private String tipo; // "ENTRADA" ou "SAIDA"
    private BigDecimal valor;
    private Boolean isDedutivel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produtor_id")
    private Produtor produtor;

    // Getters e Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    public String getTipoDocumento() { return tipoDocumento; }
    public void setTipoDocumento(String tipoDocumento) { this.tipoDocumento = tipoDocumento; }
    public String getDocumento() { return documento; }
    public void setDocumento(String documento) { this.documento = documento; }
    public String getCpfCnpjParticipante() { return cpfCnpjParticipante; }
    public void setCpfCnpjParticipante(String cpfCnpjParticipante) { this.cpfCnpjParticipante = cpfCnpjParticipante; }
    public String getHistorico() { return historico; }
    public void setHistorico(String historico) { this.historico = historico; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public Boolean getIsDedutivel() { return isDedutivel; }
    public void setIsDedutivel(Boolean isDedutivel) { this.isDedutivel = isDedutivel; }
    public Produtor getProdutor() { return produtor; }
    public void setProdutor(Produtor produtor) { this.produtor = produtor; }
}