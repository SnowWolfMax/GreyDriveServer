# 验收文档

以下步骤从零开始，覆盖注册、登录、上传、秒传、断点续传、下载、分享、回收站、版本回滚与重启持久化。

前置：JDK 21+，已构建 `target/GreyDrive.jar`，`jq` 可用。

## 0. 启动

```bash
mkdir -p /tmp/greydrive-accept && cd /tmp/greydrive-accept
cp /path/to/GreyDrive.jar .
java -jar GreyDrive.jar
```

预期：

- 日志出现 `GreyDrive 已启动`，打印 20020 服务地址、Swagger、数据目录
- 自动生成 `config/application.yml` 与 `data/` 下全部目录
- 浏览器可打开 `http://IP:20020/`（管理后台 / 服务同源页面）
- 浏览器可打开 `http://IP:20020/swagger-ui/index.html`
- 独立管理端口默认关闭，日志无 80 端口监听

```bash
BASE=http://127.0.0.1:20020/api/v1
curl -s $BASE/health | jq
# {"code":0,...,"data":{"status":"UP",...}}
```

## 1. 注册与登录

```bash
curl -s -X POST $BASE/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"Passw0rd!23","nickname":"Alice"}' | jq
# code=0

TOKEN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"Passw0rd!23"}' | jq -r .data.accessToken)
AUTH="Authorization: Bearer $TOKEN"

curl -s $BASE/users/me -H "$AUTH" | jq
# data.username=alice
```

预期：未带令牌访问 `$BASE/files` 返回 401 且 `code=10002`。

## 2. 建目录

```bash
FOLDER=$(curl -s -X POST $BASE/folders -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"docs"}' | jq -r .data.id)
echo $FOLDER
```

## 3. 分片上传（含断点续传）

```bash
printf 'hello grey' > a.txt
SHA=$(sha256sum a.txt | cut -d' ' -f1)
SIZE=$(stat -c%s a.txt)

INIT=$(curl -s -X POST $BASE/uploads -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"fileName\":\"a.txt\",\"size\":$SIZE,\"sha256\":\"$SHA\",\"folderId\":$FOLDER}")
echo $INIT | jq
UP=$(echo $INIT | jq -r .data.uploadId)
```

查询已传分片（此时为空）：

```bash
curl -s $BASE/uploads/$UP -H "$AUTH" | jq
# data.uploadedChunks=[]
```

上传分片并合并：

```bash
curl -s -X PUT "$BASE/uploads/$UP/chunks/0" -H "$AUTH" \
  -H 'Content-Type: application/octet-stream' --data-binary @a.txt | jq
# data.uploadedChunks=1

COMP=$(curl -s -X POST "$BASE/uploads/$UP/complete" -H "$AUTH")
echo $COMP | jq
FID=$(echo $COMP | jq -r .data.fileId)
```

上传大文件时可重复调用 `GET /uploads/{id}` 获取已传分片，只补传缺失分片。合并接口可重复调用，返回同一 `fileId`。

## 4. 秒传

再次用相同哈希初始化：

```bash
curl -s -X POST $BASE/uploads -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"fileName\":\"copy.txt\",\"size\":$SIZE,\"sha256\":\"$SHA\"}" | jq
# data.instant=true, data.fileId 非空

curl -s -X POST $BASE/files/check-hash -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"sha256\":\"$SHA\",\"size\":$SIZE,\"fileName\":\"copy2.txt\"}" | jq
# data.hit=true
```

## 5. 列表与筛选

```bash
curl -s "$BASE/files?folderId=$FOLDER&keyword=a" -H "$AUTH" | jq
# data.total>=1
```

## 6. 下载与 Range

```bash
curl -s -o full.bin "$BASE/files/$FID/download" -H "$AUTH"
cmp a.txt full.bin && echo "full OK"

curl -s -D - -o part.bin -H "$AUTH" -H 'Range: bytes=0-4' \
  "$BASE/files/$FID/download"
# HTTP/1.1 206 Partial Content
# Content-Range: bytes 0-4/10
# 文件内容为 hello
```

非法 Range（如 `bytes=100-`）返回 416 且带 `Content-Range: bytes */10`。

## 7. 分享

```bash
SH=$(curl -s -X POST $BASE/shares -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"fileId\":$FID,\"permission\":\"DOWNLOAD\"}")
STOKEN=$(echo $SH | jq -r .data.token)
CODE=$(echo $SH | jq -r .data.code)
echo "token=$STOKEN code=$CODE expiresAt=$(echo $SH | jq -r .data.expiresAt)"
# expiresAt 为 null 表示永不过期

curl -s $BASE/public/shares/$STOKEN | jq
# needCode=true

# 错误提取码 -> 403/14003
curl -s -o /dev/null -w '%{http_code}\n' -X POST $BASE/public/shares/$STOKEN/verify \
  -H 'Content-Type: application/json' -d '{"code":"XXXXXX"}'

SACCESS=$(curl -s -X POST $BASE/public/shares/$STOKEN/verify \
  -H 'Content-Type: application/json' -d "{\"code\":\"$CODE\"}" | jq -r .data.shareToken)

curl -s -o share.bin -H "X-Share-Token: $SACCESS" \
  "$BASE/public/shares/$STOKEN/files/$FID/download"
cmp a.txt share.bin && echo "share OK"
```

## 8. 回收站

```bash
curl -s -X DELETE $BASE/files/$FID -H "$AUTH" | jq          # code=0
curl -s "$BASE/recycle?type=FILE" -H "$AUTH" | jq           # 列表包含 a.txt
curl -s $BASE/files/$FID -H "$AUTH" | jq                    # code=12002

curl -s -X POST $BASE/recycle/restore -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"fileIds\":[$FID],\"folderIds\":[]}" | jq           # data=1
curl -s $BASE/files/$FID -H "$AUTH" | jq                    # code=0
```

## 9. 版本历史与回滚

```bash
printf 'hello grey v2' > b.txt
S2=$(sha256sum b.txt | cut -d' ' -f1); Z2=$(stat -c%s b.txt)
VINIT=$(curl -s -X POST $BASE/uploads -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"fileName\":\"a.txt\",\"size\":$Z2,\"sha256\":\"$S2\",\"fileId\":$FID}")
VUP=$(echo $VINIT | jq -r .data.uploadId)
curl -s -X PUT "$BASE/uploads/$VUP/chunks/0" -H "$AUTH" \
  -H 'Content-Type: application/octet-stream' --data-binary @b.txt > /dev/null
curl -s -X POST "$BASE/uploads/$VUP/complete" -H "$AUTH" | jq
# data.versionNo=2

curl -s $BASE/files/$FID/versions -H "$AUTH" | jq
# versionNo 2 current=true, versionNo 1 current=false

curl -s -X POST $BASE/files/$FID/versions/1/rollback -H "$AUTH" | jq
# data.versionNo=3

curl -s -o current.bin $BASE/files/$FID/download -H "$AUTH"
cmp a.txt current.bin && echo "rollback OK"
```

## 10. 其他模块

- 标签：`POST /tags`、`PUT /files/{id}/tags`
- 收藏：`PUT /files/{id}/favorite`、`GET /favorites`
- 最近访问：`GET /recent`
- 预览：`GET /files/{id}/thumbnail?size=256`、`GET /files/{id}/preview/text`
- 操作日志：`GET /logs`
- 系统：`GET /system/info`、`GET /system/storage`

## 11. 重启持久化与幂等

1. 记录当前 `TOKEN` 与 `FID`。
2. `Ctrl+C` 停止服务。
3. 在**同一目录**再次执行 `java -jar GreyDrive.jar`。
4. 预期：
   - 日志显示 `Successfully validated ...`，不重复执行已完成的迁移
   - 直接使用旧 `TOKEN` 访问 `GET /users/me` 仍返回 200（JWT 密钥已持久化）
   - `GET /files` 仍能看到之前的文件

## 12. Web 后台与二次验证

1. 浏览器打开 `http://服务器IP/`，应看到 GreyDrive 登录页。
2. 注册后首次登录：
   - 返回 `mfaSetupRequired=true`
   - 页面显示二维码与 Base32 密钥
   - 用 Google Authenticator / 1Password 扫码，输入 6 位验证码
   - 启用成功后显示 10 个恢复码，只能查看一次
3. 退出后再次登录：
   - 返回 `mfaRequired=true`
   - 输入认证器验证码进入系统；也可输入恢复码
4. 设置页：
   - 可修改昵称、邮箱、密码
   - 可查看 MFA 状态、重新生成恢复码、关闭 MFA
5. 后台功能：
   - 文件页：新建目录、上传（含进度与秒传）、下载、预览、分享、版本、标签、收藏、重命名、移动、删除
   - 回收站：文件/目录切换、恢复、彻底删除、清空
   - 分享页：复制链接、取消分享
   - 最近/收藏/标签/日志/系统页均可正常加载
6. 分享链接：复制 `http://服务器IP/#/s/<token>`，无痕窗口打开，输入提取码后可下载。

## 13. 自动化测试

```bash
mvn test
```

当前 11 个集成测试类、28 个用例，覆盖认证、MFA、目录、上传/秒传/断点、Range、版本、回收站、标签收藏最近、分享、预览、日志与系统接口。
