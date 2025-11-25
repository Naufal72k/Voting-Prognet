import java.awt.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

/**
 * =============================================================================
 * 🖥️ SERVER ADMIN: MODERN DASHBOARD (UPDATED)
 * =============================================================================
 * UPDATE FITUR:
 * - Tombol "AKHIRI SESI" (End Vote) di halaman Monitor.
 * - Tabel Dashboard menampilkan Status & Pemenang.
 * - Protokol Socket mengirimkan seluruh riwayat sesi ke Client.
 */
public class ServerAdmin extends JFrame {

    // =========================================================================
    // 🧠 DATA & STATE
    // =========================================================================
    private List<VotingSession> historySessions = new ArrayList<>();
    private VotingSession currentSession; // Sesi yang sedang dipilih/aktif
    private boolean isServerRunning = true;
    private int connectedClients = 0;

    // =========================================================================
    // 🎨 UI COMPONENTS
    // =========================================================================
    private CardLayout contentLayout;
    private JPanel mainContentPanel;

    // Navigasi
    private AppTheme.SidebarButton btnNavDash;
    private AppTheme.SidebarButton btnNavCreate;
    private AppTheme.SidebarButton btnNavMonitor;
    private AppTheme.SidebarButton btnStressTest;

    // Dashboard Components
    private JLabel lblStatTotalVotes, lblStatActiveSession, lblStatClients;
    private DefaultTableModel tableModelHistory;

    // Monitor Components
    private GraphPanel liveGraphPanel;
    private JLabel lblGraphTitle, lblGraphStatus;
    private JButton btnEndVote; // Tombol Baru

    // Create Session Components
    private JTextField txtSessionTitle, txtSessionCandidates;

    public ServerAdmin() {
        setTitle("Admin Dashboard - E-Voting System");
        setSize(1280, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        initSidebar();
        initContentArea();

        new Thread(this::startServer).start();
    }

    // =========================================================================
    // 🏗️ LAYOUT: SIDEBAR (WEST)
    // =========================================================================
    private void initSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(AppTheme.COLOR_SIDEBAR);
        sidebar.setPreferredSize(new Dimension(90, getHeight()));
        sidebar.setBorder(new EmptyBorder(30, 0, 0, 0));

        JLabel lblLogo = new JLabel("🗳️");
        lblLogo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
        lblLogo.setForeground(Color.WHITE);
        lblLogo.setAlignmentX(Component.CENTER_ALIGNMENT);

        btnNavDash = AppTheme.createSidebarButton("🏠", true);
        btnNavCreate = AppTheme.createSidebarButton("➕", false);
        btnNavMonitor = AppTheme.createSidebarButton("📊", false);

        btnNavDash.addActionListener(e -> switchPage("PAGE_DASHBOARD", btnNavDash));
        btnNavCreate.addActionListener(e -> switchPage("PAGE_CREATE", btnNavCreate));
        btnNavMonitor.addActionListener(e -> switchPage("PAGE_MONITOR", btnNavMonitor));

        btnStressTest = AppTheme.createSidebarButton("⚡", false);
        btnStressTest.setForeground(new Color(251, 191, 36));
        btnStressTest.addActionListener(e -> showStressTestDialog());

        sidebar.add(lblLogo);
        sidebar.add(Box.createVerticalStrut(50));
        addSidebarItem(sidebar, btnNavDash);
        addSidebarItem(sidebar, btnNavCreate);
        addSidebarItem(sidebar, btnNavMonitor);
        sidebar.add(Box.createVerticalStrut(30));

        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(40, 2));
        sep.setForeground(new Color(100, 116, 139));
        sidebar.add(sep);
        sidebar.add(Box.createVerticalStrut(20));
        addSidebarItem(sidebar, btnStressTest);

        sidebar.add(Box.createVerticalGlue());
        add(sidebar, BorderLayout.WEST);
    }

    private void addSidebarItem(JPanel sidebar, JComponent item) {
        item.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(item);
        sidebar.add(Box.createVerticalStrut(15));
    }

    // =========================================================================
    // 🏗️ LAYOUT: CONTENT AREA (CENTER)
    // =========================================================================
    private void initContentArea() {
        contentLayout = new CardLayout();
        mainContentPanel = new JPanel(contentLayout);
        mainContentPanel.setBackground(AppTheme.COLOR_BACKGROUND_APP);

        mainContentPanel.add(createPageDashboard(), "PAGE_DASHBOARD");
        mainContentPanel.add(createPageCreateSession(), "PAGE_CREATE");
        mainContentPanel.add(createPageMonitor(), "PAGE_MONITOR");

        add(mainContentPanel, BorderLayout.CENTER);
    }

    private void switchPage(String pageName, AppTheme.SidebarButton activeButton) {
        contentLayout.show(mainContentPanel, pageName);
        btnNavDash.setActive(false);
        btnNavCreate.setActive(false);
        btnNavMonitor.setActive(false);
        btnStressTest.setActive(false);
        activeButton.setActive(true);

        // Refresh data saat pindah halaman
        if (pageName.equals("PAGE_DASHBOARD"))
            updateHistoryTable();
        if (pageName.equals("PAGE_MONITOR"))
            refreshMonitorUI();
    }

    // =========================================================================
    // 📄 PAGE 1: DASHBOARD (UPDATED TABLE)
    // =========================================================================
    private JPanel createPageDashboard() {
        JPanel panel = new JPanel(new BorderLayout(0, 30));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(40, 40, 40, 40));

        JLabel title = new JLabel("Overview System");
        title.setFont(AppTheme.FONT_H1);
        title.setForeground(AppTheme.COLOR_TEXT_MAIN);
        panel.add(title, BorderLayout.NORTH);

        JPanel centerContainer = new JPanel(new BorderLayout(0, 30));
        centerContainer.setOpaque(false);

        JPanel statsGrid = new JPanel(new GridLayout(1, 3, 25, 0));
        statsGrid.setOpaque(false);
        statsGrid.setPreferredSize(new Dimension(0, 140));

        lblStatTotalVotes = new JLabel("0");
        lblStatActiveSession = new JLabel("-");
        lblStatClients = new JLabel("0");

        statsGrid.add(createStatCard("Total Suara (Sesi Ini)", lblStatTotalVotes));
        statsGrid.add(createStatCard("Sesi Terpilih", lblStatActiveSession));
        statsGrid.add(createStatCard("Client Terhubung", lblStatClients));

        // -- UPDATE TABEL: Menambahkan Kolom Status & Pemenang --
        JPanel tablePanel = AppTheme.createShadowPanel();
        tablePanel.setLayout(new BorderLayout());

        JLabel lblTableTitle = new JLabel("Riwayat Sesi");
        lblTableTitle.setFont(AppTheme.FONT_H2);
        lblTableTitle.setBorder(new EmptyBorder(0, 0, 15, 0));

        // Kolom Tabel Diperbarui
        String[] columns = { "ID", "Judul Sesi", "Status", "Pemenang / Hasil", "Total Suara" };
        tableModelHistory = new DefaultTableModel(columns, 0);
        JTable table = new JTable(tableModelHistory);

        table.setRowHeight(40);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(241, 245, 249));
        table.setFont(AppTheme.FONT_BODY);

        JTableHeader header = table.getTableHeader();
        header.setFont(AppTheme.FONT_BOLD);
        header.setBackground(Color.WHITE);
        header.setForeground(AppTheme.COLOR_TEXT_MUTED);
        header.setPreferredSize(new Dimension(0, 45));

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(Color.WHITE);

        tablePanel.add(lblTableTitle, BorderLayout.NORTH);
        tablePanel.add(scrollPane, BorderLayout.CENTER);

        centerContainer.add(statsGrid, BorderLayout.NORTH);
        centerContainer.add(tablePanel, BorderLayout.CENTER);

        panel.add(centerContainer, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createStatCard(String title, JLabel valueLabel) {
        JPanel card = AppTheme.createShadowPanel();
        card.setLayout(new BorderLayout());
        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(AppTheme.FONT_BODY);
        lblTitle.setForeground(AppTheme.COLOR_TEXT_MUTED);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 36));
        valueLabel.setForeground(AppTheme.COLOR_PRIMARY_START);
        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    // =========================================================================
    // 📄 PAGE 2: CREATE SESSION
    // =========================================================================
    private JPanel createPageCreateSession() {
        // ... (Kode sama seperti sebelumnya) ...
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        JPanel formCard = AppTheme.createShadowPanel();
        formCard.setPreferredSize(new Dimension(600, 500));
        formCard.setLayout(new BoxLayout(formCard, BoxLayout.Y_AXIS));

        JLabel lblHead = new JLabel("Buat Sesi Voting Baru");
        lblHead.setFont(AppTheme.FONT_H1);
        lblHead.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtSessionTitle = new JTextField();
        AppTheme.styleTextField(txtSessionTitle);
        txtSessionTitle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));

        txtSessionCandidates = new JTextField();
        AppTheme.styleTextField(txtSessionCandidates);
        txtSessionCandidates.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));

        JButton btnStart = AppTheme.createGradientButton("MULAI SESI SEKARANG", 250, 50);
        btnStart.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnStart.addActionListener(e -> startNewSession());

        // Layouting sederhana
        formCard.add(lblHead);
        formCard.add(Box.createVerticalStrut(40));
        formCard.add(new JLabel("Judul Kegiatan"));
        formCard.add(txtSessionTitle);
        formCard.add(Box.createVerticalStrut(20));
        formCard.add(new JLabel("Kandidat (Pisahkan koma)"));
        formCard.add(txtSessionCandidates);
        formCard.add(Box.createVerticalStrut(40));
        formCard.add(btnStart);

        panel.add(formCard);
        return panel;
    }

    // =========================================================================
    // 📄 PAGE 3: MONITOR (UPDATED: END VOTE BUTTON)
    // =========================================================================
    private JPanel createPageMonitor() {
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(40, 40, 40, 40));

        // -- HEADER: Title + Button --
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JPanel titleBox = new JPanel(new GridLayout(2, 1));
        titleBox.setOpaque(false);

        lblGraphTitle = new JLabel("Menunggu Sesi Dimulai...");
        lblGraphTitle.setFont(AppTheme.FONT_H1);
        lblGraphTitle.setForeground(AppTheme.COLOR_TEXT_MAIN);

        lblGraphStatus = new JLabel("Status: -");
        lblGraphStatus.setFont(AppTheme.FONT_BOLD);
        lblGraphStatus.setForeground(AppTheme.COLOR_TEXT_MUTED);

        titleBox.add(lblGraphTitle);
        titleBox.add(lblGraphStatus);

        // -- TOMBOL END VOTE (Baru) --
        btnEndVote = new JButton("AKHIRI SESI");
        btnEndVote.setFont(AppTheme.FONT_BOLD);
        btnEndVote.setForeground(Color.WHITE);
        btnEndVote.setBackground(new Color(220, 38, 38)); // Merah
        btnEndVote.setFocusPainted(false);
        btnEndVote.setBorder(new EmptyBorder(10, 20, 10, 20));
        btnEndVote.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnEndVote.setVisible(false); // Sembunyi jika belum ada sesi

        btnEndVote.addActionListener(e -> actionEndSession());

        header.add(titleBox, BorderLayout.WEST);
        header.add(btnEndVote, BorderLayout.EAST);

        liveGraphPanel = new GraphPanel();

        panel.add(header, BorderLayout.NORTH);
        panel.add(liveGraphPanel, BorderLayout.CENTER);

        return panel;
    }

    // =========================================================================
    // ⚙️ BUSINESS LOGIC
    // =========================================================================

    private void startNewSession() {
        String title = txtSessionTitle.getText();
        String rawCand = txtSessionCandidates.getText();

        if (title.isEmpty() || rawCand.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Mohon lengkapi judul dan kandidat!");
            return;
        }

        String[] cands = rawCand.split(",");
        // Jika ada sesi lama yang aktif, otomatis diakhiri? Opsional.
        // Di sini kita biarkan, tapi idealnya hanya 1 sesi aktif.

        currentSession = new VotingSession(title, cands);
        historySessions.add(currentSession);

        txtSessionTitle.setText("");
        txtSessionCandidates.setText("");

        // Pindah ke Monitor
        switchPage("PAGE_MONITOR", btnNavMonitor);
        refreshMonitorUI();
        updateHistoryTable();

        JOptionPane.showMessageDialog(this, "Sesi DIBUKA! Client bisa mulai voting.");
    }

    /**
     * LOGIC BARU: Mengakhiri Sesi
     */
    private void actionEndSession() {
        if (currentSession == null || !currentSession.isActive())
            return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Apakah Anda yakin ingin MENGHENTIKAN sesi ini?\nSuara tidak akan bisa masuk lagi.",
                "Konfirmasi Akhiri Sesi", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            currentSession.endSession(); // Kunci Data
            refreshMonitorUI(); // Update UI jadi "Final Result"
            updateHistoryTable(); // Update Tabel Dashboard
            JOptionPane.showMessageDialog(this, "Sesi Telah Ditutup.");
        }
    }

    private void refreshMonitorUI() {
        if (currentSession != null) {
            lblGraphTitle.setText(currentSession.getTitle());
            lblStatActiveSession.setText(currentSession.getTitle());
            lblStatTotalVotes.setText(String.valueOf(currentSession.getTotalVotes()));

            if (currentSession.isActive()) {
                lblGraphStatus.setText("Status: 🟢 LIVE VOTING");
                lblGraphStatus.setForeground(new Color(22, 163, 74)); // Green
                btnEndVote.setVisible(true);
                btnEndVote.setText("AKHIRI SESI");
                btnEndVote.setEnabled(true);
                btnEndVote.setBackground(new Color(220, 38, 38));
            } else {
                lblGraphStatus.setText("Status: 🏁 FINAL RESULT (Sesi Berakhir)");
                lblGraphStatus.setForeground(new Color(220, 38, 38)); // Red
                btnEndVote.setVisible(true);
                btnEndVote.setText("SESI DITUTUP");
                btnEndVote.setEnabled(false);
                btnEndVote.setBackground(Color.GRAY);
            }
        } else {
            lblGraphTitle.setText("Belum ada sesi dipilih");
            lblGraphStatus.setText("-");
            btnEndVote.setVisible(false);
        }
        liveGraphPanel.repaint();
    }

    private void updateHistoryTable() {
        tableModelHistory.setRowCount(0);
        int id = 1;
        // Tampilkan dari yang terbaru (reverse loop opsional, disini normal loop)
        for (VotingSession vs : historySessions) {
            String status = vs.isActive() ? "🟢 Aktif" : "🔴 Selesai";
            String winner = vs.isActive() ? "-" : vs.getWinnerResult(); // Helper baru

            tableModelHistory.addRow(new Object[] {
                    id++,
                    vs.getTitle(),
                    status,
                    winner,
                    vs.getTotalVotes() + " Suara"
            });
        }
    }

    private void updateRealtimeStats() {
        if (currentSession != null) {
            lblStatTotalVotes.setText(String.valueOf(currentSession.getTotalVotes()));
            liveGraphPanel.repaint();
        }
    }

    // =========================================================================
    // 📡 SERVER ENGINE (UPDATED PROTOCOL)
    // =========================================================================
    private void startServer() {
        try (ServerSocket serverSocket = new ServerSocket(AppTheme.SERVER_PORT)) {
            while (isServerRunning) {
                Socket client = serverSocket.accept();
                connectedClients++;
                SwingUtilities.invokeLater(() -> lblStatClients.setText(String.valueOf(connectedClients)));
                new Thread(() -> handleClient(client)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * PROTOKOL KOMUNIKASI BARU
     * 1. Mengirim LIST history sesi (Format: HISTORY|Title;Status;Winner#...)
     * 2. Mengirim SETUP sesi aktif jika ada (Format: SETUP|Title|Candidates)
     */
    private void handleClient(Socket socket) {
        try (
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            // 1. KIRIM DATA HISTORY (Untuk Menu Client)
            StringBuilder sbHistory = new StringBuilder("HISTORY_LIST|");
            for (VotingSession vs : historySessions) {
                // Format per item: Title;IsActive;Winner
                sbHistory.append(vs.getTitle())
                        .append(";")
                        .append(vs.isActive())
                        .append(";")
                        .append(vs.getWinnerResult())
                        .append("#"); // Separator antar sesi
            }
            out.writeUTF(sbHistory.toString());

            // 2. KIRIM SETUP (Jika ada sesi yang AKTIF saja)
            if (currentSession != null && currentSession.isActive()) {
                out.writeUTF(
                        "SETUP|" + currentSession.getTitle() + "|" + String.join(",", currentSession.getCandidates()));
            } else {
                out.writeUTF("WAIT|Tidak ada sesi voting yang sedang aktif.");
            }

            // 3. LISTEN FOR VOTES
            while (true) {
                String msg = in.readUTF(); // Block until msg received
                if (msg.startsWith("VOTE|")) {
                    String candidateName = msg.split("\\|")[1];

                    // Cek null & Active status dilakukan di dalam addVote
                    if (currentSession != null) {
                        currentSession.addVote(candidateName);
                        SwingUtilities.invokeLater(this::updateRealtimeStats);
                    }
                }
            }

        } catch (Exception e) {
            // Client disconnect
        } finally {
            connectedClients--;
            SwingUtilities.invokeLater(() -> lblStatClients.setText(String.valueOf(connectedClients)));
        }
    }

    // =========================================================================
    // 📊 GRAPH & STRESS TEST UTILS
    // =========================================================================

    private void showStressTestDialog() {
        if (currentSession == null || !currentSession.isActive()) {
            JOptionPane.showMessageDialog(this, "⚠️ Tidak ada sesi AKTIF untuk ditest!");
            return;
        }

        Object[] options = { "🛡️ Mode Aman (Sync)", "💀 Mode Rusuh (Chaos)" };
        int choice = JOptionPane.showOptionDialog(this,
                "Pilih Metode Stress Test (100 Request/detik):",
                "Konfigurasi Stress Test",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);

        if (choice != -1)
            performStressTest(choice == 1);
    }

    private void performStressTest(boolean isChaosMode) {
        if (currentSession == null)
            return;
        List<String> candidates = new ArrayList<>(currentSession.getCandidates());

        switchPage("PAGE_MONITOR", btnNavMonitor);
        btnStressTest.setActive(true);
        btnNavMonitor.setActive(false);

        int totalSerangan = 100;
        for (int i = 0; i < totalSerangan; i++) {
            new Thread(() -> {
                String randomCandidate = candidates.get((int) (Math.random() * candidates.size()));
                if (isChaosMode)
                    currentSession.addVoteUnsafe(randomCandidate);
                else
                    currentSession.addVote(randomCandidate);
                SwingUtilities.invokeLater(this::updateRealtimeStats);
            }).start();
        }
    }

    private class GraphPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
            g2.setColor(new Color(0, 0, 0, 10));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 30, 30);

            if (currentSession == null) {
                drawEmptyState(g2);
                return;
            }
            drawChart(g2);
        }

        private void drawEmptyState(Graphics2D g2) {
            g2.setColor(AppTheme.COLOR_TEXT_MUTED);
            g2.setFont(AppTheme.FONT_H2);
            FontMetrics fm = g2.getFontMetrics();
            String txt = "Belum ada data visualisasi.";
            g2.drawString(txt, (getWidth() - fm.stringWidth(txt)) / 2, getHeight() / 2);
        }

        private void drawChart(Graphics2D g2) {
            int w = getWidth();
            int h = getHeight();
            int padding = 60;

            int maxVotes = 1;
            for (int val : currentSession.getAllData().values())
                maxVotes = Math.max(maxVotes, val);

            int numCandidates = currentSession.getCandidates().size();
            int barWidth = Math.min(100, (w - (padding * 2)) / numCandidates - 20);
            int x = padding;
            int groundY = h - padding;

            for (String cand : currentSession.getCandidates()) {
                int votes = currentSession.getVoteCount(cand);
                int barHeight = (int) (((double) votes / maxVotes) * (h - padding * 3));
                if (votes == 0)
                    barHeight = 4;

                int y = groundY - barHeight;
                GradientPaint gp = new GradientPaint(x, y, AppTheme.COLOR_PRIMARY_START, x, y + barHeight,
                        AppTheme.COLOR_PRIMARY_END);
                g2.setPaint(gp);
                g2.fillRoundRect(x, y, barWidth, barHeight, 15, 15);

                g2.setColor(AppTheme.COLOR_TEXT_MAIN);
                g2.setFont(AppTheme.FONT_H2);
                String voteStr = String.valueOf(votes);
                g2.drawString(voteStr, x + (barWidth - g2.getFontMetrics().stringWidth(voteStr)) / 2, y - 10);

                g2.setColor(AppTheme.COLOR_TEXT_MUTED);
                g2.setFont(AppTheme.FONT_BOLD);
                String nameStr = cand;
                if (nameStr.length() > 10)
                    nameStr = nameStr.substring(0, 8) + "..";
                g2.drawString(nameStr, x + (barWidth - g2.getFontMetrics().stringWidth(nameStr)) / 2, groundY + 25);

                x += barWidth + 30;
            }
            g2.setColor(new Color(226, 232, 240));
            g2.setStroke(new BasicStroke(2));
            g2.drawLine(padding - 20, groundY, w - padding + 20, groundY);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ServerAdmin().setVisible(true));
    }
}