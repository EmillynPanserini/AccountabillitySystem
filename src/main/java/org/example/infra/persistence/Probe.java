package org.example.infra.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "probe", schema = "t05_app")
class Probe {
    @Id
    private Long id;

    @Column(nullable = false, length = 80)
    private String descricao;
}
