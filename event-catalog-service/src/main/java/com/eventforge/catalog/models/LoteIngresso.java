package com.eventforge.catalog.models;

import java.util.Objects;
import java.util.UUID;

public final class LoteIngresso {

  private final UUID id;
  private final UUID sessaoId;
  private String nome;
  private Dinheiro preco;
  private Estoque estoque;

  public LoteIngresso(UUID sessaoId, String nome, Dinheiro preco, int quantidadeTotal) {
    this.id = UUID.randomUUID();
    this.sessaoId = Objects.requireNonNull(sessaoId, "Sessão id é obrigatorio");
    this.nome = Objects.requireNonNull(nome, "Nome é obrigatório");
    this.preco = Objects.requireNonNull(preco, "Preço é obrigatório");
    this.estoque = Estoque.inicial(quantidadeTotal);
  }

  public void reservar(int quantidade){
    this.estoque = estoque.reservar(quantidade);
  }

  public void devolver(int quantidade){
    this.estoque = estoque.devolver(quantidade);
  }

  public boolean esgotado(){
    return estoque.esgotado();
  }

  public Dinheiro valorTotal(int quantidade){
    return preco.multiplicar(quantidade);
  }

  public UUID getId() {
    return id;
  }

  public UUID getSessaoId() {
    return sessaoId;
  }

  public String getNome() {
    return nome;
  }

  public Dinheiro getPreco() {
    return preco;
  }

  public Estoque getEstoque() {
    return estoque;
  }
}
