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

public class VoterClient extends JFrame {

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    private String voterNIK = "Guest";
    private boolean isLoggedIn = true;

    private List<SessionInfo> sessionList = new ArrayList<>();

    private Map<String, List<String>> sessionCandidates = new HashMap<>();
    private Map<String, Map<String, ImageIcon>> sessionPhotos = new HashMap<>();

    private Set<String> votedSessions = new HashSet<>();

    private CardLayout contentLayout;
    private JPanel mainContentPanel;
    private JPanel galleryContainer;
    private JPanel sidebarPanel;

    private AppTheme.SidebarButton btnNavGallery;
    private AppTheme.SidebarButton btnNavGuide;
    private AppTheme.SidebarButton btnNavInfo;

    public VoterClient() {
        setTitle("E-Voting Terminal - Client (V3.2)");
        setSize(1280, 800);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        initSidebar();
        initContentArea();

        setSidebarEnabled(true);
        switchPage("PAGE_GALLERY", btnNavGallery);

        new Thread(this::connectAndSetup).start();
    }

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

        btnNavGallery = AppTheme.createSidebarButton("🏠", true);
        btnNavGallery.setToolTipText("Galeri Pemilihan");

        btnNavGuide = AppTheme.createSidebarButton("📖", false);
        btnNavGuide.setToolTipText("Panduan Memilih");

        btnNavInfo = AppTheme.createSidebarButton("ℹ️", false);
        btnNavInfo.setToolTipText("Tentang Aplikasi");

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

    private void initContentArea() {
        contentLayout = new CardLayout();
        mainContentPanel = new JPanel(contentLayout);
        mainContentPanel.setBackground(AppTheme.COLOR_BACKGROUND_APP);

        mainContentPanel.add(createPageLoading(), "PAGE_LOADING");
        mainContentPanel.add(createPageGallery(), "PAGE_GALLERY");
        mainContentPanel.add(createPageGuide(), "PAGE_GUIDE");
        mainContentPanel.add(createPageInfo(), "PAGE_INFO");
        mainContentPanel.add(createPageSuccess(), "PAGE_SUCCESS");

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

    private JPanel createPageLoading() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        JLabel lbl = new JLabel("Menghubungkan ke Server & Mengunduh Aset...");
        lbl.setFont(AppTheme.FONT_H2);
        lbl.setForeground(AppTheme.COLOR_TEXT_MUTED);
        panel.add(lbl);
        return panel;
    }

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

        galleryContainer = new JPanel(new GridLayout(0, 3, 30, 30));
        galleryContainer.setOpaque(false);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(galleryContainer, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(wrapper);
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
        String rawData = session.voteSummary;
        String[] rows = rawData.split(",");

        String[][] data = new String[rows.length][2];
        for (int i = 0; i < rows.length; i++) {
            String[] parts = rows[i].split(":");
            if (parts.length == 2) {
                data[i][0] = parts[0];
                data[i][1] = parts[1] + " Suara";
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

    private void attemptToEnterBallot(String sessionTitle) {
        if (!sessionCandidates.containsKey(sessionTitle)) {
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
            switchPage("PAGE_BALLOT", null);
        }
    }

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

        List<String> candidates = sessionCandidates.getOrDefault(title, new ArrayList<>());

        for (String candName : candidates) {
            gridContainer.add(new CandidateCard(title, candName));
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

    private class CandidateCard extends JPanel {
        private String name;

        public CandidateCard(String sessionTitle, String name) {
            this.name = name;
            setPreferredSize(new Dimension(240, 320));
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

            JLabel lblImage = new JLabel();
            lblImage.setAlignmentX(Component.CENTER_ALIGNMENT);

            ImageIcon photo = null;
            if (sessionPhotos.containsKey(sessionTitle)) {
                photo = sessionPhotos.get(sessionTitle).get(name);
            }

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
            btnVote.addActionListener(e -> submitVote(sessionTitle, name));

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
            refreshGalleryUI();
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

    private void connectAndSetup() {
        try {
            socket = new Socket(AppTheme.SERVER_HOST, AppTheme.SERVER_PORT);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());

            String historyMsg = in.readUTF();
            if (historyMsg.startsWith("HISTORY_LIST|")) {
                parseHistoryList(historyMsg);
            }

            String header = in.readUTF();

            if (header.startsWith("MULTI_SETUP")) {
                int sessionCount = Integer.parseInt(header.split("\\|")[1]);
                for (int i = 0; i < sessionCount; i++) {
                    handleSetupMulti();
                }
            } else if (header.startsWith("WAIT")) {
                sessionCandidates.clear();
                sessionPhotos.clear();
            }

            SwingUtilities.invokeLater(this::refreshGalleryUI);

            new Thread(this::listenForUpdates).start();

        } catch (Exception e) {
            SwingUtilities
                    .invokeLater(() -> JOptionPane.showMessageDialog(this, "Gagal koneksi server: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private void handleSetupMulti() throws IOException {
        String title = in.readUTF();
        int candidateCount = in.readInt();

        List<String> cands = new ArrayList<>();
        Map<String, ImageIcon> photos = new HashMap<>();

        for (int i = 0; i < candidateCount; i++) {
            String name = in.readUTF();
            int imgSize = in.readInt();

            cands.add(name);

            if (imgSize > 0) {
                byte[] imgBytes = new byte[imgSize];
                in.readFully(imgBytes);
                ImageIcon originalIcon = new ImageIcon(imgBytes);
                ImageIcon scaledIcon = scaleImage(originalIcon, 150, 150);
                photos.put(name, scaledIcon);
            }
        }

        sessionCandidates.put(title, cands);
        sessionPhotos.put(title, photos);

        boolean found = false;
        for (SessionInfo si : sessionList) {
            if (si.title.equals(title)) {
                si.isActive = true;
                found = true;
                break;
            }
        }

        if (!found) {
            SessionInfo newSi = new SessionInfo(title, true, "-", "");
            sessionList.add(0, newSi);
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
            if (props.length >= 4) {
                sessionList.add(new SessionInfo(props[0], Boolean.parseBoolean(props[1]), props[2], props[3]));
            } else if (props.length >= 3) {
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

    private void submitVote(String sessionTitle, String candidateName) {
        if (votedSessions.contains(sessionTitle)) {
            JOptionPane.showMessageDialog(this, "Anda sudah memilih!");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "Yakin memilih " + candidateName + "?");
        if (confirm != JOptionPane.YES_OPTION)
            return;

        try {
            out.writeUTF("VOTE|" + sessionTitle + "|" + candidateName);

            votedSessions.add(sessionTitle);
            switchPage("PAGE_SUCCESS", btnNavGallery);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void listenForUpdates() {
        try {
            while (true) {
                String msg = in.readUTF();
                if (msg.startsWith("REFRESH_STATS|")) {
                    String[] parts = msg.split("\\|");
                    if (parts.length >= 3) {
                        String title = parts[1];
                        String summary = parts[2];
                        updateSessionStats(title, summary);
                    }
                } else if (msg.startsWith("MULTI_SETUP|")) {
                }
            }
        } catch (IOException e) {
        }
    }

    private void updateSessionStats(String title, String summary) {
        for (SessionInfo s : sessionList) {
            if (s.title.equals(title)) {
                s.voteSummary = summary;
                break;
            }
        }
    }

    private class SessionInfo {
        String title;
        boolean isActive;
        String winner;
        String voteSummary;

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