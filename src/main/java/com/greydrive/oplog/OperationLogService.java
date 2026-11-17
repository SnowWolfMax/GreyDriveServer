package com.greydrive.oplog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);

    private final OperationLogRepository repository;

    public OperationLogService(OperationLogRepository repository) {
        this.repository = repository;
    }

    @Async("opLogExecutor")
    public void record(Long userId, String action, String resourceType, Long resourceId,
                       String detail, String ip, String userAgent, boolean success) {
        try {
            OperationLog entry = new OperationLog();
            entry.setUserId(userId);
            entry.setAction(action);
            entry.setResourceType(resourceType);
            entry.setResourceId(resourceId);
            entry.setDetail(detail);
            entry.setIp(ip);
            entry.setUserAgent(userAgent);
            entry.setSuccess(success);
            entry.setCreatedAt(Instant.now());
            repository.save(entry);
        } catch (Exception e) {
            log.warn("写入操作日志失败 action={}", action, e);
        }
    }

    @Transactional(readOnly = true)
    public Page<OperationLog> search(Long userId, String action, String resourceType,
                                     Instant from, Instant to, Pageable pageable) {
        Specification<OperationLog> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (resourceType != null && !resourceType.isBlank()) {
                predicates.add(cb.equal(root.get("resourceType"), resourceType));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return repository.findAll(spec, pageable);
    }
}
