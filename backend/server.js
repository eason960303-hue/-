const express = require("express");
const cors = require("cors");
const fs = require("fs");
const path = require("path");
const axios = require("axios"); // 用來跟本地 AI 模型通訊

const app = express();
const PORT = process.env.PORT || 3001;

app.use(cors());
app.use(express.json());

// 確保記憶檔案存在
const memoryFile = path.join(__dirname, "memory.json");
if (!fs.existsSync(memoryFile)) {
  fs.writeFileSync(memoryFile, JSON.stringify([]));
}

app.get("/", (req, res) => {
  res.json({
    status: "success",
    message: "F.R.I.D.A.Y. 核心系統運作中（Port 3001）",
  });
});

// 核心對話與 AI 獨立思考 API
app.post("/api/chat", async (req, res) => {
  const { message } = req.body;
  if (!message) {
    return res.status(400).json({ error: "訊息不能為空" });
  }

  let aiReply = "";
  let isMemorySaved = false;

  // 1. 如果主人要求記事，直接寫入記憶檔
  if (
    message.includes("幫我記") ||
    message.includes("記錄") ||
    message.includes("筆記")
  ) {
    const rawData = fs.readFileSync(memoryFile);
    const memories = JSON.parse(rawData);

    memories.push({ content: message, time: new Date().toISOString() });
    fs.writeFileSync(memoryFile, JSON.stringify(memories, null, 2));

    aiReply = `[記憶已寫入] 主人，這件事我已經幫您記錄下來了：「${message}」。`;
    isMemorySaved = true;
  } else {
    // 2. 讓 AI 獨立思考：嘗試串接本地 Ollama AI 模型 (例如 Llama 3)
    try {
      const ollamaResponse = await axios.post(
        "http://localhost:11434/api/generate",
        {
          model: "llama3",
          prompt: `你是一個名為 F.R.I.D.A.Y. 的鋼鐵人專屬 AI 管家。你的個性冷靜、聰明、忠誠，說話帶有科技感。請針對以下主人的話進行獨立思考並回答：${message}`,
          stream: false,
        },
        { timeout: 8000 },
      );

      aiReply = ollamaResponse.data.response.trim();
    } catch (error) {
      // 如果本地 Ollama 尚未啟動，提供引導性回覆
      aiReply = `[AI 核心待命] 主人，我接收到您的訊息：「${message}」。目前本地 AI 引擎（Ollama）尚未啟動，請確保 Ollama 正在運行，或等待 9 月底 Mac mini 到貨部署！`;
    }
  }

  res.json({
    reply: aiReply,
    saved: isMemorySaved,
  });
});

app.listen(PORT, () => {
  console.log(`F.R.I.D.A.Y. 伺服器正在運行於 http://localhost:${PORT}`);
});
