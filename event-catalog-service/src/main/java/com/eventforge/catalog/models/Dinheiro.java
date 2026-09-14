package com.eventforge.catalog.models;

import java.math.BigDecimal;

public record Dinheiro(BigDecimal valor, String moeda) {

  public Dinheiro{

    if (valor == null || valor.signum() < 0){
      throw new IllegalArgumentException("O valor precisa ser positivo e não nulo");
    }
    if (moeda == null || moeda.isBlank()){
      throw new IllegalArgumentException("Moeda é obrigatória");
    }
  }

  public Dinheiro multiplicar (int quantidade){
    return new Dinheiro(valor.multiply(BigDecimal.valueOf(quantidade)), moeda);
  }
}
