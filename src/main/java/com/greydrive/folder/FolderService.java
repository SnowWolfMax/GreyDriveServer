package com.greydrive.folder;

import com.greydrive.common.BizException;
import com.greydrive.common.ErrorCode;
import com.greydrive.file.CloudFile;
import com.greydrive.file.CloudFileRepository;
import com.greydrive.folder.FolderDtos.Breadcrumb;
import com.greydrive.folder.FolderDtos.BreadcrumbItem;
import com.greydrive.folder.FolderDtos.FolderView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final CloudFileRepository fileRepository;

    public FolderService(FolderRepository folderRepository, CloudFileRepository fileRepository) {
        this.folderRepository = folderRepository;
        this.fileRepository = fileRepository;
    }

    @Transactional
    public FolderView create(long userId, Long parentId, String name) {
        String clean = validateName(name);
        if (parentId != null) {
            requireFolder(parentId, userId);
        }
        ensureUnique(userId, parentId, clean, null);
        Instant now = Instant.now();
        Folder folder = new Folder();
        folder.setUserId(userId);
        folder.setParentId(parentId);
        folder.setName(clean);
        folder.setCreatedAt(now);
        folder.setUpdatedAt(now);
        return toView(folderRepository.save(folder));
    }

    @Transactional(readOnly = true)
    public Page<FolderView> list(long userId, Long parentId, Pageable pageable) {
        Page<Folder> page = parentId == null
                ? folderRepository.findByUserIdAndParentIdIsNullAndDeletedFalse(userId, pageable)
                : folderRepository.findByUserIdAndParentIdAndDeletedFalse(userId, parentId, pageable);
        return page.map(FolderService::toView);
    }

    @Transactional(readOnly = true)
    public FolderView get(long userId, long id) {
        return toView(requireFolder(id, userId));
    }

    @Transactional(readOnly = true)
    public Breadcrumb path(long userId, long id) {
        List<BreadcrumbItem> items = new ArrayList<>();
        Folder current = requireFolder(id, userId);
        while (current != null) {
            items.add(new BreadcrumbItem(current.getId(), current.getName()));
            current = current.getParentId() == null ? null
                    : folderRepository.findByIdAndUserId(current.getParentId(), userId)
                    .filter(f -> !f.isDeleted()).orElse(null);
        }
        Collections.reverse(items);
        return new Breadcrumb(items);
    }

    @Transactional
    public FolderView rename(long userId, long id, String name) {
        String clean = validateName(name);
        Folder folder = requireFolder(id, userId);
        ensureUnique(userId, folder.getParentId(), clean, id);
        folder.setName(clean);
        folder.setUpdatedAt(Instant.now());
        return toView(folderRepository.save(folder));
    }

    @Transactional
    public FolderView move(long userId, long id, Long parentId) {
        Folder folder = requireFolder(id, userId);
        if (parentId != null) {
            requireFolder(parentId, userId);
            if (parentId.equals(id) || isDescendant(userId, parentId, id)) {
                throw new BizException(ErrorCode.INVALID_MOVE, "不能移动到自身或其子目录");
            }
        }
        ensureUnique(userId, parentId, folder.getName(), id);
        folder.setParentId(parentId);
        folder.setUpdatedAt(Instant.now());
        return toView(folderRepository.save(folder));
    }

    @Transactional
    public void delete(long userId, long id) {
        Folder folder = requireFolder(id, userId);
        softDeleteRecursive(userId, folder, Instant.now());
    }

    @Transactional(readOnly = true)
    public Folder requireFolder(long id, long userId) {
        return folderRepository.findByIdAndUserIdAndDeletedFalse(id, userId)
                .orElseThrow(() -> new BizException(ErrorCode.FOLDER_NOT_FOUND));
    }

    public static FolderView toView(Folder folder) {
        return new FolderView(folder.getId(), folder.getParentId(), folder.getName(),
                folder.getCreatedAt(), folder.getUpdatedAt());
    }

    private void softDeleteRecursive(long userId, Folder folder, Instant now) {
        List<CloudFile> files = fileRepository.findByUserIdAndFolderIdAndDeletedFalse(userId, folder.getId());
        for (CloudFile file : files) {
            file.setDeleted(true);
            file.setDeletedAt(now);
            file.setUpdatedAt(now);
            fileRepository.save(file);
        }
        List<Folder> children = folderRepository.findByUserIdAndParentIdAndDeletedFalse(userId, folder.getId());
        for (Folder child : children) {
            softDeleteRecursive(userId, child, now);
        }
        folder.setDeleted(true);
        folder.setDeletedAt(now);
        folder.setUpdatedAt(now);
        folderRepository.save(folder);
    }

    private boolean isDescendant(long userId, long candidateParentId, long ancestorId) {
        Long cursor = candidateParentId;
        int guard = 0;
        while (cursor != null && guard++ < 10_000) {
            if (cursor == ancestorId) {
                return true;
            }
            Folder parent = folderRepository.findByIdAndUserId(cursor, userId).orElse(null);
            cursor = parent == null ? null : parent.getParentId();
        }
        return false;
    }

    private void ensureUnique(long userId, Long parentId, String name, Long excludeId) {
        List<Folder> siblings = parentId == null
                ? folderRepository.findByUserIdAndParentIdIsNullAndDeletedFalse(userId)
                : folderRepository.findByUserIdAndParentIdAndDeletedFalse(userId, parentId);
        boolean conflict = siblings.stream()
                .anyMatch(f -> f.getName().equalsIgnoreCase(name) && (excludeId == null || !f.getId().equals(excludeId)));
        if (conflict) {
            throw new BizException(ErrorCode.NAME_CONFLICT, "同级目录名称已存在");
        }
    }

    private String validateName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.length() > 255) {
            throw new BizException(ErrorCode.INVALID_PARAM, "目录名长度必须为 1-255");
        }
        if (clean.equals(".") || clean.equals("..") || clean.contains("/") || clean.contains("\\")) {
            throw new BizException(ErrorCode.INVALID_PARAM, "目录名包含非法字符");
        }
        return clean;
    }
}
