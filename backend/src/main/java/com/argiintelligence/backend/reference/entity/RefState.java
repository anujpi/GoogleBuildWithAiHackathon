package com.argiintelligence.backend.reference.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ref_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefState {

    @Id
    private String stateId;
    private String label;
}
