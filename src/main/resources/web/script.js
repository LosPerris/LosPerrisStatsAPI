/*
 * LosPerrisStats — dashboard web.
 * Seguridad: todo el contenido que viene del servidor se inserta con textContent / propiedades del DOM
 * (nunca innerHTML con datos). La API key se envía siempre en el header Authorization.
 */
(function () {
    'use strict';

    // ================================================================== utilidades
    const $ = (sel, root) => (root || document).querySelector(sel);
    const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

    /** Crea un elemento. props: className, text, attrs, on, children. */
    function el(tag, props, children) {
        const e = document.createElement(tag);
        if (props) {
            if (props.className) e.className = props.className;
            if (props.text != null) e.textContent = String(props.text);
            if (props.title) e.title = props.title;
            if (props.attrs) Object.keys(props.attrs).forEach((k) => e.setAttribute(k, props.attrs[k]));
            if (props.on) Object.keys(props.on).forEach((k) => e.addEventListener(k, props.on[k]));
        }
        (children || []).forEach((c) => c != null && e.appendChild(typeof c === 'string' ? document.createTextNode(c) : c));
        return e;
    }
    const clear = (node) => { while (node.firstChild) node.removeChild(node.firstChild); return node; };

    const T = I18N.t;
    let nf, nf2;
    function setFormats() {
        nf = new Intl.NumberFormat(I18N.locale);
        nf2 = new Intl.NumberFormat(I18N.locale, { maximumFractionDigits: 2 });
    }
    setFormats();
    const fmtInt = (v) => nf.format(Math.round(Number(v) || 0));
    const fmtHours = (v) => nf2.format(Number(v) || 0) + ' h';
    const fmtKm = (v) => nf2.format(Number(v) || 0) + ' km';
    const fmtHearts = (v) => nf2.format(Number(v) || 0) + ' ❤';

    function fmtDuration(sec) {
        sec = Math.max(0, Math.floor(sec || 0));
        const d = Math.floor(sec / 86400), h = Math.floor(sec % 86400 / 3600), m = Math.floor(sec % 3600 / 60);
        if (d) return d + 'd ' + h + 'h';
        if (h) return h + 'h ' + m + 'm';
        return m + 'm';
    }
    function timeAgo(ms) {
        if (!ms) return '—';
        const s = Math.floor((Date.now() - ms) / 1000);
        if (s < 60) return T('time.now');
        if (s < 3600) return T('time.min', { n: Math.floor(s / 60) });
        if (s < 86400) return T('time.h', { n: Math.floor(s / 3600) });
        return T('time.d', { n: Math.floor(s / 86400) });
    }
    const fmtClock = (ms) => new Date(ms).toLocaleTimeString(I18N.locale, { hour: '2-digit', minute: '2-digit' });
    const fmtDate = (ms) => new Date(ms).toLocaleDateString(I18N.locale, { day: '2-digit', month: 'short', year: 'numeric' });
    const fmtDateTime = (ms) => new Date(ms).toLocaleString(I18N.locale, { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
    const VALID_NAME = /^[A-Za-z0-9_]{1,16}$/;

    // Estadísticas conocidas (clave del JSON, tipo de /api/top, icono, color, formato). La etiqueta sale de i18n.
    const STATS = [
        { key: 'player_kills', top: 'player_kills', icon: '⚔️', color: '#ef4444', fmt: fmtInt },
        { key: 'deaths', top: 'deaths', icon: '☠️', color: '#94a3b8', fmt: fmtInt },
        { key: 'mob_kills', top: 'mob_kills', icon: '🗡️', color: '#f59e0b', fmt: fmtInt },
        { key: 'hours_played', top: 'playtime', icon: '⏱️', color: '#5fbf3a', fmt: fmtHours },
        { key: 'blocks_broken', top: 'blocks_broken', icon: '⛏️', color: '#c08552', fmt: fmtInt },
        { key: 'blocks_placed', top: 'blocks_placed', icon: '🧱', color: '#e07a5f', fmt: fmtInt },
        { key: 'ores_mined', top: 'ores_mined', icon: '💎', color: '#38bdf8', fmt: fmtInt },
        { key: 'jumps', top: 'jumps', icon: '🐇', color: '#a78bfa', fmt: fmtInt },
        { key: 'distance_walked', top: 'distance_walked', icon: '👣', color: '#4fd1c5', fmt: fmtKm },
        { key: 'fish_caught', top: 'fish_caught', icon: '🎣', color: '#60a5fa', fmt: fmtInt },
        { key: 'villager_trades', top: 'villager_trades', icon: '🤝', color: '#34d399', fmt: fmtInt },
        { key: 'damage_dealt', top: 'damage_dealt', icon: '💥', color: '#f87171', fmt: fmtHearts },
        { key: 'damage_taken', top: 'damage_taken', icon: '🩹', color: '#cbd5e1', fmt: fmtHearts },
    ];
    STATS.forEach((st) => Object.defineProperty(st, 'label', { get() { return T('stat.' + st.key); } }));
    const statByKey = (k) => STATS.find((s) => s.key === k || s.top === k);
    const ORES = ['coal', 'iron', 'copper', 'gold', 'redstone', 'lapis', 'diamond', 'emerald', 'quartz', 'ancient_debris'];
    const ORE_COLORS = { coal: '#4b5563', iron: '#d6c3b3', copper: '#e08a4f', gold: '#f5c542', redstone: '#ef4444', lapis: '#3b5bdb',
        diamond: '#4fd1c5', emerald: '#22c55e', quartz: '#f1f5f9', ancient_debris: '#8b5e3c' };
    const DEATHS = ['player', 'mob', 'fall', 'lava', 'fire', 'drowning', 'explosion', 'void', 'suffocation', 'starvation',
        'magic', 'freeze', 'contact', 'lightning', 'other'];
    const DEATH_COLORS = ['#ef4444', '#22c55e', '#e2e8f0', '#f97316', '#fb923c', '#3b82f6', '#dc2626', '#7c3aed', '#94a3b8',
        '#eab308', '#d946ef', '#67e8f9', '#84cc16', '#facc15', '#64748b'];
    const TIERS = ['common', 'uncommon', 'rare', 'epic', 'legendary'];

    // ================================================================== modo público (?public)
    // Solo lectura y sin api-key: todas las llamadas van a /api/public/... y se ocultan chat y admin.
    const PUBLIC = new URLSearchParams(location.search).has('public');
    let publicInfo = null;
    /** Módulos activos según /api/summary (history, periods, achievements...). */
    let modules = {};
    const SECTION_OF = { overview: 'summary', leaderboards: 'leaderboards', players: 'players', player: 'profiles', compare: 'profiles', seasons: 'seasons', events: 'events' };
    /** Páginas que solo existen con su módulo activo. */
    const MODULE_OF = { seasons: 'seasons', events: 'events' };
    function section(name) { return !PUBLIC || !!(publicInfo && publicInfo.sections && publicInfo.sections[name]); }
    /** ¿Está disponible una función opcional? (módulo activo y, en modo público, sección visible) */
    function feature(name) { return modules[name] !== false && section(name); }
    function pageAllowed(name) {
        if (PUBLIC && (name === 'chat' || name === 'admin')) return false;
        if (MODULE_OF[name] && modules[MODULE_OF[name]] !== true) return false;
        return !SECTION_OF[name] || section(SECTION_OF[name]);
    }
    const apiUrl = (p) => (PUBLIC && p.startsWith('/api/') ? '/api/public/' + p.slice(5) : p);

    // ================================================================== avatares (con respaldo offline)
    const headCache = new Map();
    function hashCode(str) { let h = 0; for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) | 0; return Math.abs(h); }
    /** Cabeza pixelada generada localmente (si minotar.net no está disponible). */
    function generatedHead(name) {
        if (headCache.has(name)) return headCache.get(name);
        const c = document.createElement('canvas');
        c.width = c.height = 8;
        const x = c.getContext('2d');
        const h = hashCode(name.toLowerCase());
        const skins = ['#f1c27d', '#e0ac69', '#c68642', '#8d5524', '#ffdbac', '#d1a17a'];
        const hairs = ['#3b2a1a', '#6b4423', '#111111', '#d6b370', '#a33a1a', '#5a5a5a', '#2d4a8a'];
        const eyes = ['#2b59c3', '#3f8f3f', '#5b3a1e', '#7a3fb0', '#222222'];
        x.fillStyle = skins[h % skins.length]; x.fillRect(0, 0, 8, 8);
        x.fillStyle = hairs[(h >> 3) % hairs.length]; x.fillRect(0, 0, 8, 2); x.fillRect(0, 2, 1, (h >> 6) % 3 + 1); x.fillRect(7, 2, 1, (h >> 8) % 3 + 1);
        x.fillStyle = '#ffffff'; x.fillRect(1, 4, 2, 1); x.fillRect(5, 4, 2, 1);
        x.fillStyle = eyes[(h >> 10) % eyes.length]; x.fillRect(2, 4, 1, 1); x.fillRect(5, 4, 1, 1);
        x.fillStyle = 'rgba(0,0,0,.35)'; x.fillRect(3, 6, 2, 1);
        const url = c.toDataURL();
        headCache.set(name, url);
        return url;
    }
    let minotarDown = false;
    function head(name, cls) {
        const img = el('img', { className: 'head' + (cls ? ' ' + cls : ''), attrs: { alt: '', loading: 'lazy', width: '32', height: '32' } });
        img.referrerPolicy = 'no-referrer';
        const safe = VALID_NAME.test(name || '') ? name : 'Steve';
        if (minotarDown) { img.src = generatedHead(safe); return img; }
        img.addEventListener('error', () => { minotarDown = true; img.src = generatedHead(safe); }, { once: true });
        img.src = 'https://minotar.net/helm/' + encodeURIComponent(safe) + '/64.png';
        return img;
    }

    // ================================================================== API
    const KEY_STORE = 'lpa_key';
    let apiKey = sessionStorage.getItem(KEY_STORE) || localStorage.getItem(KEY_STORE) || '';

    class ApiError extends Error { constructor(status, msg) { super(msg); this.status = status; } }

    async function api(path, opts) {
        opts = opts || {};
        const headers = Object.assign({}, opts.headers || {});
        if (apiKey && !PUBLIC) headers.Authorization = 'Bearer ' + apiKey;
        if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
        const res = await fetch(apiUrl(path), {
            method: opts.method || 'GET', headers, cache: 'no-store',
            body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
        });
        let data = null;
        try { data = await res.json(); } catch (e) { /* sin cuerpo */ }
        if (res.status === 401 && !PUBLIC && !path.startsWith('/api/admin')) { onUnauthorized(); }
        if (res.status === 429 && PUBLIC) throw new ApiError(429, T('pub.rate'));
        if (!res.ok) throw new ApiError(res.status, (data && data.error) || ('HTTP ' + res.status));
        return data;
    }

    // ================================================================== toasts / diálogo
    function toast(msg, type) {
        const t = el('div', { className: 'toast' + (type ? ' ' + type : ''), text: msg });
        $('#toasts').appendChild(t);
        setTimeout(() => t.remove(), type === 'error' ? 6000 : 3500);
    }

    function confirmDialog(title, text, requireText) {
        const dlg = $('#confirm-dialog');
        $('#confirm-title').textContent = title;
        $('#confirm-text').textContent = text;
        const input = $('#confirm-input');
        input.hidden = !requireText;
        input.value = '';
        input.placeholder = requireText || '';
        const ok = $('#confirm-ok');
        const sync = () => { ok.disabled = !!requireText && input.value !== requireText; };
        input.oninput = sync; sync();
        return new Promise((resolve) => {
            dlg.addEventListener('close', () => resolve(dlg.returnValue === 'ok'), { once: true });
            if (typeof dlg.showModal === 'function') dlg.showModal();
            else resolve(window.confirm(title + '\n' + text));
        });
    }

    /** Barra de progreso (0..1). */
    function progressBar(frac, mini) {
        const bar = el('span');
        const f = Math.max(0, Math.min(1, Number(frac) || 0));
        const prog = el('div', { className: 'progress' + (mini ? ' mini' : '') + (f >= 1 ? ' full' : '') }, [bar]);
        requestAnimationFrame(() => { bar.style.width = Math.round(f * 100) + '%'; });
        return prog;
    }

    // Avisos en vivo (entradas, alertas, hitos, misiones). Cada tipo se puede apagar; se guarda en este navegador.
    const LIVE_KINDS = ['join', 'alert', 'milestone', 'quest'];
    const LiveToasts = (function () {
        const STORE = 'lpa_toasts_off';
        const ICON = { join: '👋', alert: '⚠️', milestone: '🏆', quest: '📜' };
        let off;
        try { off = new Set(JSON.parse(localStorage.getItem(STORE) || '[]')); } catch (e) { off = new Set(); }
        return {
            enabled: (kind) => !off.has(kind),
            set(kind, on) {
                if (on) off.delete(kind); else off.add(kind);
                try { localStorage.setItem(STORE, JSON.stringify(Array.from(off))); } catch (e) { /* modo privado */ }
            },
            show(m) {
                const kind = m.kind || m.type;
                if (LIVE_KINDS.indexOf(kind) < 0 || off.has(kind)) return;
                const text = kind === 'join' ? T('toast.join', { player: m.user }) : m.message;
                if (!text) return;
                toast(ICON[kind] + ' ' + text, m.level === 'warn' ? 'warn' : m.level === 'ok' ? 'ok' : '');
            },
        };
    })();
    function renderToastSwitches() {
        const box = $('#toast-switches');
        if (!box) return;
        clear(box).appendChild(el('span', { className: 'muted small', text: T('toast.title') }));
        LIVE_KINDS.forEach((kind) => {
            const input = el('input', { attrs: { type: 'checkbox' }, on: { change: (e) => LiveToasts.set(kind, e.target.checked) } });
            input.checked = LiveToasts.enabled(kind);
            box.appendChild(el('label', { className: 'check' }, [input, el('span', { text: T('toast.kind.' + kind) })]));
        });
    }

    /** Lee un stream SSE (fetch + header Authorization) y llama a onData con cada evento JSON. */
    async function readSSE(res, onData) {
        const reader = res.body.getReader();
        const dec = new TextDecoder();
        let buf = '';
        for (;;) {
            const { value, done } = await reader.read();
            if (done) return;
            buf += dec.decode(value, { stream: true });
            let idx;
            while ((idx = buf.indexOf('\n\n')) >= 0) {
                const chunk = buf.slice(0, idx);
                buf = buf.slice(idx + 2);
                const data = chunk.split('\n').filter((l) => l.startsWith('data:')).map((l) => l.slice(5).trimStart()).join('\n');
                if (!data) continue;
                let obj;
                try { obj = JSON.parse(data); } catch (e) { continue; }
                onData(obj);
            }
        }
    }

    // ================================================================== sesión
    let started = false;
    let securityEnabled = true;
    let lastSummary = null;

    function showLogin(msg) {
        $('#app').hidden = true;
        $('#login-overlay').hidden = false;
        const err = $('#login-error');
        err.hidden = !msg;
        err.textContent = msg || '';
        setTimeout(() => $('#apiKeyInput').focus(), 50);
    }

    function onUnauthorized() {
        if (!started) return;
        logout(T('login.expired'));
    }

    function logout(msg) {
        sessionStorage.removeItem(KEY_STORE);
        localStorage.removeItem(KEY_STORE);
        apiKey = '';
        started = false;
        Chat.stop();
        Console.stop();
        stopTimers();
        showLogin(msg);
    }

    async function bootPublic() {
        document.body.classList.add('public');
        let info;
        try {
            const res = await fetch('/api/public/info', { cache: 'no-store' });
            if (res.status === 404) throw new ApiError(404, T('pub.disabled'));
            if (!res.ok) throw new ApiError(res.status, 'HTTP ' + res.status);
            info = await res.json();
        } catch (e) {
            $('#login-form').classList.add('public-error');
            showLogin(e.status === 404 ? T('pub.disabled') : T('login.unreachable-detail', { error: e.message }));
            return;
        }
        publicInfo = info;
        if (info.language) I18N.serverDefault(info.language);
        if (info.title) $('#brand-sub').textContent = info.title;
        securityEnabled = false;
        if (section('summary')) { try { lastSummary = await api('/api/summary'); } catch (e) { lastSummary = null; } }
        startApp();
    }

    async function boot() {
        if (PUBLIC) { bootPublic(); return; }
        try {
            const res = await fetch('/api/summary', { headers: apiKey ? { Authorization: 'Bearer ' + apiKey } : {}, cache: 'no-store' });
            if (res.status === 401) {
                if (apiKey) { sessionStorage.removeItem(KEY_STORE); localStorage.removeItem(KEY_STORE); apiKey = ''; }
                showLogin('');
                return;
            }
            if (!res.ok) throw new Error('HTTP ' + res.status);
            lastSummary = await res.json();
            securityEnabled = lastSummary.security_enabled !== false;
            if (lastSummary.language) I18N.serverDefault(lastSummary.language);
            startApp();
        } catch (e) {
            showLogin(T('login.unreachable-detail', { error: e.message }));
        }
    }

    $('#login-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const key = $('#apiKeyInput').value.trim();
        if (!key) return;
        const btn = $('#login-form button');
        btn.disabled = true;
        try {
            const res = await fetch('/api/summary', { headers: { Authorization: 'Bearer ' + key }, cache: 'no-store' });
            if (res.status === 401) throw new Error(T('login.bad-key'));
            if (!res.ok) throw new Error(T('login.server-error', { status: res.status }));
            apiKey = key;
            (($('#rememberKey').checked) ? localStorage : sessionStorage).setItem(KEY_STORE, key);
            lastSummary = await res.json();
            if (lastSummary.language) I18N.serverDefault(lastSummary.language);
            $('#apiKeyInput').value = '';
            startApp();
        } catch (err) {
            const box = $('#login-error');
            box.hidden = false;
            box.textContent = err.message === 'Failed to fetch' ? T('login.unreachable') : err.message;
        } finally {
            btn.disabled = false;
        }
    });
    $('#logout-btn').addEventListener('click', () => logout(''));
    $('#lang-btn').addEventListener('click', () => I18N.toggle());
    I18N.onChange(() => {
        setFormats();
        renderChips();
        renderToastSwitches();
        if (started) { updateServerPill(lastSummary); route(); }
    });

    function applyModules(sum) {
        if (sum && sum.modules) modules = sum.modules;
        $$('.nav-link').forEach((a) => { a.hidden = !pageAllowed(a.dataset.page); });
        $('#lb-period').hidden = !feature('periods');
        const ap = $('#admin-periods');
        if (ap) ap.hidden = !feature('periods');
    }

    function startApp() {
        started = true;
        $('#login-overlay').hidden = true;
        $('#app').hidden = false;
        $('#logout-btn').hidden = !securityEnabled;
        $('#public-badge').hidden = !PUBLIC;
        applyModules(lastSummary);
        if (!PUBLIC) Chat.start();
        updateServerPill(lastSummary);
        refreshNames();
        route();
    }

    // ================================================================== timers por página
    let timers = [];
    function every(ms, fn) { timers.push(setInterval(() => { if (!document.hidden) fn(); }, ms)); }
    function stopTimers() { timers.forEach(clearInterval); timers = []; }
    // Pill del servidor (cada 15 s, en todas las páginas)
    setInterval(async () => {
        if (!started || document.hidden || !section('summary')) return;
        try { lastSummary = await api('/api/summary'); applyModules(lastSummary); updateServerPill(lastSummary); } catch (e) { updateServerPill(null); }
    }, 15000);

    function updateServerPill(sum) {
        const pill = $('#server-pill');
        pill.classList.remove('ok', 'warn', 'bad');
        if (PUBLIC && !sum) {
            pill.classList.add('ok');
            $('#server-pill-text').textContent = T('pub.badge');
            return;
        }
        if (!sum || !sum.server || !sum.server.updated) {
            pill.classList.add('bad');
            $('#server-pill-text').textContent = T(sum ? 'pill.no-data' : 'pill.offline');
            return;
        }
        const s = sum.server;
        pill.classList.add(s.tps >= 18 ? 'ok' : 'warn');
        $('#server-pill-text').textContent = T('pill.status', { online: s.online, max: s.max_players, tps: s.tps });
    }

    async function refreshNames() {
        try {
            const list = await api('/api/players/list?size=100&sort=last_updated');
            const dl = clear($('#player-names'));
            list.items.forEach((p) => dl.appendChild(el('option', { attrs: { value: p.name } })));
        } catch (e) { /* opcional */ }
    }

    $('#quick-search').addEventListener('submit', (e) => {
        e.preventDefault();
        const v = $('#quickSearchInput').value.trim();
        if (!VALID_NAME.test(v)) { toast(T('search.invalid'), 'warn'); return; }
        $('#quickSearchInput').value = '';
        location.hash = '#/player/' + encodeURIComponent(v);
    });

    // ================================================================== router
    const PAGES = {
        overview: { key: 'overview', render: renderOverview },
        leaderboards: { key: 'leaderboards', render: renderLeaderboards },
        players: { key: 'players', render: renderPlayers },
        player: { key: 'profile', render: renderProfile, section: 'profile', nav: 'players' },
        compare: { key: 'compare', render: renderCompare },
        chat: { key: 'chat', render: renderChat },
        admin: { key: 'admin', render: renderAdmin },
        seasons: { key: 'seasons', render: renderSeasons },
        events: { key: 'events', render: renderEvents },
    };

    function route() {
        if (!started) return;
        const parts = location.hash.replace(/^#\/?/, '').split('/').map((p) => { try { return decodeURIComponent(p); } catch (e) { return ''; } });
        let name = PAGES[parts[0]] && pageAllowed(parts[0]) ? parts[0] : 'overview';
        if (!pageAllowed(name)) name = Object.keys(PAGES).find(pageAllowed) || 'overview';
        const page = PAGES[name];
        stopTimers();
        $$('.page').forEach((p) => { p.hidden = true; });
        const section = $('#page-' + (page.section || name));
        section.hidden = false;
        // reinicia la animación de entrada
        section.style.animation = 'none'; void section.offsetWidth; section.style.animation = '';
        $$('.nav-link').forEach((a) => a.classList.toggle('active', a.dataset.page === (page.nav || name)));
        const title = T('page.' + page.key);
        $('#page-title').textContent = title;
        $('#page-subtitle').textContent = name === 'player' ? '' : T('page.' + page.key + '.sub');
        document.title = title + ' · LosPerris Dashboard';
        Chat.setVisible(name === 'chat');
        if (name !== 'admin') Console.stop();
        page.render(parts.slice(1));
        window.scrollTo(0, 0);
    }
    window.addEventListener('hashchange', route);

    // ================================================================== RESUMEN
    let historyHours = 24;
    $$('#history-range button').forEach((b) => b.addEventListener('click', () => {
        $$('#history-range button').forEach((x) => x.classList.toggle('active', x === b));
        historyHours = Number(b.dataset.h);
        loadHistory();
    }));

    function kpi(icon, label, value, sub, meter) {
        const children = [el('div', { className: 'kpi-label', text: label }), el('div', { className: 'kpi-value', text: value })];
        if (sub) children.push(el('div', { className: 'kpi-sub', text: sub }));
        if (meter != null) {
            const bar = el('span');
            bar.style.width = Math.round(Math.max(0, Math.min(1, meter)) * 100) + '%';
            if (meter > 0.85) bar.style.background = 'var(--redstone)';
            else if (meter > 0.65) bar.style.background = 'var(--warn)';
            children.push(el('div', { className: 'meter' }, [bar]));
        }
        return el('div', { className: 'card kpi' }, [el('div', { className: 'kpi-ico', text: icon }), el('div', null, children)]);
    }

    async function renderOverview() {
        const kpis = $('#server-kpis');
        $('#ov-history').hidden = PUBLIC; // las gráficas de TPS/online son solo del panel privado
        if (PUBLIC) $('#ov-heatmap').hidden = true;
        if (!kpis.firstChild) for (let i = 0; i < 6; i++) kpis.appendChild(el('div', { className: 'skeleton' }));
        if (PUBLIC) { await loadSummary(); every(15000, loadSummary); return; }
        await Promise.all([loadSummary(), loadHistory()]);
        loadHeatmap();
        every(15000, () => { loadSummary(); loadHistory(); });
        every(300000, loadHeatmap);
    }

    // Mapa de calor: media de jugadores online por día de la semana y hora (lunes = 0).
    async function loadHeatmap() {
        const card = $('#ov-heatmap');
        card.hidden = !feature('heatmap');
        if (card.hidden) return;
        let h;
        try { h = await api('/api/heatmap'); } catch (e) { card.hidden = true; return; }
        const grid = clear($('#heatmap-grid'));
        const max = Number(h.max) || 0;
        const dayName = (d) => new Date(2024, 0, 1 + d).toLocaleDateString(I18N.locale, { weekday: 'short' });
        grid.appendChild(el('span'));
        for (let hr = 0; hr < 24; hr++) grid.appendChild(el('span', { className: 'hm-hour', text: hr % 3 === 0 ? String(hr) : '' }));
        (h.days || []).forEach((hours, d) => {
            grid.appendChild(el('span', { className: 'hm-day', text: dayName(d) }));
            hours.forEach((v, hr) => {
                const cell = el('span', { className: 'hm-cell' + (v < 0 ? ' empty' : ''),
                    title: dayName(d) + ' ' + String(hr).padStart(2, '0') + ':00 · ' + (v < 0 ? T('ov.no-data') : T('hm.avg', { value: nf2.format(v) })) });
                if (v >= 0 && max > 0) cell.style.opacity = String(0.12 + 0.88 * (v / max));
                grid.appendChild(cell);
            });
        });
        $('#heatmap-peak').textContent = h.peak && max > 0
            ? T('hm.peak', { day: dayName(h.peak.day), hour: String(h.peak.hour).padStart(2, '0') + ':00', value: nf2.format(max) })
            : T('ov.collecting');
        $('#heatmap-sub').textContent = T('hm.sub', { weeks: h.weeks || 0, zone: h.timezone || '' });
    }

    async function loadSummary() {
        let s;
        try { s = await api('/api/summary'); } catch (e) { toast(T('ov.load-error', { error: e.message }), 'error'); return; }
        lastSummary = s;
        applyModules(s);
        updateServerPill(s);
        const srv = s.server || {};
        const k = clear($('#server-kpis'));
        k.appendChild(kpi('🟢', T('kpi.online'), (srv.online ?? '—') + ' / ' + (srv.max_players ?? '—'), null, srv.max_players ? srv.online / srv.max_players : null));
        const tpsMeter = srv.tps != null ? 1 - Math.min(1, srv.tps / 20) : null;
        const tpsCard = kpi('⚡', T('kpi.tps'), srv.tps != null ? nf2.format(srv.tps) : '—', srv.tps >= 18 ? T('kpi.stable') : srv.tps ? T('kpi.lag') : null, tpsMeter);
        k.appendChild(tpsCard);
        if (srv.memory_used_mb != null) k.appendChild(kpi('🧠', T('kpi.memory'), srv.memory_used_mb != null ? fmtInt(srv.memory_used_mb) + ' MB' : '—',
            srv.memory_max_mb ? T('kpi.memory.of', { value: fmtInt(srv.memory_max_mb) }) : null, srv.memory_max_mb ? srv.memory_used_mb / srv.memory_max_mb : null));
        k.appendChild(kpi('⏳', T('kpi.uptime'), srv.uptime_seconds != null ? fmtDuration(srv.uptime_seconds) : '—', T('kpi.uptime.sub')));
        k.appendChild(kpi('📇', T('kpi.tracked'), fmtInt(s.players_tracked), null));
        const ver = (srv.version || '').match(/MC: ([0-9.]+)/);
        k.appendChild(kpi('🧱', T('kpi.version'), ver ? ver[1] : (srv.bukkit_version || '—').split('-')[0], srv.plugin_version ? T('kpi.plugin', { value: srv.plugin_version }) : null));
        $('#tps-now').textContent = srv.tps != null ? T('ov.now', { value: nf2.format(srv.tps) }) : '';

        const tc = clear($('#totals-cards'));
        STATS.forEach((st) => {
            const card = el('div', { className: 'card stat-card' }, [
                el('div', { className: 'label' }, [el('span', { text: st.icon }), el('span', { text: st.label })]),
                el('div', { className: 'value', text: st.fmt((s.totals || {})[st.key]) }),
                el('div', { className: 'sub', text: T('ov.avg-max', { avg: st.fmt((s.averages || {})[st.key]), max: st.fmt((s.max || {})[st.key]) }) }),
            ]);
            tc.appendChild(card);
        });

        const hd = s.hours_distribution || [];
        Charts.columns($('#chart-hours'), { labels: hd.map((b) => b.label), values: hd.map((b) => b.count), color: '#4fd1c5' });
        const colors = ['#7ed957', '#4fd1c5', '#f5c542', '#64748b'];
        const act = (s.activity || []).map((a, i) => ({ label: a.key ? T('activity.' + a.key) : a.label, value: a.count, color: colors[i % colors.length] }));
        Charts.donut($('#chart-activity'), { items: act, centerText: fmtInt(s.players_tracked), centerSub: T('ov.players') });
        const lg = clear($('#activity-legend'));
        act.forEach((a) => {
            const sw = el('span', { className: 'sw' }); sw.style.background = a.color;
            lg.appendChild(el('li', null, [sw, el('span', { text: a.label }), el('span', { className: 'lv', text: fmtInt(a.value) })]));
        });

        const tm = clear($('#top-mini'));
        ['player_kills', 'playtime', 'mob_kills', 'blocks_broken'].forEach((type) => {
            const st = statByKey(type);
            const list = (s.top5 && s.top5[type]) || [];
            const ol = el('ol');
            if (!list.length) ol.appendChild(el('li', { className: 'muted small', text: T('ov.no-data') }));
            list.forEach((e, i) => {
                const a = el('a', { className: 'who', attrs: { href: '#/player/' + encodeURIComponent(e.name) } }, [
                    el('span', { className: 'rank-badge rank-' + (i + 1), text: String(i + 1) }), head(e.name), el('span', { className: 'name', text: e.name })]);
                ol.appendChild(el('li', null, [a, el('span', { className: 'v', text: st.fmt(e.value) })]));
            });
            tm.appendChild(el('div', { className: 'card' }, [
                el('div', { className: 'card-head' }, [el('h3', { text: st.icon + ' ' + st.label }), el('a', { className: 'small', text: T('ov.see-all'), attrs: { href: '#/leaderboards/' + st.top } })]),
                ol]));
        });
    }

    async function loadHistory() {
        const since = historyHours ? Date.now() - historyHours * 3600e3 : 0;
        let h;
        try { h = await api('/api/history?since=' + since); } catch (e) { return; }
        const fmtX = (t) => {
            const d = new Date(t);
            return historyHours && historyHours <= 24 ? fmtClock(t) : d.toLocaleDateString(I18N.locale, { day: '2-digit', month: '2-digit' }) + ' ' + fmtClock(t);
        };
        const empty = T('ov.collecting');
        Charts.line($('#chart-online'), {
            series: [{ label: 'Online', color: '#7ed957', data: h.map((p) => [p.t, p.online]) }],
            fmtX, fmtTip: fmtDateTime, fmtY: (v) => String(Math.round(v)), yMin: 0, integer: true, empty,
        });
        Charts.line($('#chart-tps'), {
            series: [{ label: 'TPS', color: '#f5c542', data: h.map((p) => [p.t, p.tps]) }],
            fmtX, fmtTip: fmtDateTime, fmtY: (v) => nf2.format(v), yMin: 0, yMax: 20, empty,
        });
    }

    // ================================================================== RANKING
    let lbStat = 'player_kills';
    let lbPeriod = 'total';
    let lbTracked = null; // estadísticas con top semanal/mensual
    $$('#lb-period button').forEach((b) => b.addEventListener('click', () => {
        lbPeriod = b.dataset.period;
        $$('#lb-period button').forEach((x) => x.classList.toggle('active', x === b));
        loadLeaderboard();
    }));
    function renderChips() {
        const lbChips = clear($('#lb-chips'));
        STATS.forEach((st) => {
            lbChips.appendChild(el('button', {
                className: 'chip', text: st.icon + ' ' + st.label, attrs: { type: 'button', 'data-stat': st.top, role: 'tab' },
                on: { click: () => { location.hash = '#/leaderboards/' + st.top; } },
            }));
        });
    }
    renderChips();
    renderToastSwitches();
    $('#lb-limit').addEventListener('change', () => loadLeaderboard());

    function renderLeaderboards(args) {
        const st = statByKey(args[0] || lbStat);
        lbStat = st ? st.top : 'player_kills';
        loadLeaderboard();
        every(30000, loadLeaderboard);
    }

    async function loadLeaderboard() {
        let st = statByKey(lbStat);
        const limit = Number($('#lb-limit').value) || 25;
        const periodic = lbPeriod !== 'total' && feature('periods');
        let rows;
        let ends = '';
        if (periodic) {
            let all;
            try { all = await api('/api/periods?limit=' + limit); } catch (e) { toast(T('lb.load-error', { error: e.message }), 'error'); return; }
            const per = all[lbPeriod] || {};
            lbTracked = per.enabled ? (per.stats || []) : [];
            if (lbTracked.length && lbTracked.indexOf(st.key) < 0) { st = statByKey(lbTracked[0]); lbStat = st.top; }
            rows = per.enabled && per.boards ? (per.boards[st.key] || []).map((r) => ({ name: r.name, [st.key]: r.value })) : [];
            if (per.enabled) ends = T('lb.period.ends', { time: fmtDuration((per.end - Date.now()) / 1000) });
            else ends = T('lb.period.disabled');
        } else {
            lbTracked = null;
            try { rows = await api('/api/top?stat=' + encodeURIComponent(st.top) + '&limit=' + limit); } catch (e) { toast(T('lb.load-error', { error: e.message }), 'error'); return; }
        }
        $$('#lb-chips .chip').forEach((c) => {
            const on = c.dataset.stat === st.top;
            c.classList.toggle('active', on);
            c.setAttribute('aria-selected', on ? 'true' : 'false');
            const s2 = statByKey(c.dataset.stat);
            c.disabled = !!lbTracked && lbTracked.indexOf(s2.key) < 0;
        });
        $('#lb-period-ends').textContent = ends;
        const prefix = periodic ? T('lb.period.' + lbPeriod) + ' · ' : '';
        $('#lb-chart-title').textContent = st.icon + ' ' + prefix + T('lb.title', { n: Math.min(10, rows.length || 10), stat: st.label });
        const top10 = rows.slice(0, 10);
        Charts.bars($('#chart-lb'), {
            labels: top10.map((r) => r.name), values: top10.map((r) => Number(r[st.key]) || 0), color: st.color, fmt: st.fmt,
            onClick: (i) => { location.hash = '#/player/' + encodeURIComponent(top10[i].name); },
            empty: T('lb.empty'),
        });
        const tb = clear($('#lb-table tbody'));
        if (!rows.length) {
            tb.appendChild(el('tr', null, [el('td', { className: 'empty', text: T('ov.no-data'), attrs: { colspan: '3' } })]));
        }
        rows.forEach((r, i) => {
            const tr = el('tr', { className: 'clickable', on: { click: () => { location.hash = '#/player/' + encodeURIComponent(r.name); } } }, [
                el('td', null, [el('span', { className: 'rank-badge' + (i < 3 ? ' rank-' + (i + 1) : ''), text: String(i + 1) })]),
                el('td', null, [el('span', { className: 'who' }, [head(r.name), el('span', { className: 'name', text: r.name })])]),
                el('td', { className: 'num', text: (periodic ? '+' : '') + st.fmt(r[st.key]) }),
            ]);
            tb.appendChild(tr);
        });
    }

    // ================================================================== TEMPORADAS
    let seasonStat = null;
    async function renderSeasons() {
        await loadSeasons();
        every(30000, loadSeasons);
    }

    async function loadSeasons() {
        let d;
        try { d = await api('/api/seasons?limit=25'); } catch (e) { toast(T('error', { error: e.message }), 'error'); return; }
        if (location.hash.indexOf('#/seasons') !== 0) return;
        const cur = d.current || {};
        const tracked = (cur.stats || []).filter((k) => statByKey(k));
        if (!seasonStat || tracked.indexOf(seasonStat) < 0) seasonStat = tracked[0] || null;
        $('#page-subtitle').textContent = cur.name || '';
        $('#season-ends').textContent = cur.end ? T('se.ends', { time: fmtDuration((cur.end - Date.now()) / 1000) }) : '';
        const chips = clear($('#season-chips'));
        tracked.forEach((k) => {
            const st = statByKey(k);
            chips.appendChild(el('button', { className: 'chip' + (k === seasonStat ? ' active' : ''), text: st.icon + ' ' + st.label,
                attrs: { type: 'button', role: 'tab', 'aria-selected': k === seasonStat ? 'true' : 'false' },
                on: { click: () => { seasonStat = k; loadSeasons(); } } }));
        });
        const st = seasonStat && statByKey(seasonStat);
        $('#season-board-title').textContent = st ? st.icon + ' ' + st.label : T('se.board');
        const rows = st ? ((cur.boards || {})[seasonStat] || []) : [];
        const tb = clear($('#season-table tbody'));
        if (!rows.length) tb.appendChild(el('tr', null, [el('td', { className: 'empty', text: T('ov.no-data'), attrs: { colspan: '3' } })]));
        rows.forEach((r, i) => tb.appendChild(el('tr', { className: 'clickable', on: { click: () => { location.hash = '#/player/' + encodeURIComponent(r.name); } } }, [
            el('td', null, [el('span', { className: 'rank-badge' + (i < 3 ? ' rank-' + (i + 1) : ''), text: String(i + 1) })]),
            el('td', null, [el('span', { className: 'who' }, [head(r.name), el('span', { className: 'name', text: r.name })])]),
            el('td', { className: 'num', text: '+' + st.fmt(r.value) }),
        ])));
        const hall = clear($('#season-hall'));
        if (!(d.hall || []).length) { hall.appendChild(el('p', { className: 'empty', text: T('se.hall.empty') })); return; }
        (d.hall || []).forEach((h) => {
            const list = el('div', { className: 'tags' });
            Object.keys(h.winners || {}).forEach((k) => {
                const w = (h.winners[k] || [])[0];
                const s2 = statByKey(k);
                if (w && s2) list.appendChild(el('span', { className: 'tag', text: s2.icon + ' ' + w.name + ' · ' + s2.fmt(w.value) }));
            });
            if (!list.children.length) list.appendChild(el('span', { className: 'muted small', text: T('se.no-winners') }));
            hall.appendChild(el('div', { className: 'ms-item' }, [
                el('div', { className: 'card-head' }, [el('strong', { text: '🏆 ' + h.name }),
                    el('span', { className: 'muted small', text: fmtDate(h.start) + ' – ' + fmtDate(h.end) })]),
                list,
            ]));
        });
    }

    // ================================================================== EVENTOS
    async function renderEvents() {
        await loadEvents();
        every(5000, loadEvents);
    }

    function eventTable(rows, st) {
        const tb = el('tbody');
        if (!rows.length) tb.appendChild(el('tr', null, [el('td', { className: 'empty', text: T('ev.no-participants'), attrs: { colspan: '3' } })]));
        rows.forEach((r, i) => tb.appendChild(el('tr', { className: 'clickable', on: { click: () => { location.hash = '#/player/' + encodeURIComponent(r.name); } } }, [
            el('td', null, [el('span', { className: 'rank-badge' + (i < 3 ? ' rank-' + (i + 1) : ''), text: String(i + 1) })]),
            el('td', null, [el('span', { className: 'who' }, [head(r.name), el('span', { className: 'name', text: r.name })])]),
            el('td', { className: 'num', text: '+' + (st ? st.fmt(r.value) : fmtInt(r.value)) }),
        ])));
        return el('div', { className: 'table-wrap' }, [el('table', { className: 'table' }, [
            el('thead', null, [el('tr', null, [el('th', { text: '#' }), el('th', { text: T('col.player') }), el('th', { className: 'num', text: T('col.gained') })])]), tb])]);
    }

    async function loadEvents() {
        let d;
        try { d = await api('/api/events?limit=25&lang=' + I18N.lang); } catch (e) { toast(T('error', { error: e.message }), 'error'); return; }
        if (location.hash.indexOf('#/events') !== 0) return;
        const live = clear($('#event-live'));
        const a = d.active;
        if (!a) {
            live.appendChild(el('div', { className: 'card empty', text: T('ev.none') }));
        } else {
            const st = statByKey(a.stat);
            const total = Math.max(1, a.end - a.start);
            const left = Math.max(0, a.end - (d.now || Date.now()));
            const card = el('div', { className: 'card' }, [
                el('div', { className: 'card-head' }, [el('h3', { text: '🎯 ' + a.name }),
                    el('span', { className: 'tag green', text: T('ev.live') })]),
                el('div', { className: 'grid kpis' }, [
                    kpi(st ? st.icon : '📈', T('ev.stat'), st ? st.label : a.stat, null),
                    kpi('⏳', T('ev.left'), fmtDuration(left / 1000), null, 1 - left / total),
                    kpi('👥', T('ev.participants'), fmtInt(a.participants), null),
                ]),
                eventTable(a.top || [], st),
            ]);
            live.appendChild(card);
        }
        const hist = clear($('#event-history'));
        if (!(d.history || []).length) { hist.appendChild(el('p', { className: 'empty', text: T('ev.history.empty') })); return; }
        (d.history || []).forEach((h) => {
            const st = statByKey(h.stat);
            const w = (h.top || [])[0];
            hist.appendChild(el('div', { className: 'ms-item' }, [
                el('div', { className: 'card-head' }, [el('strong', { text: h.name }),
                    el('span', { className: 'muted small', text: fmtDateTime(h.start) + ' · ' + fmtDuration((h.end - h.start) / 1000) })]),
                el('div', { className: 'muted small', text: h.cancelled ? T('ev.cancelled')
                    : w ? T('ev.winner', { player: w.name, value: st ? st.fmt(w.value) : fmtInt(w.value), n: fmtInt(h.participants) }) : T('ev.no-participants') }),
            ]));
        });
    }

    // ================================================================== JUGADORES
    const dir = { q: '', sort: 'hours_played', page: 1 };
    let dirTimer = null;
    $('#dir-search').addEventListener('input', (e) => {
        clearTimeout(dirTimer);
        dirTimer = setTimeout(() => { dir.q = e.target.value.trim(); dir.page = 1; loadDirectory(); }, 250);
    });
    $('#dir-sort').addEventListener('change', (e) => { dir.sort = e.target.value; dir.page = 1; loadDirectory(); });

    function renderPlayers() {
        loadOnline();
        loadDirectory();
        every(5000, loadOnline);
    }

    function hearts(health) {
        const full = Math.round(Math.max(0, Math.min(20, health)) / 2);
        return '❤'.repeat(full) + '♡'.repeat(10 - full);
    }

    async function loadOnline() {
        let list;
        try { list = await api('/api/players'); } catch (e) { return; }
        $('#online-count').textContent = String(list.length);
        const grid = clear($('#online-grid'));
        if (!list.length) {
            grid.appendChild(el('div', { className: 'card empty', text: T('pl.nobody') }));
            return;
        }
        list.sort((a, b) => a.name.localeCompare(b.name));
        list.forEach((p) => {
            const tags = el('div', { className: 'tags' });
            if (p.world) tags.appendChild(el('span', { className: 'tag', text: '🌍 ' + p.world }));
            if (p.ping != null && p.ping >= 0) tags.appendChild(el('span', { className: 'tag ' + (p.ping < 80 ? 'green' : p.ping < 200 ? 'amber' : 'red'), text: p.ping + ' ms' }));
            if (p.afk) tags.appendChild(el('span', { className: 'tag amber', text: 'AFK' }));
            if (p.gamemode) tags.appendChild(el('span', { className: 'tag', text: p.gamemode.toLowerCase() }));
            const card = el('div', {
                className: 'card player-card', attrs: { tabindex: '0', role: 'link' },
                on: {
                    click: () => { location.hash = '#/player/' + encodeURIComponent(p.name); },
                    keydown: (e) => { if (e.key === 'Enter') location.hash = '#/player/' + encodeURIComponent(p.name); },
                },
            }, [
                el('div', { className: 'top' }, [head(p.name, 'lg'), el('div', null, [
                    el('div', { className: 'pname', text: p.name }),
                    p.health != null ? el('div', { className: 'hearts', text: hearts(p.health), title: p.health + ' / 20' }) : null,
                ])]),
                tags,
                p.session_seconds != null ? el('div', { className: 'mini-stats' }, [
                    el('div', null, [el('b', { text: fmtDuration(p.session_seconds) }), T('pl.session')]),
                    el('div', null, [el('b', { text: String(p.level ?? '—') }), T('pl.level')]),
                    el('div', null, [el('b', { text: String(p.food ?? '—') }), T('pl.food')]),
                ]) : null,
            ]);
            grid.appendChild(card);
        });
    }

    async function loadDirectory() {
        let res;
        const qs = new URLSearchParams({ q: dir.q, sort: dir.sort, page: dir.page, size: 20 });
        try { res = await api('/api/players/list?' + qs.toString()); } catch (e) { toast(T('pl.load-error', { error: e.message }), 'error'); return; }
        const tb = clear($('#dir-table tbody'));
        if (!res.items.length) tb.appendChild(el('tr', null, [el('td', { className: 'empty', text: T('pl.none'), attrs: { colspan: '6' } })]));
        res.items.forEach((p) => {
            const nameCell = el('span', { className: 'who' }, [head(p.name), el('span', { className: 'name', text: p.name })]);
            if (p.online) nameCell.appendChild(el('span', { className: 'tag green', text: 'online' }));
            tb.appendChild(el('tr', { className: 'clickable', on: { click: () => { location.hash = '#/player/' + encodeURIComponent(p.name); } } }, [
                el('td', null, [nameCell]),
                el('td', { className: 'num', text: fmtHours(p.hours_played) }),
                el('td', { className: 'num hide-sm', text: fmtInt(p.player_kills) }),
                el('td', { className: 'num hide-sm', text: fmtInt(p.mob_kills) }),
                el('td', { className: 'num hide-sm', text: fmtInt(p.deaths) }),
                el('td', { className: 'num', text: p.online ? T('time.now') : timeAgo(p.last_updated) }),
            ]));
        });
        const pager = clear($('#dir-pager'));
        const prev = el('button', { className: 'btn btn-sm', text: T('pl.prev'), attrs: { type: 'button' }, on: { click: () => { dir.page--; loadDirectory(); } } });
        const next = el('button', { className: 'btn btn-sm', text: T('pl.next'), attrs: { type: 'button' }, on: { click: () => { dir.page++; loadDirectory(); } } });
        prev.disabled = res.page <= 1;
        next.disabled = res.page >= res.pages;
        pager.appendChild(prev);
        pager.appendChild(el('span', { text: T('pl.page', { page: res.page, pages: res.pages, total: fmtInt(res.total) }) }));
        pager.appendChild(next);
    }

    // ================================================================== PERFIL
    async function renderProfile(args) {
        const name = args[0] || '';
        const root = clear($('#profile-root'));
        if (!VALID_NAME.test(name)) {
            root.appendChild(el('div', { className: 'card empty', text: T('pf.invalid') }));
            return;
        }
        $('#page-title').textContent = name;
        $('#page-subtitle').textContent = T('page.profile.sub');
        root.appendChild(el('div', { className: 'skeleton' }));
        const extra = clear($('#profile-extra'));
        await loadProfile(name, true);
        every(10000, () => loadProfile(name, false));
        // Historial, minerales, muertes, tops y logros: se cargan una sola vez (no cada 10 s)
        if (lastProfile && lastProfile.name.toLowerCase() === name.toLowerCase()) loadProfileExtras(lastProfile, extra);
    }

    let lastProfile = null;
    async function loadProfile(name, first) {
        const root = $('#profile-root');
        let p;
        if (first) lastProfile = null;
        try { p = await api('/api/profile?name=' + encodeURIComponent(name) + '&lang=' + I18N.lang); lastProfile = p; } catch (e) {
            if (!first) return;
            clear(root).appendChild(el('div', { className: 'card empty' }, [
                el('p', { text: e.status === 404 ? T('pf.not-found', { name }) : T('error', { error: e.message }) }),
                el('a', { className: 'btn', text: T('pf.back'), attrs: { href: '#/players' } }),
            ]));
            return;
        }
        if (location.hash.indexOf('#/player/') !== 0) return;
        clear(root);
        const s = p.stats || {};
        $('#page-title').textContent = p.name;

        // Cabecera
        const badges = el('div', { className: 'tags' });
        badges.appendChild(el('span', { className: 'tag ' + (p.online ? 'green' : ''), text: p.online ? '● Online' : '○ Offline' }));
        if (p.session) {
            if (p.session.afk) badges.appendChild(el('span', { className: 'tag amber', text: 'AFK' }));
            badges.appendChild(el('span', { className: 'tag', text: '🌍 ' + p.session.world }));
            if (p.session.ping >= 0) badges.appendChild(el('span', { className: 'tag', text: p.session.ping + ' ms' }));
            badges.appendChild(el('span', { className: 'tag', text: T('pf.session', { value: fmtDuration(p.session.session_seconds) }) }));
        }
        if (!p.in_cache) badges.appendChild(el('span', { className: 'tag amber', text: T('pf.vanilla') }));
        const body = el('img', { className: 'profile-body', attrs: { alt: '' } });
        body.referrerPolicy = 'no-referrer';
        body.addEventListener('error', () => body.remove(), { once: true });
        if (!minotarDown) body.src = 'https://minotar.net/armor/body/' + encodeURIComponent(p.name) + '/100.png'; else body.hidden = true;
        const info = el('div', { className: 'info' }, [
            el('h2', { text: p.name }), badges,
            p.title ? el('div', { className: 'muted small', text: '🎖️ ' + T('pf.title', { title: p.title }) }) : null,
            el('div', { className: 'muted small', text: (s.last_updated ? T('pf.updated', { value: timeAgo(s.last_updated) }) : '') + (s.uuid ? ' · UUID ' + s.uuid : '') }),
        ]);
        if (pageAllowed('compare')) {
            info.appendChild(el('a', { className: 'btn btn-sm cmp-btn', text: '⚖️ ' + T('pf.compare-btn'), attrs: { href: '#/compare/' + encodeURIComponent(p.name) + '/' } }));
        }
        root.appendChild(el('div', { className: 'card profile-hero' }, [head(p.name, 'xl'), info, body]));

        // Estadísticas con posición
        const cards = el('div', { className: 'grid stat-cards' });
        const ranks = p.ranks || {};
        STATS.forEach((st) => {
            const card = el('div', { className: 'card stat-card' }, [
                el('div', { className: 'label' }, [el('span', { text: st.icon }), el('span', { text: st.label })]),
                el('div', { className: 'value', text: st.fmt(s[st.key]) }),
            ]);
            if (ranks[st.key]) {
                card.appendChild(el('div', { className: 'sub' }, [
                    el('span', { className: 'rank', text: '#' + ranks[st.key] }),
                    el('span', { text: T('pf.of', { value: fmtInt(ranks._total) }) }),
                ]));
            }
            cards.appendChild(card);
        });
        root.appendChild(cards);

        const two = el('div', { className: 'grid two' });
        // Percentil por estadística
        if (p.ranks && ranks._total > 0) {
            const canvas = el('canvas', { attrs: { 'aria-label': T('pf.percentile.aria') } });
            two.appendChild(el('div', { className: 'card' }, [
                el('div', { className: 'card-head' }, [el('h3', { text: T('pf.compare') }), el('span', { className: 'muted small', text: T('pf.percentile') })]),
                el('div', { className: 'chart-box mid' }, [canvas]),
            ]));
            const total = ranks._total;
            const ranked = STATS.filter((st) => ranks[st.key] > 0);
            requestAnimationFrame(() => Charts.bars(canvas, {
                labels: ranked.map((st) => st.label),
                values: ranked.map((st) => total <= 1 ? 100 : Math.round((1 - (ranks[st.key] - 1) / (total - 1)) * 100)),
                colors: ranked.map((st) => st.color), fmt: (v) => v + '%',
            }));
        }
        // Hitos
        const ms = el('div', { className: 'ms-list' });
        if (!p.milestones || !p.milestones.length) ms.appendChild(el('p', { className: 'muted', text: T('pf.no-milestones') }));
        (p.milestones || []).forEach((m) => {
            const st = statByKey(m.stat);
            const fmt = st ? st.fmt : fmtInt;
            const bar = el('span');
            const prog = el('div', { className: 'progress' + (m.progress >= 1 ? ' full' : '') }, [bar]);
            requestAnimationFrame(() => { bar.style.width = Math.round(m.progress * 100) + '%'; });
            const levels = el('div', { className: 'ms-levels' });
            (m.levels || []).forEach((l) => levels.appendChild(el('span', {
                className: 'ms-level' + (l.reached ? ' done' : ''), text: (l.reached ? '✔ ' : '') + fmt(l.value), title: l.message || '',
            })));
            ms.appendChild(el('div', { className: 'ms-item' }, [
                el('div', { className: 'ms-head' }, [
                    el('span', { text: (st ? st.icon + ' ' + st.label : m.stat) }),
                    el('span', { className: 'muted', text: m.next != null ? fmt(m.current) + ' / ' + fmt(m.next) : T('pf.completed', { value: fmt(m.current) }) }),
                ]),
                prog, levels,
            ]));
        });
        two.appendChild(el('div', { className: 'card' }, [el('div', { className: 'card-head' }, [el('h3', { text: T('pf.milestones') })]), ms]));
        root.appendChild(two);
    }

    function cardWith(title, extraHead, children) {
        return el('div', { className: 'card' }, [el('div', { className: 'card-head' }, [el('h3', { text: title })].concat(extraHead || [])), ...children]);
    }

    // Una barra pequeña por objetivo en las misiones con varios (con uno solo basta la barra de la misión)
    function questObjectives(list) {
        if (!list || list.length < 2) return null;
        return el('div', { className: 'quest-objs' }, list.map((o) => el('div', { className: 'quest-obj' }, [
            el('div', { className: 'ms-head small' }, [
                el('span', { text: (o.done ? '✔ ' : '') + o.description }),
                el('span', { className: 'muted', text: o.progress_text + ' / ' + o.goal_text }),
            ]),
            progressBar(o.goal > 0 ? o.progress / o.goal : 0, true),
        ])));
    }

    async function loadProfileExtras(p, extra) {
        const s = p.stats || {};
        const enc = encodeURIComponent(p.name);
        const stillHere = () => location.hash.toLowerCase().indexOf('#/player/' + enc.toLowerCase()) === 0;

        // Racha, tiempo activo y misiones completadas
        const mini = [];
        if (feature('streaks') && s.login_streak != null) {
            mini.push(kpi('🔥', T('pf.streak'), T('pf.days', { n: fmtInt(s.login_streak) }), T('pf.streak.best', { n: fmtInt(s.best_login_streak) })));
        }
        if (feature('playtime')) mini.push(kpi('⏱️', T('pf.active'), fmtHours((Number(s.active_playtime) || 0) / 3600), T('pf.active.sub')));
        if (feature('quests') && p.quests) mini.push(kpi('📜', T('pf.quests.total'), fmtInt(p.quests.completed_total), null));
        if (mini.length) extra.appendChild(el('div', { className: 'grid kpis' }, mini));

        // Misiones diarias y semanales
        if (feature('quests') && p.quests) {
            const two = el('div', { className: 'grid two' });
            ['daily', 'weekly'].forEach((kind) => {
                const k = p.quests[kind];
                if (!k) return;
                const list = el('div', { className: 'ms-list' });
                if (!(k.quests || []).length) list.appendChild(el('p', { className: 'muted', text: T('pf.quests.none') }));
                (k.quests || []).forEach((x) => list.appendChild(el('div', { className: 'ms-item' }, [
                    el('div', { className: 'ms-head' }, [
                        el('span', { text: (x.done ? '✔ ' : '') + x.name }),
                        el('span', { className: 'muted', text: x.progress_text + ' / ' + x.goal_text }),
                    ]),
                    progressBar(x.goal > 0 ? x.progress / x.goal : 0),
                    questObjectives(x.objectives),
                    el('div', { className: 'muted small quest-meta', text: (x.description ? x.description + ' · ' : '') + T('pf.quests.reward', { value: x.rewards || '—' }) }),
                ])));
                const head = T('pf.quests.count', { done: k.done, total: k.total }) + ' · ' + T('pf.quests.resets', { time: fmtDuration((k.resets_at - Date.now()) / 1000) });
                two.appendChild(cardWith('📜 ' + T('pf.quests.' + kind), [el('span', { className: 'muted small', text: head })], [list]));
            });
            extra.appendChild(two);
        }

        // Evolución (addon): rango actual, el siguiente y una barra por requisito
        if (feature('evolution') && p.evolution && p.evolution.rank) {
            const ev = p.evolution;
            const item = [el('div', { className: 'ms-head' }, [
                el('span', { text: ev.rank_name + ' · ' + T('pf.evo.position', { n: ev.position, total: ev.total }) }),
                el('span', { className: 'muted', text: ev.max ? T('pf.evo.max') : T('pf.evo.next', { name: ev.next.name, value: ev.percent }) }),
            ])];
            if (!ev.max) {
                item.push(progressBar(ev.percent / 100), questObjectives(ev.next.objectives));
                if (ev.next.rewards) item.push(el('div', { className: 'muted small quest-meta', text: T('pf.quests.reward', { value: ev.next.rewards }) }));
            }
            extra.appendChild(cardWith('🦖 ' + T('pf.evo.title'), [], [el('div', { className: 'ms-list' }, [el('div', { className: 'ms-item' }, item)])]));
        }

        // Moderación (solo con permisos de administración)
        if (!PUBLIC && feature('moderation') && adminOk !== false) {
            const card = cardWith('🔨 ' + T('mod.title'), [], []);
            card.hidden = true;
            extra.appendChild(card);
            loadSanctions(p.name, card);
        }

        // Historial diario
        if (feature('history')) {
            const sel = el('select', { attrs: { 'aria-label': T('pf.history') } });
            STATS.forEach((st) => sel.appendChild(el('option', { text: st.label, attrs: { value: st.key } })));
            sel.value = 'hours_played';
            const canvas = el('canvas', { attrs: { 'aria-label': T('pf.history') } });
            extra.appendChild(cardWith('📈 ' + T('pf.history'), [sel], [el('div', { className: 'chart-box' }, [canvas])]));
            let hist = null;
            const draw = () => {
                const st = statByKey(sel.value);
                const pts = ((hist && hist.days) || []).filter((d) => d.values && d.values[st.key] != null)
                    .map((d) => [Date.parse(d.date + 'T12:00:00'), Number(d.values[st.key])]);
                Charts.line(canvas, {
                    series: [{ label: st.label, color: st.color, data: pts }], empty: T('pf.history.empty'),
                    fmtX: (x) => new Date(x).toLocaleDateString(I18N.locale, { day: 'numeric', month: 'short' }), fmtY: st.fmt,
                });
            };
            sel.addEventListener('change', draw);
            api('/api/player/history?name=' + enc + '&days=30').then((h) => { hist = h; if (stillHere()) draw(); }).catch(() => draw());
        }

        // Minerales y causas de muerte
        const ores = s.ores || {};
        const causes = s.death_causes || {};
        const oreKeys = ORES.filter((k) => ores[k] > 0);
        const causeKeys = DEATHS.filter((k) => causes[k] > 0);
        if (oreKeys.length || causeKeys.length) {
            const two = el('div', { className: 'grid two' });
            const oc = el('canvas', { attrs: { 'aria-label': T('pf.ores') } });
            two.appendChild(cardWith('💎 ' + T('pf.ores'), [], [el('div', { className: 'chart-box mid' }, [oc])]));
            const dc = el('canvas', { attrs: { 'aria-label': T('pf.deaths') } });
            const legend = el('ul', { className: 'legend' });
            causeKeys.forEach((k) => {
                const sw = el('span', { className: 'sw' }); sw.style.background = DEATH_COLORS[DEATHS.indexOf(k)];
                legend.appendChild(el('li', null, [sw, el('span', { text: T('death.' + k) }), el('span', { className: 'lv', text: fmtInt(causes[k]) })]));
            });
            two.appendChild(cardWith('☠️ ' + T('pf.deaths'), [], [el('div', { className: 'donut-wrap' }, [el('div', { className: 'chart-box donut' }, [dc]), legend])]));
            extra.appendChild(two);
            requestAnimationFrame(() => {
                Charts.bars(oc, { labels: oreKeys.map((k) => T('ore.' + k)), values: oreKeys.map((k) => ores[k]),
                    colors: oreKeys.map((k) => ORE_COLORS[k]), fmt: fmtInt, empty: T('ov.no-data') });
                Charts.donut(dc, { items: causeKeys.map((k) => ({ label: T('death.' + k), value: causes[k], color: DEATH_COLORS[DEATHS.indexOf(k)] })),
                    centerSub: T('stat.deaths').toLowerCase() });
            });
        }

        // Tops semanal / mensual
        if (feature('periods')) {
            const box = el('div', { className: 'grid two' });
            extra.appendChild(box);
            api('/api/periods?limit=1&name=' + enc).then((per) => {
                if (!stillHere()) return;
                ['weekly', 'monthly'].forEach((kind) => {
                    const k = per[kind];
                    if (!k || !k.enabled || !k.player) return;
                    const tb = el('tbody');
                    (k.stats || []).forEach((key) => {
                        const st = statByKey(key);
                        const me = k.player[key] || {};
                        tb.appendChild(el('tr', null, [
                            el('td', { text: st ? st.icon + ' ' + st.label : key }),
                            el('td', { className: 'num', text: '+' + (st ? st.fmt(me.value) : me.value) }),
                            el('td', { className: 'num', text: me.rank > 0 ? '#' + me.rank : '—' }),
                        ]));
                    });
                    box.appendChild(cardWith('🗓️ ' + T('lb.period.' + kind),
                        [el('span', { className: 'muted small', text: T('lb.period.ends', { time: fmtDuration((k.end - Date.now()) / 1000) }) })],
                        [el('div', { className: 'table-wrap' }, [el('table', { className: 'table' }, [tb])])]));
                });
            }).catch(() => {});
        }

        // Galería de logros
        if (feature('achievements')) {
            const grid = el('div', { className: 'ach-grid' });
            const count = el('span', { className: 'muted small' });
            extra.appendChild(cardWith('🏅 ' + T('pf.achievements'), [count], [grid]));
            api('/api/achievements?name=' + enc).then((a) => {
                if (!stillHere()) return;
                const got = new Set(((a.player || {}).obtained) || []);
                count.textContent = T('pf.ach.count', { count: got.size, total: (a.achievements || []).length });
                if (!(a.achievements || []).length) grid.appendChild(el('p', { className: 'muted', text: T('pf.ach.none') }));
                (a.achievements || []).forEach((x) => {
                    const st = statByKey(x.stat);
                    const ok = got.has(x.id);
                    grid.appendChild(el('div', { className: 'ach tier-' + x.tier + (ok ? ' got' : ' locked'), title: x.message || '' }, [
                        el('div', { className: 'ach-ico', text: ok ? (st ? st.icon : '🏅') : '🔒' }),
                        el('div', { className: 'ach-name', text: (st ? st.label : x.stat) + ' · ' + (st ? st.fmt(x.value) : x.value) }),
                        el('div', { className: 'ach-tier', text: T('tier.' + x.tier) + ' · ' + T('pf.ach.owners', { value: nf2.format(x.percent || 0) }) }),
                    ]));
                });
            }).catch(() => {});
        }
    }

    const MOD_ACTIONS = ['warn', 'kick', 'mute', 'unmute', 'ban', 'unban'];
    const MOD_TIMED = { mute: true, ban: true };
    async function loadSanctions(name, card) {
        let st;
        try { st = await api('/api/admin/sanctions?player=' + encodeURIComponent(name)); } catch (e) { card.remove(); return; }
        if (!card.isConnected) return;
        card.hidden = false;
        while (card.children.length > 1) card.removeChild(card.lastChild);

        const tags = el('div', { className: 'tags' });
        tags.appendChild(el('span', { className: 'tag ' + (st.banned ? 'red' : 'green'),
            text: st.banned ? T('mod.banned', { until: st.banned_until ? fmtDateTime(st.banned_until) : T('mod.forever') }) : T('mod.not-banned') }));
        if (st.muted) tags.appendChild(el('span', { className: 'tag amber', text: T('mod.muted', { until: st.muted_until ? fmtDateTime(st.muted_until) : T('mod.forever') }) }));
        tags.appendChild(el('span', { className: 'tag' + (st.warnings ? ' amber' : ''), text: T('mod.warnings', { n: st.warnings || 0 }) }));
        card.appendChild(tags);

        const action = el('select', { attrs: { name: 'action', 'aria-label': T('mod.action') } });
        MOD_ACTIONS.forEach((a) => action.appendChild(el('option', { text: T('mod.do.' + a), attrs: { value: a } })));
        const duration = el('input', { attrs: { name: 'duration', maxlength: '16', placeholder: T('mod.duration.ph'), autocomplete: 'off' } });
        const reason = el('input', { attrs: { name: 'reason', maxlength: '200', placeholder: T('mod.reason.ph'), autocomplete: 'off' } });
        const durLabel = el('label', null, [el('span', { text: T('mod.duration') }), duration]);
        const sync = () => { durLabel.hidden = !MOD_TIMED[action.value]; };
        action.addEventListener('change', sync); sync();
        const btn = el('button', { className: 'btn btn-warn', text: T('mod.apply'), attrs: { type: 'submit' } });
        const form = el('form', { className: 'form mod-form' }, [
            el('div', { className: 'row' }, [el('label', null, [el('span', { text: T('mod.action') }), action]), durLabel]),
            el('label', null, [el('span', { text: T('mod.reason') }), reason]),
            el('div', { className: 'btn-row' }, [btn]),
        ]);
        form.addEventListener('submit', async (e) => {
            e.preventDefault();
            const a = action.value;
            const body = { action: a, player: st.player || name, reason: reason.value.trim() };
            if (MOD_TIMED[a] && duration.value.trim()) body.duration = duration.value.trim();
            if ((a === 'ban' || a === 'kick') && !(await confirmDialog(T('mod.confirm', { action: T('mod.do.' + a), player: body.player }), body.reason || T('mod.no-reason')))) return;
            btn.disabled = true;
            if (await adminPost('moderation', body, T('mod.done', { action: T('mod.do.' + a), player: body.player }))) loadSanctions(name, card);
            else btn.disabled = false;
        });
        card.appendChild(form);

        const tb = el('tbody');
        if (!(st.history || []).length) tb.appendChild(el('tr', null, [el('td', { className: 'empty', text: T('mod.history.empty'), attrs: { colspan: '4' } })]));
        (st.history || []).forEach((h) => tb.appendChild(el('tr', null, [
            el('td', { text: fmtDateTime(h.at) }),
            el('td', { text: T('mod.type.' + h.type) + (h.until ? ' · ' + fmtDuration((h.until - h.at) / 1000) : '') }),
            el('td', { text: h.reason || '—' }),
            el('td', { className: 'hide-sm muted', text: h.by || '' }),
        ])));
        card.appendChild(el('h4', { className: 'sub-title', text: T('mod.history') }));
        card.appendChild(el('div', { className: 'table-wrap' }, [el('table', { className: 'table' }, [
            el('thead', null, [el('tr', null, [el('th', { text: T('col.time') }), el('th', { text: T('col.action') }), el('th', { text: T('mod.reason') }), el('th', { className: 'hide-sm', text: T('mod.by') })])]),
            tb])]));
    }

    // ================================================================== COMPARAR
    $('#compare-form').addEventListener('submit', (e) => {
        e.preventDefault();
        const a = $('#cmpA').value.trim(), b = $('#cmpB').value.trim();
        if (!VALID_NAME.test(a) || !VALID_NAME.test(b)) { toast(T('search.invalid'), 'warn'); return; }
        if (a.toLowerCase() === b.toLowerCase()) { toast(T('cmp.same'), 'warn'); return; }
        location.hash = '#/compare/' + encodeURIComponent(a) + '/' + encodeURIComponent(b);
    });

    async function renderCompare(args) {
        const a = args[0] || '', b = args[1] || '';
        $('#cmpA').value = a; $('#cmpB').value = b;
        const root = clear($('#compare-root'));
        if (!VALID_NAME.test(a) || !VALID_NAME.test(b)) {
            root.appendChild(el('div', { className: 'card empty', text: T('cmp.hint') }));
            (VALID_NAME.test(a) ? $('#cmpB') : $('#cmpA')).focus();
            return;
        }
        root.appendChild(el('div', { className: 'skeleton' }));
        let pa, pb;
        try {
            [pa, pb] = await Promise.all([a, b].map((n) => api('/api/profile?name=' + encodeURIComponent(n))));
        } catch (e) {
            clear(root).appendChild(el('div', { className: 'card empty', text: e.status === 404 ? T('cmp.not-found') : T('error', { error: e.message }) }));
            return;
        }
        if (location.hash.indexOf('#/compare/') !== 0) return;
        clear(root);
        const sa = pa.stats || {}, sb = pb.stats || {};
        const kdr = (s) => (Number(s.deaths) > 0 ? Number(s.player_kills || 0) / Number(s.deaths) : Number(s.player_kills || 0));
        // "menos es mejor" solo para muertes y daño recibido
        const lower = { deaths: true, damage_taken: true };
        const rows = STATS.map((st) => ({ label: st.icon + ' ' + st.label, va: Number(sa[st.key]) || 0, vb: Number(sb[st.key]) || 0, fmt: st.fmt, low: !!lower[st.key] }));
        rows.push({ label: '⚖️ KDR', va: kdr(sa), vb: kdr(sb), fmt: (v) => nf2.format(v), low: false });
        let wa = 0, wb = 0;
        const tb = el('tbody');
        rows.forEach((r) => {
            const better = r.va === r.vb ? 0 : ((r.va > r.vb) !== r.low ? 1 : 2);
            if (better === 1) wa++; else if (better === 2) wb++;
            const max = Math.max(r.va, r.vb, 1e-9);
            const barA = el('span'); barA.style.width = Math.round(r.va / max * 100) + '%';
            const barB = el('span'); barB.style.width = Math.round(r.vb / max * 100) + '%';
            tb.appendChild(el('tr', null, [
                el('td', { className: 'num ' + (better === 1 ? 'win' : ''), text: r.fmt(r.va) }),
                el('td', { className: 'cmp-bar left' }, [barA]),
                el('td', { className: 'cmp-label', text: r.label }),
                el('td', { className: 'cmp-bar' }, [barB]),
                el('td', { className: (better === 2 ? 'win' : ''), text: r.fmt(r.vb) }),
            ]));
        });
        const side = (p, wins) => el('a', { className: 'cmp-side', attrs: { href: '#/player/' + encodeURIComponent(p.name) } }, [
            head(p.name, 'xl'), el('h2', { text: p.name }),
            el('span', { className: 'tag ' + (p.online ? 'green' : ''), text: p.online ? '● Online' : '○ Offline' }),
            el('div', { className: 'cmp-wins', text: T('cmp.wins', { n: wins, total: rows.length }) }),
        ]);
        const verdict = wa === wb ? T('cmp.tie') : T('cmp.leader', { player: wa > wb ? pa.name : pb.name });
        root.appendChild(el('div', { className: 'card cmp-head' }, [side(pa, wa), el('div', { className: 'cmp-vs' }, [el('b', { text: 'VS' }), el('span', { className: 'muted small', text: verdict })]), side(pb, wb)]));
        root.appendChild(el('div', { className: 'card' }, [el('div', { className: 'table-wrap' }, [el('table', { className: 'table cmp-table' }, [tb])])]));
    }

    // ================================================================== CHAT
    const Chat = (function () {
        const MAX = 500;
        const box = $('#chat-box');
        const newBtn = $('#chat-new');
        let messages = [];
        let seen = new Set();
        let controller = null;
        let retry = 0;
        let retryTimer = null;
        let visible = false;
        let unread = 0;
        let stickToBottom = true;
        let running = false;

        const filter = { q: '', type: 'all' };
        $('#chat-search').addEventListener('input', (e) => { filter.q = e.target.value.trim().toLowerCase(); renderAll(); });
        $('#chat-filter').addEventListener('change', (e) => { filter.type = e.target.value; renderAll(); });
        box.addEventListener('scroll', () => {
            stickToBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 40;
            if (stickToBottom) newBtn.hidden = true;
        });
        newBtn.addEventListener('click', () => { stickToBottom = true; scrollBottom(); newBtn.hidden = true; });

        function setStatus(cls, text) {
            const pill = $('#chat-status');
            pill.classList.remove('ok', 'warn', 'bad');
            if (cls) pill.classList.add(cls);
            pill.lastElementChild.textContent = text;
        }
        const keyOf = (m) => m.time + '|' + m.type + '|' + m.user + '|' + m.message;
        const matches = (m) => (filter.type === 'all' || (m.type || 'chat') === filter.type)
            && (!filter.q || (m.user || '').toLowerCase().includes(filter.q) || (m.message || '').toLowerCase().includes(filter.q));

        function node(m) {
            const type = m.type || 'chat';
            const user = el('span', { className: 'user', text: type === 'system' ? '📢 ' + (m.user || T('chat.system')) : m.user });
            if (type !== 'system' && VALID_NAME.test(m.user || '')) user.addEventListener('click', () => { location.hash = '#/player/' + encodeURIComponent(m.user); });
            return el('div', { className: 'msg ' + type }, [
                type === 'system' ? el('img', { className: 'head', attrs: { alt: '', src: '/web/favicon.svg', width: '32', height: '32' } }) : head(m.user),
                el('div', null, [
                    el('div', { className: 'meta' }, [user, el('span', { className: 'time', text: m.time ? fmtClock(m.time) : '' })]),
                    el('div', { className: 'text', text: chatText(m) }),
                ]),
            ]);
        }
        /** Entradas/salidas: el texto lo pone el idioma del dashboard, no el del servidor. */
        function chatText(m) {
            if (m.type === 'join' || m.type === 'quit') return T('chat.event.' + m.type);
            return m.message;
        }
        function scrollBottom() { box.scrollTop = box.scrollHeight; }
        function renderAll() {
            clear(box);
            const list = messages.filter(matches);
            if (!list.length) box.appendChild(el('div', { className: 'chat-sep', text: T(messages.length ? 'chat.no-match' : 'chat.empty') }));
            list.forEach((m) => box.appendChild(node(m)));
            stickToBottom = true;
            scrollBottom();
        }
        function add(m, live) {
            if (!m || typeof m.message !== 'string') return;
            if (m.type === 'notify') { if (live) LiveToasts.show(m); return; }
            const k = keyOf(m);
            if (seen.has(k)) return;
            seen.add(k);
            if (live && m.type === 'join') LiveToasts.show(m);
            messages.push(m);
            if (messages.length > MAX) { const old = messages.shift(); seen.delete(keyOf(old)); }
            if (!live) return;
            if (!visible) { unread++; const b = $('#chat-unread'); b.hidden = false; b.textContent = unread > 99 ? '99+' : String(unread); return; }
            if (!matches(m)) return;
            const empty = box.querySelector('.chat-sep'); if (empty && box.children.length === 1) empty.remove();
            box.appendChild(node(m));
            while (box.children.length > MAX) box.removeChild(box.firstChild);
            if (stickToBottom) scrollBottom(); else newBtn.hidden = false;
        }

        async function loadHistory() {
            try {
                const h = await api('/api/chat/history?limit=200');
                h.forEach((m) => add(m, false));
                messages.sort((a, b) => (a.time || 0) - (b.time || 0));
                if (visible) renderAll();
            } catch (e) { /* el stream sigue igual */ }
        }

        async function connect() {
            if (!running) return;
            controller = new AbortController();
            setStatus('warn', T('chat.connecting'));
            try {
                const res = await fetch('/api/chat/stream', {
                    headers: apiKey ? { Authorization: 'Bearer ' + apiKey } : {}, signal: controller.signal, cache: 'no-store',
                });
                if (res.status === 401) { onUnauthorized(); return; }
                if (!res.ok || !res.body) throw new Error('HTTP ' + res.status);
                retry = 0;
                setStatus('ok', T('chat.live'));
                loadHistory();
                await readSSE(res, (m) => add(m, true));
                throw new Error('stream cerrado');
            } catch (e) {
                if (!running || (e && e.name === 'AbortError')) return;
                const delay = Math.min(30000, 1000 * Math.pow(2, retry++)) * (0.8 + Math.random() * 0.4);
                let left = Math.ceil(delay / 1000);
                setStatus('bad', T('chat.retry', { n: left }));
                const countdown = setInterval(() => { left--; if (left > 0) setStatus('bad', T('chat.retry', { n: left })); }, 1000);
                retryTimer = setTimeout(() => { clearInterval(countdown); connect(); }, delay);
            }
        }

        return {
            start() { if (running) return; running = true; retry = 0; connect(); },
            stop() {
                running = false;
                if (controller) controller.abort();
                clearTimeout(retryTimer);
                messages = []; seen = new Set(); setStatus('', T('chat.disconnected'));
            },
            setVisible(v) {
                visible = v;
                if (v) { unread = 0; $('#chat-unread').hidden = true; renderAll(); }
            },
        };
    })();

    function renderChat() { /* el stream ya está activo desde el inicio de sesión */ }

    // ================================================================== ADMIN
    let adminOk = null;
    async function renderAdmin() {
        const off = $('#admin-disabled');
        const rootEl = $('#admin-root');
        try {
            await api('/api/admin/check');
            adminOk = true;
            off.hidden = true;
            rootEl.hidden = false;
            loadAdminLog();
            loadAdminPanels();
            $('#admin-console').hidden = !feature('web-console');
            if (feature('web-console')) Console.start();
            every(30000, () => { loadAdminLog(); loadAdminPanels(); });
        } catch (e) {
            adminOk = false;
            rootEl.hidden = true;
            off.hidden = false;
            clear(off).appendChild(el('h3', { text: T('ad.unavailable') }));
            const msg = e.status === 403 ? (securityEnabled
                ? T('ad.disabled')
                : T('ad.needs-security'))
                : e.status === 429 ? T('ad.rate')
                    : e.status === 401 ? T('ad.bad-key') : T('error', { error: e.message });
            off.appendChild(el('p', { text: msg }));
        }
    }

    async function loadAdminLog() {
        let log;
        try { log = await api('/api/admin/log'); } catch (e) { return; }
        const tb = clear($('#admin-log tbody'));
        if (!log.length) tb.appendChild(el('tr', null, [el('td', { className: 'empty', text: T('ad.log.empty'), attrs: { colspan: '4' } })]));
        log.forEach((l) => tb.appendChild(el('tr', null, [
            el('td', { text: fmtDateTime(l.time) }),
            el('td', { className: l.ok ? 'ok-cell' : 'fail-cell', text: (l.ok ? '✔ ' : '✖ ') + l.action }),
            el('td', { text: l.details }),
            el('td', { className: 'hide-sm muted', text: l.remote }),
        ])));
    }
    $('#admin-log-refresh').addEventListener('click', loadAdminLog);

    async function adminPost(action, body, okMsg) {
        try {
            const r = await api('/api/admin/' + action, { method: 'POST', body: body || {} });
            toast(okMsg || (r && r.message) || T('ad.done'));
            loadAdminLog();
            return r;
        } catch (e) {
            toast(T('error', { error: e.message }), 'error');
            loadAdminLog();
            return null;
        }
    }

    const statForm = $('#form-stat');
    async function showCurrentStat() {
        const name = statForm.player.value.trim();
        const out = $('#stat-current');
        if (!VALID_NAME.test(name)) { out.textContent = ''; return; }
        try {
            const p = await api('/api/profile?name=' + encodeURIComponent(name));
            const st = statByKey(statForm.stat.value);
            out.textContent = T('ad.current', { player: p.name, value: st.fmt((p.stats || {})[st.key]) }) + (p.online ? ' (online)' : ' (offline)');
        } catch (e) { out.textContent = e.status === 404 ? T('ad.not-found') : ''; }
    }
    let curTimer = null;
    statForm.player.addEventListener('input', () => { clearTimeout(curTimer); curTimer = setTimeout(showCurrentStat, 350); });
    statForm.stat.addEventListener('change', showCurrentStat);
    statForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const body = { player: statForm.player.value.trim(), stat: statForm.stat.value, mode: statForm.mode.value, value: Number(statForm.value.value) };
        if (!VALID_NAME.test(body.player) || !isFinite(body.value)) { toast(T('ad.check'), 'warn'); return; }
        const r = await adminPost('stat', body);
        if (r && r.ok) { toast(r.player + ': ' + statByKey(r.stat).label + ' = ' + statByKey(r.stat).fmt(r.value)); showCurrentStat(); }
    });

    const resetForm = $('#form-reset');
    resetForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const player = resetForm.player.value.trim();
        if (!VALID_NAME.test(player)) { toast(T('ad.invalid-name'), 'warn'); return; }
        const ok = await confirmDialog(T('ad.reset.title', { player }), T('ad.reset.text'), player);
        if (!ok) return;
        if (await adminPost('reset', { player }, T('ad.reset.done', { player }))) resetForm.reset();
    });

    // Vista previa con códigos de color & (textContent por segmento)
    const MC = { 0: '#000000', 1: '#0000AA', 2: '#00AA00', 3: '#00AAAA', 4: '#AA0000', 5: '#AA00AA', 6: '#FFAA00', 7: '#AAAAAA', 8: '#555555', 9: '#5555FF', a: '#55FF55', b: '#55FFFF', c: '#FF5555', d: '#FF55FF', e: '#FFFF55', f: '#FFFFFF' };
    function mcPreview(target, text) {
        clear(target);
        let color = '#FFFFFF', bold = false, italic = false;
        text.split(/(&[0-9a-fk-or])/i).forEach((part) => {
            const m = /^&([0-9a-fk-or])$/i.exec(part);
            if (m) {
                const c = m[1].toLowerCase();
                if (MC[c]) { color = MC[c]; bold = italic = false; } else if (c === 'l') bold = true; else if (c === 'o') italic = true; else if (c === 'r') { color = '#FFFFFF'; bold = italic = false; }
                return;
            }
            if (!part) return;
            const span = el('span', { text: part });
            span.style.color = color;
            if (bold) span.style.fontWeight = '700';
            if (italic) span.style.fontStyle = 'italic';
            target.appendChild(span);
        });
    }
    const bForm = $('#form-broadcast');
    bForm.message.addEventListener('input', () => mcPreview($('#broadcast-preview'), bForm.message.value));
    bForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const message = bForm.message.value.trim();
        if (!message) return;
        if (await adminPost('broadcast', { message }, T('ad.broadcast.done'))) { bForm.reset(); clear($('#broadcast-preview')); }
    });

    $$('[data-admin]').forEach((b) => b.addEventListener('click', async () => {
        const action = b.dataset.admin;
        if (action === 'reload') {
            const ok = await confirmDialog(T('ad.reload.title'), T('ad.reload.text'));
            if (!ok) return;
        }
        b.disabled = true;
        const msgs = { 'leaderboard/refresh': T('ad.msg.refresh'), save: T('ad.msg.save'), reload: T('ad.msg.reload') };
        await adminPost(action, {}, msgs[action]);
        setTimeout(() => { b.disabled = false; }, 1500);
    }));

    // Cerrar ya el top semanal/mensual (anuncia ganadores, premia y empieza otro)
    $$('[data-period-end]').forEach((b) => b.addEventListener('click', async () => {
        const type = b.dataset.periodEnd;
        const period = T('period.' + type);
        if (!(await confirmDialog(T('ad.period.title', { period }), T('ad.period.text')))) return;
        b.disabled = true;
        await adminPost('period/end', { type }, T('ad.period.done', { period }));
        setTimeout(() => { b.disabled = false; }, 1500);
    }));

    // ---------- Temporadas, eventos y Discord
    async function loadSeasonAdmin() {
        const card = $('#admin-seasons');
        let d;
        try { d = await api('/api/seasons?limit=1'); } catch (e) { card.hidden = true; return; }
        card.hidden = false;
        const cur = d.current || {};
        $('#admin-season-text').textContent = T('ad.season.text', { name: cur.name || '?',
            time: cur.end ? fmtDuration((cur.end - Date.now()) / 1000) : T('ad.season.manual') });
    }
    $('#season-end-btn').addEventListener('click', async (e) => {
        if (!(await confirmDialog(T('ad.season.title'), T('ad.season.confirm')))) return;
        e.target.disabled = true;
        await adminPost('season/end', {}, T('ad.season.done'));
        setTimeout(() => { e.target.disabled = false; loadSeasonAdmin(); }, 2500);
    });

    async function loadEventAdmin() {
        const card = $('#admin-events');
        let d;
        try { d = await api('/api/events?limit=1&lang=' + I18N.lang); } catch (e) { card.hidden = true; return; }
        card.hidden = false;
        const sel = $('#event-what');
        const prev = sel.value;
        clear(sel);
        (d.presets || []).forEach((p) => sel.appendChild(el('option', { text: '★ ' + p.name + ' (' + fmtDuration(p.duration) + ')', attrs: { value: p.id } })));
        STATS.forEach((st) => sel.appendChild(el('option', { text: st.icon + ' ' + st.label, attrs: { value: st.key } })));
        if (prev) sel.value = prev;
        const running = !!d.active;
        $('#admin-event-status').textContent = running
            ? T('ad.event.running', { name: d.active.name, time: fmtDuration((d.active.end - Date.now()) / 1000) }) : T('ad.event.idle');
        $('#event-start-btn').disabled = running;
        $('#event-stop-btn').disabled = !running;
        $('#event-cancel-btn').disabled = !running;
    }
    $('#admin-events').addEventListener('submit', async (e) => {
        e.preventDefault();
        const f = e.target;
        const body = { what: f.what.value };
        if (f.duration.value.trim()) body.duration = f.duration.value.trim();
        if (f.name.value.trim()) body.name = f.name.value.trim();
        if (await adminPost('event/start', body, T('ad.event.started'))) f.reset();
        loadEventAdmin();
    });
    [['#event-stop-btn', false], ['#event-cancel-btn', true]].forEach(([id, cancel]) => $(id).addEventListener('click', async () => {
        if (!(await confirmDialog(T(cancel ? 'ad.event.cancel' : 'ad.event.stop'), T(cancel ? 'ad.event.cancel.text' : 'ad.event.stop.text')))) return;
        await adminPost('event/stop', { cancel }, T('ad.event.stopped'));
        loadEventAdmin();
    }));

    async function loadDiscordStatus() {
        const card = $('#admin-discord');
        let d;
        try { d = await api('/api/discord'); } catch (e) { card.hidden = true; return; }
        card.hidden = false;
        const box = clear($('#discord-status'));
        const ok = d.connected || d.state === 'discordsrv';
        const tags = el('div', { className: 'tags' }, [
            el('span', { className: 'tag ' + (ok ? 'green' : d.state === 'no-token' ? 'amber' : 'red'), text: T('dc.state.' + d.state) || d.state }),
            el('span', { className: 'tag', text: T('dc.mode', { mode: d.mode }) }),
        ]);
        if (d.bot) tags.appendChild(el('span', { className: 'tag', text: '🤖 ' + d.bot }));
        if (d.ping != null) tags.appendChild(el('span', { className: 'tag', text: d.ping + ' ms' }));
        if (d.discordsrv) tags.appendChild(el('span', { className: 'tag', text: 'DiscordSRV ' + d.discordsrv }));
        box.appendChild(tags);
        const facts = [T(d.bridge ? 'dc.bridge.on' : 'dc.bridge.off')];
        if (d.links >= 0) facts.push(T('dc.links', { n: fmtInt(d.links) }));
        if (d.last_role_sync) facts.push(T('dc.roles', { time: timeAgo(d.last_role_sync) }));
        box.appendChild(el('p', { className: 'muted small', text: facts.join(' · ') }));
        if (d.failure || d.last_error) box.appendChild(el('p', { className: 'fail-cell small', text: '⚠ ' + (d.failure || d.last_error) }));
    }

    // ---------- Alertas, copias, actualizaciones y CSV
    function loadAdminPanels() {
        if (feature('alerts')) loadAlerts(); else $('#admin-alerts').hidden = true;
        if (modules.seasons) loadSeasonAdmin(); else $('#admin-seasons').hidden = true;
        if (modules.events) loadEventAdmin(); else $('#admin-events').hidden = true;
        if (modules['discord-bot']) loadDiscordStatus(); else $('#admin-discord').hidden = true;
        if (feature('backups')) loadBackups(); else $('#admin-backups').hidden = true;
        if (feature('updates')) loadUpdates(); else $('#admin-updates').hidden = true;
    }

    async function loadAlerts() {
        const card = $('#admin-alerts');
        let a;
        try { a = await api('/api/admin/alerts'); } catch (e) { card.hidden = true; return; }
        card.hidden = false;
        const body = clear($('#alerts-body'));
        const active = new Set(a.active || []);
        const th = a.thresholds || {};
        const tags = el('div', { className: 'tags' });
        tags.appendChild(el('span', { className: 'tag ' + (active.has('tps') ? 'red' : 'green'),
            text: 'TPS ' + nf2.format(a.tps || 0) + (th.tps != null ? ' (≥ ' + nf2.format(th.tps) + ')' : '') }));
        tags.appendChild(el('span', { className: 'tag ' + (active.has('memory') ? 'red' : 'green'),
            text: T('al.memory', { value: fmtInt(a.memory_percent) }) + (th.memory_percent != null ? ' (≤ ' + fmtInt(th.memory_percent) + '%)' : '') }));
        tags.appendChild(el('span', { className: 'tag ' + (a.database_ok === false ? 'red' : 'green'), text: T(a.database_ok === false ? 'al.db.bad' : 'al.db.ok') }));
        body.appendChild(tags);
        body.appendChild(el('p', { className: 'muted small', text: active.size ? T('al.active', { n: active.size }) : T('al.none-active') }));
        const ul = el('ul', { className: 'plain-list' });
        if (!(a.recent || []).length) ul.appendChild(el('li', { className: 'muted', text: T('al.empty') }));
        (a.recent || []).forEach((r) => ul.appendChild(el('li', null, [
            el('span', { className: r.level === 'ok' ? 'ok-cell' : 'fail-cell', text: (r.level === 'ok' ? '✔ ' : '⚠ ') + r.message }),
            el('span', { className: 'muted small', text: fmtDateTime(r.time) }),
        ])));
        body.appendChild(ul);
    }

    const fmtSize = (b) => (b >= 1048576 ? nf2.format(b / 1048576) + ' MB' : nf2.format((b || 0) / 1024) + ' KB');
    async function loadBackups() {
        const card = $('#admin-backups');
        let b;
        try { b = await api('/api/admin/backups'); } catch (e) { card.hidden = true; return; }
        card.hidden = false;
        const parts = [];
        if (b.running) parts.push(T('bk.running'));
        else if (b.last) parts.push(b.last.ok ? T('bk.last-ok', { time: timeAgo(b.last.time), size: fmtSize(b.last.size), files: fmtInt(b.last.files) }) : T('bk.last-fail', { error: b.last.error || '?' }));
        if (b.next_run) parts.push(T('bk.next', { time: fmtDateTime(b.next_run) }));
        parts.push(T('bk.keep', { n: b.keep }));
        $('#backups-status').textContent = parts.join(' · ');
        const ul = clear($('#backups-list'));
        if (!(b.backups || []).length) ul.appendChild(el('li', { className: 'muted', text: T('bk.none') }));
        (b.backups || []).forEach((f) => ul.appendChild(el('li', null, [el('span', { className: 'mono', text: f.file }), el('span', { className: 'muted small', text: fmtSize(f.size) + ' · ' + fmtDateTime(f.time) })])));
        $('#backup-run').disabled = !!b.running;
    }
    $('#backup-run').addEventListener('click', async () => {
        $('#backup-run').disabled = true;
        await adminPost('backups/run', {}, T('bk.started'));
        loadBackups();
        setTimeout(loadBackups, 4000);
    });

    async function loadUpdates() {
        const card = $('#admin-updates');
        let u;
        try { u = await api('/api/admin/updates'); } catch (e) { card.hidden = true; return; }
        card.hidden = false;
        const out = clear($('#updates-status'));
        if (!u.configured) { out.appendChild(el('span', { className: 'muted', text: T('up.not-configured', { current: u.current }) })); $('#updates-check').disabled = true; return; }
        $('#updates-check').disabled = false;
        if (u.update_available) {
            out.appendChild(el('span', { className: 'tag amber', text: T('up.available', { latest: u.latest, current: u.current }) }));
            if (u.url && /^https:\/\//.test(u.url)) out.appendChild(el('a', { className: 'small', text: ' ' + T('up.download'), attrs: { href: u.url, target: '_blank', rel: 'noopener noreferrer' } }));
        } else if (u.error) {
            out.appendChild(el('span', { className: 'tag red', text: T('error', { error: u.error }) }));
        } else {
            out.appendChild(el('span', { className: 'tag green', text: u.latest ? T('up.latest', { current: u.current }) : T('up.unknown', { current: u.current }) }));
        }
        if (u.checked_at) out.appendChild(el('div', { className: 'muted small', text: T('up.checked', { time: timeAgo(u.checked_at), source: u.source }) }));
    }
    $('#updates-check').addEventListener('click', async () => {
        $('#updates-check').disabled = true;
        await adminPost('updates/check', {}, T('up.checking'));
        setTimeout(loadUpdates, 3000);
    });

    // El CSV se pide con el header Authorization y se descarga como blob (la key nunca va en la URL)
    $('#export-csv').addEventListener('click', async () => {
        const b = $('#export-csv');
        b.disabled = true;
        try {
            const res = await fetch('/api/admin/export.csv', { headers: { Authorization: 'Bearer ' + apiKey }, cache: 'no-store' });
            if (!res.ok) { let msg = 'HTTP ' + res.status; try { msg = (await res.json()).error || msg; } catch (e) { /* sin cuerpo */ } throw new Error(msg); }
            const blob = await res.blob();
            const m = /filename="?([^";]+)"?/.exec(res.headers.get('Content-Disposition') || '');
            const a = el('a', { attrs: { href: URL.createObjectURL(blob), download: m ? m[1] : 'players.csv' } });
            document.body.appendChild(a); a.click(); a.remove();
            setTimeout(() => URL.revokeObjectURL(a.href), 10000);
            toast(T('ad.csv.done'));
            loadAdminLog();
        } catch (e) { toast(T('error', { error: e.message }), 'error'); }
        setTimeout(() => { b.disabled = false; }, 1500);
    });

    // ---------- Consola web en vivo
    const Console = (function () {
        const MAX = 500;
        const box = $('#console-box');
        let controller = null, running = false, retry = 0, retryTimer = null;
        function setStatus(cls, text) {
            const pill = $('#console-status');
            pill.classList.remove('ok', 'warn', 'bad');
            if (cls) pill.classList.add(cls);
            pill.lastElementChild.textContent = text;
        }
        const fmtTime = (ms) => new Date(ms).toLocaleTimeString(I18N.locale, { hour: '2-digit', minute: '2-digit', second: '2-digit' });
        function line(o) {
            if (!o || typeof o.m !== 'string') return;
            const stick = box.scrollHeight - box.scrollTop - box.clientHeight < 40;
            box.appendChild(el('div', { className: 'cl ' + String(o.l || 'INFO').toLowerCase() }, [el('span', { className: 'cl-time', text: fmtTime(o.t) }), el('span', { text: o.m })]));
            while (box.children.length > MAX) box.removeChild(box.firstChild);
            if (stick) box.scrollTop = box.scrollHeight;
        }
        async function connect() {
            if (!running) return;
            controller = new AbortController();
            setStatus('warn', T('chat.connecting'));
            const queue = [];
            let ready = false;
            try {
                const res = await fetch('/api/admin/console/stream', { headers: { Authorization: 'Bearer ' + apiKey }, signal: controller.signal, cache: 'no-store' });
                if (res.status === 503) { setStatus('bad', T('con.busy')); return; }
                if (!res.ok || !res.body) throw new Error('HTTP ' + res.status);
                retry = 0;
                // El buffer se pide con el stream ya abierto; lo que llegue mientras tanto se encola y se deduplica
                api('/api/admin/console').then((first) => {
                    clear(box);
                    const seen = new Set();
                    (first.lines || []).forEach((o) => { seen.add(o.t + '|' + o.m); line(o); });
                    queue.forEach((o) => { if (!seen.has(o.t + '|' + o.m)) line(o); });
                    ready = true;
                    box.scrollTop = box.scrollHeight;
                }).catch(() => { ready = true; queue.forEach(line); });
                setStatus('ok', T('chat.live'));
                await readSSE(res, (o) => { if (ready) line(o); else queue.push(o); });
                throw new Error('stream cerrado');
            } catch (e) {
                if (!running || (e && e.name === 'AbortError')) return;
                const delay = Math.min(30000, 1000 * Math.pow(2, retry++));
                setStatus('bad', T('chat.retry', { n: Math.ceil(delay / 1000) }));
                retryTimer = setTimeout(connect, delay);
            }
        }
        $('#console-form').addEventListener('submit', async (e) => {
            e.preventDefault();
            const input = e.target.command;
            const command = input.value.trim().replace(/^\//, '');
            if (!command) return;
            try {
                await api('/api/admin/console/command', { method: 'POST', body: { command } });
                input.value = '';
            } catch (err) { toast(T('error', { error: err.message }), 'error'); }
            loadAdminLog();
        });
        return {
            start() { if (running) return; running = true; retry = 0; connect(); },
            stop() { running = false; if (controller) controller.abort(); clearTimeout(retryTimer); setStatus('', T('chat.disconnected')); },
        };
    })();

    // ================================================================== inicio
    boot();
})();
