(() => {
'use strict';

const API = '/api/v1';
const state = {
    token: sessionStorage.getItem('gd_token') || null,
    user: JSON.parse(sessionStorage.getItem('gd_user') || 'null'),
    folderId: null,
    keyword: '',
    tab: 'FILE',
    shareAccess: JSON.parse(sessionStorage.getItem('gd_share_access') || '{}'),
    shareFolder: null
};

const $ = (sel, root = document) => root.querySelector(sel);
const app = () => document.getElementById('app');
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const fmtSize = (n) => { if (n == null) return '-'; const u=['B','KB','MB','GB','TB']; let i=0,v=Number(n); while(v>=1024&&i<u.length-1){v/=1024;i++;} return (i===0?v:v.toFixed(1))+' '+u[i]; };
const fmtTime = (t) => t ? new Date(t).toLocaleString() : '-';
const extOf = (name) => { const i=(name||'').lastIndexOf('.'); return i>0?name.slice(i+1).toLowerCase():''; };
const isImage = (f) => (f.mimeType||'').startsWith('image/');
const isText = (f) => (f.mimeType||'').startsWith('text/') || ['txt','md','markdown','json','yaml','yml','xml','html','css','js','ts','java','kt','py','go','rs','c','h','cpp','sh','sql','log','csv','ini','conf','properties'].includes(extOf(f.name));
const qs = (obj) => { const p=new URLSearchParams(); for(const [k,v] of Object.entries(obj||{})){ if(v!==undefined&&v!==null&&v!=='') p.set(k,v);} return p.toString(); };

class ApiError extends Error { constructor(code,message,status){ super(message); this.code=code; this.status=status; } }

async function api(path, {method='GET', body, headers={}} = {}) {
    const opts = { method, headers: { ...headers } };
    if (state.token) opts.headers['Authorization'] = 'Bearer ' + state.token;
    if (body !== undefined) {
        if (body instanceof Blob || body instanceof FormData) opts.body = body;
        else { opts.headers['Content-Type'] = 'application/json'; opts.body = JSON.stringify(body); }
    }
    const res = await fetch(API + path, opts);
    let payload = null;
    const ct = res.headers.get('content-type') || '';
    if (ct.includes('application/json')) { try { payload = await res.json(); } catch (e) {} }
    const loginEntry = path.startsWith('/auth/login') || path.startsWith('/auth/register');
    if (res.status === 401 && !path.startsWith('/public/') && !loginEntry && state.token) {
        logout();
        throw new ApiError(10002, '登录已过期', 401);
    }
    if (!res.ok) throw new ApiError(payload?.code || res.status, payload?.message || res.statusText, res.status);
    return payload?.data;
}

async function apiBlob(path, headers = {}) {
    const opts = { headers: { ...headers } };
    if (state.token) opts.headers['Authorization'] = 'Bearer ' + state.token;
    const res = await fetch(API + path, opts);
    if (!res.ok) {
        let msg = res.statusText;
        try { const j = await res.json(); msg = j.message || msg; } catch (e) {}
        throw new ApiError(res.status, msg, res.status);
    }
    return res.blob();
}

function saveBlob(blob, name) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = name || 'download';
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 15000);
}

function toast(msg, type = 'info') {
    const el = document.createElement('div');
    el.className = 'toast ' + type;
    el.textContent = msg;
    document.getElementById('toast-root').appendChild(el);
    setTimeout(() => el.remove(), 3500);
}

function openModal(title, html, onMount, wide = false) {
    const root = document.getElementById('modal-root');
    root.innerHTML = `<div class="modal ${wide?'wide':''}"><div class="modal-head"><h3>${esc(title)}</h3><button class="modal-close">×</button></div><div class="modal-body">${html}</div></div>`;
    root.classList.add('open');
    root.querySelector('.modal-close').onclick = closeModal;
    root.onclick = (e) => { if (e.target === root) closeModal(); };
    if (onMount) onMount(root.querySelector('.modal'));
}
function closeModal() {
    const root = document.getElementById('modal-root');
    root.classList.remove('open'); root.innerHTML = ''; root.onclick = null;
}

function setSession(login) {
    state.token = login.accessToken;
    state.user = login.user;
    sessionStorage.setItem('gd_token', state.token);
    sessionStorage.setItem('gd_user', JSON.stringify(state.user));
}
function logout() {
    state.token = null; state.user = null;
    sessionStorage.removeItem('gd_token'); sessionStorage.removeItem('gd_user');
    closeModal();
    location.hash = '#/login';
}
function requireAuth() {
    if (!state.token) { location.hash = '#/login'; return false; }
    return true;
}

const routes = [
    { p: /^#\/login$/, fn: renderLogin, public: true },
    { p: /^#\/s\/([^/]+)$/, fn: (m) => renderPublicShare(m[1]), public: true },
    { p: /^#\/files$/, fn: renderFiles },
    { p: /^#\/recent$/, fn: renderRecent },
    { p: /^#\/favorites$/, fn: renderFavorites },
    { p: /^#\/shares$/, fn: renderShares },
    { p: /^#\/tags$/, fn: renderTagsPage },
    { p: /^#\/recycle$/, fn: renderRecycle },
    { p: /^#\/logs$/, fn: renderLogs },
    { p: /^#\/system$/, fn: renderSystem },
    { p: /^#\/settings$/, fn: renderSettings }
];

async function route() {
    const hash = location.hash || '#/files';
    for (const r of routes) {
        const m = hash.match(r.p);
        if (m) {
            if (!r.public && !requireAuth()) return;
            closeModal();
            try { await r.fn(m); } catch (e) { toast(e.message || '加载失败', 'error'); console.error(e); }
            return;
        }
    }
    location.hash = state.token ? '#/files' : '#/login';
}
window.addEventListener('hashchange', route);

function renderShell(active, title, content, topActions = '') {
    const items = [['files','文件','▤'],['recent','最近','◷'],['favorites','收藏','★'],['shares','分享','⇗'],['tags','标签','#'],['recycle','回收站','🗑'],['logs','日志','☰'],['system','系统','⚙'],['settings','设置','⚙']];
    app().innerHTML = `<div class="layout">
        <aside class="sidebar">
            <div class="brand">Grey<span>Drive</span></div>
            ${items.map(([k,l,i]) => `<a class="nav-item ${active===k?'active':''}" href="#/${k}"><span class="nav-icon">${i}</span>${l}</a>`).join('')}
            <div class="spacer"></div>
            <div class="who">${esc(state.user?.nickname || state.user?.username || '')}</div>
        </aside>
        <main class="main">
            <header class="topbar"><div class="title">${esc(title)}</div>
            <div class="actions">${topActions}<button class="btn small" id="logout-btn">退出</button></div></header>
            <section class="content">${content}</section>
        </main>
    </div>`;
    const lo = $('#logout-btn');
    if (lo) lo.onclick = logout;
}

function authCard(title, body) {
    return `<div class="auth-wrap"><div class="auth-card"><h1>GreyDrive</h1><div class="sub">${esc(title)}</div>${body}</div></div>`;
}

/* ---------------- auth ---------------- */

function renderLogin() {
    app().innerHTML = authCard('单机云盘后台', `
        <div class="tabs"><button id="tab-login" class="active">登录</button><button id="tab-register">注册</button></div>
        <form id="login-form">
            <label>用户名<input id="l-user" autocomplete="username" required></label>
            <label>密码<input id="l-pass" type="password" autocomplete="current-password" required></label>
            <button class="btn primary" style="width:100%">登录</button>
        </form>
        <form id="register-form" style="display:none">
            <label>用户名<input id="r-user" required></label>
            <label>密码（至少 8 位）<input id="r-pass" type="password" required></label>
            <label>昵称<input id="r-nick"></label>
            <label>邮箱（可选）<input id="r-email" type="email"></label>
            <button class="btn primary" style="width:100%">注册并登录</button>
        </form>`);
    $('#tab-login').onclick = () => {
        $('#tab-login').classList.add('active'); $('#tab-register').classList.remove('active');
        $('#login-form').style.display = ''; $('#register-form').style.display = 'none';
    };
    $('#tab-register').onclick = () => {
        $('#tab-register').classList.add('active'); $('#tab-login').classList.remove('active');
        $('#login-form').style.display = 'none'; $('#register-form').style.display = '';
    };
    $('#login-form').onsubmit = async (e) => {
        e.preventDefault();
        try {
            const res = await api('/auth/login', { method: 'POST', body: { username: $('#l-user').value.trim(), password: $('#l-pass').value } });
            handleLoginResult(res);
        } catch (err) { toast(err.message, 'error'); }
    };
    $('#register-form').onsubmit = async (e) => {
        e.preventDefault();
        try {
            await api('/auth/register', { method: 'POST', body: {
                username: $('#r-user').value.trim(), password: $('#r-pass').value,
                nickname: $('#r-nick').value.trim() || null, email: $('#r-email').value.trim() || null }});
            const res = await api('/auth/login', { method: 'POST', body: { username: $('#r-user').value.trim(), password: $('#r-pass').value } });
            handleLoginResult(res);
        } catch (err) { toast(err.message, 'error'); }
    };
}

function handleLoginResult(res) {
    if (res.mfaSetupRequired) { renderMfaSetupLogin(res.mfaToken); return; }
    if (res.mfaRequired) { renderMfaVerifyLogin(res.mfaToken); return; }
    setSession(res);
    location.hash = '#/files';
    route();
}

async function renderMfaSetupLogin(mfaToken) {
    app().innerHTML = authCard('绑定二次验证', `<div id="mfa-body">正在生成密钥…</div>`);
    try {
        const data = await api('/auth/mfa/setup', { method: 'POST', body: { mfaToken } });
        $('#mfa-body').innerHTML = `
            <p>用 Google Authenticator、1Password 等 App 扫描二维码。</p>
            <img class="qr" src="${data.qrDataUrl}" alt="TOTP QR">
            <div class="secret">${esc(data.secret)}</div>
            <label>输入 App 中的 6 位验证码<input id="mfa-code" maxlength="6" inputmode="numeric" autocomplete="one-time-code"></label>
            <button class="btn primary" id="mfa-confirm" style="width:100%">确认启用</button>`;
        $('#mfa-confirm').onclick = async () => {
            try {
                const res = await api('/auth/mfa/enable', { method: 'POST', body: { mfaToken, code: $('#mfa-code').value.trim() } });
                setSession(res);
                showRecoveryCodes(res.recoveryCodes, () => { location.hash = '#/files'; route(); });
            } catch (err) { toast(err.message, 'error'); }
        };
    } catch (err) { $('#mfa-body').innerHTML = `<p>${esc(err.message)}</p><a href="#/login">返回登录</a>`; }
}

function renderMfaVerifyLogin(mfaToken) {
    app().innerHTML = authCard('二次验证', `
        <p>请输入认证器中的 6 位验证码，或使用恢复码。</p>
        <label>验证码 / 恢复码<input id="mfa-code" autocomplete="one-time-code" autofocus></label>
        <button class="btn primary" id="mfa-verify" style="width:100%">验证</button>
        <div style="margin-top:14px;text-align:center"><a href="#/login">返回登录</a></div>`);
    const submit = async () => {
        try {
            const res = await api('/auth/mfa/verify', { method: 'POST', body: { mfaToken, code: $('#mfa-code').value.trim() } });
            setSession(res);
            location.hash = '#/files'; route();
        } catch (err) { toast(err.message, 'error'); }
    };
    $('#mfa-verify').onclick = submit;
    $('#mfa-code').onkeydown = (e) => { if (e.key === 'Enter') submit(); };
}

function showRecoveryCodes(codes, done) {
    openModal('保存恢复码', `
        <p>这些恢复码只显示一次，请离线保存。每个只能使用一次。</p>
        <div class="recovery">${(codes||[]).map(c => `<code>${esc(c)}</code>`).join('')}</div>
        <button class="btn primary" id="recovery-done" style="width:100%">我已保存</button>`, (root) => {
        root.querySelector('#recovery-done').onclick = () => { closeModal(); if (done) done(); };
    });
}

/* ---------------- files ---------------- */

async function renderFiles() {
    const folderId = state.folderId;
    const keyword = state.keyword || '';
    const [folders, files, path] = await Promise.all([
        api('/folders?' + qs({ parentId: folderId === null ? undefined : folderId, size: 100 })),
        api('/files?' + qs({ folderId: folderId === null ? 0 : folderId, size: 100, keyword: keyword || undefined })),
        folderId === null ? Promise.resolve({ items: [] }) : api('/folders/' + folderId + '/path')
    ]);
    const crumbs = ['<a href="#/files" data-crumb="root">根目录</a>']
        .concat(path.items.map(it => `<span>/</span><a href="#/files" data-crumb="${it.id}">${esc(it.name)}</a>`)).join(' ');

    const folderRows = folders.items.map(f => `
        <tr>
            <td class="name"><a href="#/files" data-act="open-folder" data-id="${f.id}">📁 ${esc(f.name)}</a></td>
            <td class="muted">-</td><td class="muted">${fmtTime(f.updatedAt)}</td>
            <td class="right row-actions">
                <button class="btn small" data-act="rename-folder" data-id="${f.id}" data-name="${esc(f.name)}">重命名</button>
                <button class="btn small" data-act="move-folder" data-id="${f.id}">移动</button>
                <button class="btn small danger" data-act="delete-folder" data-id="${f.id}">删除</button>
            </td>
        </tr>`).join('');
    const fileRows = files.items.map(f => `
        <tr>
            <td class="name"><a href="javascript:void(0)" data-act="preview-file" data-id="${f.id}">${isImage(f)?'🖼️':'📄'} ${esc(f.name)} ${f.favorite?'<span class="badge warn">收藏</span>':''}</a></td>
            <td class="muted nowrap">${fmtSize(f.size)}</td>
            <td class="muted nowrap">${fmtTime(f.updatedAt)}</td>
            <td class="right row-actions">
                <button class="btn small" data-act="download-file" data-id="${f.id}" data-name="${esc(f.name)}">下载</button>
                <button class="btn small" data-act="more-file" data-id="${f.id}">更多</button>
            </td>
        </tr>`).join('');

    renderShell('files', '文件', `
        <div class="toolbar">
            <button class="btn primary" id="btn-upload">上传</button>
            <button class="btn" id="btn-new-folder">新建文件夹</button>
            <input id="search-input" placeholder="搜索文件名…" value="${esc(keyword)}" style="max-width:300px">
            <button class="btn" id="btn-search">搜索</button>
            ${keyword ? '<button class="btn ghost" id="btn-clear">清除</button>' : ''}
            <span class="muted">${files.total} 个文件 · ${folders.total} 个目录</span>
        </div>
        <div class="crumbs">${crumbs}</div>
        <div class="panel">
            <table>
                <thead><tr><th>名称</th><th>大小</th><th>更新时间</th><th class="right">操作</th></tr></thead>
                <tbody>${folderRows}${fileRows}${(folderRows+fileRows)==='' ? '<tr><td colspan="4" class="empty">空目录</td></tr>' : ''}</tbody>
            </table>
        </div>`);

    $('#btn-upload').onclick = () => pickFiles(folderId);
    $('#btn-new-folder').onclick = () => newFolderModal(folderId);
    $('#btn-search').onclick = () => { state.keyword = $('#search-input').value.trim(); renderFiles(); };
    const clear = $('#btn-clear');
    if (clear) clear.onclick = () => { state.keyword = ''; renderFiles(); };
    $('#search-input').onkeydown = (e) => { if (e.key === 'Enter') { state.keyword = e.target.value.trim(); renderFiles(); } };

    $('.content').addEventListener('click', async (e) => {
        const crumb = e.target.closest('[data-crumb]');
        if (crumb) { e.preventDefault(); state.folderId = crumb.dataset.crumb === 'root' ? null : Number(crumb.dataset.crumb); state.keyword=''; renderFiles(); return; }
        const btn = e.target.closest('[data-act]');
        if (!btn) return;
        e.preventDefault();
        const id = Number(btn.dataset.id);
        const act = btn.dataset.act;
        try {
            if (act === 'open-folder') { state.folderId = id; state.keyword=''; renderFiles(); }
            else if (act === 'preview-file') { previewFile(id); }
            else if (act === 'download-file') { downloadFile(id, btn.dataset.name); }
            else if (act === 'more-file') { const f = files.items.find(x => x.id === id); fileActions(f); }
            else if (act === 'rename-folder') { renameFolderModal(id, btn.dataset.name); }
            else if (act === 'move-folder') { moveFolderModal(id); }
            else if (act === 'delete-folder') { if (confirm('删除该目录？')) { await api('/folders/' + id, { method: 'DELETE' }); toast('已移入回收站', 'ok'); renderFiles(); } }
        } catch (err) { toast(err.message, 'error'); }
    });
}

function newFolderModal(parentId) {
    openModal('新建文件夹', `<label>名称<input id="folder-name" autofocus></label><button class="btn primary" id="folder-create" style="width:100%">创建</button>`, (root) => {
        root.querySelector('#folder-create').onclick = async () => {
            try { await api('/folders', { method: 'POST', body: { name: root.querySelector('#folder-name').value.trim(), parentId: parentId } }); closeModal(); toast('已创建','ok'); renderFiles(); }
            catch (e) { toast(e.message, 'error'); }
        };
    });
}

function renameFolderModal(id, name) {
    openModal('重命名', `<label>名称<input id="rn" value="${esc(name)}"></label><button class="btn primary" id="rn-ok" style="width:100%">保存</button>`, (root) => {
        root.querySelector('#rn-ok').onclick = async () => {
            try { await api('/folders/' + id, { method: 'PATCH', body: { name: root.querySelector('#rn').value.trim() } }); closeModal(); toast('已重命名','ok'); renderFiles(); }
            catch (e) { toast(e.message, 'error'); }
        };
    });
}

async function allFolders() {
    const result = []; const queue = [null];
    while (queue.length) {
        const parent = queue.shift();
        const page = await api('/folders?' + qs({ parentId: parent === null ? undefined : parent, size: 100 }));
        for (const f of page.items) { result.push(f); queue.push(f.id); }
    }
    return result;
}

async function moveFolderModal(id) {
    try {
        const folders = await allFolders();
        openModal('移动到', `<label>目标目录<select id="mv"><option value="">根目录</option>${folders.filter(f=>f.id!==id).map(f=>`<option value="${f.id}">${esc(f.name)}</option>`).join('')}</select></label><button class="btn primary" id="mv-ok" style="width:100%">移动</button>`, (root) => {
            root.querySelector('#mv-ok').onclick = async () => {
                try { const v = root.querySelector('#mv').value; await api('/folders/' + id + '/move', { method: 'POST', body: { parentId: v ? Number(v) : null } }); closeModal(); toast('已移动','ok'); renderFiles(); }
                catch (e) { toast(e.message, 'error'); }
            };
        });
    } catch (e) { toast(e.message, 'error'); }
}

function renameFileModal(file) {
    openModal('重命名文件', `<label>名称<input id="rn" value="${esc(file.name)}"></label><button class="btn primary" id="rn-ok" style="width:100%">保存</button>`, (root) => {
        root.querySelector('#rn-ok').onclick = async () => {
            try { await api('/files/' + file.id, { method: 'PATCH', body: { name: root.querySelector('#rn').value.trim() } }); closeModal(); toast('已重命名','ok'); renderFiles(); }
            catch (e) { toast(e.message, 'error'); }
        };
    });
}

async function moveFileModal(file) {
    try {
        const folders = await allFolders();
        openModal('移动文件', `<label>目标目录<select id="mv"><option value="">根目录</option>${folders.map(f=>`<option value="${f.id}">${esc(f.name)}</option>`).join('')}</select></label><button class="btn primary" id="mv-ok" style="width:100%">移动</button>`, (root) => {
            root.querySelector('#mv-ok').onclick = async () => {
                try { const v = root.querySelector('#mv').value; await api('/files/' + file.id + '/move', { method: 'POST', body: { folderId: v ? Number(v) : null } }); closeModal(); toast('已移动','ok'); renderFiles(); }
                catch (e) { toast(e.message, 'error'); }
            };
        });
    } catch (e) { toast(e.message, 'error'); }
}

async function downloadFile(id, name) {
    try { saveBlob(await apiBlob('/files/' + id + '/download'), name); }
    catch (e) { toast(e.message, 'error'); }
}

async function previewFile(id) {
    try {
        const file = await api('/files/' + id);
        if (isImage(file)) {
            const blob = await apiBlob('/files/' + id + '/thumbnail?size=1024');
            const url = URL.createObjectURL(blob);
            openModal(file.name, `<img src="${url}" style="max-width:100%;border-radius:8px" alt="">`, null, true);
        } else if (isText(file)) {
            const data = await api('/files/' + id + '/preview/text');
            openModal(file.name, `<pre class="mono" style="white-space:pre-wrap;max-height:60vh;overflow:auto;background:var(--bg);padding:12px;border-radius:8px">${esc(data.content)}</pre>${data.truncated?'<p class="muted">内容过长，已截断。</p>':''}`, null, true);
        } else {
            toast('该类型不支持在线预览，请下载后查看');
        }
    } catch (e) { toast(e.message, 'error'); }
}

function fileActions(file) {
    if (!file) return;
    openModal(file.name, `<div class="list">
        <button class="btn" data-fa="preview">预览</button>
        <button class="btn" data-fa="download">下载</button>
        <button class="btn" data-fa="share">创建分享</button>
        <button class="btn" data-fa="versions">版本历史</button>
        <button class="btn" data-fa="tags">标签</button>
        <button class="btn" data-fa="fav">${file.favorite ? '取消收藏' : '收藏'}</button>
        <button class="btn" data-fa="rename">重命名</button>
        <button class="btn" data-fa="move">移动</button>
        <button class="btn danger" data-fa="delete">删除</button>
    </div>`, (root) => {
        root.querySelectorAll('[data-fa]').forEach(btn => btn.onclick = async () => {
            const a = btn.dataset.fa;
            try {
                if (a === 'preview') { closeModal(); previewFile(file.id); }
                else if (a === 'download') { closeModal(); downloadFile(file.id, file.name); }
                else if (a === 'share') { closeModal(); shareModal(file); }
                else if (a === 'versions') { closeModal(); versionsModal(file); }
                else if (a === 'tags') { closeModal(); tagsModal(file); }
                else if (a === 'fav') {
                    await api('/files/' + file.id + '/favorite', { method: file.favorite ? 'DELETE' : 'PUT' });
                    closeModal(); toast(file.favorite ? '已取消收藏' : '已收藏', 'ok'); renderFiles();
                }
                else if (a === 'rename') { closeModal(); renameFileModal(file); }
                else if (a === 'move') { closeModal(); moveFileModal(file); }
                else if (a === 'delete') { await api('/files/' + file.id, { method: 'DELETE' }); closeModal(); toast('已移入回收站','ok'); renderFiles(); }
            } catch (e) { toast(e.message, 'error'); }
        });
    });
}

/* ---------------- upload ---------------- */

function pickFiles(folderId) {
    const input = document.createElement('input');
    input.type = 'file'; input.multiple = true;
    input.onchange = () => { const files = [...input.files]; if (files.length) uploadFiles(files, folderId); };
    input.click();
}

async function sha256Hex(file) {
    const buffer = await file.arrayBuffer();
    const digest = await crypto.subtle.digest('SHA-256', buffer);
    return [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, '0')).join('');
}

function uploadFiles(files, folderId) {
    openModal('上传文件', `<div id="upload-list"></div><p class="muted" style="margin-top:12px">关闭窗口不会中断已开始的请求；未完成的分片可在下次重新选择同一文件时续传。</p>`, () => {});
    const list = $('#upload-list');
    let chain = Promise.resolve();
    files.forEach(file => {
        const row = document.createElement('div');
        row.className = 'upload-row';
        row.innerHTML = `<div class="upload-name"><span>${esc(file.name)}</span><span class="muted">0%</span></div><div class="progress"><div></div></div>`;
        list.appendChild(row);
        chain = chain.then(() => uploadOne(file, folderId, row));
    });
    chain.then(() => { toast('上传完成', 'ok'); setTimeout(() => { closeModal(); if (state.folderId === folderId) renderFiles(); }, 600); })
         .catch(e => toast(e.message || '上传失败', 'error'));
}

async function uploadOne(file, folderId, row) {
    const bar = row.querySelector('.progress > div');
    const pct = row.querySelector('.upload-name span:last-child');
    const setP = (p) => { bar.style.width = p + '%'; pct.textContent = p + '%'; };
    let sha = null;
    if (file.size <= 256 * 1024 * 1024) { try { sha = await sha256Hex(file); } catch (e) { sha = null; } }
    const init = await api('/uploads', { method: 'POST', body: { fileName: file.name, size: file.size, sha256: sha, folderId: folderId === null ? null : folderId } });
    if (init.instant) { setP(100); pct.textContent = '秒传完成'; return; }
    let uploaded = new Set();
    try { const st = await api('/uploads/' + init.uploadId); uploaded = new Set(st.uploadedChunks || []); } catch (e) {}
    const cs = init.chunkSize;
    for (let i = 0; i < init.totalChunks; i++) {
        if (uploaded.has(i)) { setP(Math.round((i + 1) / init.totalChunks * 100)); continue; }
        const start = i * cs, end = Math.min(file.size, start + cs);
        const res = await fetch(API + '/uploads/' + init.uploadId + '/chunks/' + i, {
            method: 'PUT',
            headers: { 'Authorization': 'Bearer ' + state.token, 'Content-Type': 'application/octet-stream' },
            body: file.slice(start, end)
        });
        if (!res.ok) {
            let msg = '分片上传失败';
            try { const j = await res.json(); msg = j.message || msg; } catch (e) {}
            throw new ApiError(res.status, msg, res.status);
        }
        setP(Math.round((i + 1) / init.totalChunks * 100));
    }
    await api('/uploads/' + init.uploadId + '/complete', { method: 'POST', body: {} });
    pct.textContent = '完成';
}

/* ---------------- share / versions / tags ---------------- */

function shareModal(file) {
    openModal('创建分享', `
        <label>权限<select id="sh-perm"><option value="DOWNLOAD">可下载</option><option value="VIEW">仅预览</option></select></label>
        <label>提取码（留空自动生成）<input id="sh-code" maxlength="32" placeholder="自动生成"></label>
        <label>有效期<select id="sh-exp"><option value="">永不过期</option><option value="86400">1 天</option><option value="604800">7 天</option><option value="2592000">30 天</option></select></label>
        <button class="btn primary" id="sh-ok" style="width:100%">创建链接</button>
        <div id="sh-result" style="margin-top:14px"></div>`, (root) => {
        root.querySelector('#sh-ok').onclick = async () => {
            try {
                const code = root.querySelector('#sh-code').value.trim();
                const exp = root.querySelector('#sh-exp').value;
                const res = await api('/shares', { method: 'POST', body: {
                    fileId: file.id, permission: root.querySelector('#sh-perm').value,
                    code: code || null, expiresInSeconds: exp ? Number(exp) : null } });
                const link = location.origin + '/#/s/' + res.token;
                root.querySelector('#sh-result').innerHTML = `
                    <div class="link-box"><input id="sh-link" readonly value="${esc(link)}"><button class="btn small" id="sh-copy">复制</button></div>
                    <div class="link-box"><input id="sh-code-out" readonly value="${esc(res.code || '无提取码')}"><button class="btn small" id="sh-copy-code">复制提取码</button></div>
                    <p class="muted">有效期：${res.expiresAt ? fmtTime(res.expiresAt) : '永不过期'}</p>`;
                root.querySelector('#sh-copy').onclick = () => { navigator.clipboard.writeText(link); toast('链接已复制','ok'); };
                root.querySelector('#sh-copy-code').onclick = () => { if (res.code) { navigator.clipboard.writeText(res.code); toast('提取码已复制','ok'); } };
            } catch (e) { toast(e.message, 'error'); }
        };
    });
}

async function versionsModal(file) {
    try {
        const versions = await api('/files/' + file.id + '/versions');
        openModal('版本历史 · ' + file.name, `<table><thead><tr><th>版本</th><th>大小</th><th>时间</th><th class="right">操作</th></tr></thead><tbody>
            ${versions.map(v => `<tr><td>v${v.versionNo} ${v.current?'<span class="badge ok">当前</span>':''}</td><td>${fmtSize(v.size)}</td><td class="muted">${fmtTime(v.createdAt)}</td>
            <td class="right"><button class="btn small" data-vdl="${v.versionNo}">下载</button> <button class="btn small" data-vrb="${v.versionNo}">回滚</button></td></tr>`).join('')}
        </tbody></table>`, (root) => {
            root.querySelectorAll('[data-vdl]').forEach(b => b.onclick = async () => {
                try { saveBlob(await apiBlob('/files/' + file.id + '/versions/' + b.dataset.vdl + '/download'), file.name + '.v' + b.dataset.vdl); } catch (e) { toast(e.message,'error'); }
            });
            root.querySelectorAll('[data-vrb]').forEach(b => b.onclick = async () => {
                if (!confirm('回滚到 v' + b.dataset.vrb + '？将生成一个新版本。')) return;
                try { await api('/files/' + file.id + '/versions/' + b.dataset.vrb + '/rollback', { method: 'POST', body: {} }); toast('已回滚','ok'); closeModal(); renderFiles(); } catch (e) { toast(e.message,'error'); }
            });
        }, true);
    } catch (e) { toast(e.message, 'error'); }
}

async function tagsModal(file) {
    try {
        const [tags, fileTags] = await Promise.all([api('/tags'), api('/files/' + file.id + '/tags')]);
        const selected = new Set(fileTags.map(t => t.id));
        openModal('标签 · ' + file.name, `
            <div id="tag-list">${tags.length ? tags.map(t => `<label style="display:flex;gap:8px;align-items:center"><input type="checkbox" value="${t.id}" ${selected.has(t.id)?'checked':''} style="width:auto"> ${esc(t.name)}</label>`).join('') : '<p class="muted">还没有标签</p>'}</div>
            <div class="link-box"><input id="new-tag" placeholder="新标签名"><button class="btn small" id="add-tag">创建</button></div>
            <button class="btn primary" id="save-tags" style="width:100%">保存</button>`, (root) => {
            root.querySelector('#add-tag').onclick = async () => {
                try { await api('/tags', { method: 'POST', body: { name: root.querySelector('#new-tag').value.trim() } }); closeModal(); tagsModal(file); } catch (e) { toast(e.message,'error'); }
            };
            root.querySelector('#save-tags').onclick = async () => {
                try {
                    const ids = [...root.querySelectorAll('#tag-list input:checked')].map(i => Number(i.value));
                    await api('/files/' + file.id + '/tags', { method: 'PUT', body: { tagIds: ids } });
                    closeModal(); toast('标签已保存','ok');
                } catch (e) { toast(e.message,'error'); }
            };
        });
    } catch (e) { toast(e.message, 'error'); }
}

/* ---------------- other pages ---------------- */

function fileTable(files, emptyText) {
    if (!files.length) return `<div class="empty">${emptyText}</div>`;
    return `<table><thead><tr><th>名称</th><th>大小</th><th>时间</th><th class="right">操作</th></tr></thead><tbody>
        ${files.map(f => `<tr><td class="name">${isImage(f)?'🖼️':'📄'} ${esc(f.name)}</td><td class="muted">${fmtSize(f.size)}</td><td class="muted">${fmtTime(f.updatedAt || f.accessedAt)}</td>
        <td class="right"><button class="btn small" data-prev="${f.id}">预览</button> <button class="btn small" data-dl="${f.id}" data-name="${esc(f.name)}">下载</button></td></tr>`).join('')}
    </tbody></table>`;
}
function bindFileTable(rootSelector) {
    document.querySelectorAll(rootSelector + ' [data-prev]').forEach(b => b.onclick = () => previewFile(Number(b.dataset.prev)));
    document.querySelectorAll(rootSelector + ' [data-dl]').forEach(b => b.onclick = () => downloadFile(Number(b.dataset.dl), b.dataset.name));
}

async function renderRecent() {
    const data = await api('/recent?size=100');
    renderShell('recent', '最近访问', `<div class="panel"><div class="panel-body">${fileTable(data.items, '暂无记录')}</div></div>`, `<button class="btn small" id="clear-recent">清空</button>`);
    bindFileTable('.content');
    const c = $('#clear-recent'); if (c) c.onclick = async () => { await api('/recent', { method: 'DELETE' }); toast('已清空','ok'); renderRecent(); };
}

async function renderFavorites() {
    const data = await api('/favorites?size=100');
    renderShell('favorites', '收藏', `<div class="panel"><div class="panel-body">${fileTable(data.items, '暂无收藏')}</div></div>`);
    bindFileTable('.content');
}

async function renderShares() {
    const data = await api('/shares?size=100');
    const rows = data.items.map(s => {
        const link = location.origin + '/#/s/' + s.token;
        return `<tr>
            <td class="mono ellipsis">${esc(link)}</td>
            <td>${s.permission}</td>
            <td>${s.hasCode ? '<span class="badge">有提取码</span>' : '<span class="badge warn">无提取码</span>'}</td>
            <td>${s.expiresAt ? fmtTime(s.expiresAt) : '永不过期'}</td>
            <td>${esc(s.status)}</td>
            <td class="right"><button class="btn small" data-copy="${esc(link)}">复制</button> <button class="btn small danger" data-del="${s.id}">取消</button></td>
        </tr>`;
    }).join('');
    renderShell('shares', '我的分享', `<div class="panel"><table>
        <thead><tr><th>链接</th><th>权限</th><th>提取码</th><th>有效期</th><th>状态</th><th class="right">操作</th></tr></thead>
        <tbody>${rows || '<tr><td colspan="6" class="empty">还没有分享</td></tr>'}</tbody></table></div>`);
    document.querySelectorAll('[data-copy]').forEach(b => b.onclick = () => { navigator.clipboard.writeText(b.dataset.copy); toast('已复制','ok'); });
    document.querySelectorAll('[data-del]').forEach(b => b.onclick = async () => {
        if (!confirm('取消该分享？')) return;
        try { await api('/shares/' + b.dataset.del, { method: 'DELETE' }); toast('已取消','ok'); renderShares(); } catch (e) { toast(e.message,'error'); }
    });
}

async function renderTagsPage() {
    const tags = await api('/tags');
    renderShell('tags', '标签', `
        <div class="toolbar"><input id="tag-name" placeholder="新标签名" style="max-width:260px"><button class="btn primary" id="tag-add">创建标签</button></div>
        <div class="panel"><table><thead><tr><th>名称</th><th>颜色</th><th>创建时间</th><th class="right">操作</th></tr></thead><tbody>
            ${tags.map(t => `<tr><td>${esc(t.name)}</td><td class="mono">${esc(t.color || '-')}</td><td class="muted">${fmtTime(t.createdAt)}</td>
            <td class="right"><button class="btn small danger" data-del="${t.id}">删除</button></td></tr>`).join('') || '<tr><td colspan="4" class="empty">还没有标签</td></tr>'}
        </tbody></table></div>`);
    $('#tag-add').onclick = async () => {
        try { await api('/tags', { method: 'POST', body: { name: $('#tag-name').value.trim() } }); toast('已创建','ok'); renderTagsPage(); } catch (e) { toast(e.message,'error'); }
    };
    document.querySelectorAll('[data-del]').forEach(b => b.onclick = async () => {
        if (!confirm('删除标签？')) return;
        try { await api('/tags/' + b.dataset.del, { method: 'DELETE' }); toast('已删除','ok'); renderTagsPage(); } catch (e) { toast(e.message,'error'); }
    });
}

async function renderRecycle() {
    const data = await api('/recycle?' + qs({ type: state.tab, size: 100 }));
    const rows = data.items.map(it => `<tr>
        <td class="name">${it.type === 'FOLDER' ? '📁' : '📄'} ${esc(it.name)}</td>
        <td class="muted">${it.type === 'FILE' ? fmtSize(it.size) : '-'}</td>
        <td class="muted">${fmtTime(it.deletedAt)}</td>
        <td class="right"><button class="btn small" data-restore="${it.id}" data-type="${it.type}">恢复</button>
        <button class="btn small danger" data-purge="${it.id}" data-type="${it.type}">彻底删除</button></td>
    </tr>`).join('');
    renderShell('recycle', '回收站', `
        <div class="toolbar">
            <button class="btn ${state.tab==='FILE'?'primary':''}" id="tab-file">文件</button>
            <button class="btn ${state.tab==='FOLDER'?'primary':''}" id="tab-folder">目录</button>
            <span class="grow"></span>
            <button class="btn danger" id="empty-bin">清空回收站</button>
        </div>
        <div class="panel"><table><thead><tr><th>名称</th><th>大小</th><th>删除时间</th><th class="right">操作</th></tr></thead>
        <tbody>${rows || '<tr><td colspan="4" class="empty">回收站为空</td></tr>'}</tbody></table></div>`);
    $('#tab-file').onclick = () => { state.tab='FILE'; renderRecycle(); };
    $('#tab-folder').onclick = () => { state.tab='FOLDER'; renderRecycle(); };
    $('#empty-bin').onclick = async () => {
        if (!confirm('清空回收站将彻底删除所有内容，不可恢复。继续？')) return;
        try { const n = await api('/recycle', { method: 'DELETE' }); toast('已清理 ' + n + ' 项','ok'); renderRecycle(); } catch (e) { toast(e.message,'error'); }
    };
    document.querySelectorAll('[data-restore]').forEach(b => b.onclick = async () => {
        try {
            const body = b.dataset.type === 'FOLDER' ? { fileIds: [], folderIds: [Number(b.dataset.restore)] } : { fileIds: [Number(b.dataset.restore)], folderIds: [] };
            await api('/recycle/restore', { method: 'POST', body }); toast('已恢复','ok'); renderRecycle();
        } catch (e) { toast(e.message,'error'); }
    });
    document.querySelectorAll('[data-purge]').forEach(b => b.onclick = async () => {
        if (!confirm('彻底删除？不可恢复。')) return;
        try { await api('/recycle/' + b.dataset.purge + '?type=' + b.dataset.type, { method: 'DELETE' }); toast('已删除','ok'); renderRecycle(); } catch (e) { toast(e.message,'error'); }
    });
}

async function renderLogs() {
    const data = await api('/logs?size=100');
    const rows = data.items.map(l => `<tr>
        <td class="mono">${esc(l.action)}</td><td>${esc(l.resourceType || '-')}</td><td>${l.resourceId ?? '-'}</td>
        <td class="ellipsis">${esc(l.detail || '')}</td><td class="mono">${esc(l.ip || '-')}</td>
        <td>${l.success ? '<span class="badge ok">成功</span>' : '<span class="badge bad">失败</span>'}</td>
        <td class="muted nowrap">${fmtTime(l.createdAt)}</td>
    </tr>`).join('');
    renderShell('logs', '操作日志', `<div class="panel"><table>
        <thead><tr><th>动作</th><th>类型</th><th>资源</th><th>详情</th><th>IP</th><th>结果</th><th>时间</th></tr></thead>
        <tbody>${rows || '<tr><td colspan="7" class="empty">暂无日志</td></tr>'}</tbody></table></div>`);
}

async function renderSystem() {
    const [info, storage] = await Promise.all([api('/system/info'), api('/system/storage')]);
    const usedPct = storage.diskTotalBytes ? ((storage.diskTotalBytes - storage.diskFreeBytes) / storage.diskTotalBytes * 100).toFixed(1) : 0;
    renderShell('system', '系统', `
        <div class="cards">
            <div class="card"><div class="k">版本</div><div class="v">${esc(info.name)} ${esc(info.version)}</div></div>
            <div class="card"><div class="k">运行时长</div><div class="v">${Math.floor(info.uptimeSeconds/3600)}h ${Math.floor(info.uptimeSeconds%3600/60)}m</div></div>
            <div class="card"><div class="k">监听端口</div><div class="v">${info.port}</div></div>
            <div class="card"><div class="k">文件数</div><div class="v">${storage.fileCount}</div></div>
            <div class="card"><div class="k">逻辑占用</div><div class="v">${fmtSize(storage.logicalBytes)}</div></div>
            <div class="card"><div class="k">物理占用</div><div class="v">${fmtSize(storage.physicalBytes)}</div></div>
            <div class="card"><div class="k">Blob 数</div><div class="v">${storage.blobCount}</div></div>
            <div class="card"><div class="k">磁盘已用</div><div class="v">${usedPct}%</div></div>
        </div>
        <div class="panel"><div class="panel-body">
            <p>Java：${esc(info.javaVersion)}　系统：${esc(info.osName)} / ${esc(info.osArch)}</p>
            <p>数据目录：<span class="mono">${esc(info.dataDir)}</span></p>
            <p>磁盘总量 ${fmtSize(storage.diskTotalBytes)}，剩余 ${fmtSize(storage.diskUsableBytes)}。</p>
        </div></div>`);
}

async function renderSettings() {
    const [me, mfa] = await Promise.all([api('/users/me'), api('/users/me/mfa')]);
    renderShell('settings', '设置', `
        <div class="panel" style="margin-bottom:16px"><div class="panel-head"><strong>个人资料</strong></div><div class="panel-body">
            <label>用户名<input value="${esc(me.username)}" disabled></label>
            <label>昵称<input id="set-nick" value="${esc(me.nickname || '')}"></label>
            <label>邮箱<input id="set-email" value="${esc(me.email || '')}"></label>
            <button class="btn primary" id="save-profile">保存资料</button>
        </div></div>
        <div class="panel" style="margin-bottom:16px"><div class="panel-head"><strong>修改密码</strong></div><div class="panel-body">
            <label>原密码<input id="pw-old" type="password"></label>
            <label>新密码（至少 8 位）<input id="pw-new" type="password"></label>
            <button class="btn primary" id="save-pw">修改密码</button>
        </div></div>
        <div class="panel"><div class="panel-head"><strong>二次验证（TOTP）</strong>
            <span class="badge ${mfa.enabled?'ok':'warn'}">${mfa.enabled?'已启用':'未启用'}</span></div>
            <div class="panel-body">
                ${mfa.enabled ? `<p>剩余恢复码：${mfa.unusedRecoveryCodes} 个。${mfa.unusedRecoveryCodes===0?'建议重新生成。':''}</p>
                    <button class="btn" id="regen-codes">重新生成恢复码</button>
                    <button class="btn danger" id="disable-mfa">关闭二次验证</button>`
                : `<p>启用后，登录时需要输入认证器中的动态验证码。</p><button class="btn primary" id="enable-mfa">启用二次验证</button>`}
            </div>
        </div>`);
    $('#save-profile').onclick = async () => {
        try { await api('/users/me', { method: 'PUT', body: { nickname: $('#set-nick').value.trim(), email: $('#set-email').value.trim() || null } }); toast('资料已保存','ok'); } catch (e) { toast(e.message,'error'); }
    };
    $('#save-pw').onclick = async () => {
        try { await api('/users/me/password', { method: 'PUT', body: { oldPassword: $('#pw-old').value, newPassword: $('#pw-new').value } }); toast('密码已修改','ok'); $('#pw-old').value=''; $('#pw-new').value=''; } catch (e) { toast(e.message,'error'); }
    };
    const enable = $('#enable-mfa');
    if (enable) enable.onclick = async () => {
        try {
            const data = await api('/auth/mfa/setup', { method: 'POST', body: {} });
            openModal('启用二次验证', `<img class="qr" src="${data.qrDataUrl}"><div class="secret">${esc(data.secret)}</div>
                <label>输入 6 位验证码<input id="code" maxlength="6" inputmode="numeric"></label>
                <button class="btn primary" id="ok" style="width:100%">确认启用</button>`, (root) => {
                root.querySelector('#ok').onclick = async () => {
                    try { const res = await api('/auth/mfa/enable', { method: 'POST', body: { code: root.querySelector('#code').value.trim() } }); closeModal(); showRecoveryCodes(res.recoveryCodes, () => renderSettings()); }
                    catch (e) { toast(e.message,'error'); }
                };
            });
        } catch (e) { toast(e.message,'error'); }
    };
    const regen = $('#regen-codes');
    if (regen) regen.onclick = () => openModal('重新生成恢复码', `<label>当前验证码<input id="code"></label><button class="btn primary" id="ok" style="width:100%">生成</button>`, (root) => {
        root.querySelector('#ok').onclick = async () => {
            try { const res = await api('/users/me/mfa/recovery-codes', { method: 'POST', body: { code: root.querySelector('#code').value.trim() } }); closeModal(); showRecoveryCodes(res.recoveryCodes, () => renderSettings()); }
            catch (e) { toast(e.message,'error'); }
        };
    });
    const disable = $('#disable-mfa');
    if (disable) disable.onclick = () => openModal('关闭二次验证', `<label>密码<input id="pw" type="password"></label><label>当前验证码<input id="code"></label><button class="btn danger" id="ok" style="width:100%">确认关闭</button>`, (root) => {
        root.querySelector('#ok').onclick = async () => {
            try { await api('/users/me/mfa/disable', { method: 'POST', body: { password: root.querySelector('#pw').value, code: root.querySelector('#code').value.trim() } }); closeModal(); toast('已关闭','ok'); renderSettings(); }
            catch (e) { toast(e.message,'error'); }
        };
    });
}

/* ---------------- public share ---------------- */

function publicShell(body) {
    return `<div class="auth-wrap"><div class="auth-card" style="width:min(720px,100%)">
        <h1>Grey<span style="color:var(--primary)">Drive</span></h1><div class="sub">分享链接</div>${body}</div></div>`;
}

async function renderPublicShare(token) {
    if (state.currentShareToken !== token) { state.currentShareToken = token; state.shareFolder = null; }
    let info;
    try { info = await api('/public/shares/' + token); }
    catch (e) { app().innerHTML = publicShell(`<div class="empty">${esc(e.message)}</div>`); return; }
    const access = state.shareAccess[token];
    if (info.needCode && !access) {
        app().innerHTML = publicShell(`
            <p>该分享需要提取码。</p>
            <label>提取码<input id="p-code" autofocus></label>
            <button class="btn primary" id="p-verify" style="width:100%">打开分享</button>`);
        $('#p-verify').onclick = async () => {
            try {
                const res = await api('/public/shares/' + token + '/verify', { method: 'POST', body: { code: $('#p-code').value.trim() } });
                state.shareAccess[token] = res.shareToken;
                sessionStorage.setItem('gd_share_access', JSON.stringify(state.shareAccess));
                renderPublicShare(token);
            } catch (e) { toast(e.message,'error'); }
        };
        return;
    }
    const headers = access ? { 'X-Share-Token': access } : {};
    if (info.type === 'FILE') {
        app().innerHTML = publicShell(`
            <div class="list-item"><div><div class="name">📄 ${esc(info.name)}</div><div class="meta">${esc(info.permission)}${info.expiresAt?' · '+fmtTime(info.expiresAt):' · 永不过期'}</div></div>
            <button class="btn primary" id="p-dl">下载</button></div>`);
        $('#p-dl').onclick = async () => {
            try { saveBlob(await apiBlob('/public/shares/' + token + '/files/' + info.fileId + '/download', headers), info.name); }
            catch (e) { toast(e.message,'error'); }
        };
        return;
    }
    const data = await api('/public/shares/' + token + '/files?' + qs({ parentId: state.shareFolder }), { headers });
    const items = data.items.map(it => `<div class="list-item">
        <div>${it.type === 'FOLDER' ? '📁' : '📄'} ${esc(it.name)} <span class="meta">${it.type === 'FILE' ? fmtSize(it.size) : ''}</span></div>
        ${it.type === 'FOLDER'
            ? `<button class="btn small" data-open="${it.id}">打开</button>`
            : `<button class="btn small primary" data-dl="${it.id}" data-name="${esc(it.name)}">下载</button>`}
    </div>`).join('');
    app().innerHTML = publicShell(`
        <p>目录：${esc(info.name)}</p>
        <div class="list">${items || '<div class="empty">空目录</div>'}</div>`);
    document.querySelectorAll('[data-open]').forEach(b => b.onclick = () => { state.shareFolder = Number(b.dataset.open); renderPublicShare(token); });
    document.querySelectorAll('[data-dl]').forEach(b => b.onclick = async () => {
        try { saveBlob(await apiBlob('/public/shares/' + token + '/files/' + b.dataset.dl + '/download', headers), b.dataset.name); }
        catch (e) { toast(e.message,'error'); }
    });
}

/* ---------------- boot ---------------- */

if (!state.token && !location.hash) location.hash = '#/login';
route();
})();
