import java.awt.*;
import java.awt.event.*;
import java.util.*;
import javax.swing.*;

public class LexiGuess extends JFrame {

    // ─── Word Banks ──────────────────────────────────────────────────────────
    private static final String[] EASY_WORDS = {
        "CATS","DOGS","BIRD","FISH","FROG","TREE","BOOK","CAKE","RAIN","SNOW",
        "STAR","MOON","FIRE","WIND","LION","BEAR","WOLF","DEER","DUCK","FAWN",
        "LAMP","DOOR","ROAD","SHIP","FARM","BELL","HILL","ROSE","LEAF","SEED"
    };
    private static final String[] MEDIUM_WORDS = {
        "APPLE","BRAVE","CLOUD","DANCE","EARTH","FLAME","GRACE","HEART","IVORY","JELLY",
        "KNEEL","LEMON","MAGIC","NIGHT","OCEAN","PLANT","QUEST","RIVER","SOLAR","TIGER",
        "ULTRA","VIVID","WATER","XENON","YACHT","ZEBRA","BLAZE","CRISP","DROVE","ELITE"
    };
    private static final String[] HARD_WORDS = {
        "BRIDGE","CASTLE","DANGER","EMBARK","FOREST","GENTLE","HEROES","IMPACT",
        "JUNGLE","KNIGHT","LAUNCH","MIRROR","NARROW","OBJECT","PALACE","QUARTZ",
        "RADIANT","SHADOW","TUNNEL","UNIQUE","VELVET","WONDER","YELLOW","ZIPPER",
        "BLANKET","CAPTAIN","DIAMOND","ETERNAL","FREEDOM","GRANITE"
    };

    // ─── Palette ─────────────────────────────────────────────────────────────
    private static final Color BG_DARK        = new Color(8, 10, 18);
    private static final Color BG_PANEL       = new Color(14, 17, 28);
    private static final Color GOLD           = new Color(212, 175, 95);
    private static final Color GOLD_LIGHT     = new Color(240, 210, 130);
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

    // ─── State ────────────────────────────────────────────────────────────────
    private String targetWord = "";
    private String difficulty = "EASY";
    private int maxAttempts = 6;
    private int currentAttempt = 0;
    private int score = 0;
    private int sessionScore = 0;
    private boolean gameOver = false;
    private boolean gameWon = false;
    private java.util.List<String> guesses = new ArrayList<>();
    private java.util.List<int[]> feedbackList = new ArrayList<>();
    private Set<Character> correctLetters = new HashSet<>();
    private Set<Character> presentLetters = new HashSet<>();
    private Set<Character> absentLetters = new HashSet<>();
    private int wordLength;

    // ─── UI ───────────────────────────────────────────────────────────────────
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private JLabel[][] tiles;
    private JPanel gridPanel;
    private HangmanPanel hangmanPanel;
    private JTextField inputField;
    private JButton submitBtn;
    private JLabel messageLabel;
    private JLabel difficultyLabel;
    private JLabel inGameScoreLabel;
    private JLabel sessionScoreLabel;
    private Map<Character, JLabel> keyLabels = new HashMap<>();
    private JPanel keyboardPanel;

    public LexiGuess() {
        setTitle("LexiGuess — Vocabulary Enhancement Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        getContentPane().setBackground(BG_DARK);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.setBackground(BG_DARK);
        cardPanel.add(buildMenuScreen(),         "MENU");
        cardPanel.add(buildGameScreen(),         "GAME");
        cardPanel.add(buildInstructionsScreen(), "INSTRUCTIONS");

        add(cardPanel);
        setSize(960, 780);
        setLocationRelativeTo(null);
        setVisible(true);
        cardLayout.show(cardPanel, "MENU");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  MENU SCREEN
    // ═══════════════════════════════════════════════════════════════════════════
    private JPanel buildMenuScreen() {
        JPanel root = new GradientPanel(BG_DARK, new Color(12, 15, 28));
        root.setLayout(new BorderLayout());

        // Top nav bar
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 12));
        topBar.setOpaque(false);
        JButton instrBtn = makeLinkButton("How to Play");
        instrBtn.addActionListener(e -> cardLayout.show(cardPanel, "INSTRUCTIONS"));
        JButton exitBtn = makeLinkButton("Exit");
        exitBtn.addActionListener(e -> System.exit(0));
        topBar.add(instrBtn);
        topBar.add(exitBtn);
        root.add(topBar, BorderLayout.NORTH);

        // Center
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(10, 80, 20, 80));

        center.add(makeOrnamentLabel("— ✦ —"));
        center.add(Box.createVerticalStrut(10));

        JLabel title = new JLabel("LexiGuess", SwingConstants.CENTER);
        title.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 58));
        title.setForeground(GOLD);
        title.setAlignmentX(CENTER_ALIGNMENT);
        center.add(title);

        JLabel sub = new JLabel("— Vocabulary Enhancement Game —", SwingConstants.CENTER);
        sub.setFont(new Font("Serif", Font.ITALIC, 15));
        sub.setForeground(GOLD_DIM);
        sub.setAlignmentX(CENTER_ALIGNMENT);
        center.add(sub);

        center.add(Box.createVerticalStrut(8));
        center.add(makeOrnamentLabel("◆  ◇  ◆"));
        center.add(Box.createVerticalStrut(28));

        JLabel chooseLbl = new JLabel("Choose Your Challenge", SwingConstants.CENTER);
        chooseLbl.setFont(new Font("Serif", Font.BOLD, 22));
        chooseLbl.setForeground(TEXT_WHITE);
        chooseLbl.setAlignmentX(CENTER_ALIGNMENT);
        center.add(chooseLbl);
        center.add(Box.createVerticalStrut(20));

        // Difficulty cards
        JPanel diffRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 22, 0));
        diffRow.setOpaque(false);
        diffRow.add(makeDiffCard("EASY",   "Novice",     "4-letter words", "6 attempts", EASY_CLR,   "✦"));
        diffRow.add(makeDiffCard("MEDIUM", "Scholar",    "5-letter words", "6 attempts", MEDIUM_CLR, "✦✦"));
        diffRow.add(makeDiffCard("HARD",   "Mastermind", "6+ letter words","6 attempts", HARD_CLR,   "✦✦✦"));
        center.add(diffRow);

        center.add(Box.createVerticalStrut(30));
        sessionScoreLabel = new JLabel("Session Score: 0", SwingConstants.CENTER);
        sessionScoreLabel.setFont(new Font("Serif", Font.BOLD, 16));
        sessionScoreLabel.setForeground(GOLD);
        sessionScoreLabel.setAlignmentX(CENTER_ALIGNMENT);
        center.add(sessionScoreLabel);

        root.add(center, BorderLayout.CENTER);

        JLabel bottomOrnament = new JLabel("◆  ◇  ◆", SwingConstants.CENTER);
        bottomOrnament.setFont(new Font("Serif", Font.PLAIN, 14));
        bottomOrnament.setForeground(GOLD_DIM);
        bottomOrnament.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        root.add(bottomOrnament, BorderLayout.SOUTH);

        return root;
    }

    private JPanel makeDiffCard(String diff, String rankTitle, String wordInfo, String attempts, Color accent, String stars) {
        JPanel card = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint bg = new GradientPaint(0, 0,
                    new Color(accent.getRed()/7, accent.getGreen()/7, accent.getBlue()/7),
                    0, getHeight(),
                    new Color(accent.getRed()/11, accent.getGreen()/11, accent.getBlue()/11));
                g2.setPaint(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, getWidth()-3, getHeight()-3, 15, 15);
                g2.setColor(new Color(255,255,255,12));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(4, 4, getWidth()-9, getHeight()-9, 12, 12);
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(208, 210));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(18, 16, 18, 16));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel starsLbl = new JLabel(stars, SwingConstants.CENTER);
        starsLbl.setFont(new Font("Serif", Font.PLAIN, 13));
        starsLbl.setForeground(accent);
        starsLbl.setAlignmentX(CENTER_ALIGNMENT);

        JLabel titleLbl = new JLabel(diff, SwingConstants.CENTER);
        titleLbl.setFont(new Font("Serif", Font.BOLD, 24));
        titleLbl.setForeground(accent);
        titleLbl.setAlignmentX(CENTER_ALIGNMENT);

        JLabel rankLbl = new JLabel(rankTitle, SwingConstants.CENTER);
        rankLbl.setFont(new Font("Serif", Font.ITALIC, 13));
        rankLbl.setForeground(SILVER);
        rankLbl.setAlignmentX(CENTER_ALIGNMENT);

        JPanel sepLine = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 80));
                g.drawLine(10,0,getWidth()-10,0);
            }
        };
        sepLine.setOpaque(false);
        sepLine.setPreferredSize(new Dimension(160, 4));
        sepLine.setMaximumSize(new Dimension(160, 4));
        sepLine.setAlignmentX(CENTER_ALIGNMENT);

        JLabel wordLbl = new JLabel(wordInfo, SwingConstants.CENTER);
        wordLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        wordLbl.setForeground(TEXT_MUTED);
        wordLbl.setAlignmentX(CENTER_ALIGNMENT);

        JLabel attLbl = new JLabel(attempts, SwingConstants.CENTER);
        attLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        attLbl.setForeground(TEXT_MUTED);
        attLbl.setAlignmentX(CENTER_ALIGNMENT);

        JButton playBtn = new JButton("Play  →") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(accent);
                g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                super.paintComponent(g);
            }
        };
        playBtn.setFont(new Font("Serif", Font.BOLD, 13));
        playBtn.setForeground(BG_DARK);
        playBtn.setContentAreaFilled(false);
        playBtn.setBorderPainted(false);
        playBtn.setFocusPainted(false);
        playBtn.setOpaque(false);
        playBtn.setMaximumSize(new Dimension(140, 32));
        playBtn.setAlignmentX(CENTER_ALIGNMENT);
        playBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        playBtn.addActionListener(e -> { difficulty = diff; startGame(); });

        card.add(starsLbl);
        card.add(Box.createVerticalStrut(4));
        card.add(titleLbl);
        card.add(rankLbl);
        card.add(Box.createVerticalStrut(8));
        card.add(sepLine);
        card.add(Box.createVerticalStrut(8));
        card.add(wordLbl);
        card.add(attLbl);
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

    // ═══════════════════════════════════════════════════════════════════════════
    //  INSTRUCTIONS SCREEN
    // ═══════════════════════════════════════════════════════════════════════════
    private JPanel buildInstructionsScreen() {
        JPanel root = new GradientPanel(BG_DARK, new Color(12, 15, 28));
        root.setLayout(new BorderLayout(0, 0));
        root.setBorder(BorderFactory.createEmptyBorder(30, 70, 30, 70));

        JLabel title = new JLabel("How to Play", SwingConstants.CENTER);
        title.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 36));
        title.setForeground(GOLD);
        title.setBorder(BorderFactory.createEmptyBorder(0,0,18,0));
        root.add(title, BorderLayout.NORTH);

        String html = "<html><body style='font-family:Serif; color:#ebe8e1; background:#0e1118; font-size:14px; padding:18px; line-height:1.7;'>"
            + "<p style='color:#d4af5f; font-size:16px; font-weight:bold; margin:0 0 4px 0;'>Objective</p>"
            + "<p style='margin:0 0 14px 0;'>Decipher the hidden word within <b>6 attempts</b> to claim victory.</p>"
            + "<hr style='border:none; border-top:1px solid #3a3a4a; margin:12px 0;'/>"
            + "<p style='color:#d4af5f; font-size:16px; font-weight:bold; margin:0 0 8px 0;'>Colour Feedback</p>"
            + "<table cellpadding='7' style='margin-bottom:14px;'>"
            + "<tr><td><span style='background:#3C8C5A; color:white; padding:4px 14px; border-radius:4px;'>&nbsp;GREEN&nbsp;</span></td>"
            + "<td>Correct letter in the correct position</td></tr>"
            + "<tr><td><span style='background:#BE9B28; color:white; padding:4px 14px; border-radius:4px;'>&nbsp;AMBER&nbsp;</span></td>"
            + "<td>Letter exists in the word but wrong position</td></tr>"
            + "<tr><td><span style='background:#3A3A4A; color:#aaa; padding:4px 14px; border-radius:4px;'>&nbsp;GREY&nbsp;&nbsp;</span></td>"
            + "<td>Letter is not in the word at all</td></tr>"
            + "</table>"
            + "<hr style='border:none; border-top:1px solid #3a3a4a; margin:12px 0;'/>"
            + "<p style='color:#d4af5f; font-size:16px; font-weight:bold; margin:0 0 8px 0;'>Difficulty Levels</p>"
            + "<table cellpadding='5' style='margin-bottom:14px;'>"
            + "<tr><td style='color:#46aa64; font-weight:bold; width:110px;'>Easy</td><td>4-letter words</td></tr>"
            + "<tr><td style='color:#be9b28; font-weight:bold;'>Medium</td><td>5-letter words</td></tr>"
            + "<tr><td style='color:#b44646; font-weight:bold;'>Hard</td><td>6 or more letter words</td></tr>"
            + "</table>"
            + "<hr style='border:none; border-top:1px solid #3a3a4a; margin:12px 0;'/>"
            + "<p style='color:#d4af5f; font-size:16px; font-weight:bold; margin:0 0 4px 0;'>The Gallows</p>"
            + "<p style='margin:0 0 14px 0;'>Each wrong guess draws a body part. After <b>6 wrong guesses</b>, the figure is complete and the game ends in defeat.</p>"
            + "<hr style='border:none; border-top:1px solid #3a3a4a; margin:12px 0;'/>"
            + "<p style='color:#d4af5f; font-size:16px; font-weight:bold; margin:0 0 8px 0;'>Scoring</p>"
            + "<table cellpadding='5'>"
            + "<tr><td style='color:#46aa64; width:110px;'>Easy</td><td>100 pts base + 10 × remaining attempts</td></tr>"
            + "<tr><td style='color:#be9b28;'>Medium</td><td>200 pts base + 20 × remaining attempts</td></tr>"
            + "<tr><td style='color:#b44646;'>Hard</td><td>300 pts base + 30 × remaining attempts</td></tr>"
            + "</table>"
            + "</body></html>";

        JEditorPane ep = new JEditorPane("text/html", html);
        ep.setEditable(false);
        ep.setBackground(new Color(14, 17, 24));
        JScrollPane scroll = new JScrollPane(ep);
        scroll.setBorder(BorderFactory.createLineBorder(GOLD_DIM, 1));
        scroll.getViewport().setBackground(new Color(14, 17, 24));
        root.add(scroll, BorderLayout.CENTER);

        JButton back = makeFancyButton("← Return to Hall", new Color(25,20,10), GOLD_DIM);
        back.addActionListener(e -> cardLayout.show(cardPanel, "MENU"));
        JPanel bot = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bot.setOpaque(false);
        bot.setBorder(BorderFactory.createEmptyBorder(16,0,0,0));
        bot.add(back);
        root.add(bot, BorderLayout.SOUTH);

        return root;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  GAME SCREEN
    // ═══════════════════════════════════════════════════════════════════════════
    private JPanel buildGameScreen() {
        JPanel root = new GradientPanel(BG_DARK, new Color(10, 13, 22));
        root.setLayout(new BorderLayout(0, 0));

        // ── Top bar ──
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0,0,1,0, GOLD_DIM),
            BorderFactory.createEmptyBorder(10,18,10,18)
        ));

        JButton backBtn = makeFancyButton("← Back", new Color(25,22,12), GOLD_DIM);
        backBtn.addActionListener(e -> {
            sessionScore += score; score = 0;
            sessionScoreLabel.setText("Session Score: " + sessionScore);
            cardLayout.show(cardPanel, "MENU");
        });
        topBar.add(backBtn, BorderLayout.WEST);

        JPanel titleGroup = new JPanel();
        titleGroup.setOpaque(false);
        titleGroup.setLayout(new BoxLayout(titleGroup, BoxLayout.Y_AXIS));
        JLabel gTitle = new JLabel("LexiGuess", SwingConstants.CENTER);
        gTitle.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 26));
        gTitle.setForeground(GOLD);
        gTitle.setAlignmentX(CENTER_ALIGNMENT);
        difficultyLabel = new JLabel("", SwingConstants.CENTER);
        difficultyLabel.setFont(new Font("Serif", Font.ITALIC, 12));
        difficultyLabel.setForeground(GOLD_DIM);
        difficultyLabel.setAlignmentX(CENTER_ALIGNMENT);
        titleGroup.add(gTitle);
        titleGroup.add(difficultyLabel);
        topBar.add(titleGroup, BorderLayout.CENTER);

        inGameScoreLabel = new JLabel("Score: 0", SwingConstants.RIGHT);
        inGameScoreLabel.setFont(new Font("Serif", Font.BOLD, 15));
        inGameScoreLabel.setForeground(GOLD);
        topBar.add(inGameScoreLabel, BorderLayout.EAST);

        root.add(topBar, BorderLayout.NORTH);

        // ── Center ──
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(14,20,10,20));
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

        // ── Bottom ──
        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1,0,0,0, GOLD_DIM),
            BorderFactory.createEmptyBorder(12,20,14,20)
        ));

        messageLabel = new JLabel(" ", SwingConstants.CENTER);
        messageLabel.setFont(new Font("Serif", Font.ITALIC, 15));
        messageLabel.setForeground(TEXT_WHITE);
        messageLabel.setAlignmentX(CENTER_ALIGNMENT);
        bottom.add(messageLabel);
        bottom.add(Box.createVerticalStrut(8));

        // Input row
        JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        inputRow.setOpaque(false);

        inputField = new JTextField(10) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(20, 24, 38));
                g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                super.paintComponent(g);
            }
        };
        inputField.setFont(new Font("Monospaced", Font.BOLD, 20));
        inputField.setForeground(TEXT_WHITE);
        inputField.setBackground(new Color(0,0,0,0));
        inputField.setOpaque(false);
        inputField.setCaretColor(GOLD);
        inputField.setHorizontalAlignment(SwingConstants.CENTER);
        inputField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(GOLD_DIM, 1),
            BorderFactory.createEmptyBorder(6,10,6,10)
        ));
        inputField.setPreferredSize(new Dimension(210, 44));
        inputField.addKeyListener(new KeyAdapter() {
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (!Character.isLetter(c)) { e.consume(); return; }
                if (inputField.getText().length() >= wordLength) e.consume();
                else e.setKeyChar(Character.toUpperCase(c));
            }
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) submitGuess();
            }
        });

        submitBtn = makeFancyButton("Guess  →", new Color(20,40,20), EASY_CLR);
        submitBtn.addActionListener(e -> submitGuess());
        submitBtn.setPreferredSize(new Dimension(120, 44));

        JButton newWordBtn = makeFancyButton("New Word", new Color(35,30,15), GOLD_DIM);
        newWordBtn.addActionListener(e -> startGame());
        newWordBtn.setPreferredSize(new Dimension(110, 44));

        inputRow.add(inputField);
        inputRow.add(submitBtn);
        inputRow.add(newWordBtn);
        bottom.add(inputRow);
        bottom.add(Box.createVerticalStrut(10));

        keyboardPanel = new JPanel();
        keyboardPanel.setOpaque(false);
        buildKeyboard();
        bottom.add(keyboardPanel);

        root.add(bottom, BorderLayout.SOUTH);
        return root;
    }

    // ─── Keyboard ────────────────────────────────────────────────────────────
    private void buildKeyboard() {
        keyboardPanel.removeAll();
        keyboardPanel.setLayout(new BoxLayout(keyboardPanel, BoxLayout.Y_AXIS));
        keyLabels.clear();
        String[] rows = {"QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM"};
        for (String row : rows) {
            JPanel rowP = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
            rowP.setOpaque(false);
            for (char ch : row.toCharArray()) {
                JLabel k = new JLabel(String.valueOf(ch), SwingConstants.CENTER) {
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D)g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(getBackground());
                        g2.fillRoundRect(0,0,getWidth(),getHeight(),6,6);
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
            if (correctLetters.contains(ch)) { lbl.setBackground(GREEN_CORRECT); lbl.setBorder(BorderFactory.createLineBorder(GREEN_CORRECT.brighter(),1)); }
            else if (presentLetters.contains(ch)) { lbl.setBackground(YELLOW_PRESENT); lbl.setBorder(BorderFactory.createLineBorder(YELLOW_PRESENT.brighter(),1)); }
            else if (absentLetters.contains(ch)) { lbl.setBackground(GRAY_ABSENT); lbl.setForeground(TEXT_MUTED); }
        }
        keyboardPanel.repaint();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  GAME LOGIC
    // ═══════════════════════════════════════════════════════════════════════════
    private void startGame() {
        String[] bank = difficulty.equals("EASY") ? EASY_WORDS : difficulty.equals("MEDIUM") ? MEDIUM_WORDS : HARD_WORDS;
        targetWord = bank[new Random().nextInt(bank.length)];
        wordLength = targetWord.length();
        guesses.clear(); feedbackList.clear();
        correctLetters.clear(); presentLetters.clear(); absentLetters.clear();
        currentAttempt = 0; score = 0; gameOver = false; gameWon = false;

        buildGrid();
        buildKeyboard();
        hangmanPanel.repaint();

        Color dc = difficulty.equals("EASY") ? EASY_CLR : difficulty.equals("MEDIUM") ? MEDIUM_CLR : HARD_CLR;
        difficultyLabel.setForeground(dc);
        difficultyLabel.setText(difficulty + "  ·  " + wordLength + "-letter word");
        messageLabel.setText("Decipher the hidden " + wordLength + "-letter word…");
        messageLabel.setForeground(TEXT_MUTED);
        inGameScoreLabel.setText("Score: " + sessionScore);
        inputField.setText("");
        inputField.setEnabled(true);
        submitBtn.setEnabled(true);

        cardLayout.show(cardPanel, "GAME");
        inputField.requestFocus();
    }

    private void buildGrid() {
        gridPanel.removeAll();
        int tileSize = wordLength <= 5 ? 60 : wordLength == 6 ? 54 : 48;
        gridPanel.setLayout(new GridLayout(maxAttempts, wordLength, 6, 6));
        tiles = new JLabel[maxAttempts][wordLength];
        for (int r = 0; r < maxAttempts; r++) {
            for (int c = 0; c < wordLength; c++) {
                JLabel tile = new JLabel("", SwingConstants.CENTER) {
                    @Override protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D)g;
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(getBackground());
                        g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);
                        g2.setColor(new Color(255,255,255,14));
                        g2.setStroke(new BasicStroke(1f));
                        g2.drawRoundRect(1,1,getWidth()-3,getHeight()-3,9,9);
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
            messageLabel.setText("✦  Word must be exactly " + wordLength + " letters  ✦");
            messageLabel.setForeground(MEDIUM_CLR);
            return;
        }
        int[] fb = computeFeedback(guess, targetWord);
        guesses.add(guess); feedbackList.add(fb);

        for (int c = 0; c < wordLength; c++) {
            JLabel tile = tiles[currentAttempt][c];
            tile.setText(String.valueOf(guess.charAt(c)));
            tile.setBorder(null);
            if (fb[c]==2) { tile.setBackground(GREEN_CORRECT); correctLetters.add(guess.charAt(c)); }
            else if (fb[c]==1) { tile.setBackground(YELLOW_PRESENT); presentLetters.add(guess.charAt(c)); }
            else { tile.setBackground(GRAY_ABSENT); absentLetters.add(guess.charAt(c)); }
            tile.repaint();
        }

        currentAttempt++;
        updateKeyboard();
        hangmanPanel.repaint();

        boolean won = true;
        for (int f : fb) if (f != 2) { won = false; break; }

        if (won) {
            gameWon = true;
            int remaining = maxAttempts - currentAttempt;
            int base = difficulty.equals("EASY") ? 100 : difficulty.equals("MEDIUM") ? 200 : 300;
            int bonus = difficulty.equals("EASY") ? 10 : difficulty.equals("MEDIUM") ? 20 : 30;
            score = base + remaining * bonus;
            sessionScore += score;
            messageLabel.setText("✦  Magnificent! The word was " + targetWord + "  ·  +" + score + " pts  ✦");
            messageLabel.setForeground(GOLD);
            inGameScoreLabel.setText("Score: " + sessionScore);
            sessionScoreLabel.setText("Session Score: " + sessionScore);
            inputField.setEnabled(false); submitBtn.setEnabled(false);
        } else if (currentAttempt >= maxAttempts) {
            gameOver = true;
            messageLabel.setText("✦  The word was:  " + targetWord + "  — Better fortune next time  ✦");
            messageLabel.setForeground(RED_DANGER);
            inputField.setEnabled(false); submitBtn.setEnabled(false);
        } else {
            int left = maxAttempts - currentAttempt;
            messageLabel.setText("Attempt " + currentAttempt + " of " + maxAttempts + "  ·  " + left + " remaining");
            messageLabel.setForeground(TEXT_MUTED);
        }
        inputField.setText("");
        gridPanel.repaint();
    }

    private int[] computeFeedback(String guess, String target) {
        int len = target.length();
        int[] result = new int[len];
        boolean[] tUsed = new boolean[len], gUsed = new boolean[len];
        for (int i=0;i<len;i++) if (guess.charAt(i)==target.charAt(i)) { result[i]=2; tUsed[i]=gUsed[i]=true; }
        for (int i=0;i<len;i++) { if (gUsed[i]) continue;
            for (int j=0;j<len;j++) { if (tUsed[j]) continue;
                if (guess.charAt(i)==target.charAt(j)) { result[i]=1; tUsed[j]=true; break; } } }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  HANGMAN PANEL
    // ═══════════════════════════════════════════════════════════════════════════
    class HangmanPanel extends JPanel {
        HangmanPanel() {
            setOpaque(false);
            setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GOLD_DIM, 1),
                BorderFactory.createEmptyBorder(10,10,10,10)
            ));
        }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D)g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(new Color(14,17,28));
            g2.fillRoundRect(0,0,getWidth(),getHeight(),10,10);

            int wrongs = 0;
            for (int[] fb : feedbackList) {
                boolean allC = true; for (int f:fb) if(f!=2){allC=false;break;} if(!allC) wrongs++;
            }

            int w = getWidth(), h = getHeight();

            // Scaffold (gold toned)
            g2.setColor(GOLD_DIM);
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(w/6, h-22, 5*w/6, h-22);
            g2.drawLine(w/3, h-22, w/3, 18);
            g2.drawLine(w/3, 18, 2*w/3+2, 18);
            g2.setColor(new Color(160,130,60));
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine(2*w/3, 18, 2*w/3, h/5+2);

            int cx = 2*w/3, headTop = h/5, headR = w/9;
            Color bodyColor = wrongs>=6 ? new Color(200,70,70,230) : new Color(200,200,215,210);
            g2.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(bodyColor);

            if (wrongs>=1) {
                g2.drawOval(cx-headR, headTop, headR*2, headR*2);
                if (wrongs>=6 && gameOver) {
                    g2.setStroke(new BasicStroke(2f));
                    g2.fillOval(cx-headR/2-2, headTop+headR-5, 4, 4);
                    g2.fillOval(cx+headR/2-2, headTop+headR-5, 4, 4);
                    g2.drawArc(cx-headR/3, headTop+headR+4, headR*2/3, headR/3, 0, -180);
                    g2.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                }
            }
            if (wrongs>=2) g2.drawLine(cx, headTop+headR*2, cx, headTop+headR*2+h/4);
            if (wrongs>=3) g2.drawLine(cx, headTop+headR*2+h/10, cx-w/6, headTop+headR*2+h/5+h/10);
            if (wrongs>=4) g2.drawLine(cx, headTop+headR*2+h/10, cx+w/6, headTop+headR*2+h/5+h/10);
            if (wrongs>=5) g2.drawLine(cx, headTop+headR*2+h/4, cx-w/6, headTop+headR*2+h/4+h/5);
            if (wrongs>=6) g2.drawLine(cx, headTop+headR*2+h/4, cx+w/6, headTop+headR*2+h/4+h/5);

            // Attempts counter
            g2.setFont(new Font("Serif", Font.ITALIC, 11));
            g2.setColor(wrongs >= 4 ? RED_DANGER : GOLD_DIM);
            String attTxt = wrongs + " / " + maxAttempts + " wrong";
            g2.drawString(attTxt, w/2 - g2.getFontMetrics().stringWidth(attTxt)/2, h-5);

            // Panel title
            g2.setFont(new Font("Serif", Font.ITALIC | Font.BOLD, 12));
            g2.setColor(GOLD_DIM);
            String t = "The Gallows";
            g2.drawString(t, w/2 - g2.getFontMetrics().stringWidth(t)/2, 14);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════════════════════
    private JButton makeFancyButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0,0,getWidth(),getHeight(),8,8);
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Serif", Font.BOLD, 14));
        btn.setForeground(fg);
        btn.setBackground(bg);
        btn.setContentAreaFilled(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(fg, 1),
            BorderFactory.createEmptyBorder(7,14,7,14)
        ));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new MouseAdapter() {
            Color origBg = bg;
            public void mouseEntered(MouseEvent e) { btn.setBackground(origBg.brighter()); }
            public void mouseExited(MouseEvent e)  { btn.setBackground(origBg); }
        });
        return btn;
    }

    private JButton makeLinkButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Serif", Font.ITALIC, 13));
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

    private JLabel makeOrnamentLabel(String text) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER);
        lbl.setFont(new Font("Serif", Font.PLAIN, 16));
        lbl.setForeground(GOLD_DIM);
        lbl.setAlignmentX(CENTER_ALIGNMENT);
        return lbl;
    }

    // ─── Gradient background ──────────────────────────────────────────────────
    static class GradientPanel extends JPanel {
        private final Color c1, c2;
        GradientPanel(Color c1, Color c2) { this.c1=c1; this.c2=c2; setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D)g;
            g2.setPaint(new GradientPaint(0,0,c1, 0,getHeight(),c2));
            g2.fillRect(0,0,getWidth(),getHeight());
            super.paintComponent(g);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  MAIN
    // ═══════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(LexiGuess::new);
    }
    
}