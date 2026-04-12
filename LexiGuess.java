
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.Timer;

public class LexiGuess extends JFrame {

    // ── Word Entry (loaded from JSON) ─────────────────────────────────────────
    static class WordEntry {
        String word;
        String clue;
        String difficulty;
        WordEntry(String word, String clue, String difficulty) {
            this.word       = word.toUpperCase().trim();
            this.clue       = clue;
            this.difficulty = difficulty.toUpperCase().trim();
        }
    }

    // ── JSON Loader ───────────────────────────────────────────────────────────
    private static List<WordEntry> loadJson(String filePath) {
        List<WordEntry> entries = new ArrayList<>();
        try {
            String raw = new String(Files.readAllBytes(Paths.get(filePath)));
            String[] objects = raw.split("\\{");
            for (String obj : objects) {
                obj = obj.trim();
                if (obj.isEmpty()) continue;
                obj = obj.replaceAll("[}\\]]+\\s*$", "").trim();

                String word       = extractJsonValue(obj, "words");
                if (word == null || word.isEmpty())
                    word          = extractJsonValue(obj, "word");
                String clue       = extractJsonValue(obj, "clue");
                String difficulty = extractJsonValue(obj, "difficulty");

                if (word != null && !word.isEmpty()
                        && clue != null && difficulty != null) {
                    entries.add(new WordEntry(word, clue, difficulty));
                }
            }
        } catch (IOException e) {
            System.err.println("Could not load " + filePath + ": " + e.getMessage());
        }
        return entries;
    }

    private static String extractJsonValue(String obj, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile(pattern).matcher(obj);
        return m.find() ? m.group(1).trim() : null;
    }

    // ── Word Banks ────────────────────────────────────────────────────────────
    private List<WordEntry> easyWords   = new ArrayList<>();
    private List<WordEntry> mediumWords = new ArrayList<>();
    private List<WordEntry> hardWords   = new ArrayList<>();

    private void loadAllWords() {
        easyWords   = loadJson("easy.json");
        mediumWords = loadJson("medium.json");
        hardWords   = loadJson("hard.json");

        if (easyWords.isEmpty()) {
            for (String[] e : new String[][]{
                {"CODE",    "Instructions written for a computer to execute."},
                {"LOOP",    "A structure that repeats a block of code."},
                {"BYTE",    "A unit of data equal to 8 bits."},
                {"TYPE",    "A classification specifying the kind of value a variable holds."},
                {"VOID",    "A return type indicating a function returns nothing."}
            }) easyWords.add(new WordEntry(e[0], e[1], "EASY"));
        }
        if (mediumWords.isEmpty()) {
            for (String[] e : new String[][]{
                {"ARRAY",   "A collection of elements stored at contiguous memory locations."},
                {"STACK",   "A linear data structure following Last-In-First-Out order."},
                {"QUEUE",   "A linear data structure following First-In-First-Out order."},
                {"PARSE",   "To analyse a string of symbols according to formal grammar rules."},
                {"CACHE",   "Temporary storage that speeds up future data requests."}
            }) mediumWords.add(new WordEntry(e[0], e[1], "MEDIUM"));
        }
        if (hardWords.isEmpty()) {
            for (String[] e : new String[][]{
                {"MUTEX",   "A synchronisation primitive that prevents simultaneous resource access."},
                {"PRAGMA",  "A compiler directive that provides additional information to the compiler."},
                {"LAMBDA",  "An anonymous function defined without a name."},
                {"DAEMON",  "A background process that runs without direct user interaction."},
                {"ENDIAN",  "Describes the byte order used to represent multi-byte data."}
            }) hardWords.add(new WordEntry(e[0], e[1], "HARD"));
        }
    }

    // ── Colours ───────────────────────────────────────────────────────────────
    private static final Color BG_DARK        = new Color(8, 10, 18);
    private static final Color BG_PANEL       = new Color(14, 17, 28);
    private static final Color GOLD           = new Color(212, 175, 95);
    private static final Color GOLD_DIM       = new Color(140, 110, 50);
    private static final Color SILVER         = new Color(180, 185, 200);
    private static final Color TEXT_WHITE     = new Color(235, 232, 225);
    private static final Color TEXT_MUTED     = new Color(120, 118, 130);
    private static final Color TILE_EMPTY_BG  = new Color(22, 26, 42);
    private static final Color TILE_EMPTY_BD  = new Color(55, 55, 75);
    private static final Color GREEN_CORRECT  = new Color(60, 140, 90);
    private static final Color YELLOW_PRESENT = new Color(190, 155, 40);
    private static final Color GRAY_ABSENT    = new Color(45, 45, 58);
    private static final Color RED_DANGER     = new Color(180, 60, 60);
    private static final Color EASY_CLR       = new Color(70, 170, 100);
    private static final Color MEDIUM_CLR     = new Color(190, 155, 40);
    private static final Color HARD_CLR       = new Color(180, 70, 70);
    private static final Color CLUE_CLR       = new Color(140, 190, 255);

    // ── Sound ─────────────────────────────────────────────────────────────────
    private static final String SOUND_CORRECT  = "chrisiex1-correct-156911 (1).wav";
    private static final String SOUND_LOSE     = "freesound_community-080047_lose_funny_re.wav";
    private static final String SOUND_KEYCLICK = "creatorshome-keyboard-click-327728.wav";
    private static final String SOUND_START    = "creatorshome-keyboard-click-327728.wav";

    // ── Game State ────────────────────────────────────────────────────────────
    private WordEntry  currentEntry;
    private String     targetWord     = "";
    private String     currentClue    = "";
    private String     difficulty     = "EASY";
    private int        maxAttempts    = 6;
    private int        currentAttempt = 0;
    private int        sessionScore   = 0;
    private int        currentStage   = 1;
    private boolean    gameOver       = false;
    private boolean    gameWon        = false;
    private List<String>   guesses      = new ArrayList<>();
    private List<int[]>    feedbackList = new ArrayList<>();
    private Set<Character> correctLetters = new HashSet<>();
    private Set<Character> presentLetters = new HashSet<>();
    private Set<Character> absentLetters  = new HashSet<>();
    private Set<String>    usedWords      = new HashSet<>();
    private int            currentLevel   = 1;
    private int            totalLevels    = 0;
    private int            wordLength;

    // ── Per-difficulty level progress (tracks highest unlocked level per diff) ─
    private Map<String, Integer> difficultyProgress = new HashMap<>();

    private List<LeaderboardEntry> leaderboard       = new ArrayList<>();
    private String                 currentPlayerName = "Player";
    private final DatabaseManager db = new DatabaseManager();

    // ── UI ────────────────────────────────────────────────────────────────────
    private CardLayout   cardLayout;
    private JPanel       cardPanel;
    private JLabel[][]   tiles;
    private JPanel       gridPanel;
    private HangmanPanel hangmanPanel;
    private JTextField   inputField;
    private JButton      submitBtn;
    private JLabel       messageLabel;
    private JLabel       clueLabel;
    private JLabel       difficultyLabel;
    private JLabel       inGameScoreLabel;
    private JLabel       sessionScoreLabel;
    private JLabel       stageLabel;
    private Map<Character, JLabel> keyLabels = new HashMap<>();
    private JPanel       keyboardPanel;
    private JPanel       leaderboardListPanel;
    private JPanel       levelSelectPanel;

    // ── Sound ─────────────────────────────────────────────────────────────────
    private void playSound(String filePath) {
        new Thread(() -> {
            try {
                File f = new File(filePath);
                if (!f.exists()) return;
                AudioInputStream ai = AudioSystem.getAudioInputStream(f);
                Clip clip = AudioSystem.getClip();
                clip.open(ai);
                clip.start();
                clip.addLineListener(ev -> { if (ev.getType() == LineEvent.Type.STOP) clip.close(); });
            } catch (Exception ignored) {}
        }).start();
    }

    // ── Constructor ───────────────────────────────────────────────────────────
    public LexiGuess() {
        loadAllWords();
        db.connect();

        Runtime.getRuntime().addShutdownHook(new Thread(db::disconnect));

        // Initialise progress: level 1 unlocked for each difficulty
        difficultyProgress.put("EASY",   1);
        difficultyProgress.put("MEDIUM", 1);
        difficultyProgress.put("HARD",   1);

        setTitle("LexiGuess - Programming Enhancement Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        getContentPane().setBackground(BG_DARK);

        cardLayout = new CardLayout();
        cardPanel  = new JPanel(cardLayout);
        cardPanel.setBackground(BG_DARK);
        cardPanel.add(buildStartScreen(),        "START");
        cardPanel.add(buildMenuScreen(),         "MENU");
        cardPanel.add(buildLevelSelectScreen(),  "LEVELSELECT");
        cardPanel.add(buildGameScreen(),         "GAME");
        cardPanel.add(buildInstructionsScreen(), "INSTRUCTIONS");
        cardPanel.add(buildLeaderboardScreen(),  "LEADERBOARD");

        add(cardPanel);
        setSize(960, 820);
        setLocationRelativeTo(null);
        setVisible(true);
        cardLayout.show(cardPanel, "START");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    static class GradientPanel extends JPanel {
        private Color top, bottom;
        GradientPanel(Color top, Color bottom) { this.top = top; this.bottom = bottom; setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
            g2.fillRect(0, 0, getWidth(), getHeight());
            super.paintComponent(g);
        }
    }

    private JLabel makeOrnamentLabel(String text) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setFont(new Font("Serif", Font.PLAIN, 14));
        lbl.setForeground(GOLD_DIM);
        lbl.setAlignmentX(CENTER_ALIGNMENT);
        return lbl;
    }

    private JButton makeFancyButton(String text, Color bgColor, Color borderColor) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Serif", Font.BOLD, 13));
        btn.setForeground(TEXT_WHITE);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(borderColor, 1),
            BorderFactory.createEmptyBorder(6, 16, 6, 16)));
        return btn;
    }

    private JButton makeLinkButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Serif", Font.PLAIN, 13));
        btn.setForeground(GOLD_DIM);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setForeground(GOLD); }
            public void mouseExited(MouseEvent e)  { btn.setForeground(GOLD_DIM); }
        });
        return btn;
    }

    // ── End Game Dialog ───────────────────────────────────────────────────────
    private void showEndGameDialog(String title, String message, String word,
                                    String subMessage, Color accentColor, String retryLabel) {
        JDialog dialog = new JDialog(this, title, true);
        dialog.setUndecorated(true);
        dialog.setSize(450, 380);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_PANEL);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(accentColor);
                g2.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, 24, 24);
            }
        };
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(28, 40, 26, 40));
        panel.setOpaque(false);

        JLabel ornament = new JLabel("--- \u2726 ---", SwingConstants.CENTER);
        ornament.setFont(new Font("Serif", Font.PLAIN, 14));
        ornament.setForeground(GOLD_DIM);
        ornament.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(ornament);
        panel.add(Box.createVerticalStrut(10));

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 28));
        titleLabel.setForeground(accentColor.equals(RED_DANGER) ? RED_DANGER : GOLD);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(10));

        JLabel msgLabel = new JLabel(message, SwingConstants.CENTER);
        msgLabel.setFont(new Font("Serif", Font.PLAIN, 14));
        msgLabel.setForeground(TEXT_MUTED);
        msgLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(msgLabel);
        panel.add(Box.createVerticalStrut(5));

        JLabel wordLabel = new JLabel(word, SwingConstants.CENTER);
        wordLabel.setFont(new Font("Monospaced", Font.BOLD, 32));
        wordLabel.setForeground(TEXT_WHITE);
        wordLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(wordLabel);
        panel.add(Box.createVerticalStrut(8));

        JLabel clueDialogLabel = new JLabel(
            "<html><div style='text-align:center;width:320px'>" + currentClue + "</div></html>",
            SwingConstants.CENTER);
        clueDialogLabel.setFont(new Font("Serif", Font.ITALIC, 12));
        clueDialogLabel.setForeground(CLUE_CLR);
        clueDialogLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(clueDialogLabel);
        panel.add(Box.createVerticalStrut(8));

        JLabel subLabel = new JLabel(subMessage, SwingConstants.CENTER);
        subLabel.setFont(new Font("Serif", Font.ITALIC, 13));
        subLabel.setForeground(GOLD_DIM);
        subLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(subLabel);
        panel.add(Box.createVerticalStrut(22));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnPanel.setOpaque(false);

        Color retryBg = accentColor.equals(RED_DANGER) ? new Color(50,20,20) : new Color(20,40,20);
        JButton retryBtn = makeFancyButton(retryLabel, retryBg, accentColor);
        retryBtn.setFont(new Font("Serif", Font.BOLD, 14));
        retryBtn.addActionListener(e -> { dialog.dispose(); startGame(); });

        // "Level Select" button so player can pick a different level after winning/losing
        JButton lvlBtn = makeFancyButton("Level Select", new Color(20,25,45), GOLD_DIM);
        lvlBtn.setFont(new Font("Serif", Font.BOLD, 14));
        lvlBtn.addActionListener(e -> {
            dialog.dispose();
            updateLevelSelectScreen();
            cardLayout.show(cardPanel, "LEVELSELECT");
        });

        JButton exitBtn = makeFancyButton("Exit", new Color(30,28,35), GOLD_DIM);
        exitBtn.setFont(new Font("Serif", Font.BOLD, 14));
        exitBtn.addActionListener(e -> System.exit(0));

        btnPanel.add(retryBtn);
        btnPanel.add(lvlBtn);
        btnPanel.add(exitBtn);
        btnPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(btnPanel);

        dialog.setContentPane(panel);
        dialog.getContentPane().setBackground(BG_DARK);
        dialog.setVisible(true);
    }

    // ── Start Screen ──────────────────────────────────────────────────────────
    private JPanel buildStartScreen() {
        JPanel root = new GradientPanel(BG_DARK, new Color(12, 15, 28));
        root.setLayout(new BorderLayout());
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(100, 80, 100, 80));

        center.add(makeOrnamentLabel("--- * ---"));
        center.add(Box.createVerticalStrut(20));
        JLabel title = new JLabel("LexiGuess", SwingConstants.CENTER);
        title.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 72));
        title.setForeground(GOLD);
        title.setAlignmentX(CENTER_ALIGNMENT);
        center.add(title);
        JLabel sub = new JLabel("--- Programming Enhancement Game ---", SwingConstants.CENTER);
        sub.setFont(new Font("Serif", Font.ITALIC, 18));
        sub.setForeground(GOLD_DIM);
        sub.setAlignmentX(CENTER_ALIGNMENT);
        center.add(sub);
        center.add(Box.createVerticalStrut(40));
        center.add(makeOrnamentLabel("*  o  *"));
        center.add(Box.createVerticalStrut(50));

        JLabel nameLbl = new JLabel("Enter Your Name:", SwingConstants.CENTER);
        nameLbl.setFont(new Font("Serif", Font.BOLD, 18));
        nameLbl.setForeground(TEXT_WHITE);
        nameLbl.setAlignmentX(CENTER_ALIGNMENT);
        center.add(nameLbl);
        center.add(Box.createVerticalStrut(10));

        JTextField nameField = new JTextField("Player", 15);
        nameField.setFont(new Font("Serif", Font.PLAIN, 18));
        nameField.setForeground(TEXT_WHITE);
        nameField.setBackground(new Color(22, 26, 42));
        nameField.setCaretColor(GOLD);
        nameField.setHorizontalAlignment(SwingConstants.CENTER);
        nameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(GOLD_DIM, 1),
            BorderFactory.createEmptyBorder(8, 15, 8, 15)));
        nameField.setMaximumSize(new Dimension(250, 45));
        nameField.setAlignmentX(CENTER_ALIGNMENT);
        center.add(nameField);
        center.add(Box.createVerticalStrut(40));

        JButton startBtn = new JButton("START GAME") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, GOLD, 0, getHeight(), GOLD_DIM));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                super.paintComponent(g);
            }
        };
        startBtn.setFont(new Font("Serif", Font.BOLD, 24));
        startBtn.setForeground(BG_DARK);
        startBtn.setContentAreaFilled(false);
        startBtn.setBorderPainted(false);
        startBtn.setFocusPainted(false);
        startBtn.setOpaque(false);
        startBtn.setPreferredSize(new Dimension(250, 60));
        startBtn.setMaximumSize(new Dimension(250, 60));
        startBtn.setAlignmentX(CENTER_ALIGNMENT);
        startBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        startBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (!name.isEmpty()) currentPlayerName = name;
            System.out.println("[TEST] Player name is: " + currentPlayerName);
            System.out.println("[TEST] DB connected: " + db.isConnected());
            // Restore saved progress for this player from the database
            if (db.isConnected()) {
                java.util.Map<String, Integer> saved = db.loadAllProgress(currentPlayerName);
                difficultyProgress.put("EASY",   saved.get("EASY"));
                difficultyProgress.put("MEDIUM", saved.get("MEDIUM"));
                difficultyProgress.put("HARD",   saved.get("HARD"));
            }
            playSound(SOUND_START);
            cardLayout.show(cardPanel, "MENU");
        });
        startBtn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { startBtn.setForeground(TEXT_WHITE); }
            public void mouseExited(MouseEvent e)  { startBtn.setForeground(BG_DARK); }
        });
        center.add(startBtn);
        center.add(Box.createVerticalStrut(30));

        JButton leaderBtn = makeFancyButton("View Leaderboard", new Color(25,22,12), GOLD_DIM);
        leaderBtn.setAlignmentX(CENTER_ALIGNMENT);
        leaderBtn.addActionListener(e -> { updateLeaderboardDisplay(); cardLayout.show(cardPanel, "LEADERBOARD"); });
        center.add(leaderBtn);
        center.add(Box.createVerticalStrut(15));

        JButton exitBtn = makeFancyButton("Exit Game", new Color(50,20,20), RED_DANGER);
        exitBtn.setAlignmentX(CENTER_ALIGNMENT);
        exitBtn.addActionListener(e -> System.exit(0));
        center.add(exitBtn);

        root.add(center, BorderLayout.CENTER);
        JLabel bot = new JLabel("*  o  *", SwingConstants.CENTER);
        bot.setFont(new Font("Serif", Font.PLAIN, 14));
        bot.setForeground(GOLD_DIM);
        bot.setBorder(BorderFactory.createEmptyBorder(0, 0, 30, 0));
        root.add(bot, BorderLayout.SOUTH);
        return root;
    }

    // ── Menu Screen ───────────────────────────────────────────────────────────
    private JPanel buildMenuScreen() {
        JPanel root = new GradientPanel(BG_DARK, new Color(12, 15, 28));
        root.setLayout(new BorderLayout());

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 12));
        topBar.setOpaque(false);
        JButton instrBtn  = makeLinkButton("How to Play");
        instrBtn.addActionListener(e -> cardLayout.show(cardPanel, "INSTRUCTIONS"));
        JButton leaderBtn = makeLinkButton("Leaderboard");
        leaderBtn.addActionListener(e -> { updateLeaderboardDisplay(); cardLayout.show(cardPanel, "LEADERBOARD"); });
        JButton exitBtn   = makeLinkButton("Exit");
        exitBtn.addActionListener(e -> System.exit(0));
        topBar.add(instrBtn); topBar.add(leaderBtn); topBar.add(exitBtn);
        root.add(topBar, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(10, 80, 20, 80));

        center.add(makeOrnamentLabel("--- * ---"));
        center.add(Box.createVerticalStrut(10));
        JLabel title = new JLabel("LexiGuess", SwingConstants.CENTER);
        title.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 58));
        title.setForeground(GOLD); title.setAlignmentX(CENTER_ALIGNMENT);
        center.add(title);
        JLabel sub = new JLabel("--- Programming Enhancement Game ---", SwingConstants.CENTER);
        sub.setFont(new Font("Serif", Font.ITALIC, 15));
        sub.setForeground(GOLD_DIM); sub.setAlignmentX(CENTER_ALIGNMENT);
        center.add(sub);
        center.add(Box.createVerticalStrut(8));
        center.add(makeOrnamentLabel("*  o  *"));
        center.add(Box.createVerticalStrut(28));

        JLabel chooseLbl = new JLabel("Choose Your Challenge", SwingConstants.CENTER);
        chooseLbl.setFont(new Font("Serif", Font.BOLD, 22));
        chooseLbl.setForeground(TEXT_WHITE); chooseLbl.setAlignmentX(CENTER_ALIGNMENT);
        center.add(chooseLbl);
        center.add(Box.createVerticalStrut(20));

        JPanel diffRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 22, 0));
        diffRow.setOpaque(false);
        diffRow.add(makeDiffCard("EASY",   "Novice",     "Common concepts",    EASY_CLR,   "*",   easyWords.size()));
        diffRow.add(makeDiffCard("MEDIUM", "Scholar",    "Intermediate terms", MEDIUM_CLR, "**",  mediumWords.size()));
        diffRow.add(makeDiffCard("HARD",   "Mastermind", "Advanced jargon",    HARD_CLR,   "***", hardWords.size()));
        center.add(diffRow);
        center.add(Box.createVerticalStrut(30));

        sessionScoreLabel = new JLabel("Session Score: 0  |  Stage: 1", SwingConstants.CENTER);
        sessionScoreLabel.setFont(new Font("Serif", Font.BOLD, 16));
        sessionScoreLabel.setForeground(GOLD); sessionScoreLabel.setAlignmentX(CENTER_ALIGNMENT);
        center.add(sessionScoreLabel);

        root.add(center, BorderLayout.CENTER);
        JLabel bot = new JLabel("*  o  *", SwingConstants.CENTER);
        bot.setFont(new Font("Serif", Font.PLAIN, 14));
        bot.setForeground(GOLD_DIM);
        bot.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        root.add(bot, BorderLayout.SOUTH);
        return root;
    }

    // ── Difficulty Card ───────────────────────────────────────────────────────
    private JPanel makeDiffCard(String diff, String rankTitle, String descriptor,
                                 Color accent, String stars, int wordCount) {
        JPanel card = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0,
                    new Color(accent.getRed()/7, accent.getGreen()/7, accent.getBlue()/7),
                    0, getHeight(),
                    new Color(accent.getRed()/11, accent.getGreen()/11, accent.getBlue()/11)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, 15, 15);
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(208, 230));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(18, 16, 18, 16));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel starsLbl = new JLabel(stars, SwingConstants.CENTER);
        starsLbl.setFont(new Font("Serif", Font.PLAIN, 13));
        starsLbl.setForeground(accent); starsLbl.setAlignmentX(CENTER_ALIGNMENT);

        JLabel titleLbl = new JLabel(diff, SwingConstants.CENTER);
        titleLbl.setFont(new Font("Serif", Font.BOLD, 24));
        titleLbl.setForeground(accent); titleLbl.setAlignmentX(CENTER_ALIGNMENT);

        JLabel rankLbl = new JLabel(rankTitle, SwingConstants.CENTER);
        rankLbl.setFont(new Font("Serif", Font.ITALIC, 13));
        rankLbl.setForeground(SILVER); rankLbl.setAlignmentX(CENTER_ALIGNMENT);

        JPanel sep = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 80));
                g.drawLine(10, 0, getWidth()-10, 0);
            }
        };
        sep.setOpaque(false); sep.setPreferredSize(new Dimension(160, 4));
        sep.setMaximumSize(new Dimension(160, 4)); sep.setAlignmentX(CENTER_ALIGNMENT);

        JLabel descLbl = new JLabel(descriptor, SwingConstants.CENTER);
        descLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        descLbl.setForeground(TEXT_MUTED); descLbl.setAlignmentX(CENTER_ALIGNMENT);

        JLabel countLbl = new JLabel("6 attempts  |  " + wordCount + " levels", SwingConstants.CENTER);
        countLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        countLbl.setForeground(TEXT_MUTED); countLbl.setAlignmentX(CENTER_ALIGNMENT);

        JLabel clueLbl = new JLabel("Clue included \u2139", SwingConstants.CENTER);
        clueLbl.setFont(new Font("SansSerif", Font.ITALIC, 11));
        clueLbl.setForeground(CLUE_CLR); clueLbl.setAlignmentX(CENTER_ALIGNMENT);

        JButton playBtn = new JButton("Select Level  ->") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(accent);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g);
            }
        };
        playBtn.setFont(new Font("Serif", Font.BOLD, 12));
        playBtn.setForeground(BG_DARK);
        playBtn.setContentAreaFilled(false); playBtn.setBorderPainted(false);
        playBtn.setFocusPainted(false); playBtn.setOpaque(false);
        playBtn.setMaximumSize(new Dimension(160, 32)); playBtn.setAlignmentX(CENTER_ALIGNMENT);
        playBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // ── Goes to Level Select, not directly to game ──
        playBtn.addActionListener(e -> {
            difficulty = diff;
            // Restore saved progress for this difficulty
            currentLevel = difficultyProgress.getOrDefault(diff, 1);
            updateLevelSelectScreen();
            cardLayout.show(cardPanel, "LEVELSELECT");
        });

        card.add(starsLbl);
        card.add(Box.createVerticalStrut(4));
        card.add(titleLbl);
        card.add(rankLbl);
        card.add(Box.createVerticalStrut(8));
        card.add(sep);
        card.add(Box.createVerticalStrut(8));
        card.add(descLbl);
        card.add(countLbl);
        card.add(Box.createVerticalStrut(4));
        card.add(clueLbl);
        card.add(Box.createVerticalGlue());
        card.add(playBtn);

        card.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(accent, 2),
                    BorderFactory.createEmptyBorder(17,15,17,15)));
                card.repaint();
            }
            public void mouseExited(MouseEvent e) {
                card.setBorder(BorderFactory.createEmptyBorder(18,16,18,16));
                card.repaint();
            }
        });
        return card;
    }

    // ── Level Select Screen ───────────────────────────────────────────────────
    private JPanel buildLevelSelectScreen() {
        JPanel root = new GradientPanel(BG_DARK, new Color(12, 15, 28));
        root.setLayout(new BorderLayout());

        // Top bar
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, GOLD_DIM),
            BorderFactory.createEmptyBorder(12, 18, 12, 18)));

        JButton backBtn = makeFancyButton("<- Back", new Color(25,22,12), GOLD_DIM);
        backBtn.addActionListener(e -> cardLayout.show(cardPanel, "MENU"));
        topBar.add(backBtn, BorderLayout.WEST);

        JLabel titleLbl = new JLabel("Select Level", SwingConstants.CENTER);
        titleLbl.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 28));
        titleLbl.setForeground(GOLD);
        topBar.add(titleLbl, BorderLayout.CENTER);
        root.add(topBar, BorderLayout.NORTH);

        // Scrollable content area — populated by updateLevelSelectScreen()
        levelSelectPanel = new JPanel();
        levelSelectPanel.setOpaque(false);
        levelSelectPanel.setBorder(BorderFactory.createEmptyBorder(28, 50, 28, 50));

        JScrollPane scroll = new JScrollPane(levelSelectPanel);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scroll, BorderLayout.CENTER);

        // Bottom ornament
        JLabel bot = new JLabel("*  o  *", SwingConstants.CENTER);
        bot.setFont(new Font("Serif", Font.PLAIN, 14));
        bot.setForeground(GOLD_DIM);
        bot.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        root.add(bot, BorderLayout.SOUTH);

        return root;
    }

    // ── Populate / refresh Level Select ───────────────────────────────────────
    private void updateLevelSelectScreen() {
        levelSelectPanel.removeAll();
        levelSelectPanel.setLayout(new BoxLayout(levelSelectPanel, BoxLayout.Y_AXIS));

        List<WordEntry> bank = difficulty.equals("EASY")   ? easyWords
                             : difficulty.equals("MEDIUM") ? mediumWords : hardWords;
        totalLevels = bank.size();

        Color accent = difficulty.equals("EASY") ? EASY_CLR
                     : difficulty.equals("MEDIUM") ? MEDIUM_CLR : HARD_CLR;

        // How far the player has progressed in this difficulty
        int highestUnlocked = difficultyProgress.getOrDefault(difficulty, 1);

        // ── Difficulty title ──────────────────────────────────────────────
        JLabel diffLbl = new JLabel(difficulty + "  —  " + totalLevels + " Levels",
                                    SwingConstants.CENTER);
        diffLbl.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 24));
        diffLbl.setForeground(accent);
        diffLbl.setAlignmentX(CENTER_ALIGNMENT);
        levelSelectPanel.add(diffLbl);

        // Progress text
        int cleared = highestUnlocked - 1;
        JLabel progressLbl = new JLabel(
            cleared + " of " + totalLevels + " levels cleared",
            SwingConstants.CENTER);
        progressLbl.setFont(new Font("SansSerif", Font.PLAIN, 13));
        progressLbl.setForeground(TEXT_MUTED);
        progressLbl.setAlignmentX(CENTER_ALIGNMENT);
        levelSelectPanel.add(Box.createVerticalStrut(6));
        levelSelectPanel.add(progressLbl);
        levelSelectPanel.add(Box.createVerticalStrut(24));

        // ── Level grid: 5 columns ─────────────────────────────────────────
        int cols = 5;
        int rows = (int) Math.ceil((double) totalLevels / cols);
        JPanel grid = new JPanel(new GridLayout(rows, cols, 16, 16));
        grid.setOpaque(false);
        grid.setMaximumSize(new Dimension(660, rows * 110));
        grid.setAlignmentX(CENTER_ALIGNMENT);

        for (int i = 1; i <= totalLevels; i++) {
            final int levelNum = i;
            boolean cleared2  = (i < highestUnlocked);
            boolean unlocked  = (i <= highestUnlocked);

            // ── Cell panel ────────────────────────────────────────────────
            JPanel cell = new JPanel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                        RenderingHints.VALUE_ANTIALIAS_ON);
                    Color bg;
                    if (cleared2)       bg = new Color(accent.getRed()/6,  accent.getGreen()/6,  accent.getBlue()/6);
                    else if (unlocked)  bg = new Color(22, 26, 42);
                    else                bg = new Color(15, 17, 26);
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);

                    g2.setStroke(new BasicStroke(cleared2 ? 2f : 1.5f));
                    g2.setColor(cleared2 ? accent : unlocked ? GOLD_DIM : new Color(40,42,55));
                    g2.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, 13, 13);
                }
            };
            cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
            cell.setOpaque(false);
            cell.setPreferredSize(new Dimension(110, 95));
            cell.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));

            if (unlocked) {
                cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            // Level number or lock icon
            String numText = unlocked ? String.valueOf(levelNum) : "\uD83D\uDD12";
            JLabel numLbl  = new JLabel(numText, SwingConstants.CENTER);
            numLbl.setFont(new Font("Serif", Font.BOLD, unlocked ? 28 : 20));
            numLbl.setForeground(cleared2 ? accent : unlocked ? TEXT_WHITE : new Color(55,55,70));
            numLbl.setAlignmentX(CENTER_ALIGNMENT);

            // Status label
            String statusText = cleared2 ? "\u2605 Cleared" : unlocked ? "Play" : "Locked";
            JLabel statusLbl  = new JLabel(statusText, SwingConstants.CENTER);
            statusLbl.setFont(new Font("SansSerif", Font.PLAIN, 11));
            statusLbl.setForeground(cleared2 ? accent : unlocked ? GOLD_DIM : new Color(55,55,70));
            statusLbl.setAlignmentX(CENTER_ALIGNMENT);

            cell.add(Box.createVerticalGlue());
            cell.add(numLbl);
            cell.add(Box.createVerticalStrut(5));
            cell.add(statusLbl);
            cell.add(Box.createVerticalGlue());

            // Click handler — only for unlocked levels
            if (unlocked) {
                cell.addMouseListener(new MouseAdapter() {
                    @Override public void mouseClicked(MouseEvent e) {
                        currentLevel = levelNum;
                        usedWords.clear();
                        startGame();
                    }
                    @Override public void mouseEntered(MouseEvent e) {
                        cell.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(accent, 2),
                            BorderFactory.createEmptyBorder(9,7,9,7)));
                        cell.repaint();
                    }
                    @Override public void mouseExited(MouseEvent e) {
                        cell.setBorder(BorderFactory.createEmptyBorder(10,8,10,8));
                        cell.repaint();
                    }
                });
            }

            grid.add(cell);
        }

        levelSelectPanel.add(grid);
        levelSelectPanel.revalidate();
        levelSelectPanel.repaint();
    }

    // ── Instructions Screen ───────────────────────────────────────────────────
    private JPanel buildInstructionsScreen() {
        JPanel root = new GradientPanel(BG_DARK, new Color(12, 15, 28));
        root.setLayout(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(30, 70, 30, 70));

        JLabel title = new JLabel("How to Play", SwingConstants.CENTER);
        title.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 36));
        title.setForeground(GOLD);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
        root.add(title, BorderLayout.NORTH);

        String html = "<html><body style='background:#0e1118;color:#b8b9c8;font-family:SansSerif;padding:18px;'>"
            + "<h2 style='color:#d4af5f;'>Objective</h2>"
            + "<p>Decipher the hidden programming word within 6 attempts to claim victory and advance to the next level.</p><br>"
            + "<h2 style='color:#d4af5f;'>Levels</h2>"
            + "<p>Each difficulty has its own set of levels. Complete a level to unlock the next one. "
            + "You can replay any unlocked level from the Level Select screen.</p><br>"
            + "<h2 style='color:#d4af5f;'>Clue System</h2>"
            + "<p>Every word comes with a <span style='color:#8cbeff;'>clue</span> displayed beneath the grid "
            + "and shown again in the result screen.</p><br>"
            + "<h2 style='color:#d4af5f;'>Difficulty — Based on Concept Complexity</h2>"
            + "<p>Difficulty is determined by how <b>obscure or advanced</b> the programming concept is.</p>"
            + "<table border='0' cellpadding='6'>"
            + "<tr><td style='color:#46aa64;font-weight:bold;'>Easy</td><td>Everyday concepts any beginner knows</td></tr>"
            + "<tr><td style='color:#be9b28;font-weight:bold;'>Medium</td><td>Intermediate terms encountered during learning</td></tr>"
            + "<tr><td style='color:#b44646;font-weight:bold;'>Hard</td><td>Advanced / niche jargon for seasoned developers</td></tr>"
            + "</table><br>"
            + "<h2 style='color:#d4af5f;'>Colour Feedback</h2>"
            + "<table border='0' cellpadding='6'>"
            + "<tr><td style='color:#3c8c5a;font-weight:bold;'>GREEN</td><td>Correct letter, correct position</td></tr>"
            + "<tr><td style='color:#be9b28;font-weight:bold;'>AMBER</td><td>Letter exists but wrong position</td></tr>"
            + "<tr><td style='color:#787682;font-weight:bold;'>GREY</td><td>Letter not in the word at all</td></tr>"
            + "</table><br>"
            + "<h2 style='color:#d4af5f;'>Scoring</h2>"
            + "<table border='0' cellpadding='6'>"
            + "<tr><td style='color:#46aa64;font-weight:bold;'>Easy</td><td>100 pts base + 10 \u00d7 remaining attempts</td></tr>"
            + "<tr><td style='color:#be9b28;font-weight:bold;'>Medium</td><td>200 pts base + 20 \u00d7 remaining attempts</td></tr>"
            + "<tr><td style='color:#b44646;font-weight:bold;'>Hard</td><td>300 pts base + 30 \u00d7 remaining attempts</td></tr>"
            + "</table><br>"
            + "<h2 style='color:#d4af5f;'>The Stickman</h2>"
            + "<p>Each wrong guess removes a body part. 6 wrong guesses = game over!</p>"
            + "</body></html>";

        JEditorPane ep = new JEditorPane("text/html", html);
        ep.setEditable(false);
        ep.setBackground(new Color(14, 17, 24));
        JScrollPane scroll = new JScrollPane(ep);
        scroll.setBorder(BorderFactory.createLineBorder(GOLD_DIM, 1));
        scroll.getViewport().setBackground(new Color(14, 17, 24));
        root.add(scroll, BorderLayout.CENTER);

        JButton back = makeFancyButton("<- Return to Hall", new Color(25,20,10), GOLD_DIM);
        back.addActionListener(e -> cardLayout.show(cardPanel, "MENU"));
        JPanel bot = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bot.setOpaque(false);
        bot.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        bot.add(back);
        root.add(bot, BorderLayout.SOUTH);
        return root;
    }

    // ── Leaderboard Screen ────────────────────────────────────────────────────
    private JPanel buildLeaderboardScreen() {
        JPanel root = new GradientPanel(BG_DARK, new Color(12, 15, 28));
        root.setLayout(new BorderLayout());
        root.setBorder(BorderFactory.createEmptyBorder(30, 70, 30, 70));

        JLabel title = new JLabel("* Leaderboard *", SwingConstants.CENTER);
        title.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 36));
        title.setForeground(GOLD);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
        root.add(title, BorderLayout.NORTH);

        leaderboardListPanel = new JPanel();
        leaderboardListPanel.setLayout(new BoxLayout(leaderboardListPanel, BoxLayout.Y_AXIS));
        leaderboardListPanel.setOpaque(false);
        leaderboardListPanel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        JScrollPane scroll = new JScrollPane(leaderboardListPanel);
        scroll.setBorder(BorderFactory.createLineBorder(GOLD_DIM, 1));
        scroll.getViewport().setBackground(new Color(14, 17, 24));
        scroll.setOpaque(false);
        root.add(scroll, BorderLayout.CENTER);

        JPanel bot = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
        bot.setOpaque(false);
        bot.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));
        JButton backBtn = makeFancyButton("<- Return", new Color(25,20,10), GOLD_DIM);
        backBtn.addActionListener(e -> cardLayout.show(cardPanel, "MENU"));
        JButton clearBtn = makeFancyButton("Clear Leaderboard", new Color(50,20,20), RED_DANGER);
        clearBtn.addActionListener(e -> {
            leaderboard.clear();
            if (db.isConnected()) db.clearLeaderboard();
            updateLeaderboardDisplay();
        });
        bot.add(backBtn); bot.add(clearBtn);
        root.add(bot, BorderLayout.SOUTH);
        return root;
    }

    private void updateLeaderboardDisplay() {
        leaderboardListPanel.removeAll();
        if (db.isConnected()) {
            leaderboard.clear();
            for (DatabaseManager.LeaderboardRow row : db.getTopScores(10)) {
                leaderboard.add(new LeaderboardEntry(row.name, row.score, row.stage));
            }
        }
        leaderboard.sort((a, b) -> Integer.compare(b.score, a.score));
        if (leaderboard.isEmpty()) {
            JLabel e = new JLabel("No entries yet. Play to get on the board!", SwingConstants.CENTER);
            e.setFont(new Font("Serif", Font.ITALIC, 16));
            e.setForeground(TEXT_MUTED); e.setAlignmentX(CENTER_ALIGNMENT);
            leaderboardListPanel.add(Box.createVerticalStrut(50));
            leaderboardListPanel.add(e);
        } else {
            JPanel header = new JPanel(new GridLayout(1, 4, 10, 0));
            header.setOpaque(false); header.setMaximumSize(new Dimension(600, 35));
            header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, GOLD_DIM));
            for (String h : new String[]{"Rank","Player","Score","Stage"}) {
                JLabel lbl = new JLabel(h, SwingConstants.CENTER);
                lbl.setFont(new Font("Serif", Font.BOLD, 14)); lbl.setForeground(GOLD);
                header.add(lbl);
            }
            leaderboardListPanel.add(header);
            leaderboardListPanel.add(Box.createVerticalStrut(5));
            int rank = 1;
            for (LeaderboardEntry entry : leaderboard) {
                if (rank > 10) break;
                JPanel row = new JPanel(new GridLayout(1, 4, 10, 0));
                row.setOpaque(false); row.setMaximumSize(new Dimension(600, 35));
                row.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(50,50,60)));
                Color rc = rank == 1 ? GOLD : rank == 2 ? SILVER
                         : rank == 3 ? new Color(205,127,50) : TEXT_WHITE;
                for (String val : new String[]{"#"+rank, entry.name, String.valueOf(entry.score), String.valueOf(entry.stage)}) {
                    JLabel lbl = new JLabel(val, SwingConstants.CENTER);
                    lbl.setFont(new Font("Serif", Font.BOLD, 14)); lbl.setForeground(rc);
                    row.add(lbl);
                }
                leaderboardListPanel.add(row);
                leaderboardListPanel.add(Box.createVerticalStrut(3));
                rank++;
            }
        }
        leaderboardListPanel.revalidate();
        leaderboardListPanel.repaint();
    }

    // ── Game Screen ───────────────────────────────────────────────────────────
    private JPanel buildGameScreen() {
        JPanel root = new GradientPanel(BG_DARK, new Color(10, 13, 22));
        root.setLayout(new BorderLayout());

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, GOLD_DIM),
            BorderFactory.createEmptyBorder(10, 18, 10, 18)));

        JButton backBtn = makeFancyButton("<- Levels", new Color(25,22,12), GOLD_DIM);
        backBtn.addActionListener(e -> {
            sessionScoreLabel.setText("Session Score: " + sessionScore + "  |  Stage: " + currentStage);
            updateLevelSelectScreen();
            cardLayout.show(cardPanel, "LEVELSELECT");
        });
        topBar.add(backBtn, BorderLayout.WEST);

        JPanel titleGroup = new JPanel();
        titleGroup.setOpaque(false);
        titleGroup.setLayout(new BoxLayout(titleGroup, BoxLayout.Y_AXIS));
        JLabel gTitle = new JLabel("LexiGuess", SwingConstants.CENTER);
        gTitle.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 26));
        gTitle.setForeground(GOLD); gTitle.setAlignmentX(CENTER_ALIGNMENT);
        difficultyLabel = new JLabel("", SwingConstants.CENTER);
        difficultyLabel.setFont(new Font("Serif", Font.ITALIC, 12));
        difficultyLabel.setForeground(GOLD_DIM); difficultyLabel.setAlignmentX(CENTER_ALIGNMENT);
        titleGroup.add(gTitle); titleGroup.add(difficultyLabel);
        topBar.add(titleGroup, BorderLayout.CENTER);

        JPanel scorePanel = new JPanel();
        scorePanel.setOpaque(false);
        scorePanel.setLayout(new BoxLayout(scorePanel, BoxLayout.Y_AXIS));
        inGameScoreLabel = new JLabel("Score: 0", SwingConstants.RIGHT);
        inGameScoreLabel.setFont(new Font("Serif", Font.BOLD, 15));
        inGameScoreLabel.setForeground(GOLD); inGameScoreLabel.setAlignmentX(RIGHT_ALIGNMENT);
        stageLabel = new JLabel("Stage: 1", SwingConstants.RIGHT);
        stageLabel.setFont(new Font("Serif", Font.ITALIC, 12));
        stageLabel.setForeground(GOLD_DIM); stageLabel.setAlignmentX(RIGHT_ALIGNMENT);
        scorePanel.add(inGameScoreLabel); scorePanel.add(stageLabel);
        topBar.add(scorePanel, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(14, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 18, 0, 18);
        gbc.anchor = GridBagConstraints.NORTH;

        hangmanPanel = new HangmanPanel();
        hangmanPanel.setPreferredSize(new Dimension(215, 295));
        gbc.gridx = 0; gbc.gridy = 0;
        center.add(hangmanPanel, gbc);

        gridPanel = new JPanel();
        gridPanel.setOpaque(false);
        gbc.gridx = 1; gbc.gridy = 0;
        center.add(gridPanel, gbc);
        root.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, GOLD_DIM),
            BorderFactory.createEmptyBorder(10, 20, 14, 20)));

        clueLabel = new JLabel(" ", SwingConstants.CENTER);
        clueLabel.setFont(new Font("Serif", Font.ITALIC, 14));
        clueLabel.setForeground(CLUE_CLR);
        clueLabel.setAlignmentX(CENTER_ALIGNMENT);
        bottom.add(clueLabel);
        bottom.add(Box.createVerticalStrut(2));

        messageLabel = new JLabel(" ", SwingConstants.CENTER);
        messageLabel.setFont(new Font("Serif", Font.ITALIC, 14));
        messageLabel.setForeground(TEXT_WHITE);
        messageLabel.setAlignmentX(CENTER_ALIGNMENT);
        bottom.add(messageLabel);
        bottom.add(Box.createVerticalStrut(8));

        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        inputRow.setOpaque(false);

        inputField = new JTextField(10) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(20, 24, 38));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g);
            }
        };
        inputField.setFont(new Font("Monospaced", Font.BOLD, 20));
        inputField.setForeground(TEXT_WHITE);
        inputField.setBackground(new Color(0, 0, 0, 0));
        inputField.setOpaque(false);
        inputField.setCaretColor(GOLD);
        inputField.setHorizontalAlignment(SwingConstants.CENTER);
        inputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(GOLD_DIM, 1),
            BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        inputField.setPreferredSize(new Dimension(210, 44));
        inputField.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (!Character.isLetter(c)) { e.consume(); return; }
                if (inputField.getText().length() >= wordLength) e.consume();
                else { e.setKeyChar(Character.toUpperCase(c)); playSound(SOUND_KEYCLICK); }
            }
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) submitGuess();
            }
        });

        submitBtn = makeFancyButton("Guess  ->", new Color(20,40,20), EASY_CLR);
        submitBtn.addActionListener(e -> submitGuess());
        submitBtn.setPreferredSize(new Dimension(120, 44));

        JButton newWordBtn = makeFancyButton("New Word", new Color(35,30,15), GOLD_DIM);
        newWordBtn.addActionListener(e -> startGame());
        newWordBtn.setPreferredSize(new Dimension(110, 44));

        inputRow.add(inputField); inputRow.add(submitBtn); inputRow.add(newWordBtn);
        bottom.add(inputRow);
        bottom.add(Box.createVerticalStrut(10));

        keyboardPanel = new JPanel();
        keyboardPanel.setOpaque(false);
        buildKeyboard();
        bottom.add(keyboardPanel);
        root.add(bottom, BorderLayout.SOUTH);
        return root;
    }

    // ── Keyboard ──────────────────────────────────────────────────────────────
    private void buildKeyboard() {
        keyboardPanel.removeAll();
        keyboardPanel.setLayout(new BoxLayout(keyboardPanel, BoxLayout.Y_AXIS));
        keyLabels.clear();
        for (String row : new String[]{"QWERTYUIOP","ASDFGHJKL","ZXCVBNM"}) {
            JPanel rowP = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
            rowP.setOpaque(false);
            for (char ch : row.toCharArray()) {
                JLabel k = new JLabel(String.valueOf(ch), SwingConstants.CENTER) {
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(getBackground());
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                        super.paintComponent(g);
                    }
                };
                k.setFont(new Font("SansSerif", Font.BOLD, 11));
                k.setForeground(TEXT_WHITE);
                k.setBackground(new Color(30, 34, 50));
                k.setOpaque(false);
                k.setPreferredSize(new Dimension(30, 30));
                k.setBorder(BorderFactory.createLineBorder(new Color(55,55,75), 1));
                keyLabels.put(ch, k);
                rowP.add(k);
            }
            keyboardPanel.add(rowP);
        }
    }

    private void updateKeyboard() {
        for (Map.Entry<Character, JLabel> e : keyLabels.entrySet()) {
            char ch = e.getKey(); JLabel lbl = e.getValue();
            if      (correctLetters.contains(ch)) { lbl.setBackground(GREEN_CORRECT);  lbl.setBorder(BorderFactory.createLineBorder(GREEN_CORRECT.brighter(), 1)); }
            else if (presentLetters.contains(ch)) { lbl.setBackground(YELLOW_PRESENT); lbl.setBorder(BorderFactory.createLineBorder(YELLOW_PRESENT.brighter(), 1)); }
            else if (absentLetters.contains(ch))  { lbl.setBackground(GRAY_ABSENT);    lbl.setForeground(TEXT_MUTED); }
        }
        keyboardPanel.repaint();
    }

    // ── Game Logic ────────────────────────────────────────────────────────────
    private void startGame() {
        List<WordEntry> bank = difficulty.equals("EASY")   ? easyWords
                             : difficulty.equals("MEDIUM") ? mediumWords : hardWords;
        if (bank.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "No words found for difficulty: " + difficulty + "\nCheck your JSON files.",
                "Word Bank Empty", JOptionPane.WARNING_MESSAGE);
            return;
        }

        totalLevels = bank.size();

        // Clamp level within valid range
        if (currentLevel < 1) currentLevel = 1;
        if (currentLevel > totalLevels) currentLevel = totalLevels;

        currentEntry = bank.get(currentLevel - 1);
        targetWord   = currentEntry.word;
        currentClue  = currentEntry.clue;
        wordLength   = targetWord.length();

        guesses.clear(); feedbackList.clear();
        correctLetters.clear(); presentLetters.clear(); absentLetters.clear();
        currentAttempt = 0; gameOver = false; gameWon = false;

        buildGrid();
        buildKeyboard();
        hangmanPanel.resetAnimation();
        hangmanPanel.repaint();

        Color dc = difficulty.equals("EASY") ? EASY_CLR
                 : difficulty.equals("MEDIUM") ? MEDIUM_CLR : HARD_CLR;
        difficultyLabel.setForeground(dc);
        difficultyLabel.setText(difficulty + "  |  Level " + currentLevel + " of " + totalLevels
                                + "  |  " + wordLength + "-letter word");

        clueLabel.setText("Clue: " + currentClue);
        messageLabel.setText("Decipher the hidden programming word...");
        messageLabel.setForeground(TEXT_MUTED);
        inGameScoreLabel.setText("Score: " + sessionScore);
        stageLabel.setText("Stage: " + currentStage + "  |  Lv." + currentLevel + "/" + totalLevels);
        inputField.setText(""); inputField.setEnabled(true); submitBtn.setEnabled(true);

        cardLayout.show(cardPanel, "GAME");
        inputField.requestFocus();
    }

    private void buildGrid() {
        gridPanel.removeAll();
        int tileSize = wordLength <= 5 ? 60 : wordLength == 6 ? 54 : wordLength <= 8 ? 48 : 42;
        gridPanel.setLayout(new GridLayout(maxAttempts, wordLength, 6, 6));
        tiles = new JLabel[maxAttempts][wordLength];
        for (int r = 0; r < maxAttempts; r++) {
            for (int c = 0; c < wordLength; c++) {
                JLabel tile = new JLabel("", SwingConstants.CENTER) {
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(getBackground());
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                        g2.setColor(new Color(255,255,255,14));
                        g2.setStroke(new BasicStroke(1f));
                        g2.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, 9, 9);
                        super.paintComponent(g);
                    }
                };
                tile.setFont(new Font("Serif", Font.BOLD, tileSize - 22));
                tile.setForeground(TEXT_WHITE);
                tile.setBackground(TILE_EMPTY_BG);
                tile.setOpaque(false);
                tile.setBorder(BorderFactory.createLineBorder(TILE_EMPTY_BD, 1));
                tile.setPreferredSize(new Dimension(tileSize, tileSize));
                tiles[r][c] = tile;
                gridPanel.add(tile);
            }
        }
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private void submitGuess() {
        if (gameOver || gameWon) return;
        String guess = inputField.getText().trim().toUpperCase();
        if (guess.length() != wordLength) {
            messageLabel.setText("Word must be exactly " + wordLength + " letters!");
            messageLabel.setForeground(MEDIUM_CLR);
            return;
        }
        int[] fb = computeFeedback(guess, targetWord);
        guesses.add(guess); feedbackList.add(fb);

        for (int c = 0; c < wordLength; c++) {
            JLabel tile = tiles[currentAttempt][c];
            tile.setText(String.valueOf(guess.charAt(c)));
            tile.setBorder(null);
            if      (fb[c] == 2) { tile.setBackground(GREEN_CORRECT);  correctLetters.add(guess.charAt(c)); }
            else if (fb[c] == 1) { tile.setBackground(YELLOW_PRESENT); presentLetters.add(guess.charAt(c)); }
            else                 { tile.setBackground(GRAY_ABSENT);     absentLetters.add(guess.charAt(c)); }
            tile.repaint();
        }
        currentAttempt++;
        updateKeyboard();

        boolean allCorrect = true;
        for (int f : fb) if (f != 2) { allCorrect = false; break; }

        if (!allCorrect) hangmanPanel.triggerDropAnimation(countWrongGuesses());
        else             hangmanPanel.repaint();

        if (allCorrect) {
            gameWon = true;
            playSound(SOUND_CORRECT);
            int remaining  = maxAttempts - currentAttempt;
            int base       = difficulty.equals("EASY") ? 100 : difficulty.equals("MEDIUM") ? 200 : 300;
            int bonus      = difficulty.equals("EASY") ?  10 : difficulty.equals("MEDIUM") ?  20 :  30;
            int roundScore = base + remaining * bonus;
            sessionScore  += roundScore;
            currentStage++;

            // ── Unlock next level for this difficulty ──────────────────────
            int nextLevel = currentLevel + 1;
            int prevHighest = difficultyProgress.getOrDefault(difficulty, 1);
            if (nextLevel > prevHighest && nextLevel <= totalLevels) {
                difficultyProgress.put(difficulty, nextLevel);
            }
            if (db.isConnected()) {
                db.saveProgress(currentPlayerName, difficulty,
                                difficultyProgress.get(difficulty));
            }
            // If this was the last level, mark all as cleared (highest = totalLevels+1)
            if (currentLevel == totalLevels) {
                difficultyProgress.put(difficulty, totalLevels + 1);
            }

            currentLevel = nextLevel;

            messageLabel.setText("Magnificent! The word was " + targetWord + "  |  +" + roundScore + " pts");
            messageLabel.setForeground(GOLD);
            inGameScoreLabel.setText("Score: " + sessionScore);
            stageLabel.setText("Stage: " + currentStage);
            sessionScoreLabel.setText("Session Score: " + sessionScore + "  |  Stage: " + currentStage);
            inputField.setEnabled(false); submitBtn.setEnabled(false);
            addToLeaderboard(currentPlayerName, sessionScore, currentStage);
            SwingUtilities.invokeLater(() -> showEndGameDialog(
                "\u2726 You Win! \u2726", "Congratulations! You guessed:", targetWord,
                "+" + roundScore + " pts  |  Total: " + sessionScore, GREEN_CORRECT, "Next Level"));

        } else if (currentAttempt >= maxAttempts) {
            gameOver = true;
            playSound(SOUND_LOSE);
            messageLabel.setText("The word was: " + targetWord + " — Score reset!");
            messageLabel.setForeground(RED_DANGER);
            if (sessionScore > 0) addToLeaderboard(currentPlayerName, sessionScore, currentStage);
            sessionScore = 0;
            // Level progress is NOT reset on loss — player keeps unlocked levels
            inGameScoreLabel.setText("Score: 0");
            sessionScoreLabel.setText("Session Score: 0  |  Stage: " + currentStage);
            inputField.setEnabled(false); submitBtn.setEnabled(false);
            SwingUtilities.invokeLater(() -> showEndGameDialog(
                "Game Over", "The word was:", targetWord,
                "Your score has been reset.", RED_DANGER, "Retry"));
        } else {
            int left = maxAttempts - currentAttempt;
            messageLabel.setText("Attempt " + currentAttempt + " of " + maxAttempts + "  |  " + left + " remaining");
            messageLabel.setForeground(TEXT_MUTED);
        }
        inputField.setText("");
        gridPanel.repaint();
    }

    private int countWrongGuesses() {
        int w = 0;
        for (int[] fb : feedbackList) {
            boolean allC = true;
            for (int f : fb) if (f != 2) { allC = false; break; }
            if (!allC) w++;
        }
        return w;
    }

    private void addToLeaderboard(String name, int sc, int stage) {
        // Always save to the in-memory list for the current session
        boolean found = false;
        for (LeaderboardEntry e : leaderboard) {
            if (e.name.equals(name)) {
                if (sc > e.score) { e.score = sc; e.stage = stage; }
                found = true;
                break;
            }
        }
        if (!found) leaderboard.add(new LeaderboardEntry(name, sc, stage));

        // Persist to SQLite
        if (db.isConnected()) {
            db.saveScore(name, sc, stage);
        }
    }
    private int[] computeFeedback(String guess, String target) {
        int len = target.length();
        int[] result = new int[len];
        boolean[] tUsed = new boolean[len], gUsed = new boolean[len];
        for (int i = 0; i < len; i++)
            if (guess.charAt(i) == target.charAt(i)) { result[i] = 2; tUsed[i] = gUsed[i] = true; }
        for (int i = 0; i < len; i++) {
            if (gUsed[i]) continue;
            for (int j = 0; j < len; j++) {
                if (tUsed[j]) continue;
                if (guess.charAt(i) == target.charAt(j)) { result[i] = 1; tUsed[j] = true; break; }
            }
        }
        return result;
    }

    // ── Leaderboard Entry ─────────────────────────────────────────────────────
    static class LeaderboardEntry {
        String name; int score; int stage;
        LeaderboardEntry(String n, int s, int st) { name=n; score=s; stage=st; }
    }

    // ── HangmanPanel ──────────────────────────────────────────────────────────
    class HangmanPanel extends JPanel {
        private static final int PARTS = 7;
        private final double[] bx=new double[PARTS],by=new double[PARTS],angle=new double[PARTS];
        private final double[] vx=new double[PARTS],vy=new double[PARTS],omega=new double[PARTS];
        private final int[]    halfW=new int[PARTS],halfH=new int[PARTS];
        private final String[] phase=new String[PARTS];
        private Timer physicsTimer;
        private static final double GRAVITY=0.55,BOUNCE=0.15,SLIDE_FRIC=0.94,ANG_DAMP=0.80,FLAT_STEER=0.20,FLAT_SNAP=0.10;

        HangmanPanel() {
            setOpaque(false);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GOLD_DIM, 1),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
            for (int i=0;i<PARTS;i++) phase[i]="attached";
        }

        private int ropeEndY(){return 50;} private int headR(){return 15;}
        private int headCY(){return ropeEndY()+headR();}
        private int shoulderY(){return ropeEndY()+headR()*2+10;}
        private int hipsY(){return shoulderY()+52;}
        private int groundY(){return getHeight()-30;}
        private int cx(){return getWidth()/2;}

        private double nearestFlatError(double a){
            a=((a%Math.PI)+Math.PI)%Math.PI; if(a>Math.PI/2) a-=Math.PI; return a;
        }

        private void detach(int p){
            int cx=cx();
            switch(p){
                case 6: bx[p]=cx; by[p]=headCY(); vx[p]=0.8; vy[p]=-1.0; omega[p]=0.12; halfW[p]=headR(); halfH[p]=headR(); angle[p]=0; break;
                case 5: bx[p]=cx; by[p]=(shoulderY()+hipsY())/2.0; vx[p]=0.4; vy[p]=-0.5; omega[p]=0.03; halfW[p]=26; halfH[p]=5; angle[p]=Math.PI/2; break;
                case 3: bx[p]=cx+18; by[p]=shoulderY()+15; vx[p]=1.8; vy[p]=-1.5; omega[p]=0.09; halfW[p]=21; halfH[p]=5; angle[p]=Math.PI/2; break;
                case 4: bx[p]=cx-18; by[p]=shoulderY()+15; vx[p]=-1.8; vy[p]=-1.5; omega[p]=-0.09; halfW[p]=21; halfH[p]=5; angle[p]=Math.PI/2; break;
                case 1: bx[p]=cx+10; by[p]=hipsY()+25; vx[p]=1.4; vy[p]=-0.8; omega[p]=0.06; halfW[p]=27; halfH[p]=5; angle[p]=Math.PI/2; break;
                case 2: bx[p]=cx-10; by[p]=hipsY()+25; vx[p]=-1.4; vy[p]=-0.8; omega[p]=-0.06; halfW[p]=27; halfH[p]=5; angle[p]=Math.PI/2; break;
            }
            phase[p]="falling";
        }

        private void stepPart(int p){
            if("attached".equals(phase[p])||"rest".equals(phase[p])) return;
            vy[p]+=GRAVITY; bx[p]+=vx[p]; by[p]+=vy[p]; angle[p]+=omega[p];
            int gnd=groundY();
            if(p==6){
                int R=halfW[p];
                if(by[p]+R>=gnd){
                    by[p]=gnd-R;
                    if(Math.abs(vy[p])>0.5) vy[p]=-Math.abs(vy[p])*BOUNCE; else vy[p]=0;
                    phase[p]="grounded"; omega[p]=vx[p]/R; vx[p]*=0.988; omega[p]=vx[p]/R;
                    if(Math.abs(vx[p])<0.05){vx[p]=0;vy[p]=0;omega[p]=0;phase[p]="rest";}
                }
            } else {
                double sin=Math.sin(angle[p]),cos=Math.cos(angle[p]);
                double maxY=Double.NEGATIVE_INFINITY;
                int hw=halfW[p],hh=halfH[p];
                for(int[] c:new int[][]{{-hw,-hh},{hw,-hh},{-hw,hh},{hw,hh}}){
                    double wy=by[p]+c[0]*sin+c[1]*cos; if(wy>maxY) maxY=wy;
                }
                if(maxY>=gnd){
                    by[p]-=(maxY-gnd); phase[p]="grounded";
                    if(Math.abs(vy[p])>0.5) vy[p]=-Math.abs(vy[p])*BOUNCE; else vy[p]=0;
                    vx[p]*=SLIDE_FRIC; omega[p]*=ANG_DAMP;
                    double err=nearestFlatError(angle[p]); omega[p]-=err*FLAT_STEER; angle[p]-=err*FLAT_SNAP;
                    if(Math.abs(omega[p])<0.003&&Math.abs(vy[p])<0.3&&Math.abs(vx[p])<0.04){
                        vx[p]=0;vy[p]=0;omega[p]=0;angle[p]=Math.round(angle[p]/Math.PI)*Math.PI;phase[p]="rest";
                    }
                }
            }
        }

        private boolean anyActive(){
            for(int i=1;i<PARTS;i++) if("falling".equals(phase[i])||"grounded".equals(phase[i])) return true;
            return false;
        }

        void resetAnimation(){
            if(physicsTimer!=null) physicsTimer.stop();
            for(int i=0;i<PARTS;i++){bx[i]=by[i]=angle[i]=vx[i]=vy[i]=omega[i]=0;phase[i]="attached";}
            repaint();
        }

        void triggerDropAnimation(int pn){
            if(pn<1||pn>6) return;
            detach(pn);
            if(physicsTimer!=null&&physicsTimer.isRunning()) physicsTimer.stop();
            physicsTimer=new Timer(16,e->{
                for(int i=1;i<PARTS;i++) stepPart(i);
                repaint();
                if(!anyActive())((Timer)e.getSource()).stop();
            });
            physicsTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g){
            super.paintComponent(g);
            Graphics2D g2=(Graphics2D)g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(),groundY=groundY(),cx=cx(),shoulderY=shoulderY(),hipsY=hipsY(),headCY=headCY(),headR=headR(),ropeEndY=ropeEndY();
            g2.setColor(GOLD_DIM); g2.setStroke(new BasicStroke(3f));
            g2.drawLine(20,groundY,w-20,groundY); g2.drawLine(36,groundY,36,12);
            g2.drawLine(36,12,cx,12); g2.drawLine(cx,12,cx,ropeEndY);
            g2.setColor(TEXT_WHITE); g2.setStroke(new BasicStroke(2.5f));
            if("attached".equals(phase[6])){g2.drawOval(cx-headR,ropeEndY,headR*2,headR*2);g2.fillOval(cx-8,headCY-5,4,4);g2.fillOval(cx+4,headCY-5,4,4);g2.drawArc(cx-5,headCY+3,10,6,0,-180);}
            if("attached".equals(phase[5])) g2.drawLine(cx,shoulderY,cx,hipsY);
            if("attached".equals(phase[3])) g2.drawLine(cx,shoulderY,cx+30,shoulderY+30);
            if("attached".equals(phase[4])) g2.drawLine(cx,shoulderY,cx-30,shoulderY+30);
            if("attached".equals(phase[1])) g2.drawLine(cx,hipsY,cx+20,hipsY+50);
            if("attached".equals(phase[2])) g2.drawLine(cx,hipsY,cx-20,hipsY+50);
            for(int p=1;p<PARTS;p++){
                if("attached".equals(phase[p])) continue;
                Graphics2D g3=(Graphics2D)g2.create();
                g3.translate((int)bx[p],(int)by[p]); g3.rotate(angle[p]);
                if(p==6){g3.drawOval(-headR,-headR,headR*2,headR*2);g3.fillOval(-6,-headR+5,4,4);g3.fillOval(2,-headR+5,4,4);g3.drawArc(-5,-headR+13,10,6,0,-180);}
                else g3.drawLine(-halfW[p],0,halfW[p],0);
                g3.dispose();
            }
        }
    }

    public static void main(String[] args){
        SwingUtilities.invokeLater(LexiGuess::new);
    }
}
