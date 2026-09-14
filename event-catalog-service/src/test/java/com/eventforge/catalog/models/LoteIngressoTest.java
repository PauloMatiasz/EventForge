package com.eventforge.catalog.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class LoteIngressoTest {

  @Test
  void deveNascerComEstoqueIgualATotal(){
    LoteIngresso lote = loteComQuantidade(50);

    assertEquals(50, lote.getEstoque().quantidadeDisponivel());
    assertEquals(50, lote.getEstoque().quantidadeTotal());
  }

  @Test
  void reservarDiminuiDisponivel(){
    LoteIngresso lote = loteComQuantidade(50);

    lote.reservar(20);

    assertEquals(30, lote.getEstoque().quantidadeDisponivel());
  }

  @Test
  void naoDeveReservarMaisQueDisponivel(){
    LoteIngresso lote = loteComQuantidade(10);

    assertThrows(IllegalStateException.class, () -> lote.reservar(11));
  }

  @Test
  void devolverAumentaDisponivel(){
    LoteIngresso lote = loteComQuantidade(50);

    lote.reservar(20);
    lote.devolver(5);

    assertEquals(35, lote.getEstoque().quantidadeDisponivel());
  }

  @Test
  void deveEstarEsgotadoQuandoReservaTudo(){
    LoteIngresso lote = loteComQuantidade(10);

    lote.reservar(10);
    assertTrue(lote.esgotado());
  }

  @Test
  void naoDeveEstarEsgotadoComSaldo(){
    LoteIngresso lote = loteComQuantidade(10);

    lote.reservar(3);
    assertFalse(lote.esgotado());
  }

  @Test
  void valorTotalMultiplicaPrecoPelaQuantidade(){
    LoteIngresso lote = loteComQuantidade(50);

    Dinheiro total = lote.valorTotal(3);

    assertEquals(new BigDecimal("150.00"), total.valor());
  }

  private LoteIngresso loteComQuantidade(int quantidadeTotal) {
    Dinheiro preco = new Dinheiro(new BigDecimal("50.00"), "BRL");
    return new LoteIngresso(UUID.randomUUID(), "Inteira", preco, quantidadeTotal);
  }
}
