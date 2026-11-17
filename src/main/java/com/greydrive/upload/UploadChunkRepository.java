package com.greydrive.upload;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UploadChunkRepository extends JpaRepository<UploadChunk, Long> {

    List<UploadChunk> findByUploadIdOrderByChunkIndexAsc(String uploadId);

    Optional<UploadChunk> findByUploadIdAndChunkIndex(String uploadId, int chunkIndex);

    long countByUploadId(String uploadId);

    void deleteByUploadId(String uploadId);
}
