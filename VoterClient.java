import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * =============================================================================
 * 🗳️ VOTER CLIENT: MODERN LAYOUT (SIDEBAR + GALLERY)
 * =============================================================================
 * UPDATE FITUR:
 * - Layout Sidebar mirip Admin (Navigasi Kiri).
 * - Halaman "Galeri Pemilihan" menampilkan semua sesi (Aktif & Selesai).
 * - Logic Parsing Protokol Server yang baru (History List).
 */
public class VoterClient extends JFrame {

    // =========================================================================
    // 🧠 DATA & STATE
    // =========================================================================
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    // Data Sesi
    private List<SessionInfo> sessionList = new ArrayList<>();
    private SessionInfo activeSession = null; // Sesi yang sedang LIVE
    private String[] activeCandidates = null; // Kandidat untuk sesi LIVE

    // Security Flag (Per Sesi Runtime)
    private boolean hasVoted = false;

    // =========================================================================
    // 🎨 UI COMPONENTS
    // =========================================================================
    private CardLayout contentLayout;
    private JPanel mainContentPanel;
    private JPanel galleryContainer; // Container untuk kartu-kartu sesi

    // Sidebar Buttons
    private AppTheme.SidebarButton btnNavGallery;
    private AppTheme.SidebarButton btnNavVote;

    public VoterClient() {
        setTitle("E-Voting Terminal - Client");
        setSize(1280, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        initSidebar();
        initContentArea();

        // Start Connection di Thread terpisah
        new Thread(this::connectAndSetup).start();
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

        // Logo
        JLabel lblLogo = new JLabel("🗳️");
        lblLogo.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
        lblLogo.setForeground(Color.WHITE);
        lblLogo.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Menu Buttons
        btnNavGallery = AppTheme.createSidebarButton("🏠", true); // Home / Gallery
        btnNavVote = AppTheme.createSidebarButton("✍️", false); // Vote / Bilik Suara

        // Listener Navigasi
        btnNavGallery.addActionListener(e -> switchPage("PAGE_GALLERY", btnNavGallery));
        btnNavVote.addActionListener(e -> {
            if (activeSession != null) {
                switchPage("PAGE_BALLOT", btnNavVote);
            } else {
                JOptionPane.showMessageDialog(this, "Tidak ada sesi voting yang sedang aktif.");
            }
        });

        sidebar.add(lblLogo);
        sidebar.add(Box.createVerticalStrut(50));
        addSidebarItem(sidebar, btnNavGallery);
        addSidebarItem(sidebar, btnNavVote);
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

        // Tambahkan Halaman-Halaman
        mainContentPanel.add(createPageLoading(), "PAGE_LOADING");
        mainContentPanel.add(createPageGallery(), "PAGE_GALLERY");
        mainContentPanel.add(createPageSuccess(), "PAGE_SUCCESS");
        // PAGE_BALLOT akan dibuat dinamis saat data diterima

        add(mainContentPanel, BorderLayout.CENTER);
    }

    private void switchPage(String pageName, AppTheme.SidebarButton activeButton) {
        contentLayout.show(mainContentPanel, pageName);
        btnNavGallery.setActive(false);
        btnNavVote.setActive(false);
        activeButton.setActive(true);
    }

    // =========================================================================
    // 📄 PAGE 1: LOADING SCREEN
    // =========================================================================
    private JPanel createPageLoading() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        JLabel lbl = new JLabel("Menghubungkan ke Server & Mengambil Data...");
        lbl.setFont(AppTheme.FONT_H2);
        lbl.setForeground(AppTheme.COLOR_TEXT_MUTED);
        panel.add(lbl);
        return panel;
    }

    // =========================================================================
    // 📄 PAGE 2: GALLERY PEMILIHAN (HISTORY)
    // =========================================================================
    private JPanel createPageGallery() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(40, 40, 40, 40));

        // Header
        JLabel lblTitle = new JLabel("Galeri Pemilihan");
        lblTitle.setFont(AppTheme.FONT_H1);
        lblTitle.setForeground(AppTheme.COLOR_TEXT_MAIN);
        panel.add(lblTitle, BorderLayout.NORTH);

        // Grid Container untuk Kartu
        galleryContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 30, 30));
        galleryContainer.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(galleryContainer);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Membuat Kartu UI untuk satu sesi pemilihan.
     */
    private JPanel createSessionCard(SessionInfo session) {
        JPanel card = AppTheme.createShadowPanel();
        card.setPreferredSize(new Dimension(300, 220));
        card.setLayout(new BorderLayout());
        card.setBackground(Color.WHITE);

        // Header Kartu: Status
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        JLabel lblStatus = new JLabel(session.isActive ? "● LIVE" : "● SELESAI");
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatus.setForeground(session.isActive ? new Color(22, 163, 74) : new Color(220, 38, 38));
        topPanel.add(lblStatus, BorderLayout.WEST);

        // Body: Judul
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        JLabel lblTitle = new JLabel("<html>" + session.title + "</html>");
        lblTitle.setFont(AppTheme.FONT_H2);
        lblTitle.setForeground(AppTheme.COLOR_TEXT_MAIN);

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

        // Footer: Tombol Aksi
        JButton btnAction = AppTheme.createGradientButton(session.isActive ? "PILIH SEKARANG" : "LIHAT DETAIL", 250,
                40);
        btnAction.setFont(new Font("Segoe UI", Font.BOLD, 12));

        btnAction.addActionListener(e -> {
            if (session.isActive) {
                // Masuk ke Bilik Suara
                if (activeSession != null && activeSession.title.equals(session.title)) {
                    switchPage("PAGE_BALLOT", btnNavVote);
                } else {
                    JOptionPane.showMessageDialog(this, "Data sesi belum tersinkronisasi. Coba refresh.");
                }
            } else {
                // Tampilkan Info Selesai
                JOptionPane.showMessageDialog(this,
                        "Pemilihan ini telah berakhir.\nPemenang: " + session.winner,
                        "Hasil Akhir", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        card.add(topPanel, BorderLayout.NORTH);
        card.add(centerPanel, BorderLayout.CENTER);
        card.add(btnAction, BorderLayout.SOUTH);

        return card;
    }

    // =========================================================================
    // 📄 PAGE 3: BALLOT (BILIK SUARA)
    // =========================================================================
    private void setupBallotScreen(String title, String[] candidates) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(AppTheme.COLOR_BACKGROUND_APP);
        panel.setBorder(new EmptyBorder(20, 40, 40, 40));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 20, 0));

        JLabel lblTitle = new JLabel("Surat Suara: " + title);
        lblTitle.setFont(AppTheme.FONT_H1);
        header.add(lblTitle, BorderLayout.WEST);

        // Content Grid
        JPanel gridContainer = new JPanel(new FlowLayout(FlowLayout.CENTER, 40, 40));
        gridContainer.setOpaque(false);

        for (String candName : candidates) {
            if (!candName.trim().isEmpty()) {
                gridContainer.add(new CandidateCard(candName.trim()));
            }
        }

        JScrollPane scrollPane = new JScrollPane(gridContainer);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(header, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        // Tambahkan ke Main Panel
        mainContentPanel.add(panel, "PAGE_BALLOT");
    }

    private class CandidateCard extends JPanel {
        private boolean isHovered = false;
        private String name;

        public CandidateCard(String name) {
            this.name = name;
            setPreferredSize(new Dimension(220, 300));
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            JLabel icon = new JLabel("👤");
            icon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 80));
            icon.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel lblName = new JLabel(name);
            lblName.setFont(AppTheme.FONT_H2);
            lblName.setForeground(AppTheme.COLOR_TEXT_MAIN);
            lblName.setAlignmentX(Component.CENTER_ALIGNMENT);

            JButton btnVote = AppTheme.createGradientButton("PILIH", 160, 45);
            btnVote.setAlignmentX(Component.CENTER_ALIGNMENT);
            btnVote.addActionListener(e -> submitVote(name));

            add(Box.createVerticalStrut(30));
            add(icon);
            add(Box.createVerticalStrut(20));
            add(lblName);
            add(Box.createVerticalGlue());
            add(btnVote);
            add(Box.createVerticalStrut(30));

            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    isHovered = true;
                    setCursor(new Cursor(Cursor.HAND_CURSOR));
                    repaint();
                }

                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int yOffset = isHovered ? 0 : 10;
            int shadowAlpha = isHovered ? 50 : 20;

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
        btnBack.addActionListener(e -> switchPage("PAGE_GALLERY", btnNavGallery));

        c.add(icon);
        c.add(t1);
        c.add(t2);
        c.add(Box.createVerticalStrut(20));
        c.add(btnBack);
        panel.add(c);
        return panel;
    }

    // =========================================================================
    // ⚙️ LOGIC & NETWORKING
    // =========================================================================

    /**
     * Menghubungkan ke server dan membaca data awal.
     */
    private void connectAndSetup() {
        try {
            // Simulasi loading
            SwingUtilities.invokeLater(() -> contentLayout.show(mainContentPanel, "PAGE_LOADING"));
            Thread.sleep(1000);

            socket = new Socket(AppTheme.SERVER_HOST, AppTheme.SERVER_PORT);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());

            // 1. Baca History List
            String historyMsg = in.readUTF();
            if (historyMsg.startsWith("HISTORY_LIST|")) {
                parseHistoryList(historyMsg);
            }

            // 2. Baca Setup Sesi Aktif (jika ada)
            String setupMsg = in.readUTF();
            if (setupMsg.startsWith("SETUP|")) {
                String[] parts = setupMsg.split("\\|");
                String title = parts[1];
                activeCandidates = parts[2].split(",");

                // Cari sesi yang cocok di list untuk dijadikan activeSession
                for (SessionInfo si : sessionList) {
                    if (si.title.equals(title) && si.isActive) {
                        activeSession = si;
                        break;
                    }
                }

                // Setup Ballot UI di background
                SwingUtilities.invokeLater(() -> setupBallotScreen(title, activeCandidates));
            }

            // Selesai Loading -> Masuk Galeri
            SwingUtilities.invokeLater(() -> {
                refreshGalleryUI();
                switchPage("PAGE_GALLERY", btnNavGallery);
            });

        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Gagal koneksi server!"));
        }
    }

    private void parseHistoryList(String msg) {
        // Format: HISTORY_LIST|Title;Active;Winner#Title;Active;Winner...
        sessionList.clear();
        String rawData = msg.substring(13); // Hapus header
        if (rawData.isEmpty())
            return;

        String[] sessions = rawData.split("#");
        for (String s : sessions) {
            String[] props = s.split(";");
            if (props.length >= 3) {
                sessionList.add(new SessionInfo(props[0], Boolean.parseBoolean(props[1]), props[2]));
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
        if (hasVoted) {
            JOptionPane.showMessageDialog(this, "Anda sudah memilih di sesi ini!");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "Yakin memilih " + candidateName + "?");
        if (confirm != JOptionPane.YES_OPTION)
            return;

        try {
            out.writeUTF("VOTE|" + candidateName);
            hasVoted = true;
            switchPage("PAGE_SUCCESS", btnNavVote); // Tetap highlight Vote tab sbg konteks
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Helper Class untuk Data Sesi
    private class SessionInfo {
        String title;
        boolean isActive;
        String winner;

        public SessionInfo(String t, boolean a, String w) {
            this.title = t;
            this.isActive = a;
            this.winner = w;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VoterClient().setVisible(true));
    }
}