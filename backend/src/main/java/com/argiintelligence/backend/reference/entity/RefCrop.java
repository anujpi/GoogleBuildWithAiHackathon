package com.argiintelligence.backend.reference.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ref_crop")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefCrop {

    @Id
    private String cropId;
    private String label;
}
