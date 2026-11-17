# 总体设计

## 1. 技术栈

| 关注点 | 选择 |
|---|---|
| 运行时 | JDK 21 + Spring Boot 3.3.x，Maven fat jar（`GreyDrive.jar`） |
| Web | spring-boot-starter-web（内嵌 Tomcat，监听 0.0.0.0） |
| 安全 | spring-boot-starter-security + jjwt 0.12.x，无状态 JWT |
| 持久化 | spring-boot-starter-data-jpa + H2 文件模式，Flyway 管表结构 |
| 文档 | springdoc-openapi 2.x |
| 缓存 | Caffeine（进程内） |
| 缩略图 | Thumbnailator（纯 Java） |
| Web 后台 | 原生 HTML/CSS/JS（无构建），由 Spring Boot 静态资源托管，调用同一套 REST API |
| 二次验证 | TOTP（RFC 6238）+ 一次性恢复码，二维码由 ZXing 服务端生成 |
| 测试 | JUnit 5 + MockMvc + `@TempDir` |

不依赖 Redis / MinIO / MQ / Docker / 外部数据库，全部进程内或本地文件。

## 2. 分层

```
controller → service → repository(JPA)
                 ├→ StorageService（接口）→ LocalFileStorageService
                 ├→ JwtService / CacheService / ThumbnailService
                 └→ OperationLogService
common: ApiResponse / PageResult / ErrorCode / BizException / 全局异常 / traceId
security: JwtAuthFilter / SecurityConfig / CurrentUser / 分享访问 JWT
```

- 所有业务查询强制带 `user_id`；非本人资源与不存在资源返回同一错误，防探测。
- 写操作后异步记录操作日志，失败不影响主流程。

## 3. 核心数据模型

- **blob**：`sha256 → 存储键`，带 `ref_count`，内容寻址。
- **file**：用户可见文件，指向 `current_version`。
- **file_version**：每次上传/替换/回滚新增一条，引用 blob。
- **秒传**：sha256 命中已有 blob 则直接建 file/version 并 `ref_count++`，不写盘。
- **回收站**：file/folder 软删除（`deleted=1, deleted_at`），定时物理清理。
- **版本回滚**：以旧内容新增版本（复用 blob，`ref_count++`），历史不丢。
- **彻底删除**：删所有版本 → `ref_count--` → 归零删物理文件。
- **分享**：token 随机；提取码只存 BCrypt；校验通过签发 30 分钟分享 JWT。

## 4. 鉴权

- 白名单：`/api/v1/auth/**`、`/api/v1/public/**`、`/api/v1/health`、`/v3/api-docs/**`、`/swagger-ui/**`、`/`、`/index.html`、`/assets/**`。
- 其余需 `Authorization: Bearer <jwt>`。
- 分享访问用 `X-Share-Token`（verify 接口签发），不依赖登录 JWT。

## 5. Web 后台

- 服务、API 与管理后台共用 `server.port`（默认 20020）；独立管理端口默认关闭（`greydrive.admin.enabled=false`）。
- 入口：`http://服务器IP:20020/`，登录后进入管理界面。
- 同一个进程、同一套 Spring 上下文提供静态资源与 REST API。
- 单页应用（hash 路由），覆盖：文件浏览、上传进度、分享、回收站、版本、预览、标签、最近/收藏、日志、系统统计、个人设置。
- 登录：用户名密码 → 未绑定 TOTP 时强制绑定（二维码 + 6 位验证码 + 恢复码）；已绑定则输入 TOTP 或恢复码。
- 前端不使用 CDN 或外部资源，离线可用。

## 6. 启动日志

```
 GreyDrive 已启动
 服务/API:   http://127.0.0.1:20020/  http://<本机IPv4>:20020/
 Swagger : http://<本机IPv4>:20020/swagger-ui/index.html
 OpenAPI : http://<本机IPv4>:20020/v3/api-docs
 工作目录/数据目录/日志文件: 绝对路径
```

独立管理端口被占用时不会阻断启动：日志打印告警，20020 正常可用。

## 7. 运行目录（相对启动工作目录，首次自动创建）

```
config/application.yml
data/db/greydrive.mv.db
data/storage/blobs/<aa>/<bb>/<sha256>
data/chunks/<uploadId>/<index>.part
data/thumbnails/<sha256>/<w>x<h>.jpg
data/tmp/
data/secrets/jwt.key
data/logs/greydrive.log
```

- jar 移动后在新目录运行 = 全新实例，重新初始化。
- 迁移数据 = 停服后拷贝整个 `data/`。
- 配置仅缺失时生成，永不覆盖；Flyway migrate 幂等。

## 8. 默认参数（均可配置）

| 配置键 | 默认值 |
|---|---|
|  `server.port` | 20020（服务 / API / 管理后台） |
| `server.address` | 0.0.0.0 |
| `greydrive.admin.port` | 80（独立管理端口，默认关闭） |
| `greydrive.admin.enabled` | false |
| `greydrive.chunk-size` | 8388608（8 MiB） |
| `greydrive.max-file-size` | 2147483648（2 GiB） |
| `greydrive.security.jwt-ttl` | PT24H |
| `greydrive.upload.session-ttl` | PT24H |
| `greydrive.recycle.retention` | P30D |
| `greydrive.share.default-ttl` | 永不（创建时可指定） |
| `greydrive.security.share-access-ttl` | PT30M |
| `greydrive.security.mfa-required` | true |
| `greydrive.security.mfa-challenge-ttl` | PT5M |
| `greydrive.security.mfa-issuer` | GreyDrive |
| `greydrive.preview.text-max-bytes` | 1048576 |
| `greydrive.preview.thumbnail-sizes` | 256,1024 |
| `greydrive.pagination.default-size` | 20 |
| `greydrive.pagination.max-size` | 100 |
| `greydrive.cleanup.chunk-interval` | PT10M |
| `greydrive.cleanup.recycle-interval` | PT1H |
| `logging.logback.rollingpolicy.max-file-size` | 50MB |
| `logging.logback.rollingpolicy.max-history` | 30 |
| `logging.logback.rollingpolicy.total-size-cap` | 1GB |
