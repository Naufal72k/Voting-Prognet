import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * =============================================================================
 * 📦 VOTING SESSION (DATA MODEL)
 * =============================================================================
 * Merepresentasikan satu sesi pemilihan (Batch).
 * * UPDATE TERBARU:
 * - Menambahkan Status Sesi (Active/Ended).
 * - Menambahkan Logika penguncian suara jika sesi berakhir.
 * - Menambahkan Logika penentuan Pemenang (Winner Calculation).
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
    private boolean isActive; // TRUE = Sedang Berjalan, FALSE = Selesai

    // LinkedHashMap menjaga urutan kandidat sesuai input awal
    private Map<String, Integer> voteData;

    /**
     * Constructor Utama.
     * Dipanggil saat Admin membuat sesi baru.
     */
    public VotingSession(String title, String[] candidates) {
        this.sessionTitle = title;
        this.voteData = new LinkedHashMap<>();

        // Setup Waktu & Status Awal
        this.startTime = System.currentTimeMillis();
        this.isActive = true; // Sesi langsung aktif saat dibuat

        // Default Styles
        this.description = "Sesi Pemungutan Suara Resmi";
        this.themeColor = AppTheme.COLOR_PRIMARY_START;

        // Inisialisasi suara 0 untuk setiap kandidat
        for (String name : candidates) {
            if (name != null && !name.trim().isEmpty()) {
                voteData.put(name.trim(), 0);
            }
        }
    }

    // =========================================================================
    // ⚙️ CORE LOGIC (VOTING)
    // =========================================================================

    /**
     * 🛡️ SAFE METHOD: Menambah suara secara thread-safe.
     * Hanya menerima suara jika sesi masih AKTIF (isActive == true).
     */
    public synchronized void addVote(String candidateName) {
        // Cek Status: Jika sudah selesai, tolak suara.
        if (!isActive) {
            System.out.println("⚠️ REJECTED: Sesi sudah ditutup.");
            return;
        }

        if (voteData.containsKey(candidateName)) {
            voteData.put(candidateName, voteData.get(candidateName) + 1);
            System.out.println("🗳️ VOTE LOG: " + candidateName + " +1");
        }
    }

    /**
     * 💀 UNSAFE METHOD: Chaos Mode.
     * Tetap mengecek isActive agar Stress Test berhenti saat sesi di-stop admin.
     */
    public void addVoteUnsafe(String candidateName) {
        if (!isActive)
            return; // Tolak jika sesi mati

        if (voteData.containsKey(candidateName)) {
            int currentVotes = voteData.get(candidateName);
            try {
                // Simulasi Race Condition (Jeda)
                Thread.sleep((long) (Math.random() * 10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            voteData.put(candidateName, currentVotes + 1);
            System.out.println("💀 CHAOS VOTE: " + candidateName + " +1 (Unsafe)");
        }
    }

    // =========================================================================
    // 🛑 SESSION MANAGEMENT
    // =========================================================================

    /**
     * Mengakhiri sesi pemilihan (Finalisasi).
     * Setelah dipanggil, suara tidak bisa masuk lagi.
     */
    public void endSession() {
        this.isActive = false;
        this.endTime = System.currentTimeMillis();
        System.out.println("🏁 SESSION ENDED: " + sessionTitle);
    }

    /**
     * Mengecek apakah sesi masih berjalan.
     */
    public boolean isActive() {
        return isActive;
    }

    // =========================================================================
    // 🏆 WINNER CALCULATION LOGIC
    // =========================================================================

    /**
     * Mengembalikan String info pemenang.
     * Contoh: "Budi (50 Suara)" atau "SERI (20 Suara)"
     */
    public String getWinnerResult() {
        if (voteData.isEmpty())
            return "Tidak ada data";

        String winnerName = "-";
        int maxVotes = -1;
        boolean isTie = false;

        // Loop cari nilai tertinggi
        for (Map.Entry<String, Integer> entry : voteData.entrySet()) {
            int votes = entry.getValue();

            if (votes > maxVotes) {
                maxVotes = votes;
                winnerName = entry.getKey();
                isTie = false; // Reset status seri karena ada record baru tertinggi
            } else if (votes == maxVotes && maxVotes > 0) {
                isTie = true; // Ada nilai yang sama tingginya
            }
        }

        if (maxVotes == 0)
            return "Belum ada suara";
        if (isTie)
            return "Hasl Seri / Draw (" + maxVotes + " Suara)";

        return winnerName + " (" + maxVotes + " Suara)";
    }

    // =========================================================================
    // 🎨 SETTERS & GETTERS
    // =========================================================================

    public void setDescription(String description) {
        this.description = description;
    }

    public void setThemeColor(Color color) {
        this.themeColor = color;
    }

    public String getTitle() {
        return sessionTitle;
    }

    public String getDescription() {
        return description;
    }

    public Color getThemeColor() {
        return themeColor;
    }

    public Set<String> getCandidates() {
        return voteData.keySet();
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

    public long getEndTime() {
        return endTime;
    }

    /**
     * Mengembalikan salinan data agar data asli aman.
     */
    public Map<String, Integer> getAllData() {
        return new LinkedHashMap<>(voteData);
    }

    @Override
    public String toString() {
        String status = isActive ? " [LIVE]" : " [ENDED]";
        return sessionTitle + status + " - " + getTotalVotes() + " Suara";
    }
}