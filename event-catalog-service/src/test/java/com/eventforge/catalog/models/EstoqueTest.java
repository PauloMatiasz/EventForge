package com.eventforge.catalog.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EstoqueTest {
  @Test
  void inicialDeixaDisponivelIgualATotal(){
    Estoque estoque = Estoque.inicial(100);

    assertEquals(100, estoque.quantidadeTotal());
    assertEquals(100, estoque.quantidadeDisponivel());
  }

  @Test
  void reservarDiminuiDisponivel(){
    Estoque estoque = Estoque.inicial(100);

    Estoque atualizado = estoque.reservar(30);

    assertEquals(70, atualizado.quantidadeDisponivel());
    assertEquals(100, atualizado.quantidadeTotal());
  }

  @Test
  void naoDeveReservarMaisQueODisponivel(){
    Estoque estoque = Estoque.inicial(10);

    assertThrows(IllegalStateException.class, () -> estoque.reservar(11));
  }

  @Test
  void devolverAumentaDisponivel(){
    Estoque estoque = Estoque.inicial(100).reservar(40);

    Estoque atualizado = estoque.devolver(10);

    assertEquals(70, atualizado.quantidadeDisponivel());
  }

  @Test
  void deveEstarEsgotadoQuandoDisponivelChegaAZero(){
    Estoque estoque = Estoque.inicial(5).reservar(5);

    assertTrue(estoque.esgotado());
  }

  @Test
  void naoDeveEstarEsgotadoComDisponivelPositivo(){
    Estoque estoque = Estoque.inicial(5).reservar(2);

    assertFalse(estoque.esgotado());
  }
}
