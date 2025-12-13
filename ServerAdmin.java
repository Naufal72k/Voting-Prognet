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

public class ServerAdmin extends JFrame {

    private List<VotingSession> historySessions = new ArrayList<>();
    private List<VotingSession> activeSessions = new ArrayList<>();
    private VotingSession viewedSession;

    private boolean isServerRunning = true;
    private int connectedClients = 0;
    private List<CandidateInputRow> inputRows = new ArrayList<>();

    private List<DataOutputStream> activeClientStreams = Collections.synchronizedList(new ArrayList<>());

    private String lastPageTag = "PAGE_DASHBOARD";
    private AppTheme.SidebarButton lastActiveButton = null;

    private CardLayout contentLayout;
    private JPanel mainContentPanel;

    private AppTheme.SidebarButton btnNavDash;
    private AppTheme.SidebarButton btnNavHistory;
    private AppTheme.SidebarButton btnNavCreate;
    private AppTheme.SidebarButton btnStressTest;

    private JLabel lblStatTotalVotes, lblStatTotalSessions, lblStatClients;
    private DefaultTableModel tableModelDashboard;
    private JTable dashboardTable;

    private DefaultTableModel tableModelHistory;
    private JTable historyTable;

    private GraphPanel liveGraphPanel;
    private JLabel lblGraphTitle, lblGraphStatus;
    private JButton btnEndVote;
    private JButton btnManageCandidates;
    private JButton btnBackNavigation;
    private JPanel monitorHeaderPanel;

    private JTextField txtSessionTitle;
    private JPanel candidatesContainer;

    public ServerAdmin() {
        setTitle("Admin Dashboard - E-Voting System (V3.2)");
        setSize(1280, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        DatabaseManager.initDatabase();
        loadHistoryFromDB();

        initSidebar();
        initContentArea();

        new Thread(this::startServer).start();
    }

    private void loadHistoryFromDB() {
        historySessions.clear();
        historySessions.addAll(DatabaseManager.getAllHistory());
    }

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
        btnNavHistory = AppTheme.createSidebarButton("📜", false);
        btnNavCreate = AppTheme.createSidebarButton("➕", false);
        btnStressTest = AppTheme.createSidebarButton("⚡", false);
        btnStressTest.setForeground(new Color(251, 191, 36));

        btnNavDash.addActionListener(e -> switchPage("PAGE_DASHBOARD", btnNavDash));
        btnNavHistory.addActionListener(e -> switchPage("PAGE_HISTORY", btnNavHistory));
        btnNavCreate.addActionListener(e -> switchPage("PAGE_CREATE", btnNavCreate));
        btnStressTest.addActionListener(e -> showAdvancedStressTestDialog());

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

    private void initContentArea() {
        contentLayout = new CardLayout();
        mainContentPanel = new JPanel(contentLayout);
        mainContentPanel.setBackground(AppTheme.COLOR_BACKGROUND_APP);

        mainContentPanel.add(createPageDashboard(), "PAGE_DASHBOARD");
        mainContentPanel.add(createPageHistory(), "PAGE_HISTORY");
        mainContentPanel.add(createPageCreateSession(), "PAGE_CREATE");
        mainContentPanel.add(createPageMonitor(), "PAGE_MONITOR");

        add(mainContentPanel, BorderLayout.CENTER);

        updateDashboardTable();
        updateHistoryTable();
    }

    private void switchPage(String pageName, AppTheme.SidebarButton activeButton) {
        contentLayout.show(mainContentPanel, pageName);

        btnNavDash.setActive(false);
        btnNavHistory.setActive(false);
        btnNavCreate.setActive(false);
        btnStressTest.setActive(false);

        if (activeButton != null) {
            activeButton.setActive(true);
            lastActiveButton = activeButton;
            lastPageTag = pageName;
        }

        if (pageName.equals("PAGE_DASHBOARD"))
            updateDashboardTable();
        if (pageName.equals("PAGE_HISTORY"))
            updateHistoryTable();
        if (pageName.equals("PAGE_MONITOR"))
            refreshMonitorUI();
    }

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
        lblStatTotalSessions = new JLabel("0");
        lblStatClients = new JLabel("0");

        statsGrid.add(createStatCard("Total Suara (Live)", lblStatTotalVotes));
        statsGrid.add(createStatCard("Total Kegiatan", lblStatTotalSessions));
        statsGrid.add(createStatCard("Client Terhubung", lblStatClients));

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

        dashboardTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1) {
                    int row = dashboardTable.getSelectedRow();
                    if (row != -1) {
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

    private JPanel createPageHistory() {
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(40, 40, 40, 40));

        JLabel title = new JLabel("Riwayat Lengkap Sesi Voting");
        title.setFont(AppTheme.FONT_H1);
        title.setForeground(AppTheme.COLOR_TEXT_MAIN);

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.add(title, BorderLayout.WEST);

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        searchPanel.setOpaque(false);
        JLabel lblSearch = new JLabel("🔍 Cari Sesi:");
        lblSearch.setFont(AppTheme.FONT_BOLD);
        JTextField txtSearch = new JTextField(15);
        txtSearch.putClientProperty("JTextField.placeholderText", "Ketik nama kegiatan...");

        txtSearch.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyReleased(java.awt.event.KeyEvent evt) {
                updateHistoryTable(txtSearch.getText());
            }
        });

        searchPanel.add(lblSearch);
        searchPanel.add(txtSearch);
        headerPanel.add(searchPanel, BorderLayout.EAST);

        panel.add(headerPanel, BorderLayout.NORTH);

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

        historyTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1) {
                    int row = historyTable.getSelectedRow();
                    if (row != -1) {
                        String titleClicked = (String) tableModelHistory.getValueAt(row, 1);

                        for (VotingSession vs : historySessions) {
                            if (vs.getTitle().equals(titleClicked)) {
                                loadSessionToMonitor(vs);
                                break;
                            }
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

    private JPanel createPageMonitor() {
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(20, 40, 40, 40));

        monitorHeaderPanel = new JPanel(new BorderLayout());
        monitorHeaderPanel.setOpaque(false);

        JPanel leftBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftBox.setOpaque(false);

        btnBackNavigation = new JButton("⬅ Kembali");
        btnBackNavigation.setFont(AppTheme.FONT_BOLD);
        btnBackNavigation.setBorderPainted(false);
        btnBackNavigation.setContentAreaFilled(false);
        btnBackNavigation.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnBackNavigation.addActionListener(e -> {
            switchPage(lastPageTag, lastActiveButton);
        });

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

        JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightBox.setOpaque(false);

        btnManageCandidates = new JButton("⚙ KELOLA KANDIDAT");
        btnManageCandidates.setFont(AppTheme.FONT_BOLD);
        btnManageCandidates.setForeground(AppTheme.COLOR_PRIMARY_START);
        btnManageCandidates.setBackground(Color.WHITE);
        btnManageCandidates.setFocusPainted(false);
        btnManageCandidates.addActionListener(e -> openCandidateManager());

        btnEndVote = new JButton("AKHIRI SESI & SIMPAN");
        btnEndVote.setFont(AppTheme.FONT_BOLD);
        btnEndVote.setForeground(Color.WHITE);
        btnEndVote.setBackground(new Color(220, 38, 38));
        btnEndVote.setOpaque(true);
        btnEndVote.setBorderPainted(false);
        btnEndVote.setFocusPainted(false);
        btnEndVote.setRolloverEnabled(false);
        btnEndVote.addActionListener(e -> actionEndSession());

        rightBox.add(btnManageCandidates);
        rightBox.add(btnEndVote);

        JPanel headerContent = new JPanel(new BorderLayout());
        headerContent.setOpaque(false);
        headerContent.add(btnBackNavigation, BorderLayout.NORTH);
        headerContent.add(titleBox, BorderLayout.WEST);
        headerContent.add(rightBox, BorderLayout.EAST);

        monitorHeaderPanel.add(headerContent, BorderLayout.CENTER);

        liveGraphPanel = new GraphPanel();

        panel.add(monitorHeaderPanel, BorderLayout.NORTH);
        panel.add(liveGraphPanel, BorderLayout.CENTER);

        return panel;
    }

    private void loadSessionToMonitor(VotingSession session) {
        this.viewedSession = session;
        contentLayout.show(mainContentPanel, "PAGE_MONITOR");
        refreshMonitorUI();
    }

    private void refreshMonitorUI() {
        if (viewedSession != null) {
            lblGraphTitle.setText(viewedSession.getTitle());
            boolean isLive = viewedSession.isActive();

            if (isLive) {
                lblGraphStatus.setText("Status: 🟢 LIVE VOTING");
                lblGraphStatus.setForeground(new Color(22, 163, 74));
                btnEndVote.setVisible(true);
                btnManageCandidates.setVisible(true);
            } else {
                lblGraphStatus.setText("Status: 🏁 ARSIP / SELESAI");
                lblGraphStatus.setForeground(Color.GRAY);
                btnEndVote.setVisible(false);
                btnManageCandidates.setVisible(false);
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

        VotingSession newSession = new VotingSession(title, validNames.toArray(new String[0]),
                validPaths.toArray(new String[0]));
        activeSessions.add(newSession);
        historySessions.add(newSession);

        resetFormToDefault();
        loadSessionToMonitor(newSession);

        lastPageTag = "PAGE_DASHBOARD";
        lastActiveButton = btnNavDash;

        updateDashboardTable();
        updateHistoryTable();

        JOptionPane.showMessageDialog(this, "Sesi '" + title + "' DIBUKA! Client dapat melihat sesi baru.");
    }

    private void actionEndSession() {
        if (viewedSession == null || !viewedSession.isActive())
            return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Akhiri sesi '" + viewedSession.getTitle() + "' dan SIMPAN hasil?", "Konfirmasi",
                JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            viewedSession.endSession();
            activeSessions.remove(viewedSession);
            DatabaseManager.saveSession(viewedSession);

            refreshMonitorUI();
            updateDashboardTable();
            updateHistoryTable();
            JOptionPane.showMessageDialog(this, "Sesi Ditutup & Data Tersimpan!");
        }
    }

    private void updateDashboardTable() {
        tableModelDashboard.setRowCount(0);

        lblStatTotalSessions.setText(String.valueOf(historySessions.size()));

        int startIdx = historySessions.size() - 1;
        int endIdx = Math.max(0, historySessions.size() - 5);

        int displayId = 1;
        for (int i = startIdx; i >= endIdx; i--) {
            VotingSession vs = historySessions.get(i);
            String status = vs.isActive() ? "🟢 Aktif" : "🔴 Selesai";
            tableModelDashboard.addRow(new Object[] { displayId++, vs.getTitle(), status, vs.getWinnerResult(),
                    vs.getTotalVotes() + " Suara" });
        }
    }

    private void updateHistoryTable() {
        updateHistoryTable("");
    }

    private void updateHistoryTable(String searchQuery) {
        tableModelHistory.setRowCount(0);
        int displayId = 1;
        String query = searchQuery.toLowerCase().trim();

        for (int i = historySessions.size() - 1; i >= 0; i--) {
            VotingSession vs = historySessions.get(i);

            if (!query.isEmpty() && !vs.getTitle().toLowerCase().contains(query)) {
                continue;
            }

            String status = vs.isActive() ? "🟢 Aktif" : "🔴 Selesai";
            Date date = new Date(vs.getStartTime());
            tableModelHistory.addRow(new Object[] { displayId++, vs.getTitle(), status, vs.getWinnerResult(),
                    vs.getTotalVotes(), date.toString() });
        }
    }

    private void updateRealtimeStats() {
        if (viewedSession != null && viewedSession.isActive()) {
            lblStatTotalVotes.setText(String.valueOf(viewedSession.getTotalVotes()));
            liveGraphPanel.repaint();
        }
    }

    private void showAdvancedStressTestDialog() {
        JDialog dialog = new JDialog(this, "Stress Test Configuration", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(400, 400);
        dialog.setLocationRelativeTo(this);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        content.add(new JLabel("Target Sesi:"));
        JComboBox<String> comboSession = new JComboBox<>();

        for (VotingSession vs : activeSessions) {
            comboSession.addItem("LIVE: " + vs.getTitle());
        }
        for (int i = historySessions.size() - 1; i >= Math.max(0, historySessions.size() - 5); i--) {
            VotingSession vs = historySessions.get(i);
            if (!vs.isActive()) {
                comboSession.addItem("ARCHIVE: " + vs.getTitle());
            }
        }
        content.add(comboSession);
        content.add(Box.createVerticalStrut(15));

        content.add(new JLabel("Mode Threading:"));
        JRadioButton radioSafe = new JRadioButton("Safe (Synchronized)", true);
        JRadioButton radioUnsafe = new JRadioButton("Unsafe (Race Condition)");
        ButtonGroup grpThread = new ButtonGroup();
        grpThread.add(radioSafe);
        grpThread.add(radioUnsafe);
        content.add(radioSafe);
        content.add(radioUnsafe);
        content.add(Box.createVerticalStrut(15));

        content.add(new JLabel("Mode Penyimpanan:"));
        JRadioButton radioSave = new JRadioButton("Save to DB (Jika Live)", true);
        JRadioButton radioNoSave = new JRadioButton("No Save (RAM Simulation)");
        ButtonGroup grpPersist = new ButtonGroup();
        grpPersist.add(radioSave);
        grpPersist.add(radioNoSave);
        content.add(radioSave);
        content.add(radioNoSave);
        content.add(Box.createVerticalStrut(20));

        JButton btnStart = new JButton("MULAI SIMULASI (50 Votes)");
        btnStart.addActionListener(e -> {
            String selectedItem = (String) comboSession.getSelectedItem();
            if (selectedItem == null)
                return;

            VotingSession target = null;
            if (selectedItem.startsWith("LIVE")) {
                for (VotingSession vs : activeSessions) {
                    if (selectedItem.contains(vs.getTitle())) {
                        target = vs;
                        break;
                    }
                }
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

        for (int i = 0; i < 50; i++) {
            new Thread(() -> {
                String rand = candidates.get((int) (Math.random() * candidates.size()));

                if (isUnsafe) {
                    target.addVoteUnsafe(rand);
                } else {
                    target.addVote(rand);
                }

                SwingUtilities.invokeLater(() -> {
                    liveGraphPanel.repaint();
                    if (activeSessions.contains(target))
                        lblStatTotalVotes.setText(String.valueOf(target.getTotalVotes()));
                });
            }).start();
        }
    }

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
        DataInputStream in = null;
        DataOutputStream out = null;
        try {
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            activeClientStreams.add(out);

            StringBuilder sbHistory = new StringBuilder("HISTORY_LIST|");
            for (VotingSession vs : historySessions) {
                sbHistory.append(vs.getTitle()).append(";")
                        .append(vs.isActive()).append(";")
                        .append(vs.getWinnerResult()).append(";")
                        .append(vs.getVoteSummary()).append("#");
            }
            out.writeUTF(sbHistory.toString());

            sendActiveSessionsPayload(out);

            while (true) {
                String msg = in.readUTF();
                if (msg.startsWith("VOTE|")) {
                    String[] parts = msg.split("\\|");
                    if (parts.length >= 3) {
                        String sessionTitle = parts[1];
                        String candidateName = parts[2];

                        for (VotingSession vs : activeSessions) {
                            if (vs.getTitle().equals(sessionTitle)) {
                                vs.addVote(candidateName);
                                SwingUtilities.invokeLater(this::updateRealtimeStats);
                                broadcastUpdate();
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        } finally {
            if (out != null)
                activeClientStreams.remove(out);
            connectedClients--;
            SwingUtilities.invokeLater(() -> lblStatClients.setText(String.valueOf(connectedClients)));
            try {
                socket.close();
            } catch (IOException ex) {
            }
        }
    }

    private void sendActiveSessionsPayload(DataOutputStream out) throws IOException {
        List<VotingSession> liveSessions = new ArrayList<>(activeSessions);

        if (!liveSessions.isEmpty()) {
            out.writeUTF("MULTI_SETUP|" + liveSessions.size());

            for (VotingSession session : liveSessions) {
                out.writeUTF(session.getTitle());
                java.util.Set<String> candidates = session.getCandidates();
                out.writeInt(candidates.size());

                for (String name : candidates) {
                    out.writeUTF(name);
                    String path = session.getCandidateImage(name);
                    File imgFile = new File(path);
                    if (imgFile.exists() && !imgFile.isDirectory()) {
                        byte[] bytes = Files.readAllBytes(imgFile.toPath());
                        out.writeInt(bytes.length);
                        out.write(bytes);
                    } else {
                        out.writeInt(0);
                    }
                }
            }
        } else {
            out.writeUTF("WAIT|Tidak ada sesi voting aktif.");
        }
    }

    private void broadcastUpdate() {
        synchronized (activeClientStreams) {
            for (DataOutputStream out : activeClientStreams) {
                try {
                    for (VotingSession vs : activeSessions) {
                        out.writeUTF("REFRESH_STATS|" + vs.getTitle() + "|" + vs.getVoteSummary());
                    }
                } catch (IOException e) {
                }
            }
        }
    }

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

    private void openCandidateManager() {
        if (viewedSession == null || !viewedSession.isActive())
            return;

        JDialog dialog = new JDialog(this, "Kelola Kandidat: " + viewedSession.getTitle(), true);
        dialog.setSize(500, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        for (String name : viewedSession.getCandidates()) {
            JPanel item = new JPanel(new BorderLayout(10, 0));
            item.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Color.LIGHT_GRAY),
                    new EmptyBorder(10, 10, 10, 10)));

            JLabel lblName = new JLabel(name);
            lblName.setFont(AppTheme.FONT_BOLD);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

            JButton btnEdit = new JButton("✏");
            btnEdit.addActionListener(e -> {
                String newName = JOptionPane.showInputDialog(dialog, "Ganti nama:", name);
                if (newName != null && !newName.trim().isEmpty() && !newName.equals(name)) {
                    if (viewedSession.updateCandidateName(name, newName)) {
                        dialog.dispose();
                        openCandidateManager();
                        refreshMonitorUI();
                    } else {
                        JOptionPane.showMessageDialog(dialog, "Nama sudah ada atau tidak valid!");
                    }
                }
            });

            JButton btnDelete = new JButton("🗑");
            btnDelete.setForeground(Color.RED);
            btnDelete.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(dialog, "Hapus kandidat ini? Suara akan hilang!", "Hapus",
                        JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    viewedSession.removeCandidate(name);
                    dialog.dispose();
                    openCandidateManager();
                    refreshMonitorUI();
                }
            });

            actions.add(btnEdit);
            actions.add(btnDelete);

            item.add(lblName, BorderLayout.CENTER);
            item.add(actions, BorderLayout.EAST);

            listPanel.add(item);
            listPanel.add(Box.createVerticalStrut(10));
        }

        dialog.add(new JScrollPane(listPanel), BorderLayout.CENTER);

        JButton btnAdd = new JButton("+ Tambah Kandidat Baru");
        btnAdd.setFont(AppTheme.FONT_BOLD);
        btnAdd.addActionListener(e -> {
            String newName = JOptionPane.showInputDialog(dialog, "Nama Kandidat Baru:");
            if (newName != null && !newName.trim().isEmpty()) {
                if (viewedSession.addCandidate(newName, "")) {
                    dialog.dispose();
                    openCandidateManager();
                    refreshMonitorUI();
                } else {
                    JOptionPane.showMessageDialog(dialog, "Gagal tambah. Nama mungkin duplikat.");
                }
            }
        });

        dialog.add(btnAdd, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

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