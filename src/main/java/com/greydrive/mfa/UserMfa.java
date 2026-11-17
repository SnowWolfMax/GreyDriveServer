package com.greydrive.mfa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "user_mfa")
public class UserMfa {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 64)
    private String secret;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant confirmedAt;
}
