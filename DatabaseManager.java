import java.sql.*;

/**
 * DatabaseManager — SQLite persistence layer for LexiGuess.
 *
 * Tables
 * ──────
 *   players        (id, name, created_at)
 *   leaderboard    (id, player_id, score, stage, recorded_at)
 *   level_progress (id, player_id, difficulty, highest_unlocked)
 *
 * Requires sqlite-jdbc on the class-path.
 * Download: https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.3.0/sqlite-jdbc-3.45.3.0.jar
 * Compile : javac -cp .:sqlite-jdbc.jar DatabaseManager.java LexiGuess.java
 * Run     : java  -cp .:sqlite-jdbc.jar LexiGuess
 */
public class DatabaseManager {

    // ── Database file path (created next to the .jar / working directory) ────
    private static final String DB_URL = "jdbc:sqlite:lexiguess.db";

    private Connection conn;

    // ═══════════════════════════════════════════════════════════════════════════
    //  LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Opens (or creates) the database and ensures all tables exist.
     * Call once at application start-up.
     */
    public void connect() {
        try {
            // Ensure the JDBC driver is loaded
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(DB_URL);

            // Keep WAL mode so reads never block writes
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL;");
                st.execute("PRAGMA foreign_keys=ON;");
            }

            createTables();
            System.out.println("[DB] Connected to " + DB_URL);
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] sqlite-jdbc driver not found. "
                + "Add sqlite-jdbc.jar to your classpath.");
        } catch (SQLException e) {
            System.err.println("[DB] Connection error: " + e.getMessage());
        }
    }

    /** Cleanly closes the database connection. Call on application exit. */
    public void disconnect() {
        if (conn != null) {
            try {
                conn.close();
                System.out.println("[DB] Connection closed.");
            } catch (SQLException e) {
                System.err.println("[DB] Error closing connection: " + e.getMessage());
            }
        }
    }

    /** Returns true when the connection is live and usable. */
    public boolean isConnected() {
        try {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  SCHEMA
    // ═══════════════════════════════════════════════════════════════════════════

    private void createTables() throws SQLException {
        try (Statement st = conn.createStatement()) {

            // ── players ───────────────────────────────────────────────────────
            st.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    name       TEXT    NOT NULL UNIQUE COLLATE NOCASE,
                    created_at TEXT    NOT NULL DEFAULT (datetime('now'))
                );
            """);

            // ── leaderboard ───────────────────────────────────────────────────
            // One row per player; updated in-place when a higher score is achieved.
            st.execute("""
                CREATE TABLE IF NOT EXISTS leaderboard (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_id   INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                    score       INTEGER NOT NULL DEFAULT 0,
                    stage       INTEGER NOT NULL DEFAULT 1,
                    recorded_at TEXT    NOT NULL DEFAULT (datetime('now'))
                );
            """);

            // ── level_progress ────────────────────────────────────────────────
            // One row per (player, difficulty); highest_unlocked = next playable level index.
            st.execute("""
                CREATE TABLE IF NOT EXISTS level_progress (
                    id               INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_id        INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                    difficulty       TEXT    NOT NULL CHECK(difficulty IN ('EASY','MEDIUM','HARD')),
                    highest_unlocked INTEGER NOT NULL DEFAULT 1,
                    UNIQUE(player_id, difficulty)
                );
            """);
        }
        System.out.println("[DB] Tables verified / created.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PLAYERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the player's ID, creating a new row if this name has never been seen.
     *
     * @param name  The player's display name (case-insensitive uniqueness).
     * @return      The player's integer ID, or -1 on error.
     */
    public int getOrCreatePlayer(String name) {
        if (!isConnected()) return -1;
        try {
            // Try to find existing player
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM players WHERE name = ? COLLATE NOCASE")) {
                ps.setString(1, name);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getInt("id");
            }

            // Create new player
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO players (name) VALUES (?)")) {
                ps.setString(1, name);
                ps.executeUpdate();
            }

            // Retrieve the new ID using last_insert_rowid()
            try (Statement st = conn.createStatement()) {
                ResultSet rs = st.executeQuery("SELECT last_insert_rowid()");
                if (rs.next()) {
                    int id = rs.getInt(1);
                    System.out.println("[DB] New player '" + name + "' created with id=" + id);
                    return id;
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] getOrCreatePlayer error: " + e.getMessage());
        }
        return -1;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  LEADERBOARD
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Saves a player's score.  If a row already exists for this player and the
     * new score is higher, the existing row is updated.  Lower scores are ignored.
     *
     * @param playerName  Display name (used to look up / create the player row).
     * @param score       Session score to save.
     * @param stage       Stage reached in this session.
     */
    public void saveScore(String playerName, int score, int stage) {
        if (!isConnected()) return;
        int playerId = getOrCreatePlayer(playerName);
        if (playerId == -1) return;

        try {
            // Check whether a row already exists for this player
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id, score FROM leaderboard WHERE player_id = ?")) {
                ps.setInt(1, playerId);
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int existingScore = rs.getInt("score");
                    if (score > existingScore) {
                        // Update to the better score
                        try (PreparedStatement upd = conn.prepareStatement(
                                "UPDATE leaderboard SET score=?, stage=?, recorded_at=datetime('now') WHERE player_id=?")) {
                            upd.setInt(1, score);
                            upd.setInt(2, stage);
                            upd.setInt(3, playerId);
                            upd.executeUpdate();
                            System.out.println("[DB] Leaderboard updated for '" + playerName
                                + "': " + existingScore + " -> " + score);
                        }
                    }
                } else {
                    // Insert first entry
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO leaderboard (player_id, score, stage) VALUES (?,?,?)")) {
                        ins.setInt(1, playerId);
                        ins.setInt(2, score);
                        ins.setInt(3, stage);
                        ins.executeUpdate();
                        System.out.println("[DB] Leaderboard entry created for '"
                            + playerName + "' score=" + score);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[DB] saveScore error: " + e.getMessage());
        }
    }

    /**
     * Returns the top {@code limit} leaderboard entries sorted by score descending.
     *
     * @param limit  Maximum number of rows to return (e.g. 10).
     * @return       Array of {@link LeaderboardRow}; empty array on error.
     */
    public LeaderboardRow[] getTopScores(int limit) {
        if (!isConnected()) return new LeaderboardRow[0];
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT p.name, l.score, l.stage, l.recorded_at
                FROM leaderboard l
                JOIN players p ON p.id = l.player_id
                ORDER BY l.score DESC
                LIMIT ?
            """)) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();

            java.util.List<LeaderboardRow> rows = new java.util.ArrayList<>();
            while (rs.next()) {
                rows.add(new LeaderboardRow(
                    rs.getString("name"),
                    rs.getInt("score"),
                    rs.getInt("stage"),
                    rs.getString("recorded_at")));
            }
            return rows.toArray(new LeaderboardRow[0]);
        } catch (SQLException e) {
            System.err.println("[DB] getTopScores error: " + e.getMessage());
            return new LeaderboardRow[0];
        }
    }

    /** Wipes all leaderboard rows (keeps player records intact). */
    public void clearLeaderboard() {
        if (!isConnected()) return;
        try (Statement st = conn.createStatement()) {
            st.execute("DELETE FROM leaderboard;");
            System.out.println("[DB] Leaderboard cleared.");
        } catch (SQLException e) {
            System.err.println("[DB] clearLeaderboard error: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  LEVEL PROGRESS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the highest unlocked level for this player + difficulty.
     * Defaults to 1 (first level) if no progress row exists yet.
     *
     * @param playerName  Display name of the player.
     * @param difficulty  One of "EASY", "MEDIUM", "HARD".
     * @return            The highest level the player may enter.
     */
    public int getProgress(String playerName, String difficulty) {
        if (!isConnected()) return 1;
        int playerId = getOrCreatePlayer(playerName);
        if (playerId == -1) return 1;

        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT highest_unlocked FROM level_progress WHERE player_id=? AND difficulty=?")) {
            ps.setInt(1, playerId);
            ps.setString(2, difficulty.toUpperCase());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("highest_unlocked");
        } catch (SQLException e) {
            System.err.println("[DB] getProgress error: " + e.getMessage());
        }
        return 1; // default: only level 1 unlocked
    }

    /**
     * Persists a player's level progress, but only if it's an improvement.
     *
     * @param playerName      Display name of the player.
     * @param difficulty      One of "EASY", "MEDIUM", "HARD".
     * @param highestUnlocked The new highest unlocked level to record.
     */
    public void saveProgress(String playerName, String difficulty, int highestUnlocked) {
        if (!isConnected()) return;
        int playerId = getOrCreatePlayer(playerName);
        if (playerId == -1) return;

        try {
            // UPSERT: insert or update, but only raise the value, never lower it
            try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO level_progress (player_id, difficulty, highest_unlocked)
                    VALUES (?, ?, ?)
                    ON CONFLICT(player_id, difficulty) DO UPDATE
                        SET highest_unlocked = MAX(excluded.highest_unlocked, highest_unlocked)
                """)) {
                ps.setInt(1, playerId);
                ps.setString(2, difficulty.toUpperCase());
                ps.setInt(3, highestUnlocked);
                ps.executeUpdate();
                System.out.println("[DB] Progress saved: " + playerName
                    + " / " + difficulty + " -> level " + highestUnlocked);
            }
        } catch (SQLException e) {
            System.err.println("[DB] saveProgress error: " + e.getMessage());
        }
    }

    /**
     * Loads all three difficulties' progress for a player into a Map.
     * Convenience method so LexiGuess can restore {@code difficultyProgress} in one call.
     *
     * @param playerName  Display name of the player.
     * @return            Map of difficulty -> highest_unlocked (always contains all 3 keys).
     */
    public java.util.Map<String, Integer> loadAllProgress(String playerName) {
        java.util.Map<String, Integer> map = new java.util.HashMap<>();
        map.put("EASY",   getProgress(playerName, "EASY"));
        map.put("MEDIUM", getProgress(playerName, "MEDIUM"));
        map.put("HARD",   getProgress(playerName, "HARD"));
        return map;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  DATA CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    /** A single leaderboard row returned by {@link #getTopScores}. */
    public static class LeaderboardRow {
        public final String name;
        public final int    score;
        public final int    stage;
        public final String recordedAt;

        LeaderboardRow(String name, int score, int stage, String recordedAt) {
            this.name       = name;
            this.score      = score;
            this.stage      = stage;
            this.recordedAt = recordedAt;
        }

        @Override
        public String toString() {
            return name + "  score=" + score + "  stage=" + stage + "  @" + recordedAt;
        }
    }
}