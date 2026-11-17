package com.greydrive.oplog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "operation_logs")
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(length = 32)
    private String resourceType;

    private Long resourceId;

    @Column(length = 512)
    private String detail;

    @Column(length = 64)
    private String ip;

    @Column(length = 256)
    private String userAgent;

    @Column(nullable = false)
    private boolean success;

    @Column(nullable = false)
    private Instant createdAt;
}
