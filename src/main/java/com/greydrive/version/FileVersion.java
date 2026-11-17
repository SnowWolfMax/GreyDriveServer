package com.greydrive.version;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "file_versions")
public class FileVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long fileId;

    @Column(nullable = false)
    private int versionNo;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(nullable = false)
    private long size;

    @Column(length = 128)
    private String mimeType;

    @Column(length = 255)
    private String note;

    private Long createdBy;

    @Column(nullable = false)
    private Instant createdAt;
}
