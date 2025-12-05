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
 * 🖥️ SERVER ADMIN: IMPROVED UX (V3)
 * =============================================================================
 * FITUR BARU:
 * 1. Login Admin (Security).
 * 2. Tabel History Bisa Diklik (Load ke Monitor).
 * 3. Monitor Fleksibel (Bisa lihat Live vs Arsip).
 * 4. Form Cerdas (Hapus Baris, Hapus Foto, Reset Form).
 * 5. Validasi Nama Kembar & Kosong.
 */
public class ServerAdmin extends JFrame {

    // =========================================================================
    // 🧠 DATA & STATE
    // =========================================================================
    private List<VotingSession> historySessions = new ArrayList<>();

    // `currentSession` = Sesi yang sedang LIVE / Menerima Suara (Backend Logic)
    private VotingSession currentSession;

    // `viewedSession` = Sesi yang sedang DILIHAT di layar Monitor (UI Logic)
    // Bisa sama dengan currentSession, bisa juga sesi masa lalu (History).
    private VotingSession viewedSession;

    private boolean isServerRunning = true;
    private int connectedClients = 0;

    // List untuk melacak input UI kandidat (Dynamic Input)
    private List<CandidateInputRow> inputRows = new ArrayList<>();

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
    private JTable historyTable; // Disimpan reference-nya

    // Monitor Components
    private GraphPanel liveGraphPanel;
    private JLabel lblGraphTitle, lblGraphStatus;
    private JButton btnEndVote;
    private JButton btnBackToLive; // Tombol baru untuk kembali ke sesi aktif
    private JPanel monitorHeaderPanel;

    // Create Session Components
    private JTextField txtSessionTitle;
    private JPanel candidatesContainer;

    public ServerAdmin() {
        // 1. LOGIN DIALOG (Goals 5)
        if (!showLoginDialog()) {
            System.exit(0);
        }

        setTitle("Admin Dashboard - E-Voting System (V3 UX Upgrade)");
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

    /**
     * Menampilkan dialog login sederhana.
     * Username: admin, Password: admin
     */
    private boolean showLoginDialog() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        JTextField txtUser = new JTextField();
        JPasswordField txtPass = new JPasswordField();

        panel.add(new JLabel("Username:"));
        panel.add(txtUser);
        panel.add(new JLabel("Password:"));
        panel.add(txtPass);

        while (true) {
            int result = JOptionPane.showConfirmDialog(null, panel, "Login Administrator",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (result == JOptionPane.OK_OPTION) {
                String user = txtUser.getText();
                String pass = new String(txtPass.getPassword());

                // Validasi Sederhana
                if (user.equals("admin") && pass.equals("admin")) {
                    return true;
                } else {
                    JOptionPane.showMessageDialog(null, "Username atau Password salah!");
                }
            } else {
                return false; // User cancel
            }
        }
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

        // Initial data update
        updateHistoryTable();
    }

    private void switchPage(String pageName, AppTheme.SidebarButton activeButton) {
        contentLayout.show(mainContentPanel, pageName);
        btnNavDash.setActive(false);
        btnNavCreate.setActive(false);
        btnNavMonitor.setActive(false);
        btnStressTest.setActive(false);
        activeButton.setActive(true);

        if (pageName.equals("PAGE_DASHBOARD"))
            updateHistoryTable();
        if (pageName.equals("PAGE_MONITOR"))
            refreshMonitorUI(); // Refresh based on viewedSession
    }

    // =========================================================================
    // 📄 PAGE 1: DASHBOARD
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

        statsGrid.add(createStatCard("Total Suara (Live)", lblStatTotalVotes));
        statsGrid.add(createStatCard("Sesi Terpilih", lblStatActiveSession));
        statsGrid.add(createStatCard("Client Terhubung", lblStatClients));

        // Tabel Riwayat
        JPanel tablePanel = AppTheme.createShadowPanel();
        tablePanel.setLayout(new BorderLayout());

        JLabel lblTableTitle = new JLabel("Riwayat Sesi (Klik untuk Detail Grafik)");
        lblTableTitle.setFont(AppTheme.FONT_H2);
        lblTableTitle.setBorder(new EmptyBorder(0, 0, 15, 0));

        String[] columns = { "ID", "Judul Sesi", "Status", "Pemenang / Hasil", "Total Suara" };
        tableModelHistory = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Agar tabel tidak bisa diedit manual
            }
        };
        historyTable = new JTable(tableModelHistory);

        historyTable.setRowHeight(40);
        historyTable.setShowVerticalLines(false);
        historyTable.setGridColor(new Color(241, 245, 249));
        historyTable.setFont(AppTheme.FONT_BODY);
        historyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // --- GOALS 1: TABLE INTERACTIVE ---
        historyTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1) { // Single click cukup
                    int row = historyTable.getSelectedRow();
                    if (row != -1) {
                        loadSessionToMonitorFromTable(row);
                    }
                }
            }
        });

        JTableHeader header = historyTable.getTableHeader();
        header.setFont(AppTheme.FONT_BOLD);
        header.setBackground(Color.WHITE);
        header.setForeground(AppTheme.COLOR_TEXT_MUTED);
        header.setPreferredSize(new Dimension(0, 45));

        JScrollPane scrollPane = new JScrollPane(historyTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(Color.WHITE);

        tablePanel.add(lblTableTitle, BorderLayout.NORTH);
        tablePanel.add(scrollPane, BorderLayout.CENTER);

        centerContainer.add(statsGrid, BorderLayout.NORTH);
        centerContainer.add(tablePanel, BorderLayout.CENTER);

        panel.add(centerContainer, BorderLayout.CENTER);
        return panel;
    }

    private void loadSessionToMonitorFromTable(int rowIndex) {
        // Karena tableModel diisi descending (terbaru di atas),
        // dan list historySessions diisi sequential (terbaru di akhir),
        // Kita harus mapping index tabel ke index list.

        // Logika di updateHistoryTable: loop i = size-1 down to 0.
        // Baris 0 di tabel = historySessions.get(size - 1)
        int sessionIndex = historySessions.size() - 1 - rowIndex;

        if (sessionIndex >= 0 && sessionIndex < historySessions.size()) {
            viewedSession = historySessions.get(sessionIndex);
            switchPage("PAGE_MONITOR", btnNavMonitor);

            // Beri notifikasi kecil (opsional) atau update status UI otomatis di switchPage
        }
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
    // 📄 PAGE 2: CREATE SESSION (DYNAMIC INPUT + IMAGE UPLOAD)
    // =========================================================================
    private JPanel createPageCreateSession() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(40, 40, 40, 40));

        // --- Form Container ---
        JPanel formCard = AppTheme.createShadowPanel();
        formCard.setLayout(new BorderLayout(0, 20));

        // Header Input
        JPanel topInput = new JPanel(new GridLayout(0, 1, 0, 10));
        topInput.setOpaque(false);
        JLabel lblHead = new JLabel("Buat Sesi Voting Baru");
        lblHead.setFont(AppTheme.FONT_H1);

        txtSessionTitle = new JTextField();
        AppTheme.styleTextField(txtSessionTitle);
        topInput.add(lblHead);
        topInput.add(new JLabel("Judul Kegiatan"));
        topInput.add(txtSessionTitle);

        // Container Kandidat (Dynamic)
        candidatesContainer = new JPanel();
        candidatesContainer.setLayout(new BoxLayout(candidatesContainer, BoxLayout.Y_AXIS));
        candidatesContainer.setOpaque(false);

        JScrollPane scrollCandidates = new JScrollPane(candidatesContainer);
        scrollCandidates.setBorder(null);
        scrollCandidates.getVerticalScrollBar().setUnitIncrement(16);
        scrollCandidates.setPreferredSize(new Dimension(0, 300));

        // Tombol Actions
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

        // Tambah 2 row awal secara default
        resetFormToDefault();

        return panel;
    }

    private void resetFormToDefault() {
        candidatesContainer.removeAll();
        inputRows.clear();
        txtSessionTitle.setText("");

        // Add 2 baris default
        addCandidateRow();
        addCandidateRow();

        candidatesContainer.revalidate();
        candidatesContainer.repaint();
    }

    /**
     * Menambahkan baris input kandidat baru ke UI
     */
    private void addCandidateRow() {
        CandidateInputRow row = new CandidateInputRow(inputRows.size() + 1);
        inputRows.add(row);
        candidatesContainer.add(row.panel);
        candidatesContainer.add(Box.createVerticalStrut(10)); // Spacer
        candidatesContainer.revalidate();
        candidatesContainer.repaint();

        // Update penomoran jika perlu (opsional, tapi lebih rapi)
        updateRowNumbers();
    }

    private void removeCandidateRow(CandidateInputRow row) {
        if (inputRows.size() <= 2) {
            JOptionPane.showMessageDialog(this, "Minimal harus ada 2 kandidat!");
            return;
        }

        inputRows.remove(row);
        candidatesContainer.remove(row.panel);

        // Perlu remove juga spacernya, tapi karena kita pake Box.createVerticalStrut
        // yang anonim, agak susah.
        // Cara gampangnya: Rebuild semua dari list inputRows, atau simply refresh UI.
        // Untuk code ini, spacer akan tertinggal.
        // Solusi: Kita refresh container.

        candidatesContainer.removeAll();
        for (CandidateInputRow r : inputRows) {
            candidatesContainer.add(r.panel);
            candidatesContainer.add(Box.createVerticalStrut(10));
        }

        updateRowNumbers();
        candidatesContainer.revalidate();
        candidatesContainer.repaint();
    }

    private void updateRowNumbers() {
        for (int i = 0; i < inputRows.size(); i++) {
            inputRows.get(i).updateLabel(i + 1);
        }
    }

    /**
     * Helper Class untuk mengelola setiap baris input kandidat.
     * UPDATE: Hapus Baris & Hapus Foto
     */
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
            panel.setMaximumSize(new Dimension(2000, 60)); // Limit tinggi agar rapi

            lblNumber = new JLabel("Kandidat #" + number);

            txtName = new JTextField();
            AppTheme.styleTextField(txtName);
            txtName.setPreferredSize(new Dimension(300, 40));

            // Container Tombol Kanan
            JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            rightPanel.setOpaque(false);

            // Upload Button
            JButton btnUpload = new JButton("📷");
            btnUpload.setToolTipText("Upload Foto");
            btnUpload.setBackground(Color.WHITE);
            btnUpload.addActionListener(e -> chooseImage());

            // Remove Image Button (Hidden by default)
            btnRemoveImage = new JButton("🗑️ Foto");
            btnRemoveImage.setToolTipText("Hapus Foto");
            btnRemoveImage.setForeground(Color.RED);
            btnRemoveImage.setVisible(false);
            btnRemoveImage.addActionListener(e -> removeImage());

            lblFile = new JLabel("No Image");
            lblFile.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            lblFile.setForeground(Color.GRAY);

            // Delete Row Button (X)
            JButton btnDeleteRow = new JButton("❌");
            btnDeleteRow.setToolTipText("Hapus Baris Ini");
            btnDeleteRow.setForeground(Color.RED);
            btnDeleteRow.setBorderPainted(false);
            btnDeleteRow.setContentAreaFilled(false);
            btnDeleteRow.setFont(new Font("Segoe UI", Font.BOLD, 16));
            btnDeleteRow.addActionListener(e -> removeCandidateRow(this));

            rightPanel.add(btnUpload);
            rightPanel.add(btnRemoveImage);
            rightPanel.add(lblFile);
            rightPanel.add(Box.createHorizontalStrut(10));
            rightPanel.add(btnDeleteRow);

            panel.add(lblNumber, BorderLayout.WEST);
            panel.add(txtName, BorderLayout.CENTER);
            panel.add(rightPanel, BorderLayout.EAST);
        }

        public void updateLabel(int num) {
            lblNumber.setText("Kandidat #" + num);
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

    // =========================================================================
    // 📄 PAGE 3: MONITOR (FLEXIBLE)
    // =========================================================================
    private JPanel createPageMonitor() {
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(40, 40, 40, 40));

        // Header Monitor
        monitorHeaderPanel = new JPanel(new BorderLayout());
        monitorHeaderPanel.setOpaque(false);

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

        // Container Tombol Kanan (End Vote & Back to Live)
        JPanel btnContainer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnContainer.setOpaque(false);

        // Tombol Back to Live (Goals 2)
        btnBackToLive = new JButton("⬅️ KEMBALI KE LIVE SESSION");
        btnBackToLive.setFont(AppTheme.FONT_BOLD);
        btnBackToLive.setBackground(new Color(59, 130, 246));
        btnBackToLive.setForeground(Color.WHITE);
        btnBackToLive.setVisible(false);
        btnBackToLive.addActionListener(e -> {
            viewedSession = currentSession;
            refreshMonitorUI();
        });

        // Tombol End Vote
        btnEndVote = new JButton("AKHIRI SESI & SIMPAN");
        btnEndVote.setFont(AppTheme.FONT_BOLD);
        btnEndVote.setForeground(Color.WHITE);
        btnEndVote.setBackground(new Color(220, 38, 38));
        btnEndVote.setFocusPainted(false);
        btnEndVote.setVisible(false);
        btnEndVote.addActionListener(e -> actionEndSession());

        btnContainer.add(btnBackToLive);
        btnContainer.add(btnEndVote);

        monitorHeaderPanel.add(titleBox, BorderLayout.WEST);
        monitorHeaderPanel.add(btnContainer, BorderLayout.EAST);

        liveGraphPanel = new GraphPanel();

        panel.add(monitorHeaderPanel, BorderLayout.NORTH);
        panel.add(liveGraphPanel, BorderLayout.CENTER);

        return panel;
    }

    // =========================================================================
    // ⚙️ BUSINESS LOGIC
    // =========================================================================

    private void startNewSession() {
        String title = txtSessionTitle.getText().trim();
        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Judul sesi tidak boleh kosong!");
            return;
        }

        // Kumpulkan data & Validasi
        List<String> validNames = new ArrayList<>();
        List<String> validPaths = new ArrayList<>();
        Set<String> uniqueCheck = new HashSet<>();

        // Buat folder 'server_images' jika belum ada
        File imgDir = new File("server_images");
        if (!imgDir.exists())
            imgDir.mkdir();

        for (CandidateInputRow row : inputRows) {
            String name = row.txtName.getText().trim();

            // Goals 4: Cek Kosong
            if (name.isEmpty())
                continue;

            // Goals 4: Cek Kembar (Case Insensitive)
            if (uniqueCheck.contains(name.toLowerCase())) {
                JOptionPane.showMessageDialog(this, "Nama kandidat tidak boleh kembar: " + name);
                return;
            }
            uniqueCheck.add(name.toLowerCase());

            validNames.add(name);

            // Proses Copy Gambar
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
                    System.err.println("Gagal copy gambar: " + ex.getMessage());
                }
            }
            validPaths.add(finalPath);
        }

        if (validNames.size() < 2) {
            JOptionPane.showMessageDialog(this, "Minimal masukkan 2 kandidat yang valid!");
            return;
        }

        // Buat Sesi Baru
        currentSession = new VotingSession(
                title,
                validNames.toArray(new String[0]),
                validPaths.toArray(new String[0]));
        historySessions.add(currentSession);

        // Set viewedSession ke sesi yang baru dibuat
        viewedSession = currentSession;

        // Goals 3: Reset Form Total
        resetFormToDefault();

        // Pindah ke Monitor
        switchPage("PAGE_MONITOR", btnNavMonitor);
        updateHistoryTable();

        JOptionPane.showMessageDialog(this, "Sesi DIBUKA! Client akan menerima data.");
    }

    private void actionEndSession() {
        if (currentSession == null || !currentSession.isActive())
            return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "Akhiri sesi dan SIMPAN hasil ke Database?",
                "Konfirmasi", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            currentSession.endSession();
            DatabaseManager.saveSession(currentSession);
            refreshMonitorUI();
            updateHistoryTable();
            JOptionPane.showMessageDialog(this, "Sesi Ditutup & Data Tersimpan!");
        }
    }

    /**
     * Memperbarui UI Monitor berdasarkan `viewedSession`.
     * Bisa menampilkan sesi LIVE atau sesi ARSIP (History).
     */
    private void refreshMonitorUI() {
        if (viewedSession != null) {
            lblGraphTitle.setText(viewedSession.getTitle());

            // Logic untuk menentukan Status Label
            boolean isWatchingLive = (viewedSession == currentSession) && currentSession.isActive();

            if (isWatchingLive) {
                lblGraphStatus.setText("Status: 🟢 LIVE VOTING");
                lblGraphStatus.setForeground(new Color(22, 163, 74));

                // Button End Vote hanya aktif jika kita nonton sesi live
                btnEndVote.setVisible(true);
                btnEndVote.setText("AKHIRI SESI & SIMPAN");
                btnEndVote.setEnabled(true);
                btnEndVote.setBackground(new Color(220, 38, 38));

                // Tidak perlu tombol Back to Live karena kita sedang di Live
                btnBackToLive.setVisible(false);
            } else {
                // Kita sedang melihat Arsip atau Sesi Live yang sudah selesai
                lblGraphStatus.setText("Status: 🏁 ARSIP / SELESAI");
                lblGraphStatus.setForeground(Color.GRAY);

                btnEndVote.setVisible(false); // Tidak bisa end session lagi

                // Tampilkan tombol Back to Live jika ada sesi live yang berjalan di background
                if (currentSession != null && currentSession.isActive() && viewedSession != currentSession) {
                    btnBackToLive.setVisible(true);
                } else {
                    btnBackToLive.setVisible(false);
                }
            }

            // Update Stats Dashboard juga jika yang dilihat adalah sesi live
            if (viewedSession == currentSession) {
                lblStatActiveSession.setText(currentSession.getTitle());
                lblStatTotalVotes.setText(String.valueOf(currentSession.getTotalVotes()));
            }

        } else {
            lblGraphTitle.setText("Belum ada sesi dipilih");
            lblGraphStatus.setText("-");
            btnEndVote.setVisible(false);
            btnBackToLive.setVisible(false);
        }
        liveGraphPanel.repaint();
    }

    private void updateHistoryTable() {
        tableModelHistory.setRowCount(0);
        int id = 1;
        // Tampilkan sesi terbaru di atas
        for (int i = historySessions.size() - 1; i >= 0; i--) {
            VotingSession vs = historySessions.get(i);
            String status = vs.isActive() ? "🟢 Aktif" : "🔴 Selesai";

            tableModelHistory.addRow(new Object[] {
                    id++,
                    vs.getTitle(),
                    status,
                    vs.getWinnerResult(),
                    vs.getTotalVotes() + " Suara"
            });
        }
    }

    private void updateRealtimeStats() {
        // Hanya repaint jika kita sedang menonton sesi yang sedang aktif
        if (viewedSession == currentSession) {
            lblStatTotalVotes.setText(String.valueOf(currentSession.getTotalVotes()));
            liveGraphPanel.repaint();
        }
    }

    // =========================================================================
    // 📡 SERVER ENGINE (PROTOKOL V2)
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
        try (
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            // 1. KIRIM HISTORY
            StringBuilder sbHistory = new StringBuilder("HISTORY_LIST|");
            for (VotingSession vs : historySessions) {
                sbHistory.append(vs.getTitle()).append(";")
                        .append(vs.isActive()).append(";")
                        .append(vs.getWinnerResult()).append("#");
            }
            out.writeUTF(sbHistory.toString());

            // 2. KIRIM SETUP (BINARY MODE)
            if (currentSession != null && currentSession.isActive()) {
                out.writeUTF("SETUP_V2_IMAGES");
                out.writeUTF(currentSession.getTitle());

                java.util.Set<String> candidates = currentSession.getCandidates();
                out.writeInt(candidates.size());

                for (String name : candidates) {
                    out.writeUTF(name); // Kirim Nama

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

            // 3. LISTEN FOR VOTES
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
            // Client disconnect
        } finally {
            connectedClients--;
            SwingUtilities.invokeLater(() -> lblStatClients.setText(String.valueOf(connectedClients)));
        }
    }

    // =========================================================================
    // 📊 GRAPH & STRESS TEST
    // =========================================================================

    private void showStressTestDialog() {
        if (currentSession == null || !currentSession.isActive()) {
            JOptionPane.showMessageDialog(this, "⚠️ Tidak ada sesi AKTIF untuk ditest!");
            return;
        }

        // Pastikan kita menonton sesi live agar grafik gerak
        viewedSession = currentSession;
        switchPage("PAGE_MONITOR", btnNavMonitor);

        List<String> candidates = new ArrayList<>(currentSession.getCandidates());
        for (int i = 0; i < 50; i++) { // Kirim 50 suara acak
            new Thread(() -> {
                String rand = candidates.get((int) (Math.random() * candidates.size()));
                currentSession.addVoteUnsafe(rand);
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

            // Background
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);
            g2.setColor(new Color(0, 0, 0, 10));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 30, 30);

            if (viewedSession == null) { // Gunakan viewedSession, bukan currentSession
                g2.setColor(AppTheme.COLOR_TEXT_MUTED);
                g2.drawString("Pilih sesi dari riwayat atau buat baru.", getWidth() / 2 - 80, getHeight() / 2);
                return;
            }
            drawChart(g2);
        }

        private void drawChart(Graphics2D g2) {
            int w = getWidth();
            int h = getHeight();
            int padding = 60;

            int maxVotes = 1;
            // Gunakan viewedSession
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