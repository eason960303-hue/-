const express = require("express");
const cors = require("cors");
const fs = require("fs"); // 用來讀寫檔案的內建模組
const path = require("path");

const app = express();
const PORT = process.env.PORT || 3001;

app.use(cors());
app.use(express.json());

// 記憶檔案的路徑（會在 backend 資料夾下自動建立 memory.json 來幫你記事情）
const memoryFile = path.join(__dirname, "memory.json");

// 初始化記憶檔
if (!fs.existsSync(memoryFile)) {
  fs.writeFileSync(memoryFile, JSON.stringify([]));
}

// 測試路由
app.get("/", (req, res) => {
  res.json({ status: "success", message: "Friday 後端核心與記憶模組運行中！" });
});

// 接收前端訊息與記事的核心 API
app.post("/api/chat", (req, res) => {
  const { message } = req.body;
  if (!message) {
    return res.status(400).json({ error: "訊息不能為空" });
  }

  let reply = "";
  let isMemorySaved = false;

  // 簡單的學習與記事邏輯（隨著你跟它互動，它就幫你記下來）
  if (
    message.includes("幫我記") ||
    message.includes("記錄") ||
    message.includes("筆記")
  ) {
    // 讀取現有記憶
    const rawData = fs.readFileSync(memoryFile);
    const memories = JSON.parse(rawData);

    // 新增一筆記錄
    const newMemory = {
      content: message,
      time: new Date().toISOString(),
    };
    memories.push(newMemory);

    // 寫回檔案
    fs.writeFileSync(memoryFile, JSON.stringify(memories, null, 2));

    reply = `好的主人，這件事我已經幫您記下來了：「${message}」。我們一起繼續累積學習進度！`;
    isMemorySaved = true;
  } else if (message.includes("學習") || message.includes("進度")) {
    reply =
      "主人，我們目前正在建立你的專屬學習管家系統。只要你有新的想法，隨時叫我幫你記下來！";
  } else {
    // 一般對話回應（未來這裡可以無縫接軌 Ollama 本地 AI 模型）
    reply = `收到你的想法：「${message}」。這對我們的學習很有幫助，我會陪你一起把它完成。`;
  }

  res.json({
    reply: reply,
    saved: isMemorySaved,
  });
});

app.listen(PORT, () => {
  console.log(`Friday 伺服器正在運行於 http://localhost:${PORT}`);
});
