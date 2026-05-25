package dev.veryalpha.loader;

import javax.swing.*;
import java.awt.*;

public class LoadingScreen {
    private JWindow window;
    private JProgressBar bar;
    private JLabel status;

    public LoadingScreen() {
        try {
            SwingUtilities.invokeAndWait(() -> {
                window = new JWindow();
                window.setSize(400, 150);
                window.setLocationRelativeTo(null);

                JPanel bg = new JPanel(new BorderLayout(10, 10)) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        g.setColor(new Color(0x8B572A));
                        g.fillRect(0, 0, getWidth(), getHeight());
                        g.setColor(new Color(0x6B3F1A));
                        g.fillRect(0, 0, getWidth(), 2);
                        g.fillRect(0, getHeight() - 2, getWidth(), 2);
                        g.fillRect(0, 0, 2, getHeight());
                        g.fillRect(getWidth() - 2, 0, 2, getHeight());
                    }
                };
                bg.setBorder(BorderFactory.createEmptyBorder(18, 18, 14, 18));

                JLabel title = new JLabel("VAInjector", SwingConstants.CENTER);
                title.setForeground(Color.WHITE);
                title.setFont(new Font("Dialog", Font.BOLD, 24));
                bg.add(title, BorderLayout.NORTH);

                status = new JLabel("Starting...", SwingConstants.CENTER);
                status.setForeground(new Color(0xCCCCCC));
                status.setFont(new Font("Dialog", Font.PLAIN, 12));
                bg.add(status, BorderLayout.CENTER);

                bar = new JProgressBar(0, 100);
                bar.setValue(0);
                bar.setStringPainted(true);
                bar.setString("0%");
                bar.setForeground(new Color(0x55FF55));
                bar.setBackground(new Color(0x2A2A2A));
                bar.setBorder(BorderFactory.createLineBorder(new Color(0x3A3A3A), 2));
                bar.setFont(new Font("Dialog", Font.BOLD, 11));
                JPanel bp = new JPanel(new BorderLayout());
                bp.setOpaque(false);
                bp.add(bar);
                bg.add(bp, BorderLayout.SOUTH);

                window.add(bg);
                window.setAlwaysOnTop(true);
                window.setVisible(true);
                window.toFront();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void update(int progress, String text) {
        SwingUtilities.invokeLater(() -> {
            if (window == null) return;
            bar.setValue(Math.min(progress, 100));
            bar.setString(Math.min(progress, 100) + "%");
            status.setText(text);
        });
    }

    public void close() {
        SwingUtilities.invokeLater(() -> {
            if (window != null) {
                window.dispose();
                window = null;
            }
        });
    }
}
