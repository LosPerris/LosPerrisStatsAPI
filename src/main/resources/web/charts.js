/*
 * Mini librería de gráficas en <canvas> para el dashboard (sin dependencias externas).
 * Soporta: líneas (series temporales), barras horizontales, columnas (histograma) y donut.
 * Maneja devicePixelRatio, redimensionado (ResizeObserver), animación de entrada y tooltips.
 */
(function () {
    'use strict';

    const FONT = '12px system-ui, -apple-system, "Segoe UI", Roboto, sans-serif';
    const GRID = 'rgba(255,255,255,0.06)';
    const AXIS = '#8b98a8';
    const states = new WeakMap();

    function niceStep(range, ticks) {
        const raw = range / Math.max(1, ticks);
        const mag = Math.pow(10, Math.floor(Math.log10(raw || 1)));
        const n = raw / mag;
        const step = n < 1.5 ? 1 : n < 3 ? 2 : n < 7 ? 5 : 10;
        return step * mag;
    }

    function compact(v) {
        const a = Math.abs(v);
        if (a >= 1e6) return (v / 1e6).toFixed(a >= 1e7 ? 0 : 1) + 'M';
        if (a >= 1e3) return (v / 1e3).toFixed(a >= 1e4 ? 0 : 1) + 'k';
        return Math.round(v * 100) / 100 + '';
    }

    function ease(t) { return 1 - Math.pow(1 - t, 3); }

    function getState(canvas) {
        let st = states.get(canvas);
        if (st) return st;
        st = { spec: null, draw: null, progress: 1, hover: null, tip: null };
        states.set(canvas, st);
        if (window.ResizeObserver) {
            const ro = new ResizeObserver(() => render(canvas));
            ro.observe(canvas.parentElement || canvas);
        } else {
            window.addEventListener('resize', () => render(canvas));
        }
        canvas.addEventListener('mousemove', (e) => {
            const r = canvas.getBoundingClientRect();
            st.hover = { x: e.clientX - r.left, y: e.clientY - r.top };
            render(canvas);
        });
        canvas.addEventListener('mouseleave', () => { st.hover = null; hideTip(st); render(canvas); });
        return st;
    }

    function tipEl(st, canvas) {
        if (!st.tip) {
            st.tip = document.createElement('div');
            st.tip.className = 'chart-tip';
            st.tip.hidden = true;
            (canvas.parentElement || document.body).appendChild(st.tip);
        }
        return st.tip;
    }

    function showTip(st, canvas, x, y, lines) {
        const el = tipEl(st, canvas);
        el.textContent = '';
        lines.forEach((l, i) => {
            const d = document.createElement('div');
            d.textContent = l;
            if (i === 0) d.style.fontWeight = '700';
            el.appendChild(d);
        });
        el.style.left = x + 'px';
        el.style.top = y + 'px';
        el.hidden = false;
    }

    function hideTip(st) { if (st.tip) st.tip.hidden = true; }

    function setup(canvas) {
        const dpr = window.devicePixelRatio || 1;
        const w = canvas.clientWidth, h = canvas.clientHeight;
        if (!w || !h) return null;
        if (canvas.width !== Math.round(w * dpr) || canvas.height !== Math.round(h * dpr)) {
            canvas.width = Math.round(w * dpr);
            canvas.height = Math.round(h * dpr);
        }
        const ctx = canvas.getContext('2d');
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        ctx.clearRect(0, 0, w, h);
        ctx.font = FONT;
        return { ctx, w, h };
    }

    function render(canvas) {
        const st = states.get(canvas);
        if (!st || !st.draw) return;
        const env = setup(canvas);
        if (!env) return;
        st.draw(env, st);
    }

    function mount(canvas, spec, draw, animate) {
        const st = getState(canvas);
        const first = !st.spec;
        st.spec = spec;
        st.draw = draw;
        if (first && animate !== false && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
            const start = performance.now();
            const tick = (now) => {
                st.progress = ease(Math.min(1, (now - start) / 600));
                render(canvas);
                if (st.progress < 1) requestAnimationFrame(tick);
            };
            st.progress = 0;
            requestAnimationFrame(tick);
        } else {
            st.progress = 1;
            render(canvas);
        }
    }

    function emptyText(ctx, w, h, text) {
        ctx.fillStyle = AXIS;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(text || (window.I18N ? I18N.t('ov.no-data') : 'Sin datos todavía'), w / 2, h / 2);
    }

    // ------------------------------------------------------------------ líneas
    function line(canvas, spec) {
        mount(canvas, spec, ({ ctx, w, h }, st) => {
            const s = st.spec;
            const all = [];
            s.series.forEach((se) => se.data.forEach((p) => all.push(p)));
            if (all.length < 2) { emptyText(ctx, w, h, s.empty); return; }
            const pad = { l: 40, r: 12, t: 12, b: 26 };
            const xs = all.map((p) => p[0]), ys = all.map((p) => p[1]);
            const xMin = Math.min(...xs), xMax = Math.max(...xs);
            let yMin = s.yMin != null ? s.yMin : Math.min(0, ...ys);
            let yMax = s.yMax != null ? s.yMax : Math.max(...ys);
            if (yMax === yMin) yMax = yMin + 1;
            let step = niceStep(yMax - yMin, 4);
            if (s.integer) step = Math.max(1, Math.round(step)); // conteos: sin marcas repetidas
            yMax = Math.ceil(yMax / step) * step;
            const X = (x) => pad.l + (xMax === xMin ? 0 : (x - xMin) / (xMax - xMin)) * (w - pad.l - pad.r);
            const Y = (y) => h - pad.b - (y - yMin) / (yMax - yMin) * (h - pad.t - pad.b);

            ctx.strokeStyle = GRID; ctx.fillStyle = AXIS; ctx.lineWidth = 1;
            ctx.textAlign = 'right'; ctx.textBaseline = 'middle';
            for (let v = yMin; v <= yMax + 1e-9; v += step) {
                ctx.beginPath(); ctx.moveTo(pad.l, Y(v) + .5); ctx.lineTo(w - pad.r, Y(v) + .5); ctx.stroke();
                ctx.fillText((s.fmtY || compact)(v), pad.l - 6, Y(v));
            }
            ctx.textAlign = 'center'; ctx.textBaseline = 'top';
            const nX = Math.max(2, Math.min(6, Math.floor((w - pad.l) / 90)));
            for (let i = 0; i < nX; i++) {
                const x = xMin + (xMax - xMin) * i / (nX - 1);
                ctx.fillText((s.fmtX || String)(x), Math.min(w - 24, Math.max(pad.l + 16, X(x))), h - pad.b + 8);
            }

            const clipW = pad.l + (w - pad.l - pad.r) * st.progress;
            ctx.save();
            ctx.beginPath(); ctx.rect(0, 0, clipW, h); ctx.clip();
            s.series.forEach((se) => {
                if (se.data.length < 1) return;
                ctx.beginPath();
                se.data.forEach((p, i) => (i ? ctx.lineTo(X(p[0]), Y(p[1])) : ctx.moveTo(X(p[0]), Y(p[1]))));
                ctx.strokeStyle = se.color; ctx.lineWidth = 2; ctx.lineJoin = 'round'; ctx.stroke();
                if (s.fill !== false) {
                    ctx.lineTo(X(se.data[se.data.length - 1][0]), Y(yMin));
                    ctx.lineTo(X(se.data[0][0]), Y(yMin));
                    ctx.closePath();
                    const g = ctx.createLinearGradient(0, pad.t, 0, h - pad.b);
                    g.addColorStop(0, se.color + '55'); g.addColorStop(1, se.color + '00');
                    ctx.fillStyle = g; ctx.fill();
                }
            });
            ctx.restore();

            // Tooltip: punto más cercano en X
            if (st.hover && st.progress === 1) {
                const se0 = s.series[0];
                let best = null;
                se0.data.forEach((p) => { const d = Math.abs(X(p[0]) - st.hover.x); if (!best || d < best.d) best = { d, p }; });
                if (best) {
                    const px = X(best.p[0]);
                    ctx.strokeStyle = 'rgba(255,255,255,.25)';
                    ctx.beginPath(); ctx.moveTo(px + .5, pad.t); ctx.lineTo(px + .5, h - pad.b); ctx.stroke();
                    const lines = [(s.fmtTip || s.fmtX || String)(best.p[0])];
                    s.series.forEach((se) => {
                        const q = se.data.find((d) => d[0] === best.p[0]);
                        if (!q) return;
                        ctx.fillStyle = se.color; ctx.beginPath(); ctx.arc(px, Y(q[1]), 4, 0, Math.PI * 2); ctx.fill();
                        lines.push(se.label + ': ' + (s.fmtY || compact)(q[1]));
                    });
                    showTip(st, canvas, px, Y(best.p[1]), lines);
                }
            } else hideTip(st);
        });
    }

    // ------------------------------------------------------------------ barras horizontales
    function bars(canvas, spec) {
        mount(canvas, spec, ({ ctx, w, h }, st) => {
            const s = st.spec;
            const n = s.values.length;
            if (!n) { emptyText(ctx, w, h, s.empty); return; }
            const labelW = Math.min(140, Math.max(70, w * 0.3));
            const valW = 64;
            const rowH = Math.min(34, (h - 4) / n);
            const barH = Math.max(6, rowH * 0.62);
            const max = Math.max(...s.values, 1e-9);
            const avail = w - labelW - valW - 10;
            let hoverIdx = -1;
            if (st.hover) hoverIdx = Math.floor((st.hover.y - 2) / rowH);
            s.values.forEach((v, i) => {
                const y = 2 + i * rowH + (rowH - barH) / 2;
                const bw = Math.max(2, (v / max) * avail * st.progress);
                ctx.fillStyle = i === hoverIdx ? '#e6edf3' : AXIS;
                ctx.textAlign = 'right'; ctx.textBaseline = 'middle';
                let label = s.labels[i];
                while (ctx.measureText(label).width > labelW - 12 && label.length > 3) label = label.slice(0, -2) + '…';
                ctx.fillText(label, labelW - 8, y + barH / 2);
                const color = (s.colors && s.colors[i]) || s.color || '#5fbf3a';
                const g = ctx.createLinearGradient(labelW, 0, labelW + bw, 0);
                g.addColorStop(0, color + 'aa'); g.addColorStop(1, color);
                ctx.fillStyle = g;
                roundRect(ctx, labelW, y, bw, barH, 4); ctx.fill();
                if (i === hoverIdx) { ctx.strokeStyle = '#fff'; ctx.lineWidth = 1; ctx.stroke(); }
                ctx.fillStyle = '#e6edf3'; ctx.textAlign = 'left';
                ctx.fillText((s.fmt || compact)(v), labelW + bw + 6, y + barH / 2);
            });
            canvas.style.cursor = hoverIdx >= 0 && hoverIdx < n && s.onClick ? 'pointer' : 'default';
            if (!canvas._barClick) {
                canvas._barClick = true;
                canvas.addEventListener('click', (e) => {
                    const cur = states.get(canvas).spec;
                    if (!cur.onClick) return;
                    const r = canvas.getBoundingClientRect();
                    const rh = Math.min(34, (canvas.clientHeight - 4) / cur.values.length);
                    const idx = Math.floor((e.clientY - r.top - 2) / rh);
                    if (idx >= 0 && idx < cur.values.length) cur.onClick(idx);
                });
            }
        });
    }

    // ------------------------------------------------------------------ columnas (histograma)
    function columns(canvas, spec) {
        mount(canvas, spec, ({ ctx, w, h }, st) => {
            const s = st.spec;
            const n = s.values.length;
            if (!n || s.values.every((v) => v === 0)) { emptyText(ctx, w, h, s.empty); return; }
            const pad = { l: 34, r: 8, t: 18, b: 26 };
            let max = Math.max(...s.values, 1);
            const step = niceStep(max, 4);
            max = Math.ceil(max / step) * step;
            const Y = (v) => h - pad.b - (v / max) * (h - pad.t - pad.b);
            ctx.strokeStyle = GRID; ctx.fillStyle = AXIS; ctx.textAlign = 'right'; ctx.textBaseline = 'middle';
            for (let v = 0; v <= max + 1e-9; v += step) {
                ctx.beginPath(); ctx.moveTo(pad.l, Y(v) + .5); ctx.lineTo(w - pad.r, Y(v) + .5); ctx.stroke();
                ctx.fillText(compact(v), pad.l - 6, Y(v));
            }
            const slot = (w - pad.l - pad.r) / n;
            const bw = Math.min(56, slot * 0.7);
            let hoverIdx = st.hover ? Math.floor((st.hover.x - pad.l) / slot) : -1;
            s.values.forEach((v, i) => {
                const x = pad.l + i * slot + (slot - bw) / 2;
                const top = Y(v * st.progress);
                const color = s.color || '#4fd1c5';
                const g = ctx.createLinearGradient(0, top, 0, h - pad.b);
                g.addColorStop(0, color); g.addColorStop(1, color + '66');
                ctx.fillStyle = g;
                roundRect(ctx, x, top, bw, h - pad.b - top, 4); ctx.fill();
                if (i === hoverIdx) { ctx.strokeStyle = '#fff'; ctx.stroke(); }
                ctx.fillStyle = '#e6edf3'; ctx.textAlign = 'center'; ctx.textBaseline = 'bottom';
                if (v > 0) ctx.fillText(String(v), x + bw / 2, top - 3);
                ctx.fillStyle = AXIS; ctx.textBaseline = 'top';
                let lbl = s.labels[i];
                if (ctx.measureText(lbl).width > slot - 2) lbl = lbl.replace(/ h$/, '');
                ctx.fillText(lbl, x + bw / 2, h - pad.b + 7);
            });
        });
    }

    // ------------------------------------------------------------------ donut
    function donut(canvas, spec) {
        mount(canvas, spec, ({ ctx, w, h }, st) => {
            const s = st.spec;
            const total = s.items.reduce((a, b) => a + b.value, 0);
            const r = Math.min(w, h) / 2 - 6;
            const cx = w / 2, cy = h / 2;
            if (!total) {
                ctx.strokeStyle = GRID; ctx.lineWidth = r * 0.32;
                ctx.beginPath(); ctx.arc(cx, cy, r * 0.82, 0, Math.PI * 2); ctx.stroke();
                emptyText(ctx, w, h, '0');
                return;
            }
            let a = -Math.PI / 2;
            const full = Math.PI * 2 * st.progress;
            s.items.forEach((it) => {
                const ang = (it.value / total) * full;
                if (ang <= 0) return;
                ctx.beginPath();
                ctx.strokeStyle = it.color; ctx.lineWidth = r * 0.32; ctx.lineCap = 'butt';
                ctx.arc(cx, cy, r * 0.82, a, a + ang - 0.02);
                ctx.stroke();
                a += ang;
            });
            ctx.fillStyle = '#e6edf3'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
            ctx.font = '700 22px system-ui, sans-serif';
            ctx.fillText(s.centerText != null ? s.centerText : String(total), cx, cy - 6);
            ctx.font = FONT; ctx.fillStyle = AXIS;
            ctx.fillText(s.centerSub || 'total', cx, cy + 16);
        });
    }

    function roundRect(ctx, x, y, w, h, r) {
        r = Math.max(0, Math.min(r, w / 2, h / 2));
        ctx.beginPath();
        ctx.moveTo(x + r, y);
        ctx.arcTo(x + w, y, x + w, y + h, r);
        ctx.arcTo(x + w, y + h, x, y + h, r);
        ctx.arcTo(x, y + h, x, y, r);
        ctx.arcTo(x, y, x + w, y, r);
        ctx.closePath();
    }

    window.Charts = { line, bars, columns, donut, compact };
})();
