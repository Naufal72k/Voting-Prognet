import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * =============================================================================
 * 📦 VOTING SESSION (DATA MODEL) - V3.2 ENHANCED
 * =============================================================================
 * UPDATE FITUR V3.2:
 * - Menambahkan metode `getVoteSummary()` untuk menghasilkan string statistik
 * yang akan dikirim ke Client (Format: "Nama:Suara,Nama:Suara").
 */
public class VotingSession {

    // =========================================================================
    // 🧠 DATA FIELDS
    // =========================================================================
    private String sessionTitle;
    private String description;
    private Color themeColor;

    // Waktu & Status
    private long startTime;
    private long endTime;

    // --- IDENTITY MANAGEMENT (Goals 1) ---
    private boolean isActive; // TRUE = Sedang Berjalan (Live)
    private boolean isFromDatabase; // TRUE = Data Arsip dari MySQL (Read-Only)

    // LinkedHashMap menjaga urutan kandidat sesuai input awal
    private Map<String, Integer> voteData;
    private Map<String, String> candidateImages;

    /**
     * Constructor Utama.
     * Membuat Sesi BARU (Live).
     */
    public VotingSession(String title, String[] candidates, String[] imagePaths) {
        this.sessionTitle = title;
        this.voteData = new LinkedHashMap<>();
        this.candidateImages = new LinkedHashMap<>();

        // Default State untuk Sesi Baru
        this.startTime = System.currentTimeMillis();
        this.isActive = true;
        this.isFromDatabase = false; // Default: Bukan dari DB (Live Session)

        this.description = "Sesi Pemungutan Suara Resmi";
        this.themeColor = AppTheme.COLOR_PRIMARY_START;

        if (candidates != null) {
            for (int i = 0; i < candidates.length; i++) {
                String name = candidates[i].trim();
                String path = (imagePaths != null && i < imagePaths.length) ? imagePaths[i] : "";

                if (!name.isEmpty()) {
                    voteData.put(name, 0);
                    candidateImages.put(name, path);
                }
            }
        }
    }

    // =========================================================================
    // ⚙️ CORE LOGIC (VOTING)
    // =========================================================================

    /**
     * Menambah suara dengan proteksi penuh.
     */
    public synchronized void addVote(String candidateName) {
        // 1. Cek apakah sesi masih aktif
        if (!isActive) {
            System.out.println("⚠️ REJECTED: Sesi sudah ditutup.");
            return;
        }

        // 2. Cek apakah ini data arsip (Goals 1)
        if (isFromDatabase) {
            System.out.println("⚠️ REJECTED: Tidak bisa mengubah data arsip database!");
            return;
        }

        // 3. Tambahkan suara
        if (voteData.containsKey(candidateName)) {
            voteData.put(candidateName, voteData.get(candidateName) + 1);
            System.out.println("🗳️ VOTE LOG: " + candidateName + " +1");
        }
    }

    public void addVoteUnsafe(String candidateName) {
        // Proteksi juga diterapkan di mode unsafe
        if (!isActive || isFromDatabase)
            return;

        if (voteData.containsKey(candidateName)) {
            int currentVotes = voteData.get(candidateName);
            try {
                Thread.sleep((long) (Math.random() * 10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            voteData.put(candidateName, currentVotes + 1);
        }
    }

    // =========================================================================
    // 🛑 SESSION MANAGEMENT
    // =========================================================================

    /**
     * MERESET semua suara menjadi 0.
     * Fitur ini digunakan khusus untuk Stress Test mode "No Save"
     * agar bisa melakukan simulasi berulang kali tanpa restart server.
     */
    public synchronized void resetVotes() {
        for (String key : voteData.keySet()) {
            voteData.put(key, 0);
        }
        System.out.println("🔄 VOTES RESET: " + sessionTitle);
    }

    /**
     * Digunakan oleh Admin untuk MENGHENTIKAN sesi Live.
     */
    public void endSession() {
        this.isActive = false;
        this.endTime = System.currentTimeMillis();
        // isFromDatabase tetap FALSE karena ini baru saja selesai, belum tentu
        // tersimpan
        System.out.println("🏁 SESSION ENDED: " + sessionTitle);
    }

    public boolean isActive() {
        return isActive;
    }

    /**
     * Mengembalikan status apakah sesi ini adalah arsip dari database.
     */
    public boolean isFromDatabase() {
        return isFromDatabase;
    }

    // =========================================================================
    // 🛠️ DATABASE RESTORATION HELPERS
    // =========================================================================

    /**
     * Dipanggil oleh DatabaseManager saat me-load riwayat.
     * Mengunci objek ini sebagai ARSIP (Read-Only).
     */
    public void forceEndSession() {
        this.isActive = false;
        this.isFromDatabase = true; // Menandai sebagai Arsip DB
    }

    public void overwriteStartTime(long timestamp) {
        this.startTime = timestamp;
    }

    public void setVoteCountManual(String candidateName, int count) {
        if (voteData.containsKey(candidateName)) {
            voteData.put(candidateName, count);
        }
    }

    // =========================================================================
    // 🏆 WINNER & STATS CALCULATION LOGIC
    // =========================================================================

    public String getWinnerResult() {
        if (voteData.isEmpty())
            return "Tidak ada data";

        String winnerName = "-";
        int maxVotes = -1;
        boolean isTie = false;

        for (Map.Entry<String, Integer> entry : voteData.entrySet()) {
            int votes = entry.getValue();

            if (votes > maxVotes) {
                maxVotes = votes;
                winnerName = entry.getKey();
                isTie = false;
            } else if (votes == maxVotes && maxVotes > 0) {
                isTie = true;
            }
        }

        if (maxVotes == 0)
            return "Belum ada suara";
        if (isTie)
            return "Seri / Draw (" + maxVotes + " Suara)";

        return winnerName + " (" + maxVotes + " Suara)";
    }

    /**
     * NEW V3.2: Menghasilkan string ringkasan untuk detail suara.
     * Format: "Nama1:10,Nama2:5,Nama3:20"
     */
    public String getVoteSummary() {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, Integer> entry : voteData.entrySet()) {
            if (count > 0) {
                sb.append(","); // Separator antar kandidat
            }
            // Format: Nama:JumlahSuara
            sb.append(entry.getKey()).append(":").append(entry.getValue());
            count++;
        }
        return sb.toString();
    }

    // =========================================================================
    // 🎨 GETTERS
    // =========================================================================

    public String getTitle() {
        return sessionTitle;
    }

    public Set<String> getCandidates() {
        return voteData.keySet();
    }

    public String getCandidateImage(String name) {
        return candidateImages.getOrDefault(name, "");
    }

    public int getVoteCount(String candidateName) {
        return voteData.getOrDefault(candidateName, 0);
    }

    public int getTotalVotes() {
        int total = 0;
        for (int count : voteData.values()) {
            total += count;
        }
        return total;
    }

    public long getStartTime() {
        return startTime;
    }

    public Map<String, Integer> getAllData() {
        return new LinkedHashMap<>(voteData);
    }

    public Map<String, String> getAllImages() {
        return new LinkedHashMap<>(candidateImages);
    }
}