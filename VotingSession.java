import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class VotingSession {

    private String sessionTitle;
    private long startTime;

    private boolean isActive;
    private boolean isFromDatabase;

    private Map<String, Integer> voteData;
    private Map<String, String> candidateImages;

    public VotingSession(String title, String[] candidates, String[] imagePaths) {
        this.sessionTitle = title;
        this.voteData = new LinkedHashMap<>();
        this.candidateImages = new LinkedHashMap<>();

        this.startTime = System.currentTimeMillis();
        this.isActive = true;
        this.isFromDatabase = false;

        this.isFromDatabase = false;

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

    public synchronized void addVote(String candidateName) {
        if (!isActive) {
            System.out.println("⚠️ REJECTED: Sesi sudah ditutup.");
            return;
        }

        if (isFromDatabase) {
            System.out.println("⚠️ REJECTED: Tidak bisa mengubah data arsip database!");
            return;
        }

        if (voteData.containsKey(candidateName)) {
            voteData.put(candidateName, voteData.get(candidateName) + 1);
            System.out.println("🗳️ VOTE LOG: " + candidateName + " +1");
        }
    }

    public void addVoteUnsafe(String candidateName) {
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

    public synchronized void resetVotes() {
        for (String key : voteData.keySet()) {
            voteData.put(key, 0);
        }
        System.out.println("🔄 VOTES RESET: " + sessionTitle);
    }

    public void endSession() {
        this.isActive = false;
        System.out.println("🏁 SESSION ENDED: " + sessionTitle);
    }

    public boolean isActive() {
        return isActive;
    }

    public boolean isFromDatabase() {
        return isFromDatabase;
    }

    public void forceEndSession() {
        this.isActive = false;
        this.isFromDatabase = true;
    }

    public void overwriteStartTime(long timestamp) {
        this.startTime = timestamp;
    }

    public void setVoteCountManual(String candidateName, int count) {
        if (voteData.containsKey(candidateName)) {
            voteData.put(candidateName, count);
        }
    }

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

    public String getVoteSummary() {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, Integer> entry : voteData.entrySet()) {
            if (count > 0) {
                sb.append(",");
            }
            sb.append(entry.getKey()).append(":").append(entry.getValue());
            count++;
        }
        return sb.toString();
    }

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

    public synchronized boolean updateCandidateName(String oldName, String newName) {
        if (!voteData.containsKey(oldName) || voteData.containsKey(newName)) {
            return false;
        }

        int votes = voteData.remove(oldName);
        String img = candidateImages.remove(oldName);

        voteData.put(newName, votes);
        candidateImages.put(newName, img);

        return true;
    }

    public synchronized void updateCandidateImage(String name, String newPath) {
        if (candidateImages.containsKey(name)) {
            candidateImages.put(name, newPath);
        }
    }

    public synchronized void removeCandidate(String name) {
        if (voteData.containsKey(name)) {
            voteData.remove(name);
            candidateImages.remove(name);
        }
    }

    public synchronized boolean addCandidate(String name, String imagePath) {
        if (voteData.containsKey(name))
            return false;

        voteData.put(name, 0);
        candidateImages.put(name, imagePath);
        return true;
    }
}