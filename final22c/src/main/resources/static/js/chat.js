// /static/js/chat.js
document.addEventListener("DOMContentLoaded", () => {
  console.log("[chat] 페이지 로딩 확인");

  const input   = document.getElementById("chat-input");
  const sendBtn = document.getElementById("chat-send");
  const log     = document.getElementById("chat-log");

  // Chart.js 인스턴스
  let chartInstance = null;

  // (선택) CSRF
  const CSRF_TOKEN  = document.querySelector('meta[name="_csrf"]')?.content;
  const CSRF_HEADER = document.querySelector('meta[name="_csrf_header"]')?.content;

  /* ---------- SQL pretty print ---------- */
  function prettySql(sql) {
    if (!sql) return "";
    let s = sql.replace(/\s+/g, " ").trim();

    // SELECT ~ FROM 사이 컬럼 줄바꿈 (JS는 (?s) 미지원 → [\s\S]*? 사용)
    s = s.replace(/SELECT\s+([\s\S]*?)\s+FROM\s/i, (_m, cols) => {
      const colsPretty = String(cols).replace(/\s*,\s*/g, ",\n  ");
      return `SELECT\n  ${colsPretty}\nFROM `;
    });

    const rules = [
      { re: /\s+FROM\s+/gi,        to: "\nFROM\n  " },
      { re: /\s+LEFT\s+JOIN\s+/gi, to: "\n  LEFT JOIN " },
      { re: /\s+RIGHT\s+JOIN\s+/gi,to: "\n  RIGHT JOIN " },
      { re: /\s+INNER\s+JOIN\s+/gi,to: "\n  INNER JOIN " },
      { re: /\s+OUTER\s+JOIN\s+/gi,to: "\n  OUTER JOIN " },
      { re: /\s+JOIN\s+/gi,        to: "\n  JOIN " },
      { re: /\s+ON\s+/gi,          to: "\n    ON " },
      { re: /\s+WHERE\s+/gi,       to: "\nWHERE\n  " },
      { re: /\s+GROUP\s+BY\s+/gi,  to: "\nGROUP BY\n  " },
      { re: /\s+HAVING\s+/gi,      to: "\nHAVING\n  " },
      { re: /\s+ORDER\s+BY\s+/gi,  to: "\nORDER BY\n  " },
      { re: /\s+UNION\s+ALL\s+/gi, to: "\nUNION ALL\n" },
      { re: /\s+UNION\s+/gi,       to: "\nUNION\n" },
      { re: /\s+FETCH\s/gi,        to: "\nFETCH " },
      { re: /\s+LIMIT\s/gi,        to: "\nLIMIT " },
      { re: /\s+OFFSET\s/gi,       to: "\nOFFSET " }
    ];
    rules.forEach(r => s = s.replace(r.re, r.to));
    return s;
  }

  /* ---------- 공통 UI ---------- */
  const append = (role, node) => {
    const wrap = document.createElement("div");
    wrap.className = "mb-2";
    const name = document.createElement("div");
    name.className = "fw-semibold small text-secondary";
    name.textContent = role === "user" ? "사용자" : "챗봇";
    wrap.appendChild(name);
    wrap.appendChild(node);
    log.appendChild(wrap);
    log.scrollTop = log.scrollHeight;
  };

  const bubble = (text, light=false) => {
    const b = document.createElement("div");
    b.className = `p-2 border rounded-3 ${light ? "bg-light" : "bg-white"}`;
    b.style.whiteSpace = "pre-wrap";
    b.textContent = text;
    return b;
  };

  const renderRowsTable = (rows) => {
    if (!rows || !rows.length) return null;
    const table = document.createElement("table");
    table.className = "table table-sm table-bordered mt-2";
    const thead = document.createElement("thead");
    const tbody = document.createElement("tbody");
    const cols = Object.keys(rows[0]);

    const trh = document.createElement("tr");
    cols.forEach(c => {
      const th = document.createElement("th");
      th.textContent = c;
      trh.appendChild(th);
    });
    thead.appendChild(trh);

    rows.forEach(r => {
      const tr = document.createElement("tr");
      cols.forEach(c => {
        const td = document.createElement("td");
        const v = r[c];
        td.textContent = (v === null || v === undefined) ? "" : String(v);
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });

    table.appendChild(thead);
    table.appendChild(tbody);
    return table;
  };

  // AiResult -> 채팅 영역 렌더
  const showAssistantAnswer = (ans) => {
    // AiResult: { answer, sql, rows, chart }
    const message = ans?.answer ?? ans?.message ?? "(응답 없음)";
    const sql     = ans?.sql ?? null;
    const rows    = Array.isArray(ans?.rows) ? ans.rows : null;
    const tableMd = ans?.tableMd ?? null;

    const box = document.createElement("div");
    box.appendChild(bubble(message, true));

    if (sql) {
      const details = document.createElement("details");
      details.className = "mt-2";

      const summary = document.createElement("summary");
      summary.textContent = "실행된 SQL 보기";

      const pre = document.createElement("pre");
      pre.textContent = prettySql(sql);       // ← 포맷팅해서 출력

      details.appendChild(summary);
      details.appendChild(pre);
      box.appendChild(details);

      const sqlBox = document.getElementById("sqlBox");
      if (sqlBox) sqlBox.textContent = prettySql(sql);
    }

    if (rows && rows.length) {
      const table = renderRowsTable(rows);
      if (table) box.appendChild(table);
    } else if (tableMd) {
      const pre = document.createElement("pre");
      pre.className = "mt-2";
      pre.textContent = tableMd;
      box.appendChild(pre);
    }

    append("assistant", box);
  };

  /* ---------- 캔버스 확보(없으면 생성) ---------- */
  function getOrCreateChartCanvas() {
    let canvas = document.getElementById("aiChart");
    if (canvas) return canvas;

    const after = document.getElementById("chat-card");
    const card = document.createElement("div");
    card.className = "card mt-3";
    card.id = "aiChartCard";
    card.innerHTML = `
      <div class="card-body" style="height:320px">
        <canvas id="aiChart" style="max-height:280px;"></canvas>
      </div>
    `;
    if (after && after.parentNode) {
      after.parentNode.insertBefore(card, after.nextSibling);
    } else {
      document.body.appendChild(card);
    }
    return card.querySelector("#aiChart");
  }

  /* ---------- 차트 렌더 ---------- */
  function drawChart(payload){
    if (!payload) return;

    const canvas = getOrCreateChartCanvas();
    const ctx = canvas.getContext("2d");
    if (chartInstance?.destroy) chartInstance.destroy();

    const type = (payload.type || 'bar').toLowerCase();
    const horizontal = !!payload.horizontal;
    const fmt = (payload.format || (payload.valueCol?.includes('원') ? 'currency' : 'count'));

    const labels = payload.labels || [];
    const values = (payload.values || []).map(Number);
    const qtys   = (payload.quantities || []).map(Number);

    // ▼ 컬러 팔레트
    const PALETTE = [
      "#4F46E5","#06B6D4","#10B981","#F59E0B","#EF4444",
      "#8B5CF6","#22C55E","#0EA5E9","#F43F5E","#A3E635",
      "#6366F1","#14B8A6","#F97316","#EC4899","#84CC16"
    ];
    const colors = values.map((_, i) => PALETTE[i % PALETTE.length]);

    // 헥사 -> rgba
    const hexToRgba = (hex, a=1) => {
      const h = hex.replace('#','');
      const bigint = parseInt(h, 16);
      const r = (bigint >> 16) & 255, g = (bigint >> 8) & 255, b = bigint & 255;
      return `rgba(${r}, ${g}, ${b}, ${a})`;
    };

    const isPie = (type === 'pie' || type === 'doughnut');

    const dataset = {
      label: payload.valueCol || '값',
      data: values,
      backgroundColor: isPie
        ? colors.map(c => hexToRgba(c, 0.9))
        : (type === 'bar'
            ? colors.map(c => hexToRgba(c, 0.35))
            : hexToRgba(colors[0], 0.25)),
      borderColor: isPie
        ? colors
        : (type === 'bar' ? colors : colors[0]),
      borderWidth: (type === 'line') ? 2 : 1,
      tension: (type === 'line') ? 0.35 : 0,
      fill: (type === 'line')
    };

    const options = {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: horizontal ? 'y' : 'x',
      plugins: {
        title: { display: !!payload.title, text: payload.title || '' },
        legend: { display: isPie }, // 선/막대는 범례 숨김
        tooltip: {
          callbacks: {
            label: (c) => {
              const i = c.dataIndex;
              const num = values[i] ?? 0;
              const q   = qtys[i];
              let v = Number(num).toLocaleString('ko-KR');
              if (fmt === 'currency') v += '원';
              if (fmt === 'percent')  v += '%';
              return (q != null && isFinite(q))
                ? [`${v}`, `수량: ${Number(q).toLocaleString('ko-KR')}개`]
                : v;
            }
          }
        }
      },
      scales: isPie ? {} : {
        y: {
          beginAtZero: true,
          ticks: {
            callback: (v) => {
              let s = Number(v).toLocaleString('ko-KR');
              if (fmt === 'currency') s += '원';
              if (fmt === 'percent')  s += '%';
              return s;
            }
          }
        }
      }
    };

    chartInstance = new Chart(ctx, {
      type,
      data: { labels, datasets: [ dataset ] },
      options
    });
  }

  /* ---------- 전송 ---------- */
  const send = async () => {
    const text = (input.value || "").trim();
    if (!text) return;

    append("user", bubble(text));
    input.value = "";
    input.focus();

    sendBtn.disabled = true;
    sendBtn.textContent = "전송중...";

    try {
      const headers = { "Content-Type": "application/json", "Accept": "application/json" };
      if (CSRF_TOKEN && CSRF_HEADER) headers[CSRF_HEADER] = CSRF_TOKEN;

      const res = await fetch("/chat/ask", {
        method: "POST",
        headers,
        body: JSON.stringify({ message: text })
      });

      if (!res.ok) {
        const t = await res.text();
        showAssistantAnswer({ answer: `[오류 ${res.status}] ${t}` });
        return;
      }

      const data = await res.json(); // AiResult: { answer, sql, rows, chart }
      console.log("[chat] AiResult:", data);
      showAssistantAnswer(data);

      if (data.chart) {
        console.log("[chat] chart payload 수신:", data.chart);
        drawChart(data.chart);
      } else {
        console.log("[chat] chart 없음");
      }

    } catch (e) {
      showAssistantAnswer({ answer: "[네트워크 오류] " + e.message });
    } finally {
      sendBtn.disabled = false;
      sendBtn.textContent = "전송";
    }
  };

  document.getElementById("chat-send")?.addEventListener("click", send);
  document.getElementById("chat-input")?.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  });

  console.log("[chat] Chart.js version =", window.Chart?.version);
});
