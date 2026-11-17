package com.greydrive.file;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "files")
public class CloudFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    private Long folderId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 32)
    private String ext;

    @Column(length = 128)
    private String mimeType;

    @Column(nullable = false)
    private long size;

    @Column(length = 64)
    private String sha256;

    @Column(nullable = false)
    private int currentVersion;

    @Column(nullable = false)
    private boolean favorite;

    @Column(nullable = false)
    private boolean deleted;

    private Instant deletedAt;

    private Instant lastAccessedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;
}
