// Share glue for the Compose wasmJs app. Kotlin builds a JSON spec and calls
// openftbaShare(json); this draws a 1080×1080 dark card and shares it via the Web Share
// API (with the PNG file), falling back to download + copy-text. Self-contained.

function openftbaShare(specJson) {
    let spec;
    try { spec = JSON.parse(specJson); } catch (e) { return; }
    const canvas = ftbaBuildCard(spec);
    ftbaShareCanvas(canvas, spec.text || "", spec.fileNameBase || "openftba");
}

function ftbaHexA(hex, a) {
    const n = parseInt((hex || "#5BE3C8").slice(1), 16);
    return `rgba(${(n >> 16) & 255},${(n >> 8) & 255},${n & 255},${a})`;
}

function ftbaBuildCard(spec) {
    const S = 1080;
    const c = document.createElement("canvas");
    c.width = S; c.height = S;
    const ctx = c.getContext("2d");
    ctx.fillStyle = "#0C0E12"; ctx.fillRect(0, 0, S, S);
    const glow = ctx.createRadialGradient(S * 0.8, S * 0.15, 40, S * 0.8, S * 0.15, S * 0.8);
    glow.addColorStop(0, ftbaHexA(spec.accent, 0.18)); glow.addColorStop(1, "rgba(0,0,0,0)");
    ctx.fillStyle = glow; ctx.fillRect(0, 0, S, S);

    const pad = 80;
    ctx.textBaseline = "alphabetic";
    ctx.font = "700 34px sans-serif"; ctx.fillStyle = "#8A93A3"; ctx.fillText("OPEN", pad, 110);
    const ow = ctx.measureText("OPEN").width;
    ctx.fillStyle = spec.accent || "#5BE3C8"; ctx.fillText("FTBA", pad + ow, 110);

    ctx.font = "400 30px sans-serif"; ctx.fillStyle = "#8A93A3"; ctx.fillText(spec.subtitle || "", pad, 160);

    ctx.fillStyle = "#E6E9EF"; ctx.font = "700 190px sans-serif"; ctx.fillText(spec.bigValue, pad - 4, 380);
    const bw = ctx.measureText(spec.bigValue).width;
    ctx.font = "500 56px sans-serif"; ctx.fillStyle = "#8A93A3"; ctx.fillText(spec.bigUnit || "", pad + bw + 14, 380);

    if (spec.tierLabel) {
        const by = 250, bx = S - pad - 360, tc = spec.tierColor || "#5BE3C8";
        ctx.fillStyle = ftbaHexA(tc, 0.16); ftbaRound(ctx, bx, by, 360, 90, 24); ctx.fill();
        ctx.strokeStyle = tc; ctx.lineWidth = 2; ftbaRound(ctx, bx, by, 360, 90, 24); ctx.stroke();
        ctx.fillStyle = tc; ctx.font = "600 44px sans-serif";
        ctx.textAlign = "center"; ctx.textBaseline = "middle";
        ctx.fillText(spec.tierLabel, bx + 180, by + 47);
        ctx.textAlign = "left"; ctx.textBaseline = "alphabetic";
    }

    // Art band: 3D track silhouette when available, speed sparkline as the fallback.
    const art = spec.trackArt;
    const spark = spec.spark || [];
    if (art && art.xs && art.xs.length >= 2) {
        ftbaDrawTrackArt(ctx, art, pad, 415, S - pad * 2, 230);
    } else if (spark.length >= 2) {
        let mn = Infinity, mx = -Infinity;
        for (const v of spark) { if (v < mn) mn = v; if (v > mx) mx = v; }
        const span = (mx - mn) || 1, x = pad, y = 430, w = S - pad * 2, h = 200;
        const fill = ctx.createLinearGradient(0, y, 0, y + h);
        fill.addColorStop(0, ftbaHexA(spec.accent, 0.35)); fill.addColorStop(1, ftbaHexA(spec.accent, 0));
        ctx.beginPath(); ctx.moveTo(x, y + h);
        spark.forEach((v, i) => ctx.lineTo(x + (i / (spark.length - 1)) * w, y + h - ((v - mn) / span) * h));
        ctx.lineTo(x + w, y + h); ctx.closePath(); ctx.fillStyle = fill; ctx.fill();
        ctx.beginPath();
        spark.forEach((v, i) => { const px = x + (i / (spark.length - 1)) * w, py = y + h - ((v - mn) / span) * h; i ? ctx.lineTo(px, py) : ctx.moveTo(px, py); });
        ctx.strokeStyle = spec.accent || "#5BE3C8"; ctx.lineWidth = 4; ctx.stroke();
    }

    const stats = spec.stats || [], gx = pad, gy = 700, colW = (S - pad * 2) / 2;
    stats.forEach((st, i) => {
        const x = gx + (i % 2) * colW, y = gy + Math.floor(i / 2) * 130;
        ctx.font = "500 26px sans-serif"; ctx.fillStyle = "#8A93A3"; ctx.fillText((st.label || "").toUpperCase(), x, y);
        ctx.font = "700 64px sans-serif"; ctx.fillStyle = st.color || "#E6E9EF"; ctx.fillText(st.value, x, y + 66);
    });

    ctx.font = "400 26px sans-serif"; ctx.fillStyle = "#5A6372";
    ctx.fillText("local-only cycling analytics · no tracking", pad, S - 56);
    return c;
}

// Geometry and colors arrive fully precomputed (normalized 0..1) — just scale and stroke.
function ftbaDrawTrackArt(ctx, art, x, y, w, h) {
    const px = v => x + v * w, py = v => y + v * h;
    const seg = (x0, y0, x1, y1) => {
        ctx.beginPath(); ctx.moveTo(px(x0), py(y0)); ctx.lineTo(px(x1), py(y1)); ctx.stroke();
    };
    ctx.lineCap = "round";
    const grid = art.grid || [];
    ctx.strokeStyle = art.gridColor || "rgba(42,47,56,0.55)"; ctx.lineWidth = 1;
    for (let i = 0; i + 3 < grid.length; i += 4) seg(grid[i], grid[i + 1], grid[i + 2], grid[i + 3]);
    ctx.lineWidth = 3;
    for (let i = 0; i < art.shadowXs.length - 1; i++) {
        ctx.strokeStyle = art.shadowColors[i];
        seg(art.shadowXs[i], art.shadowYs[i], art.shadowXs[i + 1], art.shadowYs[i + 1]);
    }
    const drops = art.drops || [], dropColors = art.dropColors || [];
    ctx.lineWidth = 1.5;
    for (let i = 0; i < dropColors.length; i++) {
        ctx.strokeStyle = dropColors[i];
        seg(drops[i * 4], drops[i * 4 + 1], drops[i * 4 + 2], drops[i * 4 + 3]);
    }
    ctx.lineWidth = 4;
    for (let i = 0; i < art.xs.length - 1; i++) {
        ctx.strokeStyle = art.colors[i];
        seg(art.xs[i], art.ys[i], art.xs[i + 1], art.ys[i + 1]);
    }
    ctx.lineCap = "butt";
}

function ftbaRound(ctx, x, y, w, h, r) {
    ctx.beginPath(); ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r); ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r); ctx.arcTo(x, y, x + w, y, r); ctx.closePath();
}

async function ftbaShareCanvas(canvas, text, base) {
    const blob = await new Promise(res => canvas.toBlob(res, "image/png"));
    const file = new File([blob], base + ".png", { type: "image/png" });
    if (navigator.canShare && navigator.canShare({ files: [file] })) {
        try { await navigator.share({ files: [file], text }); ftbaToast("✓"); return; }
        catch (e) { if (e && e.name === "AbortError") return; }
    }
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a"); a.href = url; a.download = file.name; a.click();
    URL.revokeObjectURL(url);
    try { await navigator.clipboard.writeText(text); } catch (e) {}
    ftbaToast(text ? "🖼️ + 📋" : "🖼️");
}

let ftbaToastTimer = null;
function ftbaToast(msg) {
    let el = document.getElementById("ftba-toast");
    if (!el) {
        el = document.createElement("div"); el.id = "ftba-toast";
        el.style.cssText = "position:fixed;bottom:28px;left:50%;transform:translateX(-50%);background:#1C2027;color:#E6E9EF;border:1px solid #2A2F38;border-radius:12px;padding:12px 20px;font-family:sans-serif;z-index:9999;transition:opacity .2s;";
        document.body.appendChild(el);
    }
    el.textContent = msg; el.style.opacity = "1";
    clearTimeout(ftbaToastTimer);
    ftbaToastTimer = setTimeout(() => { el.style.opacity = "0"; }, 2600);
}
