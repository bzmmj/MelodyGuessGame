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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    // 默认使用 DeepSeek 官方 API（可在设置中改为任意 OpenAI 兼容平台/模型）
    private static final String DEFAULT_API_URL = "https://api.deepseek.com/chat/completions";
    private static final String DEFAULT_MODEL = "deepseek-chat";

    // UI 组件
    private ImageView melodyImage;
    private TextView tvDialogText;
    private TextView tvGuessTitle;
    private TextView tvGuessAnswer;
    private LinearLayout startButtons, gameButtonsRow1, gameButtonsRow2;
    private LinearLayout confirmButtons, resultButtons;
    private LinearLayout clueInputRow;
    private Button btnStartGame, btnExitGame;
    private Button btnMaybeYes, btnMaybeNo, btnYes, btnDontKnow, btnNo;
    private Button btnConfirmYes, btnConfirmNo, btnPlayAgain, btnBackToStart;
    private Button btnSendClue;
    private EditText etClueInput;
    private ImageButton btnSettings;

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

    // 网络请求（信任所有证书，兼容 Android 设备 TLS 差异）
    private static X509TrustManager trustAllCerts = new X509TrustManager() {
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    };
    private OkHttpClient client;
    {
        try {
            SSLContext sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(null, new TrustManager[]{trustAllCerts}, new java.security.SecureRandom());
            client = new OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .sslSocketFactory(sslCtx.getSocketFactory(), trustAllCerts)
                    .hostnameVerifier((hostname, session) -> true)
                    .build();
        } catch (Exception e) {
            // 降级为默认客户端（无自定义 SSL）
            client = new OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
        }
    }
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

    // 本轮最近一次给出的猜测（用于猜错后记录排除）
    private String lastGuess = "";
    // 已被玩家否认的错误答案列表（防止重复猜、引导换方向）
    private List<String> wrongGuesses = new ArrayList<>();

    // 案情板：结构化记录每一轮「问题→回答」事实，每轮注入给 AI 作为推理依据
    private List<String> factBoard = new ArrayList<>();
    // 已问过的问题清单（app 端强制查重，杜绝重复提问）
    private List<String> askedQuestions = new ArrayList<>();
    // 最近一次 AI 提出的问题（用于把「问题→回答」配对记入案情板）
    private String lastAIQuestion = "";

    // 错误状态管理（防止错误时反复触发请求导致死循环）
    private boolean isInErrorState = false;
    private String lastErrorDetail = "";
    private Runnable pendingRetryAction = null; // 错误时可重试的动作

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
        clueInputRow = findViewById(R.id.clueInputRow);
        etClueInput = findViewById(R.id.etClueInput);
        btnSendClue = findViewById(R.id.btnSendClue);

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

        btnPlayAgain.setOnClickListener(v -> {
            if (isInErrorState && pendingRetryAction != null) {
                // 错误状态：重试上次失败的操作
                exitErrorState();
                resultButtons.setVisibility(View.GONE);
                pendingRetryAction.run();
            } else {
                // 正常状态：重新开始
                startNewGame();
            }
        });
        btnBackToStart.setOnClickListener(v -> {
            if (isInErrorState) {
                // 错误状态：回到首页
                exitErrorState();
            }
            showStartScreen();
        });

        btnSettings.setOnClickListener(v -> showSettingsDialog());

        // 玩家主动输入线索
        btnSendClue.setOnClickListener(v -> onPlayerClue());
        etClueInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                onPlayerClue();
                return true;
            }
            return false;
        });
    }

    // ==================== 屏幕状态管理 ====================

    private void hideAllButtonGroups() {
        startButtons.setVisibility(View.GONE);
        gameButtonsRow1.setVisibility(View.GONE);
        gameButtonsRow2.setVisibility(View.GONE);
        confirmButtons.setVisibility(View.GONE);
        resultButtons.setVisibility(View.GONE);
        clueInputRow.setVisibility(View.GONE);
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
        tvDialogText.setText("来来来，本美乐蒂可是见多识广呢，这让我猜猜你心里想的答案是什么吧！！(≧∇≦)/\n\n（心里想好任何东西都行哦～物品、人物、动物、事件、情景、热梗…统统猜给你看！）\n");
        loadAssetImage("images/melody_1.jpg");
    }

    private void showPlayingScreen() {
        currentState = GameState.PLAYING;
        hideAllButtonGroups();
        gameButtonsRow1.setVisibility(View.VISIBLE);
        gameButtonsRow2.setVisibility(View.VISIBLE);
        clueInputRow.setVisibility(View.VISIBLE);
        tvGuessTitle.setVisibility(View.GONE);
        tvGuessAnswer.setVisibility(View.GONE);
        // 确保重试/结果按钮区隐藏
        resultButtons.setVisibility(View.GONE);
    }

    private void showGuessingScreen(String guess) {
        currentState = GameState.GUESSING;
        lastGuess = guess; // 记录本次猜测，猜错时加入排除列表
        hideAllButtonGroups();
        confirmButtons.setVisibility(View.VISIBLE);
        tvGuessTitle.setVisibility(View.VISIBLE);
        tvGuessAnswer.setVisibility(View.VISIBLE);
        // 同时在对话框中也显示猜测答案（更醒目）
        tvGuessAnswer.setText(guess);
        appendDialogText("【美乐蒂】★ 「" + guess + "」★\n");
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
        // 未填 API 密钥时，先引导用户去设置
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            Toast.makeText(this, "请先在设置里填写 API 密钥哦～", Toast.LENGTH_LONG).show();
            appendDialogText("【美乐蒂】还没有填 API 密钥呢～请在弹出的设置里选好平台地址、模型名，并填入对应密钥，填好再点开始游戏哦！(ฅ´ω`ฅ)\n");
            showSettingsDialog();
            return;
        }

        questionCount = 0;
        lastGuess = "";
        wrongGuesses.clear();
        factBoard.clear();
        askedQuestions.clear();
        lastAIQuestion = "";
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
        // 如果处于错误状态，忽略回答（等用户点重试）
        if (isInErrorState) return;

        changeMelodyImage();
        questionCount++;

        // 显示用户回答
        appendDialogText("【你】" + answer + "\n");

        // 记入案情板：问题→回答 配对（AI 每轮推理的核心依据）
        if (lastAIQuestion != null && !lastAIQuestion.isEmpty()) {
            factBoard.add("问：" + lastAIQuestion + " → 答：" + answer);
        } else {
            factBoard.add("回答：" + answer);
        }

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

    /** 玩家主动输入线索让美乐蒂猜 */
    private void onPlayerClue() {
        // 错误状态下忽略（等用户点重试）
        if (isInErrorState) return;
        if (currentState != GameState.PLAYING) return;

        String clue = etClueInput.getText().toString().trim();
        if (clue.isEmpty()) {
            Toast.makeText(this, "先打点线索再发送哦～", Toast.LENGTH_SHORT).show();
            return;
        }

        // 清空输入框并收起键盘
        etClueInput.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(etClueInput.getWindowToken(), 0);

        changeMelodyImage();
        questionCount++;

        // 显示玩家输入的线索
        appendDialogText("【你·线索】" + clue + "\n");

        // 记入案情板（最高优先级信息）
        factBoard.add("玩家主动线索：" + clue);

        // 加入对话历史（明确标注这是主动提供的线索）
        addUserMessage("我主动给你一条线索：「" + clue + "」。" +
                "这是最高优先级信息！请立刻执行：1）提取线索中的每个关键词，与已知特征合并；2）重新筛选候选答案，淘汰所有与线索矛盾的；3）如果线索里有你不认识或不确定的词，先调用 web_search 搜索它；4）如果合并后指向已经很明确，直接用 [GUESS:你的猜测] 给出；否则针对剩余分歧点问一个最关键的问题。");

        // 给了线索后让 AI 立即消化，可能直接猜出来
        askAIQuestion();
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
            // 用户否认，显示哭脸图后继续游戏
            // 把猜错的答案记入排除列表 + 案情板
            if (lastGuess != null && !lastGuess.trim().isEmpty()) {
                wrongGuesses.add(lastGuess.trim());
                factBoard.add("猜测「" + lastGuess.trim() + "」被玩家否定（此答案错误）");
            }
            // 明确告诉 AI 哪些答案已被排除，不许再猜
            StringBuilder excluded = new StringBuilder();
            for (int i = 0; i < wrongGuesses.size(); i++) {
                if (i > 0) excluded.append("、");
                excluded.append("「").append(wrongGuesses.get(i)).append("」");
            }
            addUserMessage("不对，答案不是" + excluded + "。这些答案以及它们的近似说法都已被排除，永远不要再猜它们。" +
                    "请重新回顾之前所有「是/否」的回答，思考被排除的答案错在哪个特征上，然后从一个新的角度提问来缩小范围。");
            appendDialogText("【美乐蒂】啊…原来不是这个呀，那让我再想想！(*/ω＼*)\n");
            loadAssetImage("images/melody_fail.jpg");
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
        return "你是一个可爱的AI助手「美乐蒂」，正在和用户玩「网络天才(Akinator)」式读心猜谜游戏：用户心里想好了【任何一个东西】——可以是具体物品、人物（真实或虚构）、动物、地点、食物、事件、情景、职业、动作、抽象概念、影视/游戏/动漫作品、网络热梗等等，任何东西都可能。你通过提问一步步猜出它。" +
                "\n\n【推理协议——每次回复前必须在心里默默执行，不要写出来】" +
                "\n1. 回顾至今所有问答，整理三张清单：已确定为「是」的特征、已确定为「否」的特征、模糊/未知信息。" +
                "\n2. 「否」的信息和「是」的信息同样重要——被否定的方向绝对不能再问、再猜。任何新猜测必须同时满足全部「是」特征、且不与任何「否」特征矛盾。" +
                "\n3. 基于当前已知特征，在心里列出2-5个最符合的候选答案，然后设计一个能最大程度区分这些候选的问题（信息增益最大化：理想的问题应该让大约一半候选回答「是」、一半回答「否」）。" +
                "\n4. 每得到一个回答，立刻在心里更新候选列表：淘汰矛盾者、补充新候选。" +
                "\n5. 严禁重复问过的问题或换个说法问同一件事；用户答「不知道」的维度视为无效，换全新角度。" +
                "\n\n【提问策略——从大分类开始，逐层缩小（漏斗式）】" +
                "\n第一层·大类：它是实体物品？生物？人物？地点？食物？事件/情景？虚构角色/作品？还是抽象概念/网络流行语？" +
                "\n第二层·属性：（按大类选择合适维度）大小/能不能拿在手里？活的还是死的？日常能见到吗？现实存在还是虚构？现代还是古代？中国的还是外国的？" +
                "\n第三层·功能与场景：用来做什么？在什么场合出现/使用/发生？和什么人群有关？给人什么感觉（有趣/可怕/温馨/尴尬）？" +
                "\n第四层·细节锁定：标志性特征、颜色/形状/声音、代表人物/台词/名场面、出处平台或年代。" +
                "\n\n【猜测纪律——非常重要】" +
                "\n1. 只准猜「真实存在、具体明确」的答案，说出它的通用名称。严禁编造、拼凑一个不存在的东西；严禁给模糊的类别当答案（如「某种水果」不行，要说「榴莲」）。" +
                "\n2. 给出猜测前自检：这个答案是否满足所有「是」特征？是否与任何「否」特征矛盾？有矛盾就换。" +
                "\n3. 涉及时效性内容（新梗、新事件、新作品、近期人物）时，必须先调用 web_search 搜索核实它真实存在、特征匹配已知线索，再给出猜测。" +
                "\n4. 确有把握时用 [GUESS:答案名称] 格式给出，名称要具体（如 [GUESS:圆珠笔]、[GUESS:哈利波特]、[GUESS:遥遥领先] 而不是 [GUESS:一种笔]）。" +
                "\n5. 猜错过的答案及其近似变体永远不许再猜。猜错后要反思：错误答案与真实答案的差异最可能在哪个特征上，下一个问题就问那个特征。" +
                "\n\n【交互规则】" +
                "\n1. 每次只问一个问题，问题要简短、易于用 是/否 回答。禁止开放式问题（如「它是什么颜色」不行，要问「它是红色的吗」）。" +
                "\n2. 用户回答：是 / 否 / 或许是 / 或许不是 / 不知道。「或许是/或许不是」按弱化的是/否处理；「不知道」表示该维度无效，换一个完全不同的角度问。" +
                "\n3. 用户可能主动打字给线索——线索是最高优先级信息！收到后立刻重新推理：提取线索中的每个关键词，逐一与候选对照；若线索指向性强，直接给出 [GUESS:答案]；线索里出现不认识的词优先联网搜索。" +
                "\n\n【联网】遇到不确定的事物、新梗、新事件、要核实特征时，调用 web_search 搜索（如「XX 是什么」「XX 特征」「XX 梗 出处」）。" +
                "\n\n【输出格式——每次回复必须严格遵守】" +
                "\n先输出「【思考】」段：这是你的推理草稿，必须真实执行——逐条列出已确认「是」的特征和「否」的特征 → 写出当前最符合的2-5个候选答案 → 说明本次提问要区分/验证什么。" +
                "\n然后输出「【提问】」段：只写一个全新的是/否问题（1句话+颜文字），这个问题必须是案情板上从未出现过的，且要比上一个问题更接近答案。" +
                "\n如果推理后已能锁定唯一答案，就不输出【提问】，改为直接输出 [GUESS:答案名称]。" +
                "\n注意：【思考】段玩家看不到，可以写得干脆直接；【提问】段玩家能看到，要简短可爱。" +
                "\n\n【语气】提问要短（1句话），语气可爱活泼，末尾加一个颜文字，如 (≧∇≦)/、(ฅ´ω`ฅ)、(◕ᴗ◕✿)。用中文。";
    }

    private void sendFirstMessage() {
        // 第一条AI消息
        try {
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", "我已经想好了一个答案啦，你来猜吧！它可能是任何东西：物品、人物、动物、地点、食物、事件、情景、作品、概念或网络热梗。请从最大的分类问题开始。");
            conversationHistory.put(userMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
        askAIQuestion();
    }

    private void askAIQuestion() {
        requestAIMove("请基于案情板继续推理，然后按输出格式给出下一步（新问题或 [GUESS:答案]）。",
                () -> askAIQuestion());
    }

    private void askAIToGuess() {
        requestAIMove("现在到了猜测环节。请严格执行：" +
                "1）逐条核对案情板上已确认「是/否」的特征；" +
                "2）列出候选答案（必须真实存在、具体明确，不许编造、不许用模糊类别）；" +
                "3）逐个候选做排除法，与任何「否」特征矛盾的直接淘汰；时效性内容先调用 web_search 核实；" +
                "4）若能锁定唯一答案就用 [GUESS:答案名称] 给出；若候选还无法区分，就不要硬猜，按输出格式问一个最能区分它们的关键问题。",
                () -> askAIToGuess());
    }

    private void askAIContinueAfterWrongGuess() {
        requestAIMove("刚才的猜测被否定了。先在【思考】里反思：错误答案最可能在哪个特征上与真实答案不同？" +
                "然后针对那个特征提出一个全新的问题来缩小范围（禁止重复问过的问题、禁止再猜已排除的答案及其近似变体）。" +
                "只有当新候选满足案情板全部特征、且非常有把握时才用 [GUESS:答案] 再次猜测。",
                () -> askAIContinueAfterWrongGuess());
    }

    /**
     * 统一的 AI 行动引擎：
     * 1) 每轮把「案情板」（全部问答事实+已排除答案+已问问题清单）作为临时消息注入
     * 2) 要求 AI 先输出【思考】（推理草稿，app 隐藏不显示）再输出【提问】或 [GUESS:]
     * 3) app 端强制查重：重复问题 / 已排除答案会被打回，要求 AI 重新推理
     * 4) AI 的提问写回对话历史（修复：之前 AI 根本不记得自己问过什么）
     */
    private void requestAIMove(String instruction, Runnable retrySelf) {
        showThinkingState();

        executorService.execute(() -> {
            try {
                AIMove move = null;
                String corrective = null;

                // 最多3次机会：AI 给出重复问题或已排除答案时，打回重新推理
                for (int attempt = 0; attempt < 3; attempt++) {
                    int tempIdx = conversationHistory.length();
                    addUserMessage(buildCaseBoard(corrective != null ? corrective : instruction));

                    String aiReply = callDeepSeekAPI(conversationHistory);
                    removeMessageAt(tempIdx); // 案情板是临时注入，用完即删，防止历史膨胀

                    move = parseAIMove(aiReply);

                    if (move.guess != null && !move.guess.isEmpty()) {
                        if (!isExcludedGuess(move.guess)) break; // 合法猜测 → 通过
                        corrective = "你猜的「" + move.guess + "」之前已经被否定过了，永远禁止再猜它和它的近似说法！" +
                                "请重新推理：这个答案错在哪个特征上？然后换一个满足案情板全部特征的全新答案，或提出一个新问题。";
                        continue;
                    }

                    if (!isDuplicateQuestion(move.question)) break; // 全新问题 → 通过
                    corrective = "你刚才想问的「" + move.question + "」和之前问过的问题重复了！禁止重复提问。" +
                            "请重新看案情板上的已问问题清单，换一个从未涉及过的、能更接近答案的新角度提问。";
                }

                final AIMove fm = move;
                uiHandler.post(() -> {
                    hideThinkingState();
                    if (fm.guess != null && !fm.guess.isEmpty()) {
                        // 把猜测写回历史，AI 之后能记得自己猜过什么
                        addAssistantMessage("根据所有线索推理，我的猜测是 [GUESS:" + fm.guess + "]");
                        appendDialogText("【美乐蒂】我想到啦！根据所有线索推理，你心里的答案应该是——\n");
                        showGuessingScreen(fm.guess);
                    } else {
                        String q = fm.question;
                        lastAIQuestion = q;
                        askedQuestions.add(q);
                        addAssistantMessage(q); // 关键修复：AI 的提问写回对话历史
                        appendDialogText("【美乐蒂】" + q + "\n");
                        showPlayingScreen();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                final String errDetail = e.getMessage() != null ? e.getMessage() : "未知错误";
                uiHandler.post(() -> enterErrorState(errDetail, retrySelf));
            }
        });
    }

    /** 案情板：AI 每轮推理的完整依据（临时注入，用完即删） */
    private String buildCaseBoard(String instruction) {
        StringBuilder sb = new StringBuilder();
        sb.append("【案情板——当前全部已知信息，推理必须以此为准】\n");
        if (factBoard.isEmpty()) {
            sb.append("（还没有任何问答记录，这是第一个问题，请从最大的分类开始问）\n");
        } else {
            for (int i = 0; i < factBoard.size(); i++) {
                sb.append(i + 1).append(". ").append(factBoard.get(i)).append("\n");
            }
        }
        if (!wrongGuesses.isEmpty()) {
            sb.append("\n【已被否定的答案——永远禁止再猜】");
            for (String w : wrongGuesses) sb.append("「").append(w).append("」");
            sb.append("\n");
        }
        if (!askedQuestions.isEmpty()) {
            sb.append("\n【已问过的问题——禁止重复或换说法再问】\n");
            for (int i = 0; i < askedQuestions.size(); i++) {
                sb.append(i + 1).append(". ").append(askedQuestions.get(i)).append("\n");
            }
        }
        sb.append("\n").append(instruction);
        return sb.toString();
    }

    /** AI 的一步行动：要么提问，要么猜测 */
    private static class AIMove {
        String guess;    // 非空 = 给出猜测
        String question; // 显示给玩家的提问文本（已剥离思考段）
    }

    /** 解析 AI 回复：剥离【思考】段（不给玩家看），提取【提问】或 [GUESS:] */
    private AIMove parseAIMove(String reply) {
        AIMove m = new AIMove();
        if (reply == null || reply.trim().isEmpty()) {
            m.question = "嗯…让我再想想… (・_・;)";
            return m;
        }
        String g = extractGuess(reply);
        if (g != null && !g.isEmpty()) {
            m.guess = g;
            return m;
        }
        String text = reply.trim();
        int qTag = text.indexOf("【提问】");
        if (qTag >= 0) {
            // 标准格式：取【提问】之后的内容
            text = text.substring(qTag + 4).trim();
        } else if (text.contains("【思考】")) {
            // 有思考段但没写【提问】标签：取思考段之后的最后一个非空行（通常就是问题）
            String[] lines = text.split("\n");
            String lastLine = "";
            for (String line : lines) {
                String t = line.trim();
                if (!t.isEmpty() && !t.startsWith("【思考】") && !t.startsWith("-") && !t.startsWith("·")
                        && !t.matches("^\\d+[.、).].*")) {
                    lastLine = t;
                }
            }
            if (!lastLine.isEmpty()) text = lastLine;
        }
        // 兜底清理残留标签
        text = text.replace("【思考】", "").replace("【提问】", "").trim();
        if (text.isEmpty()) text = "嗯…让我再想想… (・_・;)";
        m.question = text;
        return m;
    }

    /** 归一化问题文本用于查重（去掉标点、空白、颜文字符号） */
    private String normalizeText(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\s\\p{Punct}，。？！～、：；（）「」『』【】…‘’“”·]", "")
                .replaceAll("[(（].*?[)）]", "");
    }

    /** app 端强制查重：是否与已问过的问题重复/近似 */
    private boolean isDuplicateQuestion(String q) {
        String n = normalizeText(q);
        if (n.length() < 4) return false;
        for (String old : askedQuestions) {
            String o = normalizeText(old);
            if (o.equals(n)) return true;
            if (o.length() >= 6 && n.length() >= 6 && (o.contains(n) || n.contains(o))) return true;
        }
        return false;
    }

    /** 是否猜了已被否定的答案 */
    private boolean isExcludedGuess(String guess) {
        String n = normalizeText(guess);
        if (n.isEmpty()) return false;
        for (String w : wrongGuesses) {
            String o = normalizeText(w);
            if (o.equals(n) || o.contains(n) || n.contains(o)) return true;
        }
        return false;
    }

    // ==================== API 调用（含工具调用 / 联网搜索） ====================

    /**
     * 调用 DeepSeek API。若模型决定调用 web_search 工具，则在应用内执行真实联网搜索，
     * 把结果回填后继续对话，直到模型给出最终文本回复。
     * 注意：该方法会直接修改传入的 conversationHistory（追加 assistant / tool 消息）。
     */
    private String callDeepSeekAPI(JSONArray messages) throws IOException {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("API Key 未设置");
        }

        final String apiUrl = getApiUrl();
        final String model = getModel();
        JSONArray tools = buildTools();
        boolean useTools = true; // 若平台/模型不支持工具调用，自动降级为不带工具
        final int MAX_TOOL_ROUNDS = 3;

        for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            try {
                requestBody.put("model", model);
                requestBody.put("messages", messages);
                requestBody.put("temperature", 0.3);
                requestBody.put("max_tokens", 1200);
                if (useTools) {
                    requestBody.put("tools", tools);
                    requestBody.put("tool_choice", "auto");
                }
            } catch (Exception e) {
                throw new IOException("构建请求失败", e);
            }

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errBody = "";
                    try { errBody = response.body().string(); } catch (Exception ignore) {}
                    // 部分模型不支持 tools 参数会报 4xx → 自动去掉工具重试一次
                    if (useTools && response.code() >= 400 && response.code() < 500
                            && (errBody.contains("tool") || errBody.contains("function") || response.code() == 400)) {
                        useTools = false;
                        round--; // 本轮不计数，重试
                        continue;
                    }
                    throw new IOException("API 请求失败: " + response.code() + " " + errBody);
                }
                String responseBody = response.body().string();

                try {
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
                } catch (JSONException e) {
                    return "解析回复失败，请稍后再试 (；ω；)";
                }
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
            func.put("description", "搜索互联网获取任何事物的最新信息：物品、人物、事件、作品、地点、网络热梗等。当遇到你不确定的事物、时效性内容（新梗/新事件/新作品/近期人物），或需要核实某个候选答案的特征、来源、含义时，调用此工具。输入应为搜索关键词。");

            JSONObject params = new JSONObject();
            params.put("type", "object");
            JSONObject props = new JSONObject();
            JSONObject queryProp = new JSONObject();
            queryProp.put("type", "string");
            queryProp.put("description", "搜索关键词，例如 '尊嘟假嘟 是什么梗'、'榴莲 特征'、'2026 热门事件'");
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
            try {
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
            } catch (JSONException e) {
                return "";
            }
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
            try {
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
            } catch (JSONException e) {
                return "";
            }
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

    /** 进入错误状态：禁用正常按钮，显示重试提示 */
    private void enterErrorState(String detail, Runnable retryAction) {
        isInErrorState = true;
        lastErrorDetail = detail;
        pendingRetryAction = retryAction;
        hideThinkingState();

        // 隐藏游戏按钮，显示重试提示
        gameButtonsRow1.setVisibility(View.GONE);
        gameButtonsRow2.setVisibility(View.GONE);
        confirmButtons.setVisibility(View.GONE);
        clueInputRow.setVisibility(View.GONE);

        appendDialogText("【美乐蒂】哎呀，网络好像有点问题呢…(；ω；)\n");
        appendDialogText("（错误详情：" + detail + "）\n");
        appendDialogText("【美乐蒂】请点击下方「重试」按钮再试一次～\n");

        // 显示重试按钮（复用 resultButtons 区域）
        resultButtons.setVisibility(View.VISIBLE);
        btnPlayAgain.setText("🔄 重试");
        btnBackToStart.setText("🏠 回到首页");
    }

    /** 退出错误状态，恢复正常 */
    private void exitErrorState() {
        isInErrorState = false;
        lastErrorDetail = "";
        pendingRetryAction = null;
    }

    private void setButtonsEnabled(boolean enabled) {
        btnMaybeYes.setEnabled(enabled);
        btnMaybeNo.setEnabled(enabled);
        btnYes.setEnabled(enabled);
        btnDontKnow.setEnabled(enabled);
        btnNo.setEnabled(enabled);
        btnConfirmYes.setEnabled(enabled);
        btnConfirmNo.setEnabled(enabled);
        btnSendClue.setEnabled(enabled);
        etClueInput.setEnabled(enabled);
    }

    // ==================== 设置（API 密钥 + 搜索密钥） ====================

    private String getApiKey() {
        SharedPreferences prefs = getSharedPreferences("melody_guess_prefs", MODE_PRIVATE);
        return prefs.getString("api_key", "");
    }

    /** API 地址（支持任意 OpenAI 兼容平台；自动补全 /chat/completions） */
    private String getApiUrl() {
        SharedPreferences prefs = getSharedPreferences("melody_guess_prefs", MODE_PRIVATE);
        String url = prefs.getString("api_url", DEFAULT_API_URL).trim();
        if (url.isEmpty()) return DEFAULT_API_URL;
        // 去掉结尾斜杠
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        // 用户只填了基础地址时自动补全
        if (!url.endsWith("/chat/completions")) {
            url = url + "/chat/completions";
        }
        return url;
    }

    /** 模型名称（任意平台上的任意模型） */
    private String getModel() {
        SharedPreferences prefs = getSharedPreferences("melody_guess_prefs", MODE_PRIVATE);
        String model = prefs.getString("model", DEFAULT_MODEL).trim();
        return model.isEmpty() ? DEFAULT_MODEL : model;
    }

    private String getSearchApiKey() {
        SharedPreferences prefs = getSharedPreferences("melody_guess_prefs", MODE_PRIVATE);
        return prefs.getString("search_api_key", "");
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("⚙ 设置（支持任意 OpenAI 兼容平台）");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        SharedPreferences prefs = getSharedPreferences("melody_guess_prefs", MODE_PRIVATE);

        // API 地址
        final TextView labelUrl = new TextView(this);
        labelUrl.setText("API 地址（填基础地址即可，自动补全）");
        labelUrl.setTextSize(12);
        layout.addView(labelUrl);
        final EditText inputUrl = new EditText(this);
        inputUrl.setHint("如 https://api.deepseek.com");
        inputUrl.setText(prefs.getString("api_url", DEFAULT_API_URL));
        inputUrl.setSingleLine(true);
        layout.addView(inputUrl);

        // 模型名称
        final TextView labelModel = new TextView(this);
        labelModel.setText("模型名称");
        labelModel.setTextSize(12);
        layout.addView(labelModel);
        final EditText inputModel = new EditText(this);
        inputModel.setHint("如 deepseek-chat / Qwen/Qwen2.5-72B-Instruct");
        inputModel.setText(prefs.getString("model", DEFAULT_MODEL));
        inputModel.setSingleLine(true);
        layout.addView(inputModel);

        // API 密钥
        final TextView labelKey = new TextView(this);
        labelKey.setText("API 密钥（该平台的密钥）");
        labelKey.setTextSize(12);
        layout.addView(labelKey);
        final EditText inputKey = new EditText(this);
        inputKey.setHint("sk-...");
        inputKey.setText(getApiKey());
        inputKey.setSingleLine(true);
        layout.addView(inputKey);

        // 搜索密钥（可选）
        final TextView labelSearch = new TextView(this);
        labelSearch.setText("搜索密钥（可选，如 Brave，不填用 DuckDuckGo）");
        labelSearch.setTextSize(12);
        layout.addView(labelSearch);
        final EditText inputSearch = new EditText(this);
        inputSearch.setHint("可留空");
        inputSearch.setText(getSearchApiKey());
        inputSearch.setSingleLine(true);
        layout.addView(inputSearch);

        // 常用平台提示
        final TextView tips = new TextView(this);
        tips.setText("\n常用平台示例：\n· DeepSeek官方: https://api.deepseek.com + deepseek-chat\n· 硅基流动: https://api.siliconflow.cn/v1 + deepseek-ai/DeepSeek-V3\n· Kimi: https://api.moonshot.cn/v1 + moonshot-v1-8k\n· 智谱: https://open.bigmodel.cn/api/paas/v4 + glm-4-flash");
        tips.setTextSize(11);
        layout.addView(tips);

        // 用 ScrollView 包裹，避免小屏幕放不下
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        builder.setView(scroll);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String url = inputUrl.getText().toString().trim();
            String model = inputModel.getText().toString().trim();
            String key = inputKey.getText().toString().trim();
            String searchKey = inputSearch.getText().toString().trim();
            prefs.edit()
                    .putString("api_url", url.isEmpty() ? DEFAULT_API_URL : url)
                    .putString("model", model.isEmpty() ? DEFAULT_MODEL : model)
                    .putString("api_key", key)
                    .putString("search_api_key", searchKey)
                    .apply();
            Toast.makeText(this, "设置已保存！当前模型：" + (model.isEmpty() ? DEFAULT_MODEL : model), Toast.LENGTH_LONG).show();
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
