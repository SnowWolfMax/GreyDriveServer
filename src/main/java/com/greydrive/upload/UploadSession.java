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
@Table(name = "upload_sessions")
public class UploadSession {

    public static final String STATUS_UPLOADING = "UPLOADING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false)
    private Long userId;

    private Long folderId;

    private Long targetFileId;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false)
    private long totalSize;

    @Column(length = 64)
    private String sha256;

    @Column(nullable = false)
    private int chunkSize;

    @Column(nullable = false)
    private int totalChunks;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;
}
