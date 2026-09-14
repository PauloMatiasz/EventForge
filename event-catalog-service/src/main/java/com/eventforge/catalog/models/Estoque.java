package com.eventforge.catalog.models;

public record Estoque(int quantidadeTotal, int quantidadeDisponivel) {

  public Estoque{

    if (quantidadeTotal < 0){
      throw new IllegalArgumentException("Quantidade total não pode ser negativa");
    }
    if (quantidadeDisponivel < 0){
      throw new IllegalArgumentException("Quantidade disponivel não pode ser negativa");
    }
    if (quantidadeDisponivel > quantidadeTotal){
      throw new IllegalArgumentException("Quantidade disponivel não pode ser maior que quantidade total");
    }
  }

  public static Estoque inicial(int quantidadeTotal){

    return new Estoque(quantidadeTotal, quantidadeTotal);
  }

  public Estoque reservar(int quantidade){

    if (quantidade < 0){
      throw new IllegalArgumentException("Quantidade deve ser positiva");
    }
    if (quantidade > quantidadeDisponivel){
      throw new IllegalStateException("Quantidade solicitada maior que quantidade disponivel");
    }
    return new Estoque ( quantidadeTotal, quantidadeDisponivel - quantidade);
  }

  public Estoque devolver(int quantidade){

    if (quantidade <=0){
      throw new IllegalArgumentException("Quantidade a devolver deve ser positiva");
    }
    return new Estoque(quantidadeTotal, quantidadeDisponivel + quantidade);
  }

  public boolean esgotado(){
    return quantidadeDisponivel == 0;
  }
}
