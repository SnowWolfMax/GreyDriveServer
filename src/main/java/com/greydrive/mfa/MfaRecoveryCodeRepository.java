package com.greydrive.mfa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, Long> {

    List<MfaRecoveryCode> findByUserId(Long userId);

    long countByUserIdAndUsedFalse(Long userId);

    void deleteByUserId(Long userId);
}
