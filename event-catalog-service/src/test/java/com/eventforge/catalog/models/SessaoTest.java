package com.eventforge.catalog.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.eventforge.catalog.models.PeriodoSessao;
import com.eventforge.catalog.models.Sessao;
import com.eventforge.catalog.models.StatusSessao;

class SessaoTest {

  @Test
  void deveNascerAgendada(){
    Sessao sessao = sessaoFutura();

    assertEquals(StatusSessao.AGENDADA, sessao.getStatus());
  }

  @Test
  void deveCancelar(){
    Sessao sessao = sessaoFutura();
    sessao.cancelar();

    assertEquals(StatusSessao.CANCELADA, sessao.getStatus());
  }

  @Test
  void naoDeveCancelarDuasVezes(){
    Sessao sessao = sessaoFutura();
    sessao.cancelar();

    assertThrows(IllegalStateException.class, sessao::cancelar);
  }

  @Test
  void deveAcusarQueJaEncerrouQuandoPeriodoEstaNoPassado(){
    Instant inicio = Instant.now().minus(3, ChronoUnit.HOURS);
    Instant fim = Instant.now().minus(1, ChronoUnit.HOURS);

    Sessao sessao = new Sessao(UUID.randomUUID(), new PeriodoSessao(inicio, fim), "Arena SP");
    assertTrue(sessao.jaEncerrou());
  }

  @Test
  void naoDeveEstarEmAndamentoQuandoPeriodoEstaNoFuturo(){
    Sessao sessao = sessaoFutura();

    assertFalse(sessao.emAndamento());
  }

  private Sessao sessaoFutura(){
    Instant inicio = Instant.now().plus(1, ChronoUnit.HOURS);
    Instant fim = Instant.now().plus(3, ChronoUnit.HOURS);
    return new Sessao(UUID.randomUUID(), new PeriodoSessao(inicio, fim), "Arena SP");
  }
}
