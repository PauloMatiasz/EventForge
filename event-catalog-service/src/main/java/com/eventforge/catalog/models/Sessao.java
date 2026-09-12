package com.eventforge.catalog.models;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Sessao {
  private final UUID id;
  private final UUID eventoId;
  private String local;
  private PeriodoSessao periodo;
  private StatusSessao status;

  public Sessao(UUID eventoId, PeriodoSessao periodo, String local){
    this.id = UUID.randomUUID();
    this.eventoId = Objects.requireNonNull(eventoId, "eventoId é obrigatório");
    this.periodo = Objects.requireNonNull(periodo, "Periodo é obrigatório");
    this.local = Objects.requireNonNull(local, "Local é obrigatório");
    this.status = StatusSessao.AGENDADA;
  }

  public void cancelar(){
    if (status == StatusSessao.CANCELADA){
      throw new IllegalStateException("sessão já cancelada");

    }
    this.status = StatusSessao.CANCELADA;
  }

  public boolean jaEncerrou(){
    return periodo.jaEncerrou(Instant.now());
  }

  public boolean emAndamento(){
    return periodo.contem(Instant.now());
  }

  public Object getStatus() {
    return status;
  }
}
