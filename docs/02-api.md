# API 契约（草案）

- Swagger UI：`/swagger-ui/index.html`
- OpenAPI JSON：`/v3/api-docs`
- Web 后台：`/`
- 业务前缀：`/api/v1`

## 1. 统一响应

```json
{ "code": 0, "message": "success", "data": {}, "traceId": "b1c2..." }
```

错误：

```json
{ "code": 10004, "message": "资源不存在或无权访问", "data": null, "traceId": "b1c2..." }
```

校验错误附加 `errors: [{"field":"name","message":"不能为空"}]`。

## 2. 统一分页

请求：`page`（1 起，默认 1）、`size`（默认 20，最大 100）、`sort`（如 `createdAt,desc`）。

```json
{ "items": [], "page": 1, "size": 20, "total": 0, "totalPages": 0 }
```

## 3. 错误码

分段：`10xxx` 通用、`11xxx` 认证用户、`12xxx` 目录文件、`13xxx` 上传、`14xxx` 分享、`15xxx` 版本、`16xxx` 标签收藏、`17xxx` 预览、`18xxx` 存储系统。

| code | HTTP | 含义 |
|---|---|---|
| 10001 | 400 | 参数校验失败 |
| 10002 | 401 | 未登录或令牌无效 |
| 10003 | 403 | 无权限 |
| 10004 | 404 | 资源不存在或无权访问（统一） |
| 10005 | 409 | 冲突 |
| 10006 | 413 | 超过大小限制 |
| 10007 | 415 | 不支持的媒体类型 |
| 10008 | 416 | Range 不满足 |
| 10009 | 429 | 请求过于频繁 |
| 10010 | 500 | 内部错误 |
| 11001 | 409 | 用户名已存在 |
| 11002 | 401 | 用户名或密码错误 |
| 11003 | 401 | 令牌过期/无效 |
| 12001 | 404 | 目录不存在 |
| 12002 | 404 | 文件不存在 |
| 12003 | 409 | 同级重名 |
| 12005 | 400 | 非法移动（循环/跨用户） |
| 13001 | 404 | 上传会话不存在或已过期 |
| 13002 | 400 | 分片序号非法 |
| 13003 | 409 | 缺少分片 |
| 13004 | 400 | 大小/哈希与声明不一致 |
| 14001 | 404 | 分享不存在 |
| 14002 | 410 | 分享已过期 |
| 14003 | 403 | 提取码错误或缺失 |
| 14004 | 403 | 超出分享权限 |
| 15001 | 404 | 版本不存在 |
| 16001 | 409 | 标签已存在 |
| 17001 | 500 | 存储读写失败 |
| 18001 | 415 | 不支持该类型预览 |

## 4. 接口清单

### 认证与用户
| 方法 | 路径 | 鉴权 |
|---|---|---|
| POST | `/auth/register` | 公开 |
| POST | `/auth/login` | 公开 |
| POST | `/auth/refresh` | 公开（refresh token） |
| POST | `/auth/logout` | JWT |
| GET | `/users/me` | JWT |
| GET | `/users/me/sessions` | JWT |
| DELETE | `/users/me/sessions/{id}` | JWT |
| PUT | `/users/me` | JWT |
| PUT | `/users/me/password` | JWT |

登录响应：

```json
{ "accessToken": null, "tokenType": "Bearer", "expiresIn": 0, "user": {...},
  "mfaRequired": false, "mfaSetupRequired": true, "mfaToken": "<5分钟挑战令牌>",
  "refreshToken": null }
```

- `mfaSetupRequired=true`：账号未绑定 TOTP，必须先用 `mfaToken` 完成绑定。
- `mfaRequired=true`：已绑定 TOTP，需要提交验证码。
- 两者均为 false 且 `accessToken` 非空时才可直接访问业务接口。
- 登录成功时 `refreshToken` 非空；默认 30 天有效，每次刷新都会轮换，旧 token 立即失效。

刷新令牌：

```bash
curl -s -X POST http://127.0.0.1/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"gdr_xxx"}'
# data.accessToken / data.refreshToken
```

登出时把当前 `refreshToken` 放进请求体，可只撤销当前设备；不传则撤销该账号全部 refresh token。

### 二次验证（MFA）
| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/auth/mfa/setup` | 公开（mfaToken）或 JWT | 生成 TOTP 密钥、otpauth URI、二维码 data URL |
| POST | `/auth/mfa/enable` | 公开（mfaToken）或 JWT | 校验 6 位码并启用，返回恢复码与登录令牌 |
| POST | `/auth/mfa/verify` | 公开（mfaToken） | 登录二次验证，返回登录令牌 |
| GET | `/users/me/mfa` | JWT | 查询是否启用与剩余恢复码 |
| POST | `/users/me/mfa/disable` | JWT | 密码 + 验证码关闭 MFA |
| POST | `/users/me/mfa/recovery-codes` | JWT | 验证码校验后重新生成 10 个恢复码 |

`setup` / `enable` 的请求体：

```json
{ "mfaToken": "<登录返回的挑战令牌>", "code": "123456" }
```

已登录用户可省略 `mfaToken`，改用 `Authorization` 头。

登录二次验证：

```bash
curl -s -X POST http://127.0.0.1/api/v1/auth/mfa/verify \
  -H 'Content-Type: application/json' \
  -d '{"mfaToken":"<token>","code":"123456"}'
# data.accessToken 非空
```

恢复码也可填入 `code` 字段（自动识别）。

### 目录
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/folders` | 新建 `{name,parentId}` |
| GET | `/folders?parentId=&page=&size=` | 子目录列表 |
| GET | `/folders/{id}` | 详情 |
| GET | `/folders/{id}/path` | 面包屑 |
| PATCH | `/folders/{id}` | 重命名 |
| POST | `/folders/{id}/move` | 移动 |
| DELETE | `/folders/{id}` | 软删除 |

### 文件与秒传
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/files?folderId=&keyword=&mimeType=&ext=&minSize=&maxSize=&tagId=&favorite=&from=&to=&sort=&page=&size=` | 列表/筛选 |
| GET | `/files/{id}` | 详情 |
| PATCH | `/files/{id}` | 重命名 |
| POST | `/files/{id}/move` | 移动 |
| DELETE | `/files/{id}` | 软删除 |
| POST | `/files/check-hash` | 秒传探测 `{sha256,size,fileName,folderId}` → `{hit,fileId?,uploadId?}` |
| GET | `/files/{id}/download` | 下载，支持 Range |

### 分片上传
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/uploads` | 初始化 `{fileName,size,sha256,folderId,fileId?,chunkSize?}` |
| GET | `/uploads/{uploadId}` | 查询已传分片 |
| PUT | `/uploads/{uploadId}/chunks/{index}` | 上传/补传分片（octet-stream） |
| POST | `/uploads/{uploadId}/complete` | 合并（幂等） |
| DELETE | `/uploads/{uploadId}` | 取消清理 |

`fileId` 存在时 complete 生成新版本，否则新建文件。

### 版本
| 方法 | 路径 |
|---|---|
| GET | `/files/{id}/versions` |
| GET | `/files/{id}/versions/{versionNo}/download` |
| POST | `/files/{id}/versions/{versionNo}/rollback` |

### 回收站
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/recycle` | 列表 |
| POST | `/recycle/restore` | 批量恢复 `{fileIds:[],folderIds:[]}` |
| DELETE | `/recycle/{id}?type=FILE\|FOLDER` | 彻底删除 |
| DELETE | `/recycle` | 清空 |

### 标签收藏
| 方法 | 路径 |
|---|---|
| POST / GET / PATCH / DELETE | `/tags`、`/tags/{id}` |
| PUT | `/files/{id}/tags`（覆盖设置 `{tagIds:[]}`） |
| PUT / DELETE | `/files/{id}/favorite` |
| GET | `/favorites` |

### 最近访问
| 方法 | 路径 |
|---|---|
| GET | `/recent` |
| DELETE | `/recent` |

### 分享
| 方法 | 路径 | 鉴权 |
|---|---|---|
| POST | `/shares` | JWT |
| GET | `/shares` | JWT |
| DELETE | `/shares/{id}` | JWT |
| GET | `/public/shares/{token}` | 公开 |
| POST | `/public/shares/{token}/verify` | 公开 |
| GET | `/public/shares/{token}/files` | 分享令牌 |
| GET | `/public/shares/{token}/files/{fileId}/download` | 分享令牌 |
| GET | `/public/shares/{token}/thumbnail` | 分享令牌 |

创建分享：`expiresInSeconds` 省略或为 `0`/`null` 表示**永不过期**（默认）；大于 0 表示按秒过期。

### 预览
| 方法 | 路径 |
|---|---|
| GET | `/files/{id}/thumbnail?size=256` |
| GET | `/files/{id}/preview/text` |

### 日志
| 方法 | 路径 |
|---|---|
| GET | `/logs?action=&resourceType=&from=&to=&page=&size=` |

### 系统
| 方法 | 路径 | 鉴权 |
|---|---|---|
| GET | `/health` | 公开 |
| GET | `/system/info` | JWT |
| GET | `/system/storage` | JWT |

## 5. 核心 curl

```bash
# 注册
curl -s -X POST http://127.0.0.1:20020/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"P@ssw0rd123","nickname":"Alice"}'

# 登录
TOKEN=$(curl -s -X POST http://127.0.0.1:20020/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"P@ssw0rd123"}' | jq -r '.data.accessToken')

# 初始化分片上传
curl -s -X POST http://127.0.0.1:20020/api/v1/uploads \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"fileName":"a.bin","size":10485760,"sha256":"<sha256>","folderId":null}'

# 上传第 0 片
curl -s -X PUT "http://127.0.0.1:20020/api/v1/uploads/$UP/chunks/0" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/octet-stream' \
  --data-binary @part0

# 合并
curl -s -X POST "http://127.0.0.1:20020/api/v1/uploads/$UP/complete" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{}'

# Range 下载（期望 206 + Content-Range）
curl -s -D - -o part.bin -H "Authorization: Bearer $TOKEN" \
  -H 'Range: bytes=0-1023' "http://127.0.0.1:20020/api/v1/files/$FID/download"

# 创建分享
curl -s -X POST http://127.0.0.1:20020/api/v1/shares \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"fileId":'$FID',"permission":"DOWNLOAD","expiresInSeconds":86400}'

# 校验提取码
curl -s -X POST http://127.0.0.1:20020/api/v1/public/shares/$ST/verify \
  -H 'Content-Type: application/json' -d '{"code":"A1B2C3"}'
```
