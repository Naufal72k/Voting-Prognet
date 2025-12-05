import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;

/**
 * =============================================================================
 * 🗳️ VOTER CLIENT: IMPROVED UX (V3.2)
 * =============================================================================
 * UPDATE LOG V3.2:
 * - Layout Galeri menjadi Grid 3 Kolom (GridLayout).
 * - Tombol "Lihat Detail" menampilkan Tabel Statistik Suara.
 * - Sidebar Baru: Galeri, Panduan, Info (Menghapus tombol Vote terpisah).
 */
public class VoterClient extends JFrame {

    // =========================================================================
    // 🧠 DATA & STATE
    // =========================================================================
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    // Identitas Pemilih
    private String voterNIK = "Guest"; // Default Guest
    private boolean isLoggedIn = true; // Langsung true

    // Data Sesi
    private List<SessionInfo> sessionList = new ArrayList<>();
    private SessionInfo activeSession = null; // Sesi yang sedang LIVE data-nya dari server

    // Data Kandidat Aktif (Nama & Foto)
    private List<String> activeCandidates = new ArrayList<>();
    private Map<String, ImageIcon> candidatePhotos = new HashMap<>();

    // Security & State Flag
    private Set<String> votedSessions = new HashSet<>();

    // =========================================================================
    // 🎨 UI COMPONENTS
    // =========================================================================
    private CardLayout contentLayout;
    private JPanel mainContentPanel;
    private JPanel galleryContainer;
    private JPanel sidebarPanel;

    // Sidebar Buttons (Redesigned)
    private AppTheme.SidebarButton btnNavGallery;
    private AppTheme.SidebarButton btnNavGuide; // NEW
    private AppTheme.SidebarButton btnNavInfo; // NEW

    public VoterClient() {
        setTitle("E-Voting Terminal - Client (V3.2)");
        setSize(1280, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        initSidebar();
        initContentArea();

        // 1. SKIP LOGIN -> Langsung ke Galeri
        setSidebarEnabled(true);
        switchPage("PAGE_GALLERY", btnNavGallery);

        // Start Connection di background
        new Thread(this::connectAndSetup).start();
    }

    // =========================================================================
    // 🏗️ LAYOUT: SIDEBAR (WEST)
    // =========================================================================
    private void initSidebar() {
        sidebarPanel = new JPanel();
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
        sidebarPanel.setBackground(AppTheme.COLOR_SIDEBAR);
        sidebarPanel.setPreferredSize(new Dimension(90, getHeight()));
        sidebarPanel.setBorder(new EmptyBorder(30, 0, 0, 0));

        JLabel lblLogo = new JLabel("🗳️");
        lblLogo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
        lblLogo.setForeground(Color.WHITE);
        lblLogo.setAlignmentX(Component.CENTER_ALIGNMENT);

        // --- NEW MENU STRUCTURE ---
        btnNavGallery = AppTheme.createSidebarButton("🏠", true);
        btnNavGallery.setToolTipText("Galeri Pemilihan");

        btnNavGuide = AppTheme.createSidebarButton("📖", false);
        btnNavGuide.setToolTipText("Panduan Memilih");

        btnNavInfo = AppTheme.createSidebarButton("ℹ️", false);
        btnNavInfo.setToolTipText("Tentang Aplikasi");

        // --- NAVIGASI ---
        btnNavGallery.addActionListener(e -> {
            refreshGalleryUI();
            switchPage("PAGE_GALLERY", btnNavGallery);
        });

        btnNavGuide.addActionListener(e -> switchPage("PAGE_GUIDE", btnNavGuide));

        btnNavInfo.addActionListener(e -> switchPage("PAGE_INFO", btnNavInfo));

        sidebarPanel.add(lblLogo);
        sidebarPanel.add(Box.createVerticalStrut(50));
        addSidebarItem(sidebarPanel, btnNavGallery);
        addSidebarItem(sidebarPanel, btnNavGuide);
        addSidebarItem(sidebarPanel, btnNavInfo);
        sidebarPanel.add(Box.createVerticalGlue());

        add(sidebarPanel, BorderLayout.WEST);
    }

    private void addSidebarItem(JPanel sidebar, JComponent item) {
        item.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(item);
        sidebar.add(Box.createVerticalStrut(15));
    }

    private void setSidebarEnabled(boolean enabled) {
        btnNavGallery.setEnabled(enabled);
        btnNavGuide.setEnabled(enabled);
        btnNavInfo.setEnabled(enabled);
        sidebarPanel.setVisible(enabled);
    }

    // =========================================================================
    // 🏗️ LAYOUT: CONTENT AREA (CENTER)
    // =========================================================================
    private void initContentArea() {
        contentLayout = new CardLayout();
        mainContentPanel = new JPanel(contentLayout);
        mainContentPanel.setBackground(AppTheme.COLOR_BACKGROUND_APP);

        // Urutan Halaman
        mainContentPanel.add(createPageLoading(), "PAGE_LOADING");
        mainContentPanel.add(createPageGallery(), "PAGE_GALLERY");
        mainContentPanel.add(createPageGuide(), "PAGE_GUIDE"); // NEW
        mainContentPanel.add(createPageInfo(), "PAGE_INFO"); // NEW
        mainContentPanel.add(createPageSuccess(), "PAGE_SUCCESS");
        // PAGE_BALLOT ditambahkan dinamis

        add(mainContentPanel, BorderLayout.CENTER);
    }

    private void switchPage(String pageName, AppTheme.SidebarButton activeButton) {
        contentLayout.show(mainContentPanel, pageName);
        btnNavGallery.setActive(false);
        btnNavGuide.setActive(false);
        btnNavInfo.setActive(false);

        if (activeButton != null) {
            activeButton.setActive(true);
        }
    }

    // =========================================================================
    // 📄 PAGE 1: LOADING SCREEN
    // =========================================================================
    private JPanel createPageLoading() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        JLabel lbl = new JLabel("Menghubungkan ke Server & Mengunduh Aset...");
        lbl.setFont(AppTheme.FONT_H2);
        lbl.setForeground(AppTheme.COLOR_TEXT_MUTED);
        panel.add(lbl);
        return panel;
    }

    // =========================================================================
    // 📄 PAGE 2: GALLERY PEMILIHAN (GRID 3 KOLOM)
    // =========================================================================
    private JPanel createPageGallery() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(40, 40, 40, 40));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel lblTitle = new JLabel("Galeri Pemilihan");
        lblTitle.setFont(AppTheme.FONT_H1);
        lblTitle.setForeground(AppTheme.COLOR_TEXT_MAIN);

        JLabel lblUser = new JLabel("Mode: " + voterNIK);
        lblUser.setFont(AppTheme.FONT_BODY);
        lblUser.setForeground(AppTheme.COLOR_PRIMARY_START);

        header.add(lblTitle, BorderLayout.WEST);
        header.add(lblUser, BorderLayout.EAST);

        panel.add(header, BorderLayout.NORTH);

        // --- UPDATE LAYOUT: GRID 3 KOLOM ---
        galleryContainer = new JPanel(new GridLayout(0, 3, 30, 30));
        galleryContainer.setOpaque(false);

        // Panel Wrapper agar Grid tidak stretch jelek jika item sedikit
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(galleryContainer, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(wrapper); // Scroll wrapper
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSessionCard(SessionInfo session) {
        JPanel card = AppTheme.createShadowPanel();
        card.setPreferredSize(new Dimension(300, 240));
        card.setLayout(new BorderLayout());
        card.setBackground(Color.WHITE);

        boolean hasVotedInThisSession = votedSessions.contains(session.title);

        // --- TOP PANEL (Status) ---
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        JLabel lblStatus;
        if (hasVotedInThisSession) {
            lblStatus = new JLabel("● SUDAH MEMILIH");
            lblStatus.setForeground(Color.GRAY);
        } else {
            lblStatus = new JLabel(session.isActive ? "● LIVE" : "● SELESAI");
            lblStatus.setForeground(session.isActive ? new Color(22, 163, 74) : new Color(220, 38, 38));
        }
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        topPanel.add(lblStatus, BorderLayout.WEST);

        if (hasVotedInThisSession) {
            JLabel badge = new JLabel("✅");
            topPanel.add(badge, BorderLayout.EAST);
        }

        // --- CENTER PANEL (Info) ---
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("<html>" + session.title + "</html>");
        lblTitle.setFont(AppTheme.FONT_H2);
        lblTitle.setForeground(hasVotedInThisSession ? AppTheme.COLOR_TEXT_MUTED : AppTheme.COLOR_TEXT_MAIN);

        JLabel lblWinner = new JLabel("Pemenang:");
        lblWinner.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblWinner.setForeground(AppTheme.COLOR_TEXT_MUTED);

        JLabel lblWinnerName = new JLabel(session.isActive ? "-" : session.winner);
        lblWinnerName.setFont(AppTheme.FONT_BOLD);
        lblWinnerName.setForeground(AppTheme.COLOR_PRIMARY_START);

        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(lblTitle);
        centerPanel.add(Box.createVerticalStrut(15));
        centerPanel.add(lblWinner);
        centerPanel.add(lblWinnerName);

        // --- BOTTOM PANEL (Action) ---
        JButton btnAction;

        if (hasVotedInThisSession) {
            btnAction = new JButton("SUDAH MEMILIH");
            btnAction.setEnabled(false);
            btnAction.setBackground(Color.LIGHT_GRAY);
            btnAction.setFont(new Font("Segoe UI", Font.BOLD, 12));
            btnAction.setPreferredSize(new Dimension(250, 40));
        } else {
            btnAction = AppTheme.createGradientButton(session.isActive ? "PILIH SEKARANG" : "LIHAT DETAIL", 250, 40);
            btnAction.setFont(new Font("Segoe UI", Font.BOLD, 12));

            btnAction.addActionListener(e -> {
                if (session.isActive) {
                    attemptToEnterBallot(session.title);
                } else {
                    // --- UPDATE ACTION: SHOW DETAIL TABLE ---
                    showDetailStats(session);
                }
            });
        }

        card.add(topPanel, BorderLayout.NORTH);
        card.add(centerPanel, BorderLayout.CENTER);
        card.add(btnAction, BorderLayout.SOUTH);

        return card;
    }

    private void showDetailStats(SessionInfo session) {
        // Parsing data: "Nama:10,NamaB:5"
        String rawData = session.voteSummary;
        String[] rows = rawData.split(",");

        String[][] data = new String[rows.length][2];
        for (int i = 0; i < rows.length; i++) {
            String[] parts = rows[i].split(":");
            if (parts.length == 2) {
                data[i][0] = parts[0]; // Nama
                data[i][1] = parts[1] + " Suara"; // Jumlah
            } else {
                data[i][0] = rows[i];
                data[i][1] = "-";
            }
        }

        String[] headers = { "Kandidat", "Perolehan Suara" };
        JTable table = new JTable(data, headers);
        table.setRowHeight(30);
        table.setEnabled(false);
        JScrollPane sp = new JScrollPane(table);
        sp.setPreferredSize(new Dimension(400, 200));

        JOptionPane.showMessageDialog(this, sp, "Detail Hasil: " + session.title, JOptionPane.PLAIN_MESSAGE);
    }

    // =========================================================================
    // 📄 NEW PAGE: GUIDE (PANDUAN)
    // =========================================================================
    private JPanel createPageGuide() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(40, 40, 40, 40));

        JLabel title = new JLabel("Panduan Memilih");
        title.setFont(AppTheme.FONT_H1);
        panel.add(title, BorderLayout.NORTH);

        String guideText = "<html><body style='width: 500px'>" +
                "<h2>Langkah-Langkah Menggunakan E-Voting:</h2>" +
                "<ol>" +
                "<li>Pastikan Anda berada di halaman <b>Galeri Pemilihan</b>.</li>" +
                "<li>Cari kotak pemilihan yang berstatus <b style='color:green'>LIVE</b>.</li>" +
                "<li>Klik tombol <b>PILIH SEKARANG</b>.</li>" +
                "<li>Konfirmasi niat Anda untuk masuk ke bilik suara.</li>" +
                "<li>Klik tombol <b>PILIH</b> pada kandidat yang Anda inginkan.</li>" +
                "<li>Selesai! Suara Anda telah direkam oleh sistem.</li>" +
                "</ol>" +
                "<p><i>Catatan: Anda hanya dapat memilih satu kali per sesi pemilihan.</i></p>" +
                "</body></html>";

        JLabel content = new JLabel(guideText);
        content.setFont(AppTheme.FONT_BODY);
        content.setVerticalAlignment(SwingConstants.TOP);

        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    // =========================================================================
    // 📄 NEW PAGE: INFO APLIKASI
    // =========================================================================
    private JPanel createPageInfo() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        JPanel card = AppTheme.createShadowPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);

        JLabel icon = new JLabel("🗳️");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 60));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("E-Voting System V3.2");
        title.setFont(AppTheme.FONT_H2);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel desc = new JLabel("Sistem Pemilihan Digital Terintegrasi");
        desc.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel ip = new JLabel("Server: " + AppTheme.SERVER_HOST + ":" + AppTheme.SERVER_PORT);
        ip.setForeground(AppTheme.COLOR_TEXT_MUTED);
        ip.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(icon);
        card.add(Box.createVerticalStrut(10));
        card.add(title);
        card.add(desc);
        card.add(Box.createVerticalStrut(20));
        card.add(ip);

        panel.add(card);
        return panel;
    }

    // =========================================================================
    // 🧠 LOGIC: NAVIGASI CERDAS & KONFIRMASI
    // =========================================================================

    private void attemptToEnterBallot(String sessionTitle) {
        if (activeSession == null || !activeSession.title.equals(sessionTitle)) {
            JOptionPane.showMessageDialog(this, "Data surat suara belum siap atau tidak sinkron. Tunggu sebentar...");
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "<html>Anda akan memasuki bilik suara untuk pemilihan:<br/><b>" + sessionTitle + "</b><br/><br/>" +
                        "Pastikan pilihan Anda sudah bulat sebelum masuk.<br/>Lanjutkan?</html>",
                "Konfirmasi Masuk Bilik Suara",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            setupBallotScreen(sessionTitle);
            switchPage("PAGE_BALLOT", null); // Tidak ada tombol aktif spesifik saat di ballot
        }
    }

    // =========================================================================
    // 📄 PAGE 3: BALLOT (SURAT SUARA)
    // =========================================================================
    private void setupBallotScreen(String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(AppTheme.COLOR_BACKGROUND_APP);
        panel.setBorder(new EmptyBorder(20, 40, 40, 40));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 20, 0));

        JLabel lblTitle = new JLabel("Surat Suara: " + title);
        lblTitle.setFont(AppTheme.FONT_H1);

        JLabel lblInstruct = new JLabel("Klik tombol 'PILIH' pada kandidat pilihan Anda.");
        lblInstruct.setFont(AppTheme.FONT_BODY);

        JPanel titleBox = new JPanel(new GridLayout(2, 1));
        titleBox.setOpaque(false);
        titleBox.add(lblTitle);
        titleBox.add(lblInstruct);

        header.add(titleBox, BorderLayout.WEST);

        JPanel gridContainer = new JPanel(new FlowLayout(FlowLayout.CENTER, 40, 40));
        gridContainer.setOpaque(false);

        for (String candName : activeCandidates) {
            gridContainer.add(new CandidateCard(candName));
        }

        JScrollPane scrollPane = new JScrollPane(gridContainer);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        try {
            mainContentPanel.add(panel, "PAGE_BALLOT");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Komponen Kartu Kandidat (UI Static/Flat Update)
     */
    private class CandidateCard extends JPanel {
        private String name;

        public CandidateCard(String name) {
            this.name = name;
            setPreferredSize(new Dimension(240, 320));
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            // --- IMAGE HANDLING ---
            JLabel lblImage = new JLabel();
            lblImage.setAlignmentX(Component.CENTER_ALIGNMENT);

            ImageIcon photo = candidatePhotos.get(name);
            if (photo != null) {
                lblImage.setIcon(photo);
            } else {
                lblImage.setText("👤");
                lblImage.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 80));
                lblImage.setHorizontalAlignment(SwingConstants.CENTER);
            }

            JLabel lblName = new JLabel(name);
            lblName.setFont(AppTheme.FONT_H2);
            lblName.setForeground(AppTheme.COLOR_TEXT_MAIN);
            lblName.setAlignmentX(Component.CENTER_ALIGNMENT);

            JButton btnVote = AppTheme.createGradientButton("PILIH", 180, 45);
            btnVote.setAlignmentX(Component.CENTER_ALIGNMENT);
            btnVote.addActionListener(e -> submitVote(name));

            add(Box.createVerticalStrut(25));
            add(lblImage);
            add(Box.createVerticalStrut(20));
            add(lblName);
            add(Box.createVerticalGlue());
            add(btnVote);
            add(Box.createVerticalStrut(25));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int yOffset = 10;
            int shadowAlpha = 20;

            g2.setColor(new Color(0, 0, 0, shadowAlpha));
            g2.fillRoundRect(10, 10 + yOffset, getWidth() - 20, getHeight() - 20, 25, 25);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(5, 5 + yOffset, getWidth() - 10, getHeight() - 20, 25, 25);
        }
    }

    // =========================================================================
    // 📄 PAGE 4: SUCCESS
    // =========================================================================
    private JPanel createPageSuccess() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(236, 253, 245));

        JPanel c = new JPanel();
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.setOpaque(false);
        JLabel icon = new JLabel("✅");
        icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 100));
        icon.setAlignmentX(0.5f);
        JLabel t1 = new JLabel("Terima Kasih!");
        t1.setFont(AppTheme.FONT_H1);
        t1.setAlignmentX(0.5f);
        JLabel t2 = new JLabel("Suara Anda telah direkam.");
        t2.setFont(AppTheme.FONT_BODY);
        t2.setAlignmentX(0.5f);

        JButton btnBack = new JButton("Kembali ke Galeri");
        btnBack.setAlignmentX(0.5f);
        btnBack.addActionListener(e -> {
            refreshGalleryUI(); // Refresh agar kartu jadi disabled
            switchPage("PAGE_GALLERY", btnNavGallery);
        });

        c.add(icon);
        c.add(t1);
        c.add(t2);
        c.add(Box.createVerticalStrut(20));
        c.add(btnBack);
        panel.add(c);
        return panel;
    }

    // =========================================================================
    // ⚙️ LOGIC & NETWORKING (PROTOCOL V2)
    // =========================================================================

    private void connectAndSetup() {
        try {
            socket = new Socket(AppTheme.SERVER_HOST, AppTheme.SERVER_PORT);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());

            // 1. Baca History List (Text)
            String historyMsg = in.readUTF();
            if (historyMsg.startsWith("HISTORY_LIST|")) {
                parseHistoryList(historyMsg);
            }

            // 2. Baca Setup Sesi Aktif
            String header = in.readUTF();

            if (header.equals("SETUP_V2_IMAGES")) {
                handleSetupV2();
            } else if (header.startsWith("WAIT")) {
                activeSession = null;
            }

            // Update UI jika sudah login (Always true now)
            SwingUtilities.invokeLater(this::refreshGalleryUI);

        } catch (Exception e) {
            SwingUtilities
                    .invokeLater(() -> JOptionPane.showMessageDialog(this, "Gagal koneksi server: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private void handleSetupV2() throws IOException {
        String title = in.readUTF();
        int candidateCount = in.readInt();

        activeCandidates.clear();
        candidatePhotos.clear();

        for (int i = 0; i < candidateCount; i++) {
            String name = in.readUTF();
            int imgSize = in.readInt();

            activeCandidates.add(name);

            if (imgSize > 0) {
                byte[] imgBytes = new byte[imgSize];
                in.readFully(imgBytes);
                ImageIcon originalIcon = new ImageIcon(imgBytes);
                ImageIcon scaledIcon = scaleImage(originalIcon, 150, 150);
                candidatePhotos.put(name, scaledIcon);
            }
        }

        boolean found = false;
        for (SessionInfo si : sessionList) {
            if (si.title.equals(title)) {
                activeSession = si;
                found = true;
                break;
            }
        }

        if (!found) {
            activeSession = new SessionInfo(title, true, "-", "");
            sessionList.add(0, activeSession);
        }
    }

    private ImageIcon scaleImage(ImageIcon icon, int w, int h) {
        if (icon == null)
            return null;
        Image img = icon.getImage();
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bi.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(img, 0, 0, w, h, null);
        g2.dispose();
        return new ImageIcon(bi);
    }

    private void parseHistoryList(String msg) {
        sessionList.clear();
        String rawData = msg.substring(13);
        if (rawData.isEmpty())
            return;

        String[] sessions = rawData.split("#");
        for (String s : sessions) {
            String[] props = s.split(";");
            // V3.2: Format sekarang punya 4 elemen (Title;Active;Winner;VoteSummary)
            if (props.length >= 4) {
                sessionList.add(new SessionInfo(props[0], Boolean.parseBoolean(props[1]), props[2], props[3]));
            } else if (props.length >= 3) {
                // Backward compatibility just in case
                sessionList.add(new SessionInfo(props[0], Boolean.parseBoolean(props[1]), props[2], ""));
            }
        }
    }

    private void refreshGalleryUI() {
        galleryContainer.removeAll();
        if (sessionList.isEmpty()) {
            JLabel empty = new JLabel("Belum ada sesi pemilihan.");
            empty.setFont(AppTheme.FONT_H2);
            galleryContainer.add(empty);
        } else {
            for (SessionInfo s : sessionList) {
                galleryContainer.add(createSessionCard(s));
            }
        }
        galleryContainer.revalidate();
        galleryContainer.repaint();
    }

    private void submitVote(String candidateName) {
        if (activeSession != null && votedSessions.contains(activeSession.title)) {
            JOptionPane.showMessageDialog(this, "Anda sudah memilih!");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "Yakin memilih " + candidateName + "?");
        if (confirm != JOptionPane.YES_OPTION)
            return;

        try {
            out.writeUTF("VOTE|" + candidateName);

            if (activeSession != null) {
                votedSessions.add(activeSession.title);
            }

            switchPage("PAGE_SUCCESS", btnNavGallery); // Kembali ke galeri highlight
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class SessionInfo {
        String title;
        boolean isActive;
        String winner;
        String voteSummary; // NEW V3.2

        public SessionInfo(String t, boolean a, String w, String summary) {
            this.title = t;
            this.isActive = a;
            this.winner = w;
            this.voteSummary = summary;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
        }
        SwingUtilities.invokeLater(() -> new VoterClient().setVisible(true));
    }
}