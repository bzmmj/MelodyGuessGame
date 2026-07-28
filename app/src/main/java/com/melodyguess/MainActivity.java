package com.melodyguess;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    // 使用的模型（硅基流动，支持 Function Calling / 工具调用）
    private static final String MODEL = "Qwen/Qwen2.5-72B-Instruct";

    // UI 组件
    private ImageView melodyImage;
    private TextView tvDialogText;
    private TextView tvGuessTitle;
    private TextView tvGuessAnswer;
    private LinearLayout startButtons, gameButtonsRow1, gameButtonsRow2;
    private LinearLayout confirmButtons, resultButtons;
    private Button btnStartGame, btnExitGame;
    private Button btnMaybeYes, btnMaybeNo, btnYes, btnDontKnow, btnNo;
    private Button btnConfirmYes, btnConfirmNo, btnPlayAgain, btnBackToStart, btnSettings;

    // 游戏状态
    private enum GameState {
        START, PLAYING, GUESSING, RESULT
    }
    private GameState currentState = GameState.START;

    // 图片资源列表（游戏中随机轮换）
    private static final String[] GAME_IMAGES = {
        "images/melody_1.jpg",
        "images/melody_2.jpg",
        "images/melody_3.jpg",
        "images/melody_4.jpg",
        "images/melody_5.jpg",
        "images/melody_6.jpg",
        "images/melody_7.jpg"
    };
    private List<String> shuffledImages = new ArrayList<>();
    private int currentImageIndex = 0;

    // AI 对话历史
    private JSONArray conversationHistory = new JSONArray();

    // 网络请求
    private OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Handler uiHandler = new Handler(Looper.getMainLooper());

    // 颜文字列表
    private static final String[] KAOMOJI = {
        "(*/ω\\*)", "(≧∇≦)/", "(´▽`ʃƪ)", "(｡♥‿♥｡)",
        "(✿◠‿◠)", "(´• ω •`)", "(⁎⚈᷀᷁▴⚈᷀᷁⁎)",
        "(๑•̀ㅂ•́)و✧", "(ฅ´ω`ฅ)", "(◕ᴗ◕✿)",
        "( *´艸｀)", "(o゜▽゜)o☆", "(ﾉ>ω<)ﾉ"
    };
    private Random random = new Random();

    // 问题计数器 - 用于决定何时给出猜测答案
    private int questionCount = 0;
    private static final int QUESTIONS_BEFORE_GUESS = 8; // 问8题后开始猜测

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupClickListeners();
        showStartScreen();
    }

    private void initViews() {
        melodyImage = findViewById(R.id.melodyImage);
        tvDialogText = findViewById(R.id.tvDialogText);
        tvGuessTitle = findViewById(R.id.tvGuessTitle);
        tvGuessAnswer = findViewById(R.id.tvGuessAnswer);

        startButtons = findViewById(R.id.startButtons);
        gameButtonsRow1 = findViewById(R.id.gameButtonsRow1);
        gameButtonsRow2 = findViewById(R.id.gameButtonsRow2);
        confirmButtons = findViewById(R.id.confirmButtons);
        resultButtons = findViewById(R.id.resultButtons);

        btnStartGame = findViewById(R.id.btnStartGame);
        btnExitGame = findViewById(R.id.btnExitGame);
        btnMaybeYes = findViewById(R.id.btnMaybeYes);
        btnMaybeNo = findViewById(R.id.btnMaybeNo);
        btnYes = findViewById(R.id.btnYes);
        btnDontKnow = findViewById(R.id.btnDontKnow);
        btnNo = findViewById(R.id.btnNo);
        btnConfirmYes = findViewById(R.id.btnConfirmYes);
        btnConfirmNo = findViewById(R.id.btnConfirmNo);
        btnPlayAgain = findViewById(R.id.btnPlayAgain);
        btnBackToStart = findViewById(R.id.btnBackToStart);
        btnSettings = findViewById(R.id.btnSettings);

        tvDialogText.setMovementMethod(new ScrollingMovementMethod());
    }

    private void setupClickListeners() {
        btnStartGame.setOnClickListener(v -> startNewGame());
        btnExitGame.setOnClickListener(v -> finish());

        btnMaybeYes.setOnClickListener(v -> onAnswer("或许是"));
        btnMaybeNo.setOnClickListener(v -> onAnswer("或许不是"));
        btnYes.setOnClickListener(v -> onAnswer("是"));
        btnDontKnow.setOnClickListener(v -> onAnswer("不知道"));
        btnNo.setOnClickListener(v -> onAnswer("否"));

        btnConfirmYes.setOnClickListener(v -> onConfirmAnswer(true));
        btnConfirmNo.setOnClickListener(v -> onConfirmAnswer(false));

        btnPlayAgain.setOnClickListener(v -> startNewGame());
        btnBackToStart.setOnClickListener(v -> showStartScreen());

        btnSettings.setOnClickListener(v -> showSettingsDialog());
    }

    // ==================== 屏幕状态管理 ====================

    private void hideAllButtonGroups() {
        startButtons.setVisibility(View.GONE);
        gameButtonsRow1.setVisibility(View.GONE);
        gameButtonsRow2.setVisibility(View.GONE);
        confirmButtons.setVisibility(View.GONE);
        resultButtons.setVisibility(View.GONE);
        tvGuessTitle.setVisibility(View.GONE);
        tvGuessAnswer.setVisibility(View.GONE);
    }

    private void showStartScreen() {
        currentState = GameState.START;
        hideAllButtonGroups();
        startButtons.setVisibility(View.VISIBLE);
        tvGuessTitle.setVisibility(View.GONE);
        tvGuessAnswer.setVisibility(View.GONE);

        // 设置欢迎文本
        tvDialogText.setText("来来来，本美乐蒂可是见多识广呢，这让我猜猜你心里想的答案是什么吧！！(≧∇≦)/\n\n（提示：你心里想的，大概率是最近超火的网络热梗 / 流行语哦～）\n");
        loadAssetImage("images/melody_1.jpg");
    }

    private void showPlayingScreen() {
        currentState = GameState.PLAYING;
        hideAllButtonGroups();
        gameButtonsRow1.setVisibility(View.VISIBLE);
        gameButtonsRow2.setVisibility(View.VISIBLE);
        tvGuessTitle.setVisibility(View.GONE);
        tvGuessAnswer.setVisibility(View.GONE);
    }

    private void showGuessingScreen(String guess) {
        currentState = GameState.GUESSING;
        hideAllButtonGroups();
        confirmButtons.setVisibility(View.VISIBLE);
        tvGuessTitle.setVisibility(View.VISIBLE);
        tvGuessAnswer.setVisibility(View.VISIBLE);
        tvGuessAnswer.setText(guess);
    }

    private void showResultScreen(boolean won) {
        currentState = GameState.RESULT;
        hideAllButtonGroups();
        resultButtons.setVisibility(View.VISIBLE);
        tvGuessTitle.setVisibility(View.GONE);
        tvGuessAnswer.setVisibility(View.GONE);

        if (won) {
            loadAssetImage("images/melody_success.jpg");
            tvDialogText.setText("哇哈哈！本美乐蒂果然是最聪明的！猜对啦！！你心里的答案就是刚刚说的那个对不对~ (๑•̀ㅂ•́)و✧");
        } else {
            loadAssetImage("images/melody_fail.jpg");
            tvDialogText.setText("呜呜呜…居然猜错了…不过没关系啦！我们再玩一次好不好~ (；ω；)");
        }
    }

    // ==================== 游戏流程 ====================

    private void startNewGame() {
        questionCount = 0;
        conversationHistory = new JSONArray();
        shuffleImages();
        currentImageIndex = 0;
        changeMelodyImage();
        showPlayingScreen();

        // 添加系统提示词
        addSystemMessage();

        // 发送第一条消息
        sendFirstMessage();
    }

    private void shuffleImages() {
        shuffledImages = new ArrayList<>();
        for (String img : GAME_IMAGES) {
            shuffledImages.add(img);
        }
        Collections.shuffle(shuffledImages);
    }

    private void changeMelodyImage() {
        if (!shuffledImages.isEmpty()) {
            String imagePath = shuffledImages.get(currentImageIndex % shuffledImages.size());
            loadAssetImage(imagePath);
            currentImageIndex++;
        }
    }

    private void loadAssetImage(String path) {
        try {
            java.io.InputStream is = getAssets().open(path);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            is.close();
            melodyImage.setImageBitmap(bitmap);
        } catch (IOException e) {
            melodyImage.setImageResource(R.drawable.melody_default);
        }
    }

    private void onAnswer(String answer) {
        changeMelodyImage();
        questionCount++;

        // 显示用户回答
        appendDialogText("【你】" + answer + "\n");

        // 添加到对话历史
        addUserMessage(answer);

        // 判断是否该猜测了
        if (questionCount >= QUESTIONS_BEFORE_GUESS && shouldMakeGuess()) {
            askAIToGuess();
        } else {
            // 继续提问
            askAIQuestion();
        }
    }

    private boolean shouldMakeGuess() {
        // 每8题后有一次猜测机会，之后每5题一次
        return questionCount == QUESTIONS_BEFORE_GUESS ||
               (questionCount > QUESTIONS_BEFORE_GUESS && (questionCount - QUESTIONS_BEFORE_GUESS) % 5 == 0);
    }

    private void onConfirmAnswer(boolean confirmed) {
        if (confirmed) {
            // 用户确认猜对了
            addUserMessage("是的！你猜对了！");
            showResultScreen(true);
        } else {
            // 用户否认，继续游戏
            addUserMessage("不对哦，不是这个答案。");
            appendDialogText("【美乐蒂】啊…原来不是这个呀，那让我再想想！(*/ω＼*)\n");
            changeMelodyImage();
            showPlayingScreen();
            askAIContinueAfterWrongGuess();
        }
    }

    // ==================== AI 交互 ====================

    private void addSystemMessage() {
        try {
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", buildSystemPrompt());
            conversationHistory.put(systemMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String buildSystemPrompt() {
        return "你是一个可爱的AI助手「美乐蒂」，正在和用户玩一个「海龟汤」式的猜谜游戏，而且你猜的东西大概率是「网络热梗 / 流行语 / 表情包 / 互联网迷因（meme）」。" +
                "\n\n你的目标：通过不断提问，猜出用户心里想的是哪一个网络梗。" +
                "\n\n规则：" +
                "\n1. 你每次只问一个问题，以海龟汤（情境猜谜）的方式逐步缩小范围。" +
                "\n2. 用户会回答：是 / 否 / 或许是 / 或许不是 / 不知道。" +
                "\n3. 优先从以下维度切入：\n" +
                "   - 这个梗属于哪一类？（文字梗 / 表情包 / 视频梗 / 角色梗 / 句式梗 / 事件梗 / 二次元梗）\n" +
                "   - 它最早出圈的平台？（抖音 / 微博 / B站 / 小红书 / 贴吧 / 快手 / 微信）\n" +
                "   - 它的情绪/用途？（搞笑 / 吐槽 / 阴阳怪气 / 撒娇 / 玩梗自嘲 / 夸人）\n" +
                "   - 它是人物、动物、动作、短语，还是某个具体画面 / 名场面？" +
                "\n4. 当你觉得有足够信息时，输出 [GUESS:你的猜测] 来给出你的最佳猜测（要具体到梗的名字或准确含义）。" +
                "\n5. 如果用户说你猜错了，结合已有信息继续提问或重新猜测。" +
                "\n\n联网能力：遇到你不确定的、近期才火起来的新梗，或者需要核实某个梗的来源、含义、出处时，请调用 web_search 工具去搜索最新信息，再继续判断。" +
                "\n\n重要：你的每一句话末尾都要加一个可爱的颜文字，比如 (≧∇≦)/、(´▽`ʃƪ)、(｡♥‿♥｡)、(✿◠‿◠)、(ฅ´ω`ฅ)、(◕ᴗ◕✿) 等。" +
                "\n语气要可爱、活泼、像一只好奇心爆棚的小兔子在猜梗！用中文回复。";
    }

    private void sendFirstMessage() {
        // 第一条AI消息
        try {
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", "我已经想好了一个答案啦，你来猜吧！(提示：我想的东西大概率是最近的网络热梗或者流行语哦)");
            conversationHistory.put(userMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
        askAIQuestion();
    }

    private void askAIQuestion() {
        showThinkingState();

        executorService.execute(() -> {
            try {
                // 直接把当前对话历史发给模型，模型会基于上下文提出下一个问题（必要时联网搜索）
                String aiReply = callSiliconFlowAPI(conversationHistory);
                String guess = extractGuess(aiReply);

                uiHandler.post(() -> {
                    hideThinkingState();
                    if (guess != null && !guess.isEmpty()) {
                        // 模型在普通提问轮也给出了猜测，引导到确认界面
                        appendDialogText("【美乐蒂】咦，我突然有灵感了！我觉得你心里的答案应该是——\n");
                        showGuessingScreen(guess);
                    } else {
                        appendDialogText("【美乐蒂】" + aiReply + "\n");
                        showPlayingScreen();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                uiHandler.post(() -> {
                    hideThinkingState();
                    appendDialogText("【美乐蒂】哎呀，网络好像有点问题呢…再试一次好不好？(；ω；)\n");
                    Toast.makeText(MainActivity.this, "网络连接失败，请检查API密钥或网络", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void askAIToGuess() {
        showThinkingState();

        executorService.execute(() -> {
            try {
                int tempIdx = conversationHistory.length();
                addUserMessage("现在请根据前面所有信息，给出你目前认为最可能是的答案。如果你已经有比较明确的判断，请用 [GUESS:你的猜测] 的格式直接给出你的猜测；如果还完全不确定，就再问一个最关键的问题（必要时可联网搜索确认）。");

                String aiReply = callSiliconFlowAPI(conversationHistory);
                String guess = extractGuess(aiReply);

                // 移除本轮临时引导语（保留 assistant 与 tool 消息）
                removeMessageAt(tempIdx);

                uiHandler.post(() -> {
                    hideThinkingState();
                    if (guess != null && !guess.isEmpty()) {
                        appendDialogText("【美乐蒂】嗯嗯…我想到啦！我想你心里的答案应该是——\n");
                        showGuessingScreen(guess);
                    } else {
                        appendDialogText("【美乐蒂】" + aiReply + "\n");
                        showPlayingScreen();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                uiHandler.post(() -> {
                    hideThinkingState();
                    appendDialogText("【美乐蒂】哎呀，网络好像有点问题呢…(；ω；)\n");
                });
            }
        });
    }

    private void askAIContinueAfterWrongGuess() {
        showThinkingState();

        executorService.execute(() -> {
            try {
                int tempIdx = conversationHistory.length();
                addUserMessage("你上次猜错了，请根据之前所有信息重新思考，继续提问来缩小范围，或者如果有了新的判断也可以用 [GUESS:答案] 给出猜测（必要时可联网搜索确认）。");

                String aiReply = callSiliconFlowAPI(conversationHistory);
                String guess = extractGuess(aiReply);

                removeMessageAt(tempIdx);

                uiHandler.post(() -> {
                    hideThinkingState();
                    if (guess != null && !guess.isEmpty()) {
                        appendDialogText("【美乐蒂】嗯…那我再猜一次！我觉得应该是——\n");
                        showGuessingScreen(guess);
                    } else {
                        appendDialogText("【美乐蒂】" + aiReply + "\n");
                        showPlayingScreen();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                uiHandler.post(() -> {
                    hideThinkingState();
                    appendDialogText("【美乐蒂】哎呀，网络有点问题呢…(；ω；)\n");
                });
            }
        });
    }

    // ==================== API 调用（含工具调用 / 联网搜索） ====================

    /**
     * 调用硅基流动 API。若模型决定调用 web_search 工具，则在应用内执行真实联网搜索，
     * 把结果回填后继续对话，直到模型给出最终文本回复。
     * 注意：该方法会直接修改传入的 conversationHistory（追加 assistant / tool 消息）。
     */
    private String callSiliconFlowAPI(JSONArray messages) throws IOException {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("API Key 未设置");
        }

        JSONArray tools = buildTools();
        final int MAX_TOOL_ROUNDS = 3;

        for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            try {
                requestBody.put("model", MODEL);
                requestBody.put("messages", messages);
                requestBody.put("temperature", 0.8);
                requestBody.put("max_tokens", 1000);
                requestBody.put("tools", tools);
                requestBody.put("tool_choice", "auto");
            } catch (Exception e) {
                throw new IOException("构建请求失败", e);
            }

            Request request = new Request.Builder()
                    .url("https://api.siliconflow.cn/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("API 请求失败: " + response.code() + " " + response.body().string());
                }
                String responseBody = response.body().string();

                JSONObject json = new JSONObject(responseBody);
                JSONObject choice = json.getJSONArray("choices").getJSONObject(0);
                JSONObject message = choice.getJSONObject("message");

                // 判断是否要求调用工具
                if (message.has("tool_calls") && !message.isNull("tool_calls")) {
                    JSONArray toolCalls = message.getJSONArray("tool_calls");
                    if (toolCalls.length() > 0) {
                        // 1) 把带 tool_calls 的 assistant 消息写回历史
                        appendAssistantWithToolCalls(message, toolCalls);

                        // 2) 逐个执行工具调用
                        for (int i = 0; i < toolCalls.length(); i++) {
                            JSONObject tc = toolCalls.getJSONObject(i);
                            String toolName = tc.getJSONObject("function").getString("name");
                            String argsStr = tc.getJSONObject("function").optString("arguments", "{}");
                            String toolCallId = tc.getString("id");

                            String result;
                            if ("web_search".equals(toolName)) {
                                String query = "";
                                try {
                                    JSONObject args = new JSONObject(argsStr);
                                    query = args.optString("query", "");
                                } catch (Exception ignore) { /* 忽略解析错误 */ }
                                result = executeWebSearch(query);
                            } else {
                                result = "未知工具：" + toolName;
                            }
                            appendToolMessage(toolCallId, result);
                        }
                        // 3) 继续循环，让模型基于搜索结果给出最终回复
                        continue;
                    }
                }

                // 没有工具调用 → 返回最终文本
                return message.optString("content", "").trim();
            }
        }
        return "嗯…让我再想想… (・_・;)";
    }

    /** 联网搜索工具定义 */
    private JSONArray buildTools() {
        try {
            JSONArray tools = new JSONArray();
            JSONObject tool = new JSONObject();
            tool.put("type", "function");

            JSONObject func = new JSONObject();
            func.put("name", "web_search");
            func.put("description", "搜索互联网上最新的网络热梗、流行语、表情包、梗的来源与含义。当遇到你不确定、近期才火起来的新梗，或需要核实某个梗的出处/含义时，调用此工具获取最新信息。输入应为搜索关键词。");

            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            JSONObject queryProp = new JSONObject();
            queryProp.put("type", "string");
            queryProp.put("description", "搜索关键词，例如 '2026 最火网络热梗'、'尊嘟假嘟 是什么梗'");
            props.put("query", queryProp);
            params.put("properties", props);
            JSONArray required = new JSONArray();
            required.put("query");
            params.put("required", required);

            func.put("parameters", params);
            tool.put("function", func);
            tools.put(tool);
            return tools;
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** 在应用内执行真实联网搜索：有 Brave 密钥走 Brave，否则走免密钥的 DuckDuckGo */
    private String executeWebSearch(String query) {
        if (query == null || query.trim().isEmpty()) return "搜索词为空";
        try {
            String braveKey = getSearchApiKey();
            String result;
            if (braveKey != null && !braveKey.trim().isEmpty()) {
                result = searchBrave(query, braveKey);
            } else {
                result = searchDuckDuckGo(query);
            }
            if (result == null || result.trim().isEmpty()) {
                result = "未找到相关搜索结果";
            }
            if (result.length() > 3000) result = result.substring(0, 3000);
            return result;
        } catch (Exception e) {
            return "搜索失败：" + e.getMessage();
        }
    }

    private String searchDuckDuckGo(String query) throws IOException {
        String url = "https://api.duckduckgo.com/?q=" + URLEncoder.encode(query, "UTF-8") + "&format=json&no_html=1&skip_disambig=1";
        Request request = new Request.Builder().url(url).get().build();
        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) return "";
            String body = resp.body().string();
            JSONObject json = new JSONObject(body);
            StringBuilder sb = new StringBuilder();
            String abs = json.optString("AbstractText", "");
            if (!abs.isEmpty()) sb.append(abs).append("\n");
            JSONArray related = json.optJSONArray("RelatedTopics");
            if (related != null) {
                int count = 0;
                for (int i = 0; i < related.length() && count < 5; i++) {
                    JSONObject t = related.getJSONObject(i);
                    String text = t.optString("Text", "");
                    if (!text.isEmpty()) { sb.append("- ").append(text).append("\n"); count++; }
                }
            }
            return sb.toString();
        }
    }

    private String searchBrave(String query, String key) throws IOException {
        String url = "https://api.search.brave.com/res/v1/web/search?q=" + URLEncoder.encode(query, "UTF-8") + "&count=5";
        Request request = new Request.Builder().url(url)
                .addHeader("Accept", "application/json")
                .addHeader("X-Subscription-Token", key)
                .get().build();
        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) return "";
            String body = resp.body().string();
            JSONObject json = new JSONObject(body);
            JSONObject web = json.optJSONObject("web");
            if (web == null) return "";
            JSONArray results = web.optJSONArray("results");
            if (results == null) return "";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < results.length() && i < 5; i++) {
                JSONObject r = results.getJSONObject(i);
                String title = r.optString("title", "");
                String desc = r.optString("description", "");
                sb.append("- ").append(title).append(": ").append(desc).append("\n");
            }
            return sb.toString();
        }
    }

    private String parseAIResponse(String apiResponse) {
        try {
            JSONObject json = new JSONObject(apiResponse);
            JSONArray choices = json.getJSONArray("choices");
            JSONObject firstChoice = choices.getJSONObject(0);
            JSONObject message = firstChoice.getJSONObject("message");
            String content = message.getString("content").trim();
            return content;
        } catch (Exception e) {
            e.printStackTrace();
            return "嗯…让我想想… (・_・;)";
        }
    }

    private String extractGuess(String aiReply) {
        // 提取 [GUESS:xxx] 格式的猜测
        if (aiReply != null && aiReply.contains("[GUESS:")) {
            int start = aiReply.indexOf("[GUESS:") + 7;
            int end = aiReply.indexOf("]", start);
            if (end > start) {
                return aiReply.substring(start, end).trim();
            }
        }
        return null;
    }

    // ==================== 对话历史管理 ====================

    private void addUserMessage(String content) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            msg.put("content", content);
            conversationHistory.put(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addAssistantMessage(String content) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("role", "assistant");
            msg.put("content", content);
            conversationHistory.put(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 把模型返回的、带 tool_calls 的 assistant 消息写回历史（工具调用必需的格式） */
    private void appendAssistantWithToolCalls(JSONObject apiMessage, JSONArray toolCalls) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("role", "assistant");
            msg.put("content", apiMessage.optString("content", ""));
            msg.put("tool_calls", toolCalls);
            conversationHistory.put(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 把工具执行结果写回历史 */
    private void appendToolMessage(String toolCallId, String result) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("role", "tool");
            msg.put("content", result);
            msg.put("tool_call_id", toolCallId);
            conversationHistory.put(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 安全移除指定下标的消息（避免依赖 JSONArray.remove 的版本差异） */
    private void removeMessageAt(int index) {
        try {
            if (index < 0 || index >= conversationHistory.length()) return;
            JSONArray newArr = new JSONArray();
            for (int i = 0; i < conversationHistory.length(); i++) {
                if (i != index) newArr.put(conversationHistory.get(i));
            }
            conversationHistory = newArr;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== UI 辅助方法 ====================

    private void appendDialogText(String text) {
        String current = tvDialogText.getText().toString();
        tvDialogText.setText(current + text);

        // 自动滚动到底端
        final ScrollView scrollView = (ScrollView) tvDialogText.getParent();
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void showThinkingState() {
        String current = tvDialogText.getText().toString();
        tvDialogText.setText(current + "【美乐蒂】让我想想… (..•˘_˘•..)\n");

        // 禁用按钮
        setButtonsEnabled(false);
    }

    private void hideThinkingState() {
        setButtonsEnabled(true);
    }

    private void setButtonsEnabled(boolean enabled) {
        btnMaybeYes.setEnabled(enabled);
        btnMaybeNo.setEnabled(enabled);
        btnYes.setEnabled(enabled);
        btnDontKnow.setEnabled(enabled);
        btnNo.setEnabled(enabled);
        btnConfirmYes.setEnabled(enabled);
        btnConfirmNo.setEnabled(enabled);
    }

    // ==================== 设置（API 密钥 + 搜索密钥） ====================

    private String getApiKey() {
        SharedPreferences prefs = getSharedPreferences("melody_guess_prefs", MODE_PRIVATE);
        return prefs.getString("api_key", "sk-iemjmltmxezejynyogpcjmseanimumppteigrijcbdbqwlda");
    }

    private String getSearchApiKey() {
        SharedPreferences prefs = getSharedPreferences("melody_guess_prefs", MODE_PRIVATE);
        return prefs.getString("search_api_key", "");
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚙ 设置");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        final EditText inputKey = new EditText(this);
        inputKey.setHint("硅基流动 API 密钥");
        inputKey.setText(getApiKey());
        inputKey.setSingleLine(true);
        layout.addView(inputKey);

        final EditText inputSearch = new EditText(this);
        inputSearch.setHint("搜索API密钥(可选，填了联网更准，如 Brave)");
        inputSearch.setText(getSearchApiKey());
        inputSearch.setSingleLine(true);
        layout.addView(inputSearch);

        builder.setView(layout);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String key = inputKey.getText().toString().trim();
            String searchKey = inputSearch.getText().toString().trim();
            saveApiKey(key);
            saveSearchApiKey(searchKey);
            String msg = "设置已保存！";
            if (searchKey.isEmpty()) msg += "(未填搜索密钥，默认用 DuckDuckGo 联网)";
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        });
        builder.setNegativeButton("取消", null);
        builder.show();

        inputKey.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    private void saveApiKey(String key) {
        SharedPreferences prefs = getSharedPreferences("melody_guess_prefs", MODE_PRIVATE);
        prefs.edit().putString("api_key", key).apply();
    }

    private void saveSearchApiKey(String key) {
        SharedPreferences prefs = getSharedPreferences("melody_guess_prefs", MODE_PRIVATE);
        prefs.edit().putString("search_api_key", key).apply();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
