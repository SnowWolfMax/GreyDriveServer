package com.greydrive.upload;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "upload_chunks")
public class UploadChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String uploadId;

    @Column(nullable = false)
    private int chunkIndex;

    @Column(nullable = false)
    private long size;

    @Column(length = 64)
    private String sha256;

    @Column(nullable = false)
    private Instant createdAt;
}
