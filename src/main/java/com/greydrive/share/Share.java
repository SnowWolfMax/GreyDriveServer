package com.greydrive.share;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "shares")
public class Share {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String PERM_VIEW = "VIEW";
    public static final String PERM_DOWNLOAD = "DOWNLOAD";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String token;

    @Column(nullable = false)
    private Long userId;

    private Long fileId;

    private Long folderId;

    @Column(length = 100)
    private String codeHash;

    @Column(nullable = false, length = 16)
    private String permission;

    @Column(nullable = false, length = 16)
    private String status;

    private Instant expiresAt;

    @Column(nullable = false)
    private long downloadCount;

    @Column(nullable = false)
    private Instant createdAt;
}
