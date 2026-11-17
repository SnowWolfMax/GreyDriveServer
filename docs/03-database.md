# 数据库设计（草案）

H2 2.x 文件模式；`spring.jpa.hibernate.ddl-auto=validate`；结构全部由 Flyway（`classpath:db/migration`）管理。

## 1. 表结构

### users
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK AUTO_INCREMENT | |
| username | VARCHAR(64) | 唯一 |
| password_hash | VARCHAR(100) | BCrypt |
| nickname | VARCHAR(64) | |
| email | VARCHAR(128) | 可空，唯一 |
| status | VARCHAR(16) | ACTIVE/DISABLED |
| created_at / updated_at | TIMESTAMP | |
| last_login_at | TIMESTAMP | |

索引：`uk_users_username(username)`、`uk_users_email(email)`。

### folders
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT | |
| parent_id | BIGINT NULL | NULL 为根 |
| name | VARCHAR(255) | |
| deleted | BOOLEAN | 软删除 |
| deleted_at | TIMESTAMP | |
| created_at / updated_at | TIMESTAMP | |

索引：`idx_folders_owner_parent(user_id,parent_id,deleted)`、`uk_folders_name(user_id,parent_id,name,deleted)`。

### blobs（内容寻址）
| 字段 | 类型 | 说明 |
|---|---|---|
| sha256 | CHAR(64) PK | |
| size | BIGINT | |
| storage_key | VARCHAR(255) | 相对存储键 |
| ref_count | INT | 引用计数 |
| created_at | TIMESTAMP | |

### files
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| user_id | BIGINT | |
| folder_id | BIGINT NULL | |
| name | VARCHAR(255) | |
| ext | VARCHAR(32) | |
| mime_type | VARCHAR(128) | |
| size | BIGINT | 当前版本大小 |
| sha256 | CHAR(64) | 当前版本哈希 |
| current_version | INT | 当前版本号 |
| favorite | BOOLEAN | 收藏 |
| deleted | BOOLEAN | 软删除 |
| deleted_at | TIMESTAMP | |
| last_accessed_at | TIMESTAMP | |
| created_at / updated_at | TIMESTAMP | |

索引：`idx_files_owner_folder(user_id,folder_id,deleted)`、`idx_files_owner_name(user_id,name)`、`idx_files_sha(sha256)`、`idx_files_deleted(deleted,deleted_at)`、`idx_files_owner_fav(user_id,favorite,deleted)`。

### file_versions
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| file_id | BIGINT | |
| version_no | INT | 从 1 递增 |
| sha256 | CHAR(64) | 引用 blobs |
| size | BIGINT | |
| mime_type | VARCHAR(128) | |
| note | VARCHAR(255) | |
| created_by | BIGINT | 用户 id |
| created_at | TIMESTAMP | |

索引：`uk_versions_file_no(file_id,version_no)`、`idx_versions_sha(sha256)`。

### upload_sessions
| 字段 | 类型 | 说明 |
|---|---|---|
| id | VARCHAR(32) PK | uploadId |
| user_id | BIGINT | |
| folder_id | BIGINT NULL | |
| target_file_id | BIGINT NULL | 非空则生成新版本 |
| file_name | VARCHAR(255) | |
| total_size | BIGINT | |
| sha256 | CHAR(64) | |
| chunk_size | INT | |
| total_chunks | INT | |
| status | VARCHAR(16) | INIT/UPLOADING/COMPLETED/CANCELED/EXPIRED |
| created_at / expires_at | TIMESTAMP | |

索引：`idx_upload_user_status(user_id,status)`、`idx_upload_expire(status,expires_at)`。

### upload_chunks
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| upload_id | VARCHAR(32) | |
| chunk_index | INT | |
| size | BIGINT | |
| sha256 | CHAR(64) | |
| created_at | TIMESTAMP | |

约束：`uk_chunk(upload_id,chunk_index)`。

### tags / file_tags
- `tags(id, user_id, name, color, created_at)`，`uk_tags_owner_name(user_id,name)`。
- `file_tags(file_id, tag_id)`，`pk(file_id,tag_id)`，`idx_ft_tag(tag_id)`。

### shares
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT PK | |
| token | VARCHAR(32) | 唯一 |
| user_id | BIGINT | |
| file_id / folder_id | BIGINT NULL | 二选一 |
| code_hash | VARCHAR(100) | BCrypt，可空 |
| permission | VARCHAR(16) | VIEW/DOWNLOAD |
| status | VARCHAR(16) | ACTIVE/CANCELED |
| expires_at | TIMESTAMP NULL | 空 = 永不过期 |
| download_count | BIGINT | |
| created_at | TIMESTAMP | |

索引：`uk_shares_token(token)`、`idx_shares_owner(user_id,created_at)`。

### recent_access
`id, user_id, file_id, accessed_at`；`uk_recent(user_id,file_id)`、`idx_recent_time(user_id,accessed_at)`。

### operation_logs
`id, user_id, action, resource_type, resource_id, detail, ip, user_agent, success, created_at`；
`idx_logs_user_time(user_id,created_at)`、`idx_logs_action_time(action,created_at)`。

### user_mfa
| 字段 | 类型 | 说明 |
|---|---|---|
| user_id | BIGINT PK | 用户 id |
| secret | VARCHAR(64) | Base32 TOTP 密钥 |
| enabled | BOOLEAN | 是否已完成绑定 |
| created_at | TIMESTAMP | 生成时间 |
| confirmed_at | TIMESTAMP | 确认启用时间 |

### refresh_tokens
`id, user_id, token_hash, device_name, user_agent, ip, created_at, last_used_at, expires_at, revoked_at`；
`idx_refresh_user(user_id)`、`idx_refresh_expires(expires_at)`。只存 SHA-256 哈希，刷新时轮换。

### mfa_recovery_codes
`id, user_id, code_hash, used, created_at`；`idx_mfa_recovery_user(user_id)`。
恢复码只存 BCrypt 哈希，使用后标记 `used=true`。

## 2. Flyway 清单

| 文件 | 内容 |
|---|---|
| `V1__users.sql` | users |
| `V2__folders_files_blobs_versions.sql` | folders、blobs、files、file_versions |
| `V3__upload_sessions.sql` | upload_sessions、upload_chunks |
| `V4__tags_favorites_recent.sql` | tags、file_tags、recent_access |
| `V5__shares.sql` | shares |
| `V6__operation_logs.sql` | operation_logs |
| `V7__user_mfa.sql` | user_mfa、mfa_recovery_codes |
| `V8__refresh_tokens.sql` | refresh_tokens |

## 3. 存储策略

- 软删除：仅置 `deleted=1, deleted_at`；回收站查询 `deleted=1`。
- 版本：`file_versions` 不可变；回滚新增版本并复用旧 blob（`ref_count++`）。
- 引用计数：版本创建 `ref_count++`；版本删除 `ref_count--`；归零删除物理文件。
- 彻底删除：删除文件全部版本并递减引用；目录彻底删除递归处理子项。
- `files.sha256` 为冗余快照，秒传/筛选使用；真值以版本表为准。
