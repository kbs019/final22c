// /static/js/chat.js
document.addEventListener("DOMContentLoaded", () => {
  console.log("[chat] 페이지 로딩 확인");

  const input   = document.getElementById("chat-input");
  const sendBtn = document.getElementById("chat-send");
  const log     = document.getElementById("chat-log");

  // Chart.js 인스턴스 (전역 DOM 변수명과 충돌 피하려고 window.* 사용 안함)
  let chartInstance = null;

  // (선택) CSRF
  const CSRF_TOKEN  = document.querySelector('meta[name="_csrf"]')?.content;
  const CSRF_HEADER = document.querySelector('meta[name="_csrf_header"]')?.content;

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
    // (레거시 호환) message/tableMd가 오면 fallback
    const message = ans?.answer ?? ans?.message ?? "(응답 없음)";
    const sql     = ans?.sql ?? null;
    const rows    = Array.isArray(ans?.rows) ? ans.rows : null;
    const tableMd = ans?.tableMd ?? null;

    const box = document.createElement("div");
    box.appendChild(bubble(message, true));

    if (sql) {
      const details = document.createElement("details");
      details.className = "mt-2";
      details.innerHTML = `<summary>실행된 SQL 보기</summary><pre>${sql}</pre>`;
      box.appendChild(details);
      const sqlBox = document.getElementById("sqlBox");
      if (sqlBox) sqlBox.textContent = sql;
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
    // payload: { labels, values, quantities, valueCol, title }
    if (!payload) {
      console.warn("[chat] chart payload 없음");
      return;
    }

    // 항상 캔버스를 확보 (없으면 생성)
    const canvas = getOrCreateChartCanvas();
    if (!canvas) {
      console.warn("[chat] canvas 생성/획득 실패");
      return;
    }
    const ctx = canvas.getContext("2d");
    if (!ctx) {
      console.warn("[chat] 2D context 생성 실패");
      return;
    }

    // 이전 차트 제거 (함수 존재 확인)
    if (chartInstance && typeof chartInstance.destroy === "function") {
      chartInstance.destroy();
    }

    const labels = payload.labels || [];
    const values = (payload.values || []).map(Number);
    const qtys   = (payload.quantities || []).map(Number);

    chartInstance = new Chart(ctx, {
      type: "bar",
      data: {
        labels,
        datasets: [{
          label: payload.valueCol || "값",
          data: values
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          title: { display: !!payload.title, text: payload.title || "" },
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: (c) => {
                const i = c.dataIndex;
                const v = values[i]?.toLocaleString("ko-KR") ?? "0";
                const q = Number.isFinite(qtys[i]) ? qtys[i].toLocaleString("ko-KR") : null;
                const base = `${v}원`;
                return q != null ? [`${base}`, `수량: ${q}개`] : base;
              }
            }
          }
        },
        scales: {
          y: {
            beginAtZero: true,
            ticks: { callback: v => v.toLocaleString("ko-KR") }
          }
        }
      }
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

      // 차트 있으면 렌더
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

  // 디버그: Chart.js 로드 확인
  console.log("[chat] Chart.js version =", window.Chart?.version);
});
