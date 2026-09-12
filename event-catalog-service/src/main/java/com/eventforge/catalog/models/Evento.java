package com.eventforge.catalog.models;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Evento {

    private final UUID id;
    private String titulo;
    private String descricao;
    private StatusEvento status;
    private final Instant criadoEm;

    public Evento(String titulo, String descricao) {
        this.id = UUID.randomUUID();
        this.titulo = Objects.requireNonNull(titulo, "título é obrigatório");
        this.descricao = descricao;
        this.status = StatusEvento.RASCUNHO;
        this.criadoEm = Instant.now();
    }

    public void publicar() {
        if (status != StatusEvento.RASCUNHO) {
            throw new IllegalStateException("só é possível publicar um evento em RASCUNHO");
        }
        this.status = StatusEvento.PUBLICADO;
    }

    public void cancelar() {
        if (status == StatusEvento.CANCELADO) {
            throw new IllegalStateException("evento já está cancelado");
        }
        this.status = StatusEvento.CANCELADO;
    }

    public UUID getId() { return id; }
    public String getTitulo() { return titulo; }
    public String getDescricao() { return descricao; }
    public StatusEvento getStatus() { return status; }
    public Instant getCriadoEm() { return criadoEm; }
}
