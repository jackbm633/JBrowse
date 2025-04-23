package jbrowse;

import org.jdesktop.swingx.graphics.BlendComposite;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class Browser {
    private static final JPanel canvas = new JPanel();
    private static final int VSTEP = 18;

    private static BufferedImage rootSurface = null;

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private final BufferedImage chromeSurface;
    private static BufferedImage tabSurface;
    private String focus;

    public static BufferedImage getRootSurface() {
        return rootSurface;
    }

    public List<Tab> getTabs() {
        return tabs;
    }

    private final List<Tab> tabs;
    private final Chrome chrome;

    public Tab getActiveTab() {
        return activeTab;
    }

    private Tab activeTab;

    public Browser() {
        this.tabs = new ArrayList<>();
        this.activeTab = null;

        JFrame window = new JFrame("Browser");
        window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        window.setSize(WIDTH, HEIGHT);
        canvas.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        window.add(canvas);
        window.pack();
        window.setVisible(true);
        Browser.rootSurface = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);

        this.chrome = new Chrome(this);

        this.chromeSurface = new BufferedImage(WIDTH, chrome.getBottom(), BufferedImage.TYPE_INT_ARGB);
        this.tabSurface = null;

        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                try {
                    if (e.getY() < chrome.getBottom())
                    {
                        focus = null;
                        chrome.onClick(e);
                        rasterChrome();
                    }
                    else {
                        focus = "content";
                        activeTab.onClick(e);
                        rasterTab();
                    }
                    draw();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
        window.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    activeTab.onKeyDown();
                    draw();
                } else {
                    try {
                        if (handleKey(e))
                        {
                            rasterChrome();
                            draw();
                        } else if (Objects.equals(focus, "content")) {
                            activeTab.keypress(e.getKeyChar());
                        }
                    } catch (NoSuchAlgorithmException | IOException | KeyManagementException ex) {
                        throw new RuntimeException(ex);
                    }
                    rasterTab();
                    draw();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });
    }

    private boolean handleKey(KeyEvent e) throws NoSuchAlgorithmException, IOException, KeyManagementException {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            chrome.handleEnter();
            return true;
        }
        else if (e.getKeyChar() != KeyEvent.CHAR_UNDEFINED) {
            return chrome.keypress(e.getKeyChar());

        }
        return false;
    }

    public static JPanel getCanvas() {
        return canvas;
    }



    void newTab(URL url) throws IOException, NoSuchAlgorithmException, KeyManagementException {
        var newTab = new Tab(HEIGHT - chrome.getBottom());
        newTab.load(url, null);
        activeTab = newTab;
        tabs.add(newTab);
        rasterChrome();
        rasterTab();
        draw();
    }

    public void rasterChrome() {
        var graphics = (Graphics2D) chromeSurface.getGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    public void rasterTab() {
        var tabHeight = activeTab.getDocument().getHeight() + 2*VSTEP;
        if (tabSurface == null || tabHeight != tabSurface.getHeight()) {
            tabSurface = new BufferedImage(WIDTH, tabHeight, BufferedImage.TYPE_INT_ARGB);
        }
        var graphics = (Graphics2D) tabSurface.getGraphics();
    }

    private void draw() {
        var tabOffset = chrome.getBottom() - activeTab.getScroll();

        activeTab.raster((Graphics2D) tabSurface.getGraphics(), chrome.getBottom());
        var chromeGraphics = (Graphics2D) chromeSurface.getGraphics();
        chromeGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        for (IDrawCommand command : chrome.paint())
        {
            command.draw(chromeGraphics, 0);
        }

        chrome.paint();
        var g2d = (Graphics2D) rootSurface.getGraphics();
        g2d.setBackground(Color.WHITE);
        g2d.clearRect(0, 0, WIDTH, HEIGHT);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.drawImage(tabSurface, 0, tabOffset, null);
        g2d.drawImage(chromeSurface, 0, 0, null);
        var canvasGraphics = ((Graphics2D)canvas.getGraphics());
        canvasGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        canvasGraphics.drawImage(rootSurface, 0, 0, null);
    }

    public void setActiveTab(Tab tab) {
        this.activeTab = tab;
    }

    public static BlendComposite getCompositeFromBlendMode(String blendMode) {
        if (blendMode.equals("multiply")) {
            return BlendComposite.Multiply;
        } else if (blendMode.equals("difference")) {
            return BlendComposite.Difference;
        } else {
            return BlendComposite.Color;
        }
    }

}
