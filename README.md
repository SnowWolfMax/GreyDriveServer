# GreyDrive

单机云盘服务端，自带 Web 管理后台。只依赖 JDK 21，一个 fat jar 启动即可使用；无需预装数据库、缓存、对象存储、Docker 或任何外部中间件。服务、API 与管理后台共用 **20020** 端口，登录支持 TOTP 二次验证。

## 功能

- Web 管理后台：登录/注册、文件浏览、分片上传进度、分享、回收站、版本、预览、标签、日志、系统统计、个人设置
- 二次验证：TOTP（认证器 App）+ 一次性恢复码，默认强制启用
- 认证与用户：注册、登录（JWT）、refresh token 自动轮换、登录设备管理、资料与密码修改
- 目录管理：新建、列表、重命名、移动、面包屑、软删除
- 文件管理：分片上传、断点续传、秒传（SHA-256 去重）、Range 下载
- 回收站：软删除、恢复、彻底删除、清空、超期自动清理
- 搜索筛选：关键字、类型、扩展名、大小、标签、收藏、时间范围
- 标签与收藏
- 最近访问
- 分享链接：提取码（只存哈希）、有效期（可永不过期）、权限、目录分享
- 版本历史：列表、指定版本下载、回滚
- 预览：图片缩略图、文本预览
- 操作日志
- 系统接口：健康检查、系统信息、存储统计

## 环境要求

- JDK 21 或更高版本
- 首次运行目录必须可写

## 构建

```bash
mvn clean package
```

产物：`target/GreyDrive.jar`。

## 启动

```bash
java -jar GreyDrive.jar
```

- 服务 / API / 管理后台：**20020**（`server.port`，同一端口）
- 独立管理端口（`greydrive.admin.port`，默认 80）：**默认关闭**

管理后台与服务同源，直接访问 `http://服务器IP:20020/`。需要调整端口时：

```bash
java -jar GreyDrive.jar --server.port=9000
```

如需恢复独立的管理端口，启用 `greydrive.admin.enabled=true` 并设置 `greydrive.admin.port`。

启动后日志会打印实际端口与访问地址，例如：

```
 GreyDrive 已启动
 服务/API:   http://127.0.0.1:20020/  http://10.0.0.5:20020/
 Swagger:    http://10.0.0.5:20020/swagger-ui/index.html
 OpenAPI:    http://10.0.0.5:20020/v3/api-docs
 工作目录:   /opt/greydrive
 数据目录:   /opt/greydrive/data
 日志文件:   /opt/greydrive/data/logs/greydrive.log
```

`127.0.0.1` 仅本机可用，后面的局域网 IP 供其他设备访问；两个地址都能用。

- Web 后台：`http://服务器IP:20020/`
- 服务 / API：`http://服务器IP:20020/api/v1/...`
- Swagger UI：`:20020/swagger-ui/index.html`
- OpenAPI JSON：`:20020/v3/api-docs`

## 首次运行自动创建

所有路径相对**启动时的工作目录**，首次启动自动创建，重复启动不会覆盖已有配置：

```
config/application.yml                    # 默认配置（已存在则跳过）
data/db/greydrive.mv.db                   # H2 数据库
data/storage/blobs/<aa>/<bb>/<sha256>     # 文件内容
data/chunks/<uploadId>/<index>.part       # 上传分片
data/thumbnails/<sha256>/<size>.jpg       # 缩略图缓存
data/tmp/                                 # 合并临时文件
data/secrets/jwt.key                      # JWT 密钥（首次生成，权限 600）
data/logs/greydrive.log                   # 运行日志
```

把 jar 复制到新目录再运行，相当于一个全新实例；数据不会自动跟随 jar 移动。

## 配置

编辑 `config/application.yml` 后重启生效。常用项：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `server.port` | 20020 | 服务 / API / 管理后台监听端口 |
| `server.address` | 0.0.0.0 | 监听地址 |
| `greydrive.admin.port` | 80 | 独立管理端口（仅在 `greydrive.admin.enabled=true` 时生效） |
| `greydrive.admin.enabled` | false | 是否额外监听独立管理端口 |
| `greydrive.data-dir` | ./data | 数据根目录 |
| `greydrive.chunk-size` | 8388608 | 分片大小（8 MiB） |
| `greydrive.max-file-size` | 2147483648 | 单文件上限（2 GiB） |
| `greydrive.security.jwt-ttl` | PT24H | 登录令牌有效期 |
| `greydrive.security.jwt-secret-file` | ./data/secrets/jwt.key | JWT 密钥文件 |
| `greydrive.security.share-access-ttl` | PT30M | 分享访问令牌有效期 |
| `greydrive.security.mfa-required` | true | 是否强制所有用户绑定 TOTP |
| `greydrive.security.mfa-challenge-ttl` | PT5M | 登录二次验证挑战有效期 |
| `greydrive.security.mfa-issuer` | GreyDrive | 认证器显示的发行方 |
| `greydrive.upload.session-ttl` | PT24H | 上传会话有效期 |
| `greydrive.recycle.retention` | P30D | 回收站保留天数 |
| `greydrive.share.default-ttl` | 不配置=永不过期 | 分享默认有效期 |
| `greydrive.share.code-required` | true | 未指定提取码时是否自动生成 |
| `greydrive.preview.text-max-bytes` | 1048576 | 文本预览上限 |
| `greydrive.preview.thumbnail-sizes` | 256,1024 | 缩略图边长 |
| `greydrive.pagination.default-size` | 20 | 默认页大小 |
| `greydrive.pagination.max-size` | 100 | 最大页大小 |
| `greydrive.cleanup.enabled` | true | 是否启用定时清理 |
| `greydrive.cleanup.chunk-interval` | PT10M | 过期分片扫描间隔 |
| `greydrive.cleanup.recycle-interval` | PT1H | 回收站清理间隔 |

## 数据备份与恢复

1. 停止服务（保证 H2 文件一致）。
2. 备份整个 `data/` 与 `config/`：

```bash
tar czf greydrive-backup-$(date +%F).tgz config data
```

3. 恢复：停止服务，解压覆盖到工作目录，再启动。

`data/secrets/jwt.key` 必须一并备份；丢失后虽然数据仍在，但已签发的令牌会全部失效（需要重新登录）。

## 升级

1. 备份 `config/` 与 `data/`。
2. 用新 jar 替换旧 jar。
3. 保留原 `config/` 与 `data/` 启动。Flyway 自动迁移表结构，迁移记录见 `flyway_schema_history`。

## 常见问题

| 现象 | 处理 |
|---|---|
| 端口被占用 | 修改 `server.port` 后重启 |
| 管理端口启动失败（Permission denied） | 默认已关闭独立管理端口；如启用，需 root 或 `setcap cap_net_bind_service=+ep $(which java)` |
| 登录后要求绑定二次验证 | 用 Google Authenticator 等 App 扫描页面二维码并输入 6 位验证码 |
| `Database may be already in use` | 同一数据目录只能启动一个实例 |
| 启动报权限错误 | 确认启动目录可读写 |
| 上传返回 413 | 文件超过 `greydrive.max-file-size` |
| 缩略图/文本预览返回 415 | 文件类型不支持预览 |
| 重启后数据像“清空”了 | 很可能换了工作目录；数据相对于启动目录 |
| 分享链接打不开 | 分享已取消、已过期，或提取码错误 |

## 文档

- [总体设计](docs/01-architecture.md)
- [API 契约](docs/02-api.md)
- [数据库设计](docs/03-database.md)
- [部署与运维](docs/04-deployment-operations.md)
- [安全说明](docs/05-security.md)
- [自主决策记录](docs/06-decision-record.md)
- [验收文档](docs/07-acceptance.md)
- [已知限制](docs/08-known-limitations.md)

## 开源协议

[Apache License 2.0](LICENSE)
