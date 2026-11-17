package com.greydrive.recent;

import com.greydrive.file.CloudFile;
import com.greydrive.file.CloudFileRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class RecentService {

    private final RecentAccessRepository recentRepository;
    private final CloudFileRepository fileRepository;

    public RecentService(RecentAccessRepository recentRepository, CloudFileRepository fileRepository) {
        this.recentRepository = recentRepository;
        this.fileRepository = fileRepository;
    }

    @Transactional
    public void touch(long userId, long fileId) {
        Instant now = Instant.now();
        RecentAccess access = recentRepository.findByUserIdAndFileId(userId, fileId).orElseGet(() -> {
            RecentAccess created = new RecentAccess();
            created.setUserId(userId);
            created.setFileId(fileId);
            return created;
        });
        access.setAccessedAt(now);
        recentRepository.save(access);

        fileRepository.findByIdAndUserIdAndDeletedFalse(fileId, userId).ifPresent(file -> {
            file.setLastAccessedAt(now);
            fileRepository.save(file);
        });
    }

    @Transactional(readOnly = true)
    public Page<RecentView> list(long userId, Pageable pageable) {
        Page<RecentAccess> page = recentRepository.pageByUser(userId, pageable);
        List<RecentView> items = new ArrayList<>();
        for (RecentAccess access : page.getContent()) {
            CloudFile file = fileRepository.findByIdAndUserIdAndDeletedFalse(access.getFileId(), userId).orElse(null);
            if (file != null) {
                items.add(new RecentView(file.getId(), file.getName(), file.getSize(), file.getMimeType(),
                        access.getAccessedAt()));
            }
        }
        return new PageImpl<>(items, pageable, page.getTotalElements());
    }

    @Transactional
    public void clear(long userId) {
        recentRepository.deleteByUserId(userId);
    }

    public record RecentView(Long fileId, String name, long size, String mimeType, Instant accessedAt) {
    }
}
