package jbrowse;

import org.jdesktop.swingx.graphics.BlendComposite;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class Browser {
    private static final JPanel canvas = new JPanel();
    private static final int VSTEP = 18;
    private static Timer renderTimer;

    static boolean needsDraw = false;

    private static final double REFRESH_RATE_SEC = 1.0/30;
    public static Map<String, CookiePair> cookieJar = new HashMap<>();

    private static VolatileImage rootSurface = null;

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private VolatileImage chromeSurface;
    private static VolatileImage tabSurface;
    private String focus;

    private static final MeasureTime measure;

    static {
        try {
            measure = new MeasureTime();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static VolatileImage getRootSurface() {
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

    private final ExecutorService browserThread;

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
                shutdown();
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

        // Create the VolatileImage instances
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsConfiguration gc = ge.getDefaultScreenDevice().getDefaultConfiguration();
        
        Browser.rootSurface = gc.createCompatibleVolatileImage(WIDTH, HEIGHT, Transparency.TRANSLUCENT);

        this.chrome = new Chrome(this);

        this.chromeSurface = gc.createCompatibleVolatileImage(WIDTH, chrome.getBottom(), Transparency.TRANSLUCENT);
        tabSurface = null;

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

        // Initialize single browser thread for chrome and raster-and-draw operations
        this.browserThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Browser-Thread");
            t.setDaemon(true);
            return t;
        });



        // Modify the render timer to use browser thread
        renderTimer = new Timer((int) (REFRESH_RATE_SEC), e -> {
            browserThread.execute(() -> {
                if (activeTab != null) {
                    activeTab.render();
                }
                rasterAndDraw();
            });
        });
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
        if (needsDraw && !browserThread.isShutdown() && Thread.currentThread().getName().equals("Browser-Thread")) {
            Browser.getMeasure().time("rasterAndDraw");
            rasterChrome();
            rasterTab();
            draw();
            needsDraw = false;
            Browser.getMeasure().stop("rasterAndDraw");
        }

    }

    public void rasterChrome() {
        // Check if the volatile image needs to be recreated
        if (chromeSurface.validate(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration()) == VolatileImage.IMAGE_INCOMPATIBLE) {
            // Old surface is no longer usable, recreate it
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsConfiguration gc = ge.getDefaultScreenDevice().getDefaultConfiguration();
            chromeSurface.flush();
            chromeSurface.getGraphics().dispose();
            chromeSurface = gc.createCompatibleVolatileImage(WIDTH, chrome.getBottom(), Transparency.TRANSLUCENT);
        }
        
        var graphics = chromeSurface.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // Clear the surface before drawing
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fillRect(0, 0, chromeSurface.getWidth(), chromeSurface.getHeight());
        graphics.setComposite(AlphaComposite.SrcOver);
    }

    public void rasterTab() {
        if (activeTab == null || activeTab.getDocument() == null) {
            return;
        }
        
        var tabHeight = activeTab.getDocument().getHeight() + 2*VSTEP;
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsConfiguration gc = ge.getDefaultScreenDevice().getDefaultConfiguration();
        
        if (tabSurface == null || tabHeight != tabSurface.getHeight()) {
            // Dispose the old image if it exists
            if (tabSurface != null) {
                tabSurface.flush();
            }
            tabSurface = gc.createCompatibleVolatileImage(WIDTH, tabHeight, Transparency.TRANSLUCENT);
        } else if (tabSurface.validate(gc) == VolatileImage.IMAGE_INCOMPATIBLE) {
            tabSurface.flush();
            tabSurface = gc.createCompatibleVolatileImage(WIDTH, tabHeight, Transparency.TRANSLUCENT);
        }
        
        var graphics = tabSurface.createGraphics();
        // Clear the surface before drawing
        graphics.setComposite(AlphaComposite.Clear);
        graphics.fillRect(0, 0, tabSurface.getWidth(), tabSurface.getHeight());
        graphics.setComposite(AlphaComposite.SrcOver);
    }

    public void draw() {
        if (activeTab == null || tabSurface == null) {
            return;
        }
        var tabOffset = chrome.getBottom() - activeTab.getScroll();

        activeTab.raster(tabSurface.createGraphics(), chrome.getBottom());
        var chromeGraphics = chromeSurface.createGraphics();
        chromeGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        for (IDrawCommand command : chrome.paint())
        {
            command.draw(chromeGraphics, 0);
        }

        chrome.paint();
        
        // Check if the root surface needs to be recreated
        GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice().getDefaultConfiguration();
        if (rootSurface.validate(gc) == VolatileImage.IMAGE_INCOMPATIBLE) {
            rootSurface.flush();
            rootSurface = gc.createCompatibleVolatileImage(WIDTH, HEIGHT, Transparency.TRANSLUCENT);
        }
        
        var g2d = rootSurface.createGraphics();
        g2d.setBackground(Color.WHITE);
        g2d.clearRect(0, 0, WIDTH, HEIGHT);
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.drawImage(tabSurface, 0, tabOffset, null);
        g2d.drawImage(chromeSurface, 0, 0, null);
        g2d.dispose();
        
        var canvasGraphics = ((Graphics2D)canvas.getGraphics());
        canvasGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        canvasGraphics.drawImage(rootSurface, 0, 0, null);
        canvasGraphics.dispose();
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
    
    // Add cleanup method
    public void shutdown() {
        browserThread.shutdown();
        try {
            if (!browserThread.awaitTermination(5, TimeUnit.SECONDS)) {
                browserThread.shutdownNow();
            }
        } catch (InterruptedException e) {
            browserThread.shutdownNow();
        }
        
        // Dispose VolatileImage resources
        if (rootSurface != null) {
            rootSurface.flush();
        }
        if (chromeSurface != null) {
            chromeSurface.flush();
        }
        if (tabSurface != null) {
            tabSurface.flush();
        }
    }
}