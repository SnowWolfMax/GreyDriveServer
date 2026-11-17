package com.greydrive.blob;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "blobs")
public class Blob {

    @Id
    @Column(length = 64)
    private String sha256;

    @Column(nullable = false)
    private long size;

    @Column(nullable = false, length = 255)
    private String storageKey;

    @Column(nullable = false)
    private int refCount;

    @Column(nullable = false)
    private Instant createdAt;
}
