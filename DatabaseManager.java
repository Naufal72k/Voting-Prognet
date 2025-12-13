import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/evoting_history_db";
    private static final String USER = "root";
    private static final String PASS = "";

    public static void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS)) {
            if (conn != null) {
                Statement stmt = conn.createStatement();

                String sqlSessions = "CREATE TABLE IF NOT EXISTS sessions ("
                        + "id INT PRIMARY KEY AUTO_INCREMENT,"
                        + "title VARCHAR(255) NOT NULL,"
                        + "timestamp BIGINT NOT NULL,"
                        + "total_votes INT"
                        + ");";
                stmt.execute(sqlSessions);

                String sqlCandidates = "CREATE TABLE IF NOT EXISTS candidates ("
                        + "id INT PRIMARY KEY AUTO_INCREMENT,"
                        + "session_id INT,"
                        + "name VARCHAR(255) NOT NULL,"
                        + "image_path VARCHAR(255),"
                        + "vote_count INT,"
                        + "FOREIGN KEY(session_id) REFERENCES sessions(id) ON DELETE CASCADE"
                        + ");";
                stmt.execute(sqlCandidates);

                System.out.println("✅ Database MySQL terhubung & tabel siap.");
            }
        } catch (SQLException e) {
            System.err.println("❌ Gagal inisialisasi database. Pastikan XAMPP/MySQL aktif!");
            e.printStackTrace();
        }
    }

    public static void saveSession(VotingSession session) {
        String sqlSession = "INSERT INTO sessions(title, timestamp, total_votes) VALUES(?,?,?)";
        String sqlCandidate = "INSERT INTO candidates(session_id, name, image_path, vote_count) VALUES(?,?,?,?)";

        Connection conn = null;
        try {
            conn = DriverManager.getConnection(DB_URL, USER, PASS);
            conn.setAutoCommit(false);

            PreparedStatement pstmtSession = conn.prepareStatement(sqlSession, Statement.RETURN_GENERATED_KEYS);
            pstmtSession.setString(1, session.getTitle());
            pstmtSession.setLong(2, session.getStartTime());
            pstmtSession.setInt(3, session.getTotalVotes());
            pstmtSession.executeUpdate();

            int sessionId = -1;
            ResultSet rs = pstmtSession.getGeneratedKeys();
            if (rs.next()) {
                sessionId = rs.getInt(1);
            }

            if (sessionId != -1) {
                PreparedStatement pstmtCand = conn.prepareStatement(sqlCandidate);
                Map<String, Integer> votes = session.getAllData();
                Map<String, String> images = session.getAllImages();

                for (String name : votes.keySet()) {
                    pstmtCand.setInt(1, sessionId);
                    pstmtCand.setString(2, name);

                    String path = (images != null) ? images.getOrDefault(name, "") : "";
                    pstmtCand.setString(3, path);

                    pstmtCand.setInt(4, votes.get(name));
                    pstmtCand.addBatch();
                }
                pstmtCand.executeBatch();
            }

            conn.commit();
            System.out.println("✅ Sesi '" + session.getTitle() + "' tersimpan.");

        } catch (SQLException e) {
            System.err.println("❌ Gagal simpan sesi: " + e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
        } finally {
            try {
                if (conn != null)
                    conn.close();
            } catch (SQLException e) {
            }
        }
    }

    public static List<VotingSession> getAllHistory() {
        List<VotingSession> historyList = new ArrayList<>();

        String sqlSelectSessions = "SELECT * FROM sessions ORDER BY timestamp DESC";

        String sqlSelectCandidates = "SELECT * FROM candidates WHERE session_id = ? ORDER BY id ASC";

        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASS);
                Statement stmt = conn.createStatement();
                ResultSet rsSessions = stmt.executeQuery(sqlSelectSessions)) {

            while (rsSessions.next()) {
                int id = rsSessions.getInt("id");
                String title = rsSessions.getString("title");
                long timestamp = rsSessions.getLong("timestamp");

                PreparedStatement pstmtCand = conn.prepareStatement(sqlSelectCandidates);
                pstmtCand.setInt(1, id);
                ResultSet rsCand = pstmtCand.executeQuery();

                List<String> candidateNames = new ArrayList<>();
                List<String> candidateImages = new ArrayList<>();
                List<Integer> candidateVotes = new ArrayList<>();

                while (rsCand.next()) {
                    String name = rsCand.getString("name");
                    String imgPath = rsCand.getString("image_path");
                    int votes = rsCand.getInt("vote_count");

                    candidateNames.add(name);
                    candidateImages.add(imgPath == null ? "" : imgPath);
                    candidateVotes.add(votes);
                }

                String[] arrNames = candidateNames.toArray(new String[0]);
                String[] arrImages = candidateImages.toArray(new String[0]);

                VotingSession session = new VotingSession(title, arrNames, arrImages);

                session.forceEndSession();
                session.overwriteStartTime(timestamp);

                for (int i = 0; i < arrNames.length; i++) {
                    session.setVoteCountManual(arrNames[i], candidateVotes.get(i));
                }

                historyList.add(session);
            }

        } catch (SQLException e) {
            System.err.println("⚠️ Gagal load history: " + e.getMessage());
        }

        return historyList;
    }
}