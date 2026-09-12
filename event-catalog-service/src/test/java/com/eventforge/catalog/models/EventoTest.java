package com.eventforge.catalog.models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.eventforge.catalog.models.Evento;
import com.eventforge.catalog.models.StatusEvento;

class EventoTest {
  @Test
  void deveNascerEmRascunho(){
    Evento evento = new Evento("Show de Rock", "Descrição");

    assertEquals(StatusEvento.RASCUNHO, evento.getStatus());
  }

  @Test
  void devePublicarQuandoEstaEmRascunho(){
    Evento evento = new Evento("Show de Rock", "Descrição");
    evento.publicar();

    assertEquals(StatusEvento.PUBLICADO, evento.getStatus());
  }

  @Test
  void naoDevePublicarDuasVezes(){
    Evento evento = new Evento("Show de Rock", "Descrição");
    evento.publicar();

    assertThrows(IllegalStateException.class, evento::publicar);
  }

  @Test
  void deveCancelar(){
  Evento evento = new Evento("Show de Rock", "Descrição");
  evento.cancelar();

  assertEquals(StatusEvento.CANCELADO, evento.getStatus());
  }
}
