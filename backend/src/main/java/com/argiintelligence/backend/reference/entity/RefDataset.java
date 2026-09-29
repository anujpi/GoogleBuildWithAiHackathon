package com.argiintelligence.backend.reference.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Entity
@Table(name = "ref_dataset")
@IdClass(RefDataset.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RefDataset {

    @Id
    private String source;
    @Id
    private String datasetVersion;
    /** Latest period the dataset covers: "YYYY", "YYYY-MM" or "YYYY-MM-DD". */
    private String dataThrough;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private String source;
        private String datasetVersion;
    }
}
