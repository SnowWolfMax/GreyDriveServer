package com.greydrive.tag;

import com.greydrive.common.BizException;
import com.greydrive.common.ErrorCode;
import com.greydrive.file.FileService;
import com.greydrive.tag.TagDtos.CreateTagRequest;
import com.greydrive.tag.TagDtos.TagView;
import com.greydrive.tag.TagDtos.UpdateTagRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TagService {

    private final TagRepository tagRepository;
    private final FileTagRepository fileTagRepository;
    private final FileService fileService;

    public TagService(TagRepository tagRepository, FileTagRepository fileTagRepository, FileService fileService) {
        this.tagRepository = tagRepository;
        this.fileTagRepository = fileTagRepository;
        this.fileService = fileService;
    }

    @Transactional
    public TagView create(long userId, CreateTagRequest request) {
        String name = validateName(request.name());
        if (tagRepository.existsByUserIdAndName(userId, name)) {
            throw new BizException(ErrorCode.TAG_EXISTS);
        }
        Tag tag = new Tag();
        tag.setUserId(userId);
        tag.setName(name);
        tag.setColor(request.color());
        tag.setCreatedAt(Instant.now());
        return toView(tagRepository.save(tag));
    }

    @Transactional(readOnly = true)
    public List<TagView> list(long userId) {
        return tagRepository.findByUserIdOrderByNameAsc(userId).stream().map(TagService::toView).toList();
    }

    @Transactional
    public TagView update(long userId, long tagId, UpdateTagRequest request) {
        Tag tag = requireTag(userId, tagId);
        if (request.name() != null && !request.name().isBlank()) {
            String name = validateName(request.name());
            if (!name.equals(tag.getName()) && tagRepository.existsByUserIdAndName(userId, name)) {
                throw new BizException(ErrorCode.TAG_EXISTS);
            }
            tag.setName(name);
        }
        if (request.color() != null) {
            tag.setColor(request.color());
        }
        return toView(tagRepository.save(tag));
    }

    @Transactional
    public void delete(long userId, long tagId) {
        Tag tag = requireTag(userId, tagId);
        fileTagRepository.deleteByTagId(tagId);
        tagRepository.delete(tag);
    }

    @Transactional
    public List<TagView> setFileTags(long userId, long fileId, List<Long> tagIds) {
        fileService.requireFile(userId, fileId);
        Set<Long> unique = new LinkedHashSet<>(tagIds);
        for (Long tagId : unique) {
            requireTag(userId, tagId);
        }
        fileTagRepository.deleteByFileId(fileId);
        for (Long tagId : unique) {
            FileTag fileTag = new FileTag();
            fileTag.setFileId(fileId);
            fileTag.setTagId(tagId);
            fileTagRepository.save(fileTag);
        }
        return fileTags(userId, fileId);
    }

    @Transactional
    public List<TagView> addFileTag(long userId, long fileId, long tagId) {
        fileService.requireFile(userId, fileId);
        requireTag(userId, tagId);
        if (!fileTagRepository.existsByFileIdAndTagId(fileId, tagId)) {
            FileTag fileTag = new FileTag();
            fileTag.setFileId(fileId);
            fileTag.setTagId(tagId);
            fileTagRepository.save(fileTag);
        }
        return fileTags(userId, fileId);
    }

    @Transactional
    public List<TagView> removeFileTag(long userId, long fileId, long tagId) {
        fileService.requireFile(userId, fileId);
        fileTagRepository.findByFileId(fileId).stream()
                .filter(ft -> ft.getTagId() == tagId)
                .findFirst()
                .ifPresent(fileTagRepository::delete);
        return fileTags(userId, fileId);
    }

    @Transactional(readOnly = true)
    public List<TagView> fileTags(long userId, long fileId) {
        fileService.requireFile(userId, fileId);
        List<Long> tagIds = fileTagRepository.findByFileId(fileId).stream().map(FileTag::getTagId).toList();
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return tagRepository.findAllById(tagIds).stream()
                .filter(tag -> tag.getUserId() == userId)
                .map(TagService::toView)
                .toList();
    }

    private Tag requireTag(long userId, long tagId) {
        return tagRepository.findByIdAndUserId(tagId, userId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "标签不存在"));
    }

    private String validateName(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.length() > 64) {
            throw new BizException(ErrorCode.INVALID_PARAM, "标签名长度必须为 1-64");
        }
        return clean;
    }

    public static TagView toView(Tag tag) {
        return new TagView(tag.getId(), tag.getName(), tag.getColor(), tag.getCreatedAt());
    }
}
