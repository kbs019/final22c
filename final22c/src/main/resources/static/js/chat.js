document.addEventListener("DOMContentLoaded", () => {
  console.log("[chat] 페이지 로딩 확인");

  const input = document.getElementById("chat-input");
  const sendBtn = document.getElementById("chat-send");
  const log = document.getElementById("chat-log");

  const append = (role, text) => {
    const wrap = document.createElement("div");
    wrap.className = "mb-2";
    const name = document.createElement("div");
    name.className = "fw-semibold small text-secondary";
    name.textContent = role === "user" ? "사용자" : "챗봇";
    const bubble = document.createElement("div");
    bubble.className = role === "user" ? "p-2 border rounded-3 bg-white" : "p-2 border rounded-3 bg-light";
    bubble.style.whiteSpace = "pre-wrap";
    bubble.textContent = text;
    wrap.appendChild(name);
    wrap.appendChild(bubble);
    log.appendChild(wrap);
    log.scrollTop = log.scrollHeight;
  };

  const send = async () => {
    const text = (input.value || "").trim();
    if (!text) return;
    append("user", text);
    input.value = "";
    try {
      const res = await fetch("/api/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ message: text })
      });
      const data = await res.json();
      append("assistant", data.answer ?? "[빈 응답]");
    } catch (e) {
      append("assistant", "[네트워크 오류] " + e.message);
    }
  };

  sendBtn.addEventListener("click", send);
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  });
});
