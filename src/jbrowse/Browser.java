package jbrowse;

import org.jdesktop.swingx.graphics.BlendComposite;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;


public class Browser {
    private static final JPanel canvas = new JPanel();
    private static final int VSTEP = 18;
    private static Timer renderTimer;

    private static boolean needsDraw = false;

    private static final double REFRESH_RATE_SEC = 1.0/60;
    public static Map<String, CookiePair> cookieJar = new HashMap<>();

    private static BufferedImage rootSurface = null;

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private final BufferedImage chromeSurface;
    private static BufferedImage tabSurface;
    private String focus;

    private static final MeasureTime measure = new MeasureTime();

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
        window.addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) {

            }


            @Override
            public void windowClosing(WindowEvent e) {
                measure.finish();
            }

            @Override
            public void windowClosed(WindowEvent e) {

            }

            @Override
            public void windowIconified(WindowEvent e) {

            }

            @Override
            public void windowDeiconified(WindowEvent e) {

            }

            @Override
            public void windowActivated(WindowEvent e) {

            }

            @Override
            public void windowDeactivated(WindowEvent e) {

            }


        });

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
                    }
                    else {
                        focus = "content";
                        activeTab.onClick(e);
                    }
                    needsDraw = true;
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
                    needsDraw = true;
                } else {
                    try {
                        if (handleKey(e))
                        {
                            needsDraw = true;
                        } else if (Objects.equals(focus, "content")) {
                            activeTab.keypress(e.getKeyChar());
                        }
                    } catch (NoSuchAlgorithmException | IOException | KeyManagementException ex) {
                        throw new RuntimeException(ex);
                    }
                    needsDraw = true;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {

            }
        });

        renderTimer = new Timer((int) (REFRESH_RATE_SEC * 1000), e -> {
            if (activeTab != null) {
                activeTab.render();
            }
            rasterAndDraw();
        }); // 1000ms = 1 second
        renderTimer.start();

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
        needsDraw = true;
    }

    private void rasterAndDraw() {
        if (needsDraw)
        {
            Browser.getMeasure().time("rasterAndDraw");
            rasterChrome();
            rasterTab();
            draw();
            needsDraw = false;
            Browser.getMeasure().stop("rasterAndDraw");

        }

    }

    public void rasterChrome() {
        var graphics = (Graphics2D) chromeSurface.getGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    public void rasterTab() {
        if (activeTab == null || activeTab.getDocument() == null) {
            return;
        }
        var tabHeight = activeTab.getDocument().getHeight() + 2*VSTEP;
        if (tabSurface == null || tabHeight != tabSurface.getHeight()) {
            tabSurface = new BufferedImage(WIDTH, tabHeight, BufferedImage.TYPE_INT_ARGB);
        }
        var graphics = (Graphics2D) tabSurface.getGraphics();
    }

    private void draw() {
        if (activeTab == null || tabSurface == null) {
            return;
        }
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

    public static Composite getCompositeFromBlendMode(String blendMode, float opacity) {
        if (blendMode.equals("multiply")) {
            return BlendComposite.Multiply.derive(opacity);
        } else if (blendMode.equals("difference")) {
            return BlendComposite.Difference.derive(opacity);
        } else {
            return AlphaComposite.SrcOver.derive(opacity);
        }
    }

    public static MeasureTime getMeasure() {
        return measure;
    }
}
