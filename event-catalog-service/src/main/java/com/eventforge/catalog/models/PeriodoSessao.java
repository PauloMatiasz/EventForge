package com.eventforge.catalog.models;

import java.time.Instant;

public record PeriodoSessao(Instant inicio, Instant fim) {

    public PeriodoSessao {
        if (inicio == null || fim == null) {
            throw new IllegalArgumentException("início e fim são obrigatórios");
        }
        if (!fim.isAfter(inicio)) {
            throw new IllegalArgumentException("fim deve ser após o início");
        }
    }

    public boolean contem(Instant instant){
      return !instant.isBefore(inicio) && !instant.isAfter(fim);
    }

    public boolean jaEncerrou(Instant referencia){
      return referencia.isAfter(fim);
    }
}
