import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/**
 * =============================================================================
 * 🎨 APP THEME: DESIGN SYSTEM CORE
 * =============================================================================
 * "Pabrik Komponen" untuk merender UI Modern.
 * UPDATE:
 * - Menambahkan Image Processing Utilities (scaleImage).
 * - Menambahkan Warna Status (Live/Ended).
 */
public class AppTheme {

    // =========================================================================
    // 🌐 1. CONFIG & PALETTE
    // =========================================================================

    public static final String SERVER_HOST = "localhost";
    public static final int SERVER_PORT = 9999;

    // Warna Utama
    public static final Color COLOR_PRIMARY_START = new Color(37, 99, 235); // Royal Blue
    public static final Color COLOR_PRIMARY_END = new Color(59, 130, 246); // Lighter Blue

    // Warna Background & Surface
    public static final Color COLOR_BACKGROUND_APP = new Color(248, 250, 252);
    public static final Color COLOR_SIDEBAR = new Color(15, 23, 42);
    public static final Color COLOR_SURFACE = Color.WHITE;

    // Warna Teks
    public static final Color COLOR_TEXT_MAIN = new Color(30, 41, 59);
    public static final Color COLOR_TEXT_MUTED = new Color(100, 116, 139);
    public static final Color COLOR_TEXT_LIGHT = Color.WHITE;

    // Warna Status
    public static final Color COLOR_STATUS_LIVE = new Color(22, 163, 74); // Green 600
    public static final Color COLOR_STATUS_ENDED = new Color(220, 38, 38); // Red 600

    // Warna Efek
    public static final Color COLOR_SHADOW = new Color(0, 0, 0, 15);
    public static final Color COLOR_ACTIVE_INDICATOR = new Color(56, 189, 248); // Cyan Light

    // =========================================================================
    // 🔤 2. CUSTOM FONT LOADING
    // =========================================================================

    public static Font FONT_H1;
    public static Font FONT_H2;
    public static Font FONT_H3;
    public static Font FONT_BODY;
    public static Font FONT_BOLD;
    public static Font FONT_SIDEBAR_ICON;

    static {
        try {
            String fontName = "Segoe UI";
            if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                fontName = "Helvetica Neue";
            }
            FONT_H1 = new Font(fontName, Font.BOLD, 26);
            FONT_H2 = new Font(fontName, Font.BOLD, 20);
            FONT_H3 = new Font(fontName, Font.BOLD, 16);
            FONT_BODY = new Font(fontName, Font.PLAIN, 14);
            FONT_BOLD = new Font(fontName, Font.BOLD, 14);
            FONT_SIDEBAR_ICON = new Font("Segoe UI Emoji", Font.PLAIN, 28);
        } catch (Exception e) {
            FONT_H1 = new Font("SansSerif", Font.BOLD, 24);
            FONT_H2 = new Font("SansSerif", Font.BOLD, 18);
            FONT_H3 = new Font("SansSerif", Font.BOLD, 16);
            FONT_BODY = new Font("SansSerif", Font.PLAIN, 14);
            FONT_BOLD = new Font("SansSerif", Font.BOLD, 14);
            FONT_SIDEBAR_ICON = new Font("SansSerif", Font.PLAIN, 24);
        }
    }

    // =========================================================================
    // 🏭 3. COMPONENT FACTORY: SHADOW PANEL
    // =========================================================================

    public static JPanel createShadowPanel() {
        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int shadowSize = 5;
                int arc = 20;

                g2.setColor(COLOR_SHADOW);
                for (int i = 0; i < shadowSize; i++) {
                    g2.fillRoundRect(i + 2, i + 2, getWidth() - shadowSize * 2, getHeight() - shadowSize * 2, arc, arc);
                }

                g2.setColor(COLOR_SURFACE);
                g2.fillRoundRect(0, 0, getWidth() - shadowSize, getHeight() - shadowSize, arc, arc);

                g2.setColor(new Color(241, 245, 249));
                g2.drawRoundRect(0, 0, getWidth() - shadowSize - 1, getHeight() - shadowSize - 1, arc, arc);

                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(20, 20, 25, 25));
        return panel;
    }

    // =========================================================================
    // 🏭 4. COMPONENT FACTORY: STATUS BADGE
    // =========================================================================

    public static JLabel createStatusLabel(boolean isActive) {
        String text = isActive ? "● SEDANG BERLANGSUNG" : "● SELESAI";
        Color color = isActive ? COLOR_STATUS_LIVE : COLOR_STATUS_ENDED;

        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lbl.setForeground(color);
        return lbl;
    }

    // =========================================================================
    // 🏭 5. COMPONENT FACTORY: GRADIENT BUTTON
    // =========================================================================

    public static JButton createGradientButton(String text, int width, int height) {
        JButton btn = new JButton(text) {
            private boolean isHovered = false;
            private boolean isPressed = false;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color color1 = COLOR_PRIMARY_START;
                Color color2 = COLOR_PRIMARY_END;

                if (isPressed) {
                    color1 = color1.darker();
                    color2 = color2.darker();
                } else if (isHovered) {
                    color1 = new Color(59, 130, 246);
                    color2 = new Color(96, 165, 250);
                }

                GradientPaint gp = new GradientPaint(0, 0, color1, 0, getHeight(), color2);
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 30, 30);

                g2.setFont(FONT_BOLD);
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent()) / 2 - 4;
                g2.drawString(getText(), x, y);

                g2.dispose();
            }

            {
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) {
                        isHovered = true;
                        repaint();
                        setCursor(new Cursor(Cursor.HAND_CURSOR));
                    }

                    public void mouseExited(MouseEvent e) {
                        isHovered = false;
                        repaint();
                        setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    }

                    public void mousePressed(MouseEvent e) {
                        isPressed = true;
                        repaint();
                    }

                    public void mouseReleased(MouseEvent e) {
                        isPressed = false;
                        repaint();
                    }
                });
            }
        };

        btn.setPreferredSize(new Dimension(width, height));
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        return btn;
    }

    // =========================================================================
    // 🏭 6. COMPONENT FACTORY: SIDEBAR BUTTON
    // =========================================================================

    public static class SidebarButton extends JButton {
        private boolean isActive;
        private boolean isHovered = false;

        public SidebarButton(String iconText, boolean initialActive) {
            super(iconText);
            this.isActive = initialActive;

            setPreferredSize(new Dimension(70, 60));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setHorizontalAlignment(SwingConstants.CENTER);

            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) {
                    isHovered = true;
                    repaint();
                    setCursor(new Cursor(Cursor.HAND_CURSOR));
                }

                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                    setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                }
            });
        }

        public void setActive(boolean isActive) {
            this.isActive = isActive;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (isActive) {
                g2.setColor(new Color(255, 255, 255, 20));
                g2.fillRoundRect(5, 5, getWidth() - 10, getHeight() - 10, 15, 15);
                g2.setColor(COLOR_ACTIVE_INDICATOR);
                g2.fillRoundRect(0, 15, 4, getHeight() - 30, 5, 5);
            } else if (isHovered) {
                g2.setColor(new Color(255, 255, 255, 10));
                g2.fillRoundRect(5, 5, getWidth() - 10, getHeight() - 10, 15, 15);
            }

            if (isActive) {
                g2.setColor(Color.WHITE);
                g2.setFont(FONT_SIDEBAR_ICON);
            } else {
                g2.setColor(COLOR_TEXT_MUTED);
                g2.setFont(FONT_SIDEBAR_ICON);
            }

            FontMetrics fm = g2.getFontMetrics();
            String txt = getText();
            int x = (getWidth() - fm.stringWidth(txt)) / 2;
            int y = (getHeight() + fm.getAscent()) / 2 - 8;
            g2.drawString(txt, x, y);

            g2.dispose();
        }
    }

    public static SidebarButton createSidebarButton(String text, boolean isActive) {
        return new SidebarButton(text, isActive);
    }

    // =========================================================================
    // 🖼️ 7. IMAGE UTILITIES (BARU)
    // =========================================================================

    /**
     * Helper untuk resize gambar dari File Path.
     * Digunakan oleh: ServerAdmin
     */
    public static ImageIcon scaleImage(String imagePath, int width, int height) {
        if (imagePath == null || imagePath.isEmpty()) {
            return null;
        }
        try {
            ImageIcon icon = new ImageIcon(imagePath);
            return scaleImage(icon, width, height);
        } catch (Exception e) {
            System.err.println("Gagal memuat gambar: " + imagePath);
            return null;
        }
    }

    /**
     * Helper untuk resize ImageIcon yang sudah ada (misal dari bytes/network).
     * Digunakan oleh: VoterClient & Internal
     */
    public static ImageIcon scaleImage(ImageIcon icon, int width, int height) {
        if (icon == null)
            return null;
        if (icon.getIconWidth() <= 0 || icon.getIconHeight() <= 0)
            return null;

        Image img = icon.getImage();
        // Gunakan BufferedImage untuk hasil resize berkualitas tinggi
        BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = bi.createGraphics();

        // Settings agar gambar mulus (tidak pecah)
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.drawImage(img, 0, 0, width, height, null);
        g2.dispose();

        return new ImageIcon(bi);
    }

    // =========================================================================
    // 🛠️ UTILITIES
    // =========================================================================

    public static void styleTextField(JTextField field) {
        field.setFont(FONT_BODY);
        field.setForeground(COLOR_TEXT_MAIN);
        field.setBackground(COLOR_SURFACE);
        field.setCaretColor(COLOR_PRIMARY_START);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1, true),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
    }
}