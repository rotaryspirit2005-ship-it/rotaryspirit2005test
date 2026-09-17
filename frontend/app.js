(() => {
  const $ = (id) => document.getElementById(id);

  const dateInput = $("date");
  const today = new Date();
  dateInput.value = today.toISOString().slice(0, 10);

  $("geo-btn").addEventListener("click", () => {
    if (!navigator.geolocation) {
      alert("このブラウザは位置情報取得に対応していません。");
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        $("latitude").value = pos.coords.latitude.toFixed(5);
        $("longitude").value = pos.coords.longitude.toFixed(5);
      },
      (err) => alert("位置情報の取得に失敗しました: " + err.message)
    );
  });

  let lastResponse = null;

  function buildRequestBody() {
    return {
      latitude: parseFloat($("latitude").value),
      longitude: parseFloat($("longitude").value),
      date: $("date").value,
      garden_width: parseFloat($("garden-width").value),
      garden_depth: parseFloat($("garden-depth").value),
      garden_orientation_deg: parseFloat($("orientation").value),
      veranda_height: parseFloat($("veranda-height").value),
      garage: {
        enabled: $("garage-enabled").checked,
        x: parseFloat($("garage-x").value),
        y: parseFloat($("garage-y").value),
        width: parseFloat($("garage-width").value),
        depth: parseFloat($("garage-depth").value),
        height: parseFloat($("garage-height").value),
        rotation_deg: parseFloat($("garage-rotation").value),
      },
      sample_points: 21,
      time_step_minutes: 15,
    };
  }

  $("sim-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const statusEl = $("status");
    const btn = $("run-btn");
    btn.disabled = true;
    statusEl.textContent = "計算中... (外部の日射データを取得しています)";

    try {
      const resp = await fetch("/api/simulate", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(buildRequestBody()),
      });
      if (!resp.ok) {
        const err = await resp.json().catch(() => ({}));
        throw new Error(err.detail ? JSON.stringify(err.detail) : `HTTP ${resp.status}`);
      }
      const data = await resp.json();
      lastResponse = data;
      statusEl.textContent = "";
      renderResults(data);
    } catch (err) {
      statusEl.textContent = "エラー: " + err.message;
    } finally {
      btn.disabled = false;
    }
  });

  function renderResults(data) {
    $("results").style.display = "block";

    $("source-tag").textContent = sourceLabel(data.irradiance_source);

    const s = data.summary;
    $("sum-hours-without").textContent = s.sunlight_hours_without_garage.toFixed(2) + " h";
    $("sum-hours-with").textContent = s.sunlight_hours_with_garage.toFixed(2) + " h";
    $("sum-hours-lost").textContent = s.sunlight_hours_lost.toFixed(2) + " h";
    $("sum-irr-lost").textContent = s.irradiance_lost_percent.toFixed(1) + " %";
    $("sum-irr-without").textContent = Math.round(s.irradiance_without_garage_wh_m2) + " Wh/m²";
    $("sum-irr-with").textContent = Math.round(s.irradiance_with_garage_wh_m2) + " Wh/m²";

    const slider = $("time-slider");
    slider.min = 0;
    slider.max = data.timeline.length - 1;
    slider.value = Math.round(data.timeline.length / 2);
    slider.oninput = () => drawPlan(data, parseInt(slider.value, 10));
    drawPlan(data, parseInt(slider.value, 10));

    drawChart(data);
  }

  function sourceLabel(source) {
    switch (source) {
      case "open-meteo-forecast":
        return "データ元: Open-Meteo (予報)";
      case "open-meteo-archive":
        return "データ元: Open-Meteo (観測アーカイブ)";
      case "clear-sky-estimate":
        return "データ元: 簡易晴天モデル (外部APIに接続できませんでした)";
      default:
        return source || "";
    }
  }

  function drawPlan(data, idx) {
    const canvas = $("plan-canvas");
    const ctx = canvas.getContext("2d");
    const W = canvas.width, H = canvas.height;
    ctx.clearRect(0, 0, W, H);

    const margin = 40;
    const gw = data.garden_width, gd = data.garden_depth;
    const scale = Math.min((W - 2 * margin) / gw, (H - 2 * margin) / gd);
    const plotW = gw * scale, plotH = gd * scale;
    const ox = (W - plotW) / 2;
    const oy = margin;

    const toCanvas = (x, y) => [ox + x * scale, oy + y * scale];

    // Garden background
    ctx.fillStyle = "#e8f0e2";
    ctx.fillRect(ox, oy, plotW, plotH);
    ctx.strokeStyle = "#9fae92";
    ctx.strokeRect(ox, oy, plotW, plotH);

    // House wall along y=0 (top edge)
    ctx.fillStyle = "#cbd3c6";
    ctx.fillRect(ox - 6, oy - 16, plotW + 12, 16);
    ctx.fillStyle = "#5a6354";
    ctx.font = "11px sans-serif";
    ctx.fillText("家 (縁側)", ox, oy - 20);

    const step = data.timeline[idx];

    // Shadow polygon
    if (step.shadow_polygon && step.shadow_polygon.length > 0) {
      ctx.beginPath();
      step.shadow_polygon.forEach(([x, y], i) => {
        const [cx, cy] = toCanvas(x, y);
        if (i === 0) ctx.moveTo(cx, cy);
        else ctx.lineTo(cx, cy);
      });
      ctx.closePath();
      ctx.fillStyle = "rgba(30,30,30,0.35)";
      ctx.fill();
    }

    // Garage footprint
    if (data.garage_footprint && data.garage_footprint.length > 0) {
      ctx.beginPath();
      data.garage_footprint.forEach(([x, y], i) => {
        const [cx, cy] = toCanvas(x, y);
        if (i === 0) ctx.moveTo(cx, cy);
        else ctx.lineTo(cx, cy);
      });
      ctx.closePath();
      ctx.fillStyle = "#8a6d3b";
      ctx.globalAlpha = 0.85;
      ctx.fill();
      ctx.globalAlpha = 1;
      ctx.strokeStyle = "#4d3c1f";
      ctx.stroke();
    }

    // Veranda sample points
    const withG = step.sunlit_points_with_garage;
    data.veranda_sample_x.forEach((x, i) => {
      const [cx, cy] = toCanvas(x, 0);
      ctx.beginPath();
      ctx.arc(cx, cy, 5, 0, Math.PI * 2);
      ctx.fillStyle = withG[i] ? "#f4b400" : "#555";
      ctx.fill();
      ctx.strokeStyle = "#fff";
      ctx.lineWidth = 1;
      ctx.stroke();
    });

    // Sun direction indicator (top-right compass-ish arrow) if above horizon
    if (step.sun_altitude_deg > 0) {
      const cx0 = W - 55, cy0 = 55, r = 32;
      ctx.beginPath();
      ctx.arc(cx0, cy0, r, 0, Math.PI * 2);
      ctx.strokeStyle = "#bbb";
      ctx.stroke();
      const bearingRad = (step.sun_azimuth_deg * Math.PI) / 180;
      const px = cx0 + r * Math.sin(bearingRad);
      const py = cy0 - r * Math.cos(bearingRad);
      ctx.beginPath();
      ctx.moveTo(cx0, cy0);
      ctx.lineTo(px, py);
      ctx.strokeStyle = "#f4b400";
      ctx.lineWidth = 2;
      ctx.stroke();
      ctx.beginPath();
      ctx.arc(px, py, 4, 0, Math.PI * 2);
      ctx.fillStyle = "#f4b400";
      ctx.fill();
      ctx.fillStyle = "#888";
      ctx.font = "10px sans-serif";
      ctx.fillText("N", cx0 - 3, cy0 - r - 4);
    }

    $("time-label").textContent = step.time_local;
    $("sun-alt").textContent = step.sun_altitude_deg.toFixed(1);
    $("sun-az").textContent = step.sun_azimuth_deg.toFixed(1);
  }

  function drawChart(data) {
    const canvas = $("chart-canvas");
    const ctx = canvas.getContext("2d");
    const W = canvas.width, H = canvas.height;
    ctx.clearRect(0, 0, W, H);

    const margin = { left: 55, right: 15, top: 20, bottom: 40 };
    const plotW = W - margin.left - margin.right;
    const plotH = H - margin.top - margin.bottom;

    const timeline = data.timeline;
    const maxVal = Math.max(
      1,
      ...timeline.map((t) => Math.max(t.effective_irradiance_without_garage_wm2, t.effective_irradiance_with_garage_wm2))
    );

    // Axes
    ctx.strokeStyle = "#999";
    ctx.beginPath();
    ctx.moveTo(margin.left, margin.top);
    ctx.lineTo(margin.left, margin.top + plotH);
    ctx.lineTo(margin.left + plotW, margin.top + plotH);
    ctx.stroke();

    ctx.fillStyle = "#666";
    ctx.font = "10px sans-serif";
    for (let v = 0; v <= maxVal; v += Math.ceil(maxVal / 5 / 50) * 50 || 100) {
      const y = margin.top + plotH - (v / maxVal) * plotH;
      ctx.fillText(Math.round(v), 4, y + 3);
      ctx.strokeStyle = "#eee";
      ctx.beginPath();
      ctx.moveTo(margin.left, y);
      ctx.lineTo(margin.left + plotW, y);
      ctx.stroke();
    }

    const n = timeline.length;
    const xAt = (i) => margin.left + (i / (n - 1)) * plotW;

    // hour tick labels every ~2 hours
    timeline.forEach((t, i) => {
      if (t.time_local.endsWith(":00") && parseInt(t.time_local.slice(0, 2), 10) % 3 === 0) {
        const x = xAt(i);
        ctx.fillStyle = "#666";
        ctx.fillText(t.time_local, x - 12, margin.top + plotH + 14);
      }
    });

    function drawLine(key, color) {
      ctx.beginPath();
      timeline.forEach((t, i) => {
        const x = xAt(i);
        const y = margin.top + plotH - (t[key] / maxVal) * plotH;
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.strokeStyle = color;
      ctx.lineWidth = 2;
      ctx.stroke();
    }

    drawLine("effective_irradiance_without_garage_wm2", "#d97706");
    drawLine("effective_irradiance_with_garage_wm2", "#2563eb");

    ctx.fillStyle = "#333";
    ctx.font = "11px sans-serif";
    ctx.fillText("有効日射量 (W/m²)", margin.left, 14);
  }
})();
