import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

/**
 * =============================================================================
 * 🖥️ SERVER ADMIN: IMPROVED UX (V3.2)
 * =============================================================================
 * UPDATE LOG V3.2:
 * - Mengubah Statistik "Sesi Terpilih" menjadi "Total Kegiatan".
 * - Update Protokol Server: Mengirim data detail suara (summary string) ke
 * client.
 */
public class ServerAdmin extends JFrame {

    // =========================================================================
    // 🧠 DATA & STATE
    // =========================================================================
    private List<VotingSession> historySessions = new ArrayList<>();
    private VotingSession currentSession; // Sesi yang sedang LIVE (Backend)
    private VotingSession viewedSession; // Sesi yang sedang DILIHAT (UI)

    private boolean isServerRunning = true;
    private int connectedClients = 0;
    private List<CandidateInputRow> inputRows = new ArrayList<>();

    // State Navigasi untuk tombol "Back" di Monitor
    private String lastPageTag = "PAGE_DASHBOARD";
    private AppTheme.SidebarButton lastActiveButton = null;

    // =========================================================================
    // 🎨 UI COMPONENTS
    // =========================================================================
    private CardLayout contentLayout;
    private JPanel mainContentPanel;

    // Sidebar Buttons
    private AppTheme.SidebarButton btnNavDash;
    private AppTheme.SidebarButton btnNavHistory;
    private AppTheme.SidebarButton btnNavCreate;
    private AppTheme.SidebarButton btnStressTest;

    // Dashboard Components
    // UPDATED: lblStatActiveSession diganti menjadi lblStatTotalSessions
    private JLabel lblStatTotalVotes, lblStatTotalSessions, lblStatClients;
    private DefaultTableModel tableModelDashboard; // Hanya 5 teratas
    private JTable dashboardTable;

    // History Components
    private DefaultTableModel tableModelHistory; // Semua data
    private JTable historyTable;

    // Monitor Components
    private GraphPanel liveGraphPanel;
    private JLabel lblGraphTitle, lblGraphStatus;
    private JButton btnEndVote;
    private JButton btnBackNavigation;
    private JPanel monitorHeaderPanel;

    // Create Session Components
    private JTextField txtSessionTitle;
    private JPanel candidatesContainer;

    public ServerAdmin() {
        // 1. SKIP LOGIN - Langsung Masuk
        setTitle("Admin Dashboard - E-Voting System (V3.2)");
        setSize(1280, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 2. Inisialisasi Database & Load History
        DatabaseManager.initDatabase();
        loadHistoryFromDB();

        // 3. Setup UI
        initSidebar();
        initContentArea();

        // 4. Start Server
        new Thread(this::startServer).start();
    }

    private void loadHistoryFromDB() {
        historySessions.clear();
        historySessions.addAll(DatabaseManager.getAllHistory());
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

        // --- BUTTONS ---
        btnNavDash = AppTheme.createSidebarButton("🏠", true);
        btnNavHistory = AppTheme.createSidebarButton("📜", false); // Icon Riwayat
        btnNavCreate = AppTheme.createSidebarButton("➕", false);
        btnStressTest = AppTheme.createSidebarButton("⚡", false);
        btnStressTest.setForeground(new Color(251, 191, 36));

        // --- ACTIONS ---
        btnNavDash.addActionListener(e -> switchPage("PAGE_DASHBOARD", btnNavDash));
        btnNavHistory.addActionListener(e -> switchPage("PAGE_HISTORY", btnNavHistory));
        btnNavCreate.addActionListener(e -> switchPage("PAGE_CREATE", btnNavCreate));
        btnStressTest.addActionListener(e -> showAdvancedStressTestDialog());

        // --- ADD TO PANEL ---
        sidebar.add(lblLogo);
        sidebar.add(Box.createVerticalStrut(50));
        addSidebarItem(sidebar, btnNavDash);
        addSidebarItem(sidebar, btnNavHistory);
        addSidebarItem(sidebar, btnNavCreate);

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
        mainContentPanel.add(createPageHistory(), "PAGE_HISTORY");
        mainContentPanel.add(createPageCreateSession(), "PAGE_CREATE");
        mainContentPanel.add(createPageMonitor(), "PAGE_MONITOR");

        add(mainContentPanel, BorderLayout.CENTER);

        // Initial Update
        updateDashboardTable();
        updateHistoryTable();
    }

    private void switchPage(String pageName, AppTheme.SidebarButton activeButton) {
        contentLayout.show(mainContentPanel, pageName);

        // Reset Active State
        btnNavDash.setActive(false);
        btnNavHistory.setActive(false);
        btnNavCreate.setActive(false);
        btnStressTest.setActive(false);

        if (activeButton != null) {
            activeButton.setActive(true);
            lastActiveButton = activeButton; // Simpan untuk tombol Back
            lastPageTag = pageName; // Simpan untuk tombol Back
        }

        if (pageName.equals("PAGE_DASHBOARD"))
            updateDashboardTable();
        if (pageName.equals("PAGE_HISTORY"))
            updateHistoryTable();
        if (pageName.equals("PAGE_MONITOR"))
            refreshMonitorUI();
    }

    // =========================================================================
    // 📄 PAGE 1: DASHBOARD (TOP 5 ONLY)
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

        // Stats Grid
        JPanel statsGrid = new JPanel(new GridLayout(1, 3, 25, 0));
        statsGrid.setOpaque(false);
        statsGrid.setPreferredSize(new Dimension(0, 140));

        lblStatTotalVotes = new JLabel("0");
        lblStatTotalSessions = new JLabel("0"); // UPDATED: Default 0
        lblStatClients = new JLabel("0");

        statsGrid.add(createStatCard("Total Suara (Live)", lblStatTotalVotes));
        // UPDATED: Title changed from "Sesi Terpilih" to "Total Kegiatan"
        statsGrid.add(createStatCard("Total Kegiatan", lblStatTotalSessions));
        statsGrid.add(createStatCard("Client Terhubung", lblStatClients));

        // Tabel Dashboard (Limited 5)
        JPanel tablePanel = AppTheme.createShadowPanel();
        tablePanel.setLayout(new BorderLayout());

        JLabel lblTableTitle = new JLabel("Aktivitas Terkini (5 Terbaru)");
        lblTableTitle.setFont(AppTheme.FONT_H2);
        lblTableTitle.setBorder(new EmptyBorder(0, 0, 15, 0));

        String[] columns = { "ID", "Judul Sesi", "Status", "Pemenang / Hasil", "Total Suara" };
        tableModelDashboard = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        dashboardTable = new JTable(tableModelDashboard);
        setupTableStyle(dashboardTable);

        // Interaction: Click to Monitor
        dashboardTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1) {
                    int row = dashboardTable.getSelectedRow();
                    if (row != -1) {
                        // Mapping row index dashboard ke Session
                        int sessionIndex = historySessions.size() - 1 - row;
                        if (sessionIndex >= 0 && sessionIndex < historySessions.size()) {
                            loadSessionToMonitor(historySessions.get(sessionIndex));
                        }
                    }
                }
            }
        });

        tablePanel.add(lblTableTitle, BorderLayout.NORTH);
        tablePanel.add(new JScrollPane(dashboardTable), BorderLayout.CENTER);

        centerContainer.add(statsGrid, BorderLayout.NORTH);
        centerContainer.add(tablePanel, BorderLayout.CENTER);

        panel.add(centerContainer, BorderLayout.CENTER);
        return panel;
    }

    // =========================================================================
    // 📄 PAGE 2: RIWAYAT / HISTORY (FULL LIST)
    // =========================================================================
    private JPanel createPageHistory() {
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(40, 40, 40, 40));

        JLabel title = new JLabel("Riwayat Lengkap Sesi Voting");
        title.setFont(AppTheme.FONT_H1);
        title.setForeground(AppTheme.COLOR_TEXT_MAIN);
        panel.add(title, BorderLayout.NORTH);

        JPanel tablePanel = AppTheme.createShadowPanel();
        tablePanel.setLayout(new BorderLayout());

        String[] columns = { "ID", "Judul Sesi", "Status", "Pemenang / Hasil", "Total Suara", "Waktu Mulai" };
        tableModelHistory = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        historyTable = new JTable(tableModelHistory);
        setupTableStyle(historyTable);

        // Interaction: Click to Monitor
        historyTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1) {
                    int row = historyTable.getSelectedRow();
                    if (row != -1) {
                        // Mapping row index history ke Session (Descending)
                        int sessionIndex = historySessions.size() - 1 - row;
                        if (sessionIndex >= 0 && sessionIndex < historySessions.size()) {
                            loadSessionToMonitor(historySessions.get(sessionIndex));
                        }
                    }
                }
            }
        });

        tablePanel.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        panel.add(tablePanel, BorderLayout.CENTER);

        return panel;
    }

    private void setupTableStyle(JTable table) {
        table.setRowHeight(40);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(241, 245, 249));
        table.setFont(AppTheme.FONT_BODY);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JTableHeader header = table.getTableHeader();
        header.setFont(AppTheme.FONT_BOLD);
        header.setBackground(Color.WHITE);
        header.setForeground(AppTheme.COLOR_TEXT_MUTED);
        header.setPreferredSize(new Dimension(0, 45));
    }

    // =========================================================================
    // 📄 PAGE 3: CREATE SESSION
    // =========================================================================
    private JPanel createPageCreateSession() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(40, 40, 40, 40));

        JPanel formCard = AppTheme.createShadowPanel();
        formCard.setLayout(new BorderLayout(0, 20));

        JPanel topInput = new JPanel(new GridLayout(0, 1, 0, 10));
        topInput.setOpaque(false);
        JLabel lblHead = new JLabel("Buat Sesi Voting Baru");
        lblHead.setFont(AppTheme.FONT_H1);

        txtSessionTitle = new JTextField();
        AppTheme.styleTextField(txtSessionTitle);
        topInput.add(lblHead);
        topInput.add(new JLabel("Judul Kegiatan"));
        topInput.add(txtSessionTitle);

        candidatesContainer = new JPanel();
        candidatesContainer.setLayout(new BoxLayout(candidatesContainer, BoxLayout.Y_AXIS));
        candidatesContainer.setOpaque(false);

        JScrollPane scrollCandidates = new JScrollPane(candidatesContainer);
        scrollCandidates.setBorder(null);
        scrollCandidates.getVerticalScrollBar().setUnitIncrement(16);
        scrollCandidates.setPreferredSize(new Dimension(0, 300));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnPanel.setOpaque(false);

        JButton btnAddRow = new JButton("+ Tambah Kandidat");
        btnAddRow.addActionListener(e -> addCandidateRow());

        JButton btnStart = AppTheme.createGradientButton("MULAI SESI SEKARANG", 250, 50);
        btnStart.addActionListener(e -> startNewSession());

        btnPanel.add(btnAddRow);
        btnPanel.add(Box.createHorizontalStrut(20));
        btnPanel.add(btnStart);

        formCard.add(topInput, BorderLayout.NORTH);
        formCard.add(scrollCandidates, BorderLayout.CENTER);
        formCard.add(btnPanel, BorderLayout.SOUTH);

        panel.add(formCard, BorderLayout.CENTER);
        resetFormToDefault();
        return panel;
    }

    // =========================================================================
    // 📄 PAGE 4: MONITOR (HIDDEN VIEW)
    // =========================================================================
    private JPanel createPageMonitor() {
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(20, 40, 40, 40));

        // --- HEADER PANEL (NORTH) ---
        monitorHeaderPanel = new JPanel(new BorderLayout());
        monitorHeaderPanel.setOpaque(false);

        // Tombol Kembali (Left)
        JPanel leftBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftBox.setOpaque(false);

        btnBackNavigation = new JButton("⬅ Kembali");
        btnBackNavigation.setFont(AppTheme.FONT_BOLD);
        btnBackNavigation.setBorderPainted(false);
        btnBackNavigation.setContentAreaFilled(false);
        btnBackNavigation.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnBackNavigation.addActionListener(e -> {
            // Kembali ke halaman sebelumnya (Dashboard atau History)
            switchPage(lastPageTag, lastActiveButton);
        });

        // Info Title
        JPanel titleBox = new JPanel(new GridLayout(2, 1));
        titleBox.setOpaque(false);
        titleBox.setBorder(new EmptyBorder(10, 0, 0, 0));

        lblGraphTitle = new JLabel("Menunggu Data...");
        lblGraphTitle.setFont(AppTheme.FONT_H1);
        lblGraphTitle.setForeground(AppTheme.COLOR_TEXT_MAIN);

        lblGraphStatus = new JLabel("Status: -");
        lblGraphStatus.setFont(AppTheme.FONT_BOLD);
        lblGraphStatus.setForeground(AppTheme.COLOR_TEXT_MUTED);

        titleBox.add(lblGraphTitle);
        titleBox.add(lblGraphStatus);

        leftBox.add(btnBackNavigation);

        // Tombol Aksi (Right) - Akhiri Sesi
        JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightBox.setOpaque(false);

        btnEndVote = new JButton("AKHIRI SESI & SIMPAN");
        btnEndVote.setFont(AppTheme.FONT_BOLD);
        btnEndVote.setForeground(Color.WHITE);
        btnEndVote.setBackground(new Color(220, 38, 38)); // Red
        btnEndVote.setFocusPainted(false);
        btnEndVote.addActionListener(e -> actionEndSession());

        rightBox.add(btnEndVote);

        // Assemble Header
        JPanel headerContent = new JPanel(new BorderLayout());
        headerContent.setOpaque(false);
        headerContent.add(btnBackNavigation, BorderLayout.NORTH);
        headerContent.add(titleBox, BorderLayout.WEST);
        headerContent.add(rightBox, BorderLayout.EAST);

        monitorHeaderPanel.add(headerContent, BorderLayout.CENTER);

        // --- GRAPH PANEL (CENTER) ---
        liveGraphPanel = new GraphPanel();

        panel.add(monitorHeaderPanel, BorderLayout.NORTH); // Header pasti di atas
        panel.add(liveGraphPanel, BorderLayout.CENTER); // Grafik di tengah

        return panel;
    }

    // =========================================================================
    // ⚙️ LOGIC & HELPERS
    // =========================================================================

    private void loadSessionToMonitor(VotingSession session) {
        this.viewedSession = session;
        // Pindah ke halaman monitor tanpa mengubah tombol sidebar aktif
        contentLayout.show(mainContentPanel, "PAGE_MONITOR");
        refreshMonitorUI();
    }

    private void refreshMonitorUI() {
        if (viewedSession != null) {
            lblGraphTitle.setText(viewedSession.getTitle());
            boolean isWatchingLive = (viewedSession == currentSession) && currentSession.isActive();

            if (isWatchingLive) {
                lblGraphStatus.setText("Status: 🟢 LIVE VOTING");
                lblGraphStatus.setForeground(new Color(22, 163, 74));
                btnEndVote.setVisible(true);
            } else {
                lblGraphStatus.setText("Status: 🏁 ARSIP / SELESAI");
                lblGraphStatus.setForeground(Color.GRAY);
                btnEndVote.setVisible(false);
            }
        }
        liveGraphPanel.repaint();
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

    private void startNewSession() {
        String title = txtSessionTitle.getText().trim();
        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Judul sesi tidak boleh kosong!");
            return;
        }

        List<String> validNames = new ArrayList<>();
        List<String> validPaths = new ArrayList<>();
        Set<String> uniqueCheck = new HashSet<>();
        File imgDir = new File("server_images");
        if (!imgDir.exists())
            imgDir.mkdir();

        for (CandidateInputRow row : inputRows) {
            String name = row.txtName.getText().trim();
            if (name.isEmpty())
                continue;

            if (uniqueCheck.contains(name.toLowerCase())) {
                JOptionPane.showMessageDialog(this, "Nama kandidat tidak boleh kembar: " + name);
                return;
            }
            uniqueCheck.add(name.toLowerCase());
            validNames.add(name);

            String finalPath = "";
            if (row.imagePath != null) {
                try {
                    File source = new File(row.imagePath);
                    String ext = source.getName().substring(source.getName().lastIndexOf("."));
                    String newFileName = System.currentTimeMillis() + "_" + name.replaceAll("\\s+", "") + ext;
                    File dest = new File(imgDir, newFileName);
                    Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    finalPath = dest.getAbsolutePath();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
            validPaths.add(finalPath);
        }

        if (validNames.size() < 2) {
            JOptionPane.showMessageDialog(this, "Minimal 2 kandidat!");
            return;
        }

        currentSession = new VotingSession(title, validNames.toArray(new String[0]), validPaths.toArray(new String[0]));
        historySessions.add(currentSession);

        resetFormToDefault();
        loadSessionToMonitor(currentSession); // Langsung lihat monitor sesi baru

        // Update list navigation
        lastPageTag = "PAGE_DASHBOARD";
        lastActiveButton = btnNavDash;

        updateDashboardTable();
        updateHistoryTable();

        JOptionPane.showMessageDialog(this, "Sesi DIBUKA! Client akan menerima data.");
    }

    private void actionEndSession() {
        if (currentSession == null || !currentSession.isActive())
            return;

        int confirm = JOptionPane.showConfirmDialog(this, "Akhiri sesi dan SIMPAN hasil ke Database?", "Konfirmasi",
                JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            currentSession.endSession();
            DatabaseManager.saveSession(currentSession);
            refreshMonitorUI();
            updateDashboardTable();
            updateHistoryTable();
            JOptionPane.showMessageDialog(this, "Sesi Ditutup & Data Tersimpan!");
        }
    }

    private void updateDashboardTable() {
        tableModelDashboard.setRowCount(0);

        // UPDATED: Logic Dashboard
        // Menampilkan Total Kegiatan (Jumlah List History)
        lblStatTotalSessions.setText(String.valueOf(historySessions.size()));

        int startIdx = historySessions.size() - 1;
        int endIdx = Math.max(0, historySessions.size() - 5); // Limit 5

        int displayId = 1;
        for (int i = startIdx; i >= endIdx; i--) {
            VotingSession vs = historySessions.get(i);
            String status = vs.isActive() ? "🟢 Aktif" : "🔴 Selesai";
            tableModelDashboard.addRow(new Object[] { displayId++, vs.getTitle(), status, vs.getWinnerResult(),
                    vs.getTotalVotes() + " Suara" });
        }
    }

    private void updateHistoryTable() {
        tableModelHistory.setRowCount(0);
        int displayId = 1;
        // Loop semua data
        for (int i = historySessions.size() - 1; i >= 0; i--) {
            VotingSession vs = historySessions.get(i);
            String status = vs.isActive() ? "🟢 Aktif" : "🔴 Selesai";
            Date date = new Date(vs.getStartTime());
            tableModelHistory.addRow(new Object[] { displayId++, vs.getTitle(), status, vs.getWinnerResult(),
                    vs.getTotalVotes(), date.toString() });
        }
    }

    private void updateRealtimeStats() {
        if (viewedSession == currentSession) {
            lblStatTotalVotes.setText(String.valueOf(currentSession.getTotalVotes()));
            liveGraphPanel.repaint();
        }
    }

    // =========================================================================
    // ⚡ STRESS TEST (ADVANCED)
    // =========================================================================

    private void showAdvancedStressTestDialog() {
        JDialog dialog = new JDialog(this, "Stress Test Configuration", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(400, 400);
        dialog.setLocationRelativeTo(this);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        // 1. Target Session Selection
        content.add(new JLabel("Target Sesi:"));
        JComboBox<String> comboSession = new JComboBox<>();
        // Isi dengan sesi live + 5 history terakhir
        if (currentSession != null && currentSession.isActive()) {
            comboSession.addItem("LIVE: " + currentSession.getTitle());
        }
        for (int i = historySessions.size() - 1; i >= Math.max(0, historySessions.size() - 5); i--) {
            VotingSession vs = historySessions.get(i);
            if (vs != currentSession) {
                comboSession.addItem("ARCHIVE: " + vs.getTitle());
            }
        }
        content.add(comboSession);
        content.add(Box.createVerticalStrut(15));

        // 2. Thread Mode
        content.add(new JLabel("Mode Threading:"));
        JRadioButton radioSafe = new JRadioButton("Safe (Synchronized)", true);
        JRadioButton radioUnsafe = new JRadioButton("Unsafe (Race Condition)");
        ButtonGroup grpThread = new ButtonGroup();
        grpThread.add(radioSafe);
        grpThread.add(radioUnsafe);
        content.add(radioSafe);
        content.add(radioUnsafe);
        content.add(Box.createVerticalStrut(15));

        // 3. Persistence Mode
        content.add(new JLabel("Mode Penyimpanan:"));
        JRadioButton radioSave = new JRadioButton("Save to DB (Jika Live)", true);
        JRadioButton radioNoSave = new JRadioButton("No Save (RAM Simulation)");
        ButtonGroup grpPersist = new ButtonGroup();
        grpPersist.add(radioSave);
        grpPersist.add(radioNoSave);
        content.add(radioSave);
        content.add(radioNoSave);
        content.add(Box.createVerticalStrut(20));

        // Action Button
        JButton btnStart = new JButton("MULAI SIMULASI (50 Votes)");
        btnStart.addActionListener(e -> {
            String selectedItem = (String) comboSession.getSelectedItem();
            if (selectedItem == null)
                return;

            // Cari object sesi
            VotingSession target = null;
            if (selectedItem.startsWith("LIVE") && currentSession != null) {
                target = currentSession;
            } else {
                String title = selectedItem.replace("ARCHIVE: ", "");
                for (VotingSession vs : historySessions) {
                    if (vs.getTitle().equals(title)) {
                        target = vs;
                        break;
                    }
                }
            }

            if (target != null) {
                runStressTest(target, radioUnsafe.isSelected(), radioNoSave.isSelected());
                dialog.dispose();
                // Arahkan pandangan ke monitor sesi tersebut
                loadSessionToMonitor(target);
            }
        });

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(btnStart, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void runStressTest(VotingSession target, boolean isUnsafe, boolean isNoSave) {
        List<String> candidates = new ArrayList<>(target.getCandidates());
        if (candidates.isEmpty())
            return;

        // NOTE: "No Save" mode dalam konteks ini berarti kita hanya mengubah state di
        // RAM
        // dan tidak memicu trigger save database (karena database save manual di tombol
        // End).
        // Namun untuk sesi arsip, VotingSession memblokir vote.
        // Logic ini mensimulasikan penambahan suara.

        for (int i = 0; i < 50; i++) {
            new Thread(() -> {
                String rand = candidates.get((int) (Math.random() * candidates.size()));

                // Gunakan unsafe method jika dipilih, atau addVote biasa
                if (isUnsafe) {
                    target.addVoteUnsafe(rand);
                } else {
                    target.addVote(rand);
                }

                SwingUtilities.invokeLater(() -> {
                    liveGraphPanel.repaint();
                    if (target == currentSession)
                        lblStatTotalVotes.setText(String.valueOf(target.getTotalVotes()));
                });
            }).start();
        }
    }

    // =========================================================================
    // 🖥️ SERVER ENGINE
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

    private void handleClient(Socket socket) {
        try (DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            // UPDATED: PROTOCOL V3.2 (WITH VOTE SUMMARY)
            // Format: "Title;Active;Winner;DetailSummaryString#"
            StringBuilder sbHistory = new StringBuilder("HISTORY_LIST|");
            for (VotingSession vs : historySessions) {
                sbHistory.append(vs.getTitle()).append(";")
                        .append(vs.isActive()).append(";")
                        .append(vs.getWinnerResult()).append(";")
                        .append(vs.getVoteSummary()).append("#"); // NEW: Add Detail Summary
            }
            out.writeUTF(sbHistory.toString());

            if (currentSession != null && currentSession.isActive()) {
                out.writeUTF("SETUP_V2_IMAGES");
                out.writeUTF(currentSession.getTitle());
                java.util.Set<String> candidates = currentSession.getCandidates();
                out.writeInt(candidates.size());
                for (String name : candidates) {
                    out.writeUTF(name);
                    String path = currentSession.getCandidateImage(name);
                    File imgFile = new File(path);
                    if (imgFile.exists() && !imgFile.isDirectory()) {
                        byte[] bytes = Files.readAllBytes(imgFile.toPath());
                        out.writeInt(bytes.length);
                        out.write(bytes);
                    } else {
                        out.writeInt(0);
                    }
                }
            } else {
                out.writeUTF("WAIT|Tidak ada sesi voting aktif.");
            }

            while (true) {
                String msg = in.readUTF();
                if (msg.startsWith("VOTE|")) {
                    String candidateName = msg.split("\\|")[1];
                    if (currentSession != null && currentSession.isActive()) {
                        currentSession.addVote(candidateName);
                        SwingUtilities.invokeLater(this::updateRealtimeStats);
                    }
                }
            }
        } catch (Exception e) {
        } finally {
            connectedClients--;
            SwingUtilities.invokeLater(() -> lblStatClients.setText(String.valueOf(connectedClients)));
        }
    }

    // =========================================================================
    // 🛠️ HELPER CLASSES
    // =========================================================================

    private void addCandidateRow() {
        CandidateInputRow row = new CandidateInputRow(inputRows.size() + 1);
        inputRows.add(row);
        candidatesContainer.add(row.panel);
        candidatesContainer.add(Box.createVerticalStrut(10));
        candidatesContainer.revalidate();
        candidatesContainer.repaint();
    }

    private void resetFormToDefault() {
        candidatesContainer.removeAll();
        inputRows.clear();
        txtSessionTitle.setText("");
        addCandidateRow();
        addCandidateRow();
        candidatesContainer.revalidate();
        candidatesContainer.repaint();
    }

    // INNER CLASS ROW INPUT
    private class CandidateInputRow {
        JPanel panel;
        JTextField txtName;
        JLabel lblFile;
        JLabel lblNumber;
        JButton btnRemoveImage;
        String imagePath = null;

        public CandidateInputRow(int number) {
            panel = new JPanel(new BorderLayout(10, 0));
            panel.setOpaque(false);
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(226, 232, 240)),
                    new EmptyBorder(10, 10, 10, 10)));
            panel.setMaximumSize(new Dimension(2000, 60));

            lblNumber = new JLabel("Kandidat #" + number);
            txtName = new JTextField();
            AppTheme.styleTextField(txtName);
            txtName.setPreferredSize(new Dimension(300, 40));

            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            rightPanel.setOpaque(false);

            JButton btnUpload = new JButton("📷");
            btnUpload.setToolTipText("Upload Foto");
            btnUpload.setBackground(Color.WHITE);
            btnUpload.addActionListener(e -> chooseImage());

            // --- GOALS: Hapus Foto jadi Teks Biasa ---
            btnRemoveImage = new JButton("❌");
            btnRemoveImage.setToolTipText("Hapus Foto");
            btnRemoveImage.setForeground(Color.RED);
            btnRemoveImage.setContentAreaFilled(false);
            btnRemoveImage.setBorderPainted(false);
            btnRemoveImage.setVisible(false);
            btnRemoveImage.addActionListener(e -> removeImage());

            lblFile = new JLabel("No Image");
            lblFile.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            lblFile.setForeground(Color.GRAY);

            JButton btnDeleteRow = new JButton("Hapus Baris");
            btnDeleteRow.setForeground(Color.RED);
            btnDeleteRow.setContentAreaFilled(false);
            btnDeleteRow.setBorderPainted(false);

            // Logic Hapus Baris (Sederhana: Hapus dari UI & List)
            btnDeleteRow.addActionListener(e -> {
                if (inputRows.size() > 2) {
                    inputRows.remove(this);
                    candidatesContainer.remove(this.panel);
                    candidatesContainer.revalidate();
                    candidatesContainer.repaint();
                } else {
                    JOptionPane.showMessageDialog(ServerAdmin.this, "Minimal 2 kandidat!");
                }
            });

            rightPanel.add(btnUpload);
            rightPanel.add(btnRemoveImage);
            rightPanel.add(lblFile);
            rightPanel.add(Box.createHorizontalStrut(10));
            rightPanel.add(btnDeleteRow);

            panel.add(lblNumber, BorderLayout.WEST);
            panel.add(txtName, BorderLayout.CENTER);
            panel.add(rightPanel, BorderLayout.EAST);
        }

        private void chooseImage() {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("Gambar (JPG, PNG)", "jpg", "png", "jpeg"));
            if (chooser.showOpenDialog(ServerAdmin.this) == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                imagePath = f.getAbsolutePath();
                lblFile.setText(f.getName());
                lblFile.setForeground(new Color(22, 163, 74));
                btnRemoveImage.setVisible(true);
            }
        }

        private void removeImage() {
            imagePath = null;
            lblFile.setText("No Image");
            lblFile.setForeground(Color.GRAY);
            btnRemoveImage.setVisible(false);
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

            if (viewedSession == null) {
                g2.setColor(AppTheme.COLOR_TEXT_MUTED);
                g2.drawString("Pilih sesi dari riwayat atau dashboard.", getWidth() / 2 - 100, getHeight() / 2);
                return;
            }
            drawChart(g2);
        }

        private void drawChart(Graphics2D g2) {
            int w = getWidth();
            int h = getHeight();
            int padding = 60;

            // Mencari maxVotes dari data session
            int maxVotes = 1;
            for (int val : viewedSession.getAllData().values())
                maxVotes = Math.max(maxVotes, val);

            java.util.Set<String> cands = viewedSession.getCandidates();
            int numCandidates = cands.size();
            int barWidth = Math.min(100, (w - (padding * 2)) / Math.max(1, numCandidates) - 20);
            int x = padding;
            int groundY = h - padding;

            for (String cand : cands) {
                int votes = viewedSession.getVoteCount(cand);
                int barHeight = (int) (((double) votes / maxVotes) * (h - padding * 3));
                if (votes == 0)
                    barHeight = 4;

                int y = groundY - barHeight;
                g2.setColor(AppTheme.COLOR_PRIMARY_START);
                g2.fillRoundRect(x, y, barWidth, barHeight, 15, 15);

                g2.setColor(AppTheme.COLOR_TEXT_MAIN);
                g2.drawString(String.valueOf(votes), x + barWidth / 2 - 5, y - 10);

                String display = cand.length() > 10 ? cand.substring(0, 8) + ".." : cand;
                g2.setColor(AppTheme.COLOR_TEXT_MUTED);
                g2.drawString(display, x + barWidth / 2 - g2.getFontMetrics().stringWidth(display) / 2, groundY + 25);

                x += barWidth + 30;
            }
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
        }
        SwingUtilities.invokeLater(() -> new ServerAdmin().setVisible(true));
    }
}