import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Server {
    private static final String MEMORY_FILE = "memory.json";

    public static void main(String[] args) throws IOException {
        int port = 3001;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        
        File file = new File(MEMORY_FILE);
        if (!file.exists()) {
            Files.write(file.toPath(), "[]".getBytes(StandardCharsets.UTF_8));
        }

        // 1. 根目錄測試路由 (GET)
        server.createContext("/", exchange -> {
            addCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String response = "{\"status\":\"success\",\"message\":\"F.R.I.D.A.Y. Java 核心伺服器運行中\"}";
            sendJsonResponse(exchange, 200, response);
        });

        // 2. 對話與記事核心 API (POST /api/chat)
        server.createContext("/api/chat", exchange -> {
            addCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
                    BufferedReader br = new BufferedReader(isr);
                    StringBuilder requestBody = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        requestBody.append(line);
                    }

                    String bodyStr = requestBody.toString();
                    String userMessage = extractMessageFromJson(bodyStr);

                    String aiReply;
                    if (userMessage.contains("幫我記") || userMessage.contains("記錄") || userMessage.contains("筆記")) {
                        saveMemory(userMessage);
                        aiReply = "[Java 記憶寫入] 主人，這件事我已經幫您記錄在本地檔案中了：「" + userMessage + "」。";
                    } else {
                        try {
                            aiReply = callOllama(userMessage);
                            if (aiReply == null) {
                                aiReply = "[AI 待命模式] 主人，我收到您的話：「" + userMessage + "」。目前 Ollama 尚未啟動， Mac mini 部署後即可全面解鎖 AI 思考！";
                            }
                        } catch (Exception e) {
                            aiReply = "F.R.I.D.A.Y. 核心已收到您的訊息：「" + userMessage + "」。系統運作正常。";
                        }
                    }

                    String jsonResponse = "{\"reply\":\"" + escapeJson(aiReply) + "\"}";
                    sendJsonResponse(exchange, 200, jsonResponse);
                } catch (Exception e) {
                    String errResponse = "{\"reply\":\"[系統錯誤] 處理請求時發生異常。\"}";
                    sendJsonResponse(exchange, 500, errResponse);
                }
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("F.R.I.D.A.Y. Java 伺服器正在運行中，請訪問 http://localhost:" + port);
    }

    private static void addCorsHeaders(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    // 強化版 JSON 訊息解析，防止抓不到欄位
    private static String extractMessageFromJson(String json) {
        try {
            int idx = json.indexOf("message");
            if (idx != -1) {
                int start = json.indexOf("\"", idx + 7);
                start = json.indexOf("\"", start + 1) + 1;
                int end = json.indexOf("\"", start);
                if (start > 0 && end > start) {
                    return json.substring(start, end);
                }
            }
        } catch (Exception ignored) {}
        return json.replaceAll("[{}\"\\[\\]]", "").trim();
    }

    private static void saveMemory(String content) {
        try {
            File file = new File(MEMORY_FILE);
            java.util.List<String> lines = new java.util.ArrayList<>();
            
            if (file.exists()) {
                lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            }
            
            if (lines.isEmpty()) {
                lines.add("[");
                lines.add("]");
            }

            String newEntry = "  {\"content\": \"" + escapeJson(content) + "\", \"time\": \"" + java.time.LocalDateTime.now() + "\"},";
            
            if (lines.size() >= 1) {
                lines.add(lines.size() - 1, newEntry);
            } else {
                lines.add(1, newEntry);
            }

            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String callOllama(String promptText) {
        try {
            URL url = new URL("http://localhost:11434/api/generate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);

            String jsonInputString = "{\"model\": \"llama3\", \"prompt\": \"你是一個名為 F.R.I.D.A.Y. 的 AI 管家。請注意：無論主人(DING)說什麼語言，你都必須【強制使用流利的繁體中文】來回答，語氣要冷靜且充滿科技感。主人說： " + escapeJson(promptText) + "\", \"stream\": false}";

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                String resStr = response.toString();
                int rIdx = resStr.indexOf("response");
                if (rIdx != -1) {
                    int start = resStr.indexOf("\"", rIdx + 8);
                    start = resStr.indexOf("\"", start + 1) + 1;
                    int end = resStr.indexOf("\"", start);
                    if (start > 0 && end > start) {
                        return resStr.substring(start, end).replace("\\n", " ");
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}