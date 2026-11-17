# 部署与运维

## 1. 运行方式

```bash
java -jar GreyDrive.jar
```

- 服务、API 与管理后台共用 `server.port`，默认 `20020`。
- 独立管理端口 `greydrive.admin.port`（默认 `80`）默认关闭（`greydrive.admin.enabled=false`）；需要时再启用，Linux 绑定 80 需要 root 或 `cap_net_bind_service` 能力：
  `setcap cap_net_bind_service=+ep $(readlink -f $(which java))`。
- 独立管理端口不可用（被占用/无权限）时只打印 WARN 并跳过该连接，20020 正常启动。
- 实际端口与访问地址在启动日志中打印。
- 所有数据相对启动时工作目录，建议固定一个部署目录。

## 2. systemd 示例

```ini
[Unit]
Description=GreyDrive
After=network.target

[Service]
WorkingDirectory=/opt/greydrive
ExecStart=/usr/bin/java -jar /opt/greydrive/GreyDrive.jar
Restart=on-failure
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
```

`WorkingDirectory` 必须是可写目录，`config/` 与 `data/` 会在这里创建。

## 3. 常用参数调整

修改 `config/application.yml` 后重启：

```yaml
server:
  port: 20020                 # 服务 / API / 管理后台

greydrive:
  admin:
    enabled: false            # 独立管理端口默认关闭
    port: 80                  # 仅 enabled=true 时生效
  chunk-size: 4194304          # 分片 4 MiB
  max-file-size: 5368709120    # 单文件 5 GiB
  upload:
    session-ttl: PT48H         # 上传会话 48 小时
  recycle:
    retention: P7D             # 回收站保留 7 天
  security:
    jwt-ttl: PT12H             # 登录令牌 12 小时
    mfa-required: true         # 强制所有用户绑定 TOTP
    mfa-challenge-ttl: PT5M    # 登录二次验证挑战有效期
  share:
    default-ttl: P30D          # 分享默认 30 天；删除该行表示永不过期
```

修改分片大小只影响之后新建的上传会话，已存在的会话继续使用创建时的分片大小。

## 4. 日志

- 位置：`data/logs/greydrive.log`
- 滚动：单文件 50MB，保留 30 天，总量上限 1GB（`logging.logback.rollingpolicy.*`）。
- 日志不记录密码、JWT、提取码、文件内容。

## 5. 备份与恢复

H2 为单文件数据库，运行中直接复制可能得到不一致快照，推荐停服备份：

```bash
systemctl stop greydrive
tar czf greydrive-backup-$(date +%F).tgz config data
systemctl start greydrive
```

恢复：

```bash
systemctl stop greydrive
rm -rf config data
tar xzf greydrive-backup-YYYY-MM-DD.tgz
systemctl start greydrive
```

> 必须同时备份 `data/secrets/jwt.key`。丢失该文件会重新生成密钥，已签发的登录令牌全部失效。

## 6. 存储统计与磁盘监控

```bash
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:20020/api/v1/system/storage | jq
```

返回物理占用（去重后 blob 总大小）、逻辑占用（文件大小求和）、文件数、磁盘总量/剩余/可用。

建议对下列指标做监控：

- `data/` 所在磁盘剩余空间
- `data/chunks/` 目录大小（异常大说明有大量未完成上传）
- 服务健康检查 `/api/v1/health`

## 7. 清理机制

### 自动清理

服务内置定时任务（每 30 秒检查一次，按配置间隔执行）：

- 过期上传会话：标记 `EXPIRED`，删除分片记录与分片目录
- 孤儿分片目录：数据库中没有对应会话的 `data/chunks/<id>` 目录直接删除
- 回收站超期：`deleted_at` 早于保留期的文件/目录彻底删除，并释放不再被引用的物理文件

相关配置：`greydrive.cleanup.enabled`、`chunk-interval`、`recycle-interval`、`greydrive.recycle.retention`。

### 手工清理

- 不要直接删除 `data/storage/blobs/` 下的文件，否则数据库引用会失效。
- 需要立即释放空间时，通过 API 彻底删除回收站内容：

```bash
curl -X DELETE -H "Authorization: Bearer $TOKEN" http://127.0.0.1:20020/api/v1/recycle
```

- 停服后可以安全删除 `data/chunks/` 与 `data/tmp/`（只影响未完成的上传与临时文件）。

## 8. 反向代理与 HTTPS

服务本身只提供 HTTP。生产环境建议放在 Nginx/Caddy 之后由反向代理终止 TLS：

```nginx
location / {
    proxy_pass http://127.0.0.1:20020;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_request_buffering off;
    client_max_body_size 0;
}
```

分片上传使用 `application/octet-stream`，不要对请求体做小的体积限制。

## 9. 故障排查

| 现象 | 排查方向 |
|---|---|
| 启动失败，端口占用 | 修改 `server.port`；确认没有旧实例 |
| 独立管理端口 Permission denied | 默认已关闭；如启用，用 root 运行或 `setcap cap_net_bind_service=+ep` |
| 用户登录要求绑定 TOTP | 用认证器 App 扫码；如需关闭强制，设置 `greydrive.security.mfa-required=false` |
| H2 文件锁 | 同一 `data/` 只能一个进程 |
| 上传合并失败 | 检查 `data/tmp` 磁盘空间与权限 |
| 下载 500 | 检查对应 blob 是否存在（错误码 17001） |
| 令牌全部失效 | `jwt.key` 是否被替换或删除 |
| 分享一直 403 | 提取码错误次数过多（5 次/5 分钟）或被取消 |
