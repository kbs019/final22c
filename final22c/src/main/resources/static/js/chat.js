document.addEventListener("DOMContentLoaded", () => {
  console.log("[chat] 페이지 로딩 확인");

  const input  = document.getElementById("chat-input");
  const sendBtn = document.getElementById("chat-send");
  const log    = document.getElementById("chat-log");

  // (선택) Spring Security CSRF
  const CSRF_TOKEN  = document.querySelector('meta[name="_csrf"]')?.content;
  const CSRF_HEADER = document.querySelector('meta[name="_csrf_header"]')?.content;

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

  const showAssistantAnswer = (ans) => {
    const { message, sql, tableMd, rows } = ans || {};
    const box = document.createElement("div");

    // 1) 요약 문장
    box.appendChild(bubble(message || "(응답 없음)", true));

    // 2) (옵션) SQL 토글
    if (sql) {
      const details = document.createElement("details");
      details.className = "mt-2";
      const summary = document.createElement("summary");
      summary.textContent = "실행된 SQL 보기";
      const pre = document.createElement("pre");
      pre.textContent = sql;
      details.appendChild(summary);
      details.appendChild(pre);
      box.appendChild(details);
    }

    // 3) (옵션) 결과 표: rows 우선, 없으면 tableMd를 프리로
    if (rows && rows.length) {
      const table = renderRowsTable(rows);
      if (table) box.appendChild(table);
    } else if (tableMd) {
      const pre = document.createElement("pre");
      pre.className = "mt-2";
      pre.textContent = tableMd; // 마크다운 렌더러 없으니 원문 표기
      box.appendChild(pre);
    }

    append("assistant", box);
  };

  const send = async () => {
    const text = (input.value || "").trim();
    if (!text) return;

    // 사용자 메시지 출력
    append("user", bubble(text));
    input.value = "";
    input.focus();

    // 전송 중 비활성화
    sendBtn.disabled = true;
    sendBtn.textContent = "전송중...";

    try {
      const headers = { "Content-Type": "application/json" };
      if (CSRF_TOKEN && CSRF_HEADER) headers[CSRF_HEADER] = CSRF_TOKEN;

      // ✅ 엔드포인트를 /chat/ask 로 변경 (기존 /api/chat 아님)
      const res = await fetch("/chat/ask", {
        method: "POST",
        headers,
        body: JSON.stringify({ message: text })
      });

      if (!res.ok) {
        const t = await res.text();
        showAssistantAnswer({ message: `[오류 ${res.status}] ${t}` });
        return;
      }

      const data = await res.json();
      // data: {message, sql, tableMd, rows}
      showAssistantAnswer(data);

    } catch (e) {
      showAssistantAnswer({ message: "[네트워크 오류] " + e.message });
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
});
