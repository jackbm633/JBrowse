package jbrowse;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.Map.entry;

public final class Tab {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int VSTEP = 18;
    private static final int SCROLL_STEP = 100;
    private final int tabHeight;
    // How far you've scrolled.
    private int scroll = 0;
    private final Map<ISelector, Map<String, String>> defaultStyleSheet;

    private static final Map<FontCombo, Font> fontCache = new HashMap<>();
    private static final Map<Font, FontMetrics> fontMetricsCache = new HashMap<>();

    private Stack<URL> history = new Stack<>();

    private final Map<String, String> inheritedProperties = Map.ofEntries(
        entry("font-size", "16px"),
            entry("font-style", "normal"),
            entry("font-weight", "normal"),
            entry("color", "black")
    );

    private List<IDrawCommand> displayList;

    public DocumentLayout getDocument() {
        return document;
    }

    private DocumentLayout document;
    private URL url;
    private INode nodes;
    private Map<ISelector, Map<String, String>> rules;

    private JsContext js = null;

    private Element focus = null;

    public Tab(int tabHeight) throws IOException {
        this.tabHeight = tabHeight;
        defaultStyleSheet = new CssParser(
                new String(Objects.requireNonNull(Tab.class.getResourceAsStream("/browser.css")).readAllBytes()))
                .parse();

    }


    void onClick(MouseEvent e) throws NoSuchAlgorithmException, IOException, KeyManagementException {
        var x = e.getX();
        var y = e.getY() - (HEIGHT - tabHeight);
        // We want coordinates relative to scroll.
        y += scroll;

        int finalY = y;
        var tree = treeToLayoutList(document, new ArrayList<>());
        var objs = new ArrayList<ILayoutNode>();
        for (int i = 0; i < tree.size(); i++) {
            ILayoutNode d = tree.get(i);
            if (true) {
                if (d.getX() <= x && x < d.getX() + d.getWidth() && d.getY() <= finalY && finalY < d.getY() + d.getHeight()) {
                    objs.add(d);
                }
            } else {
                if (d.getX() <= x && x < d.getX() + d.getWidth() && d.getY() - d.getHeight() <= finalY  && finalY < d.getY()) {
                    objs.add(d);
                }
            }

        }

        if (objs.isEmpty()) {
            return;
        }
        if (focus != null) {
            focus.setFocused(false);
        }
        var element = objs.getLast().getNode();
        while (element != null) {
            if (!(element instanceof Text)) {
                switch (element) {
                    case Element el when el.getTag().equals("a") && el.getAttributes().containsKey("href") -> {
                        if (js.dispatchEvent("click", el)) return;
                        url = url.resolve(el.getAttributes().get("href"));
                        load(url, null);
                        return;
                    }
                    case Element el when el.getTag().equals("input") -> {
                        if (js.dispatchEvent("click", el)) return;
                        focus = el;
                        el.setFocused(true);
                        el.getAttributes().put("value", "");
                        render();
                        return;
                    }
                    case Element el when el.getTag().equals("button") -> {
                        if (js.dispatchEvent("click", el))return;
                        while (element != null) {
                            if (element instanceof Element ele && ele.getTag().equals("form") && ele.getAttributes().containsKey("action")) {
                                submitForm(ele);
                                return;
                            }
                            element = element.getParent();
                        }
                    }
                    default -> {
                    }
                }
            }
            element = element.getParent();
        }
        render();
    }

    private void submitForm(Element e) throws NoSuchAlgorithmException, IOException, KeyManagementException {
        if(js.dispatchEvent("submit", e)) return;
        var inputs = treeToLayoutList(e, new ArrayList<>()).stream()
                .filter(f -> f instanceof Element el && el.getTag().equals("input") && el.getAttributes().containsKey("name")).toList();

        StringBuilder body = new StringBuilder();
        for (INode input : inputs) {
            if (input instanceof Element el) {
                var name = URLEncoder.encode(el.getAttributes().get("name"), StandardCharsets.UTF_8);
                var value = URLEncoder.encode(el.getAttributes().getOrDefault("value", ""),
                        StandardCharsets.UTF_8);
                body.append("&").append(name).append("=").append(value);
            }
        }
        url = url.resolve(e.getAttributes().get("action"));
        load(url, body.toString());
    }

    public void onKeyDown()
    {
        int maxY = Math.max(document.getHeight() + 2 * VSTEP - tabHeight, 0);
        scroll = Math.min(scroll + SCROLL_STEP, maxY);
    }

    public void load(URL url, String payload) throws NoSuchAlgorithmException, IOException, KeyManagementException {
        this.url = url;
        history.add(url);
        scroll = 0;
        String body = url.request(payload);
        nodes = new HtmlParser(body).parse();
        List<String> links = treeToLayoutList(nodes, new ArrayList<>()).stream()
                .filter(s -> s instanceof Element e &&
                        Objects.equals(e.getTag(), "link") &&
                        Objects.equals(e.getAttributes().get("rel"), "stylesheet") &&
                        e.getAttributes().containsKey("href"))
                .map(s -> ((Element) s).getAttributes().get("href"))
                .toList();
        List<String> scripts = treeToLayoutList(nodes, new ArrayList<>()).stream()
                .filter(s -> s instanceof Element e &&
                        Objects.equals(e.getTag(), "script") &&
                        e.getAttributes().containsKey("src"))
                .map(s -> ((Element) s).getAttributes().get("src"))
                .toList();

        js = new JsContext(this);

        // Start all JS tasks in parallel
        List<CompletableFuture<?>> jsTasks = scripts.stream()
                .map(script -> {
                    try {
                        var scriptUrl = url.resolve(script);
                        String scriptBody = scriptUrl.request(null);
                        return runJs(scriptUrl.toString(), scriptBody);
                    } catch (Exception e) {
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .toList();


        // Continue with the rest of the loading process
        rules = new HashMap<>();
        defaultStyleSheet.forEach((selector, rule) -> {
            rules.put(selector, new HashMap<>());
            rule.forEach((key, value) -> rules.get(selector).put(key, value));
        });

        // Load stylesheets
        links.forEach(l -> {
            URL styleUrl = url.resolve(l);
            try {
                String styleBody = styleUrl.request(null);
                rules.putAll(new CssParser(styleBody).parse());
            } catch (Exception ignored) {
            }
        });

        render();
    }


    private CompletableFuture<Void> runJs(String scriptUrl, String scriptBody) {
        return CompletableFuture.runAsync(() -> {
            try {
                js.run(scriptUrl, scriptBody);
            } catch (Exception ignored) {}
        });
    }

    void render() {
        style(nodes, rules.entrySet().stream().sorted((selector, b)
                -> selector.getKey().getPriority()).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                (e1, e2) -> e1, LinkedHashMap::new)));
        document = new DocumentLayout(nodes, Browser.getCanvas());
        document.layout();
        this.displayList = new ArrayList<>();
        paintTree(document, displayList);
    }

    public static List<INode> treeToLayoutList(INode tree, List<INode> list) {
        list.add(tree);
        for (INode child : tree.getChildren()) {
            treeToLayoutList(child, list);
        }
        return list;
    }

    public static List<ILayoutNode> treeToLayoutList(ILayoutNode tree, List<ILayoutNode> list) {
        list.add(tree);
        for (ILayoutNode child : tree.getChildren()) {
            treeToLayoutList(child, list);
        }
        return list;
    }


    private void paintTree(ILayoutNode layoutObject, List<IDrawCommand> dispList) {
        List<IDrawCommand> paintList = new ArrayList<>();
        if (layoutObject.shouldPaint()) {
            paintList = layoutObject.paint();
        }

        for (ILayoutNode child : layoutObject.getChildren()) {
            paintTree(child, paintList);
        }

        if (layoutObject.shouldPaint())
        {
            paintList = layoutObject.paintEffects(paintList);
        }
        dispList.addAll(paintList);


    }

    private void printTree(INode node, int indent) {
        System.out.println(" ".repeat(indent) + node);
        for (INode child : node.getChildren()) {
            printTree(child, indent + 2);
        }
    }


    public void raster(Graphics2D g2D, int offset) {
        // Create a BufferedImage to draw on
        g2D.setBackground(Color.WHITE);
        g2D.clearRect(0, 0, WIDTH, document.getHeight() + VSTEP);
        g2D.setColor(Color.BLACK);

        // Enable anti-aliasing and set rendering hints
        g2D.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        // Determine scale factor for HiDPI displays
        // Draw all display list items onto the buffered image
        for (IDrawCommand it : this.displayList) {
            it.draw(g2D, 0);
        }

        g2D.dispose();

    }

    public void style(INode node, Map<ISelector, Map<String, String>> rules) {
        node.getStyle().clear();

        inheritedProperties.forEach((property, defaultValue) -> {
            if (node.getParent() != null) {
                node.getStyle().put(property, node.getParent().getStyle().get(property));
            } else {
                node.getStyle().put(property, defaultValue);
            }
        });

        rules.forEach((selector, body) -> {
            if (selector.matches(node)) {
                body.forEach((property, value) -> {
                    node.getStyle().put(property, value);
                });
            }
        });
        if (node instanceof Element e && e.getAttributes().containsKey("style")) {
            Map<String, String> pairs = new CssParser(e.getAttributes().get("style")).body();
            pairs.forEach((property, value) -> node.getStyle().put(property, value));
        }
        if (node.getStyle().get("font-size").endsWith("%")) {
            String parentFontSize;
            if (node.getParent() != null) {
                parentFontSize = node.getParent().getStyle().get("font-size");
            } else {
                parentFontSize = inheritedProperties.get("font-size");
            }

            double nodePct = Float.parseFloat(node.getStyle().get("font-size").replace("%", "")) / 100.0;
            double parentPx = Float.parseFloat(parentFontSize.replace("px", ""));
            node.getStyle().put("font-size", nodePct * parentPx + "px");
        }
        for (INode child : node.getChildren()) {
            style(child, rules);
        }
    }

    /**
     * Retrieves a font from the cache or creates a new one if it doesn't exist.
     *
     * @param size The font size.
     * @param style The font style.
     * @return The font with the specified size and style.
     */
    @SuppressWarnings("MagicConstant")
    public static Font getFont(int size, int style) {
        FontCombo key = new FontCombo(size, style);
        if (fontCache.containsKey(key)) {
            return fontCache.get(key);
        } else {
            Font font = new Font(Font.SERIF, style, size);
            fontCache.put(key, font);
            return font;
        }
    }

    /**
     * Retrieves the font metrics for the specified font.
     *
     * @param f The font to get metrics for.
     * @return The font metrics for the specified font.
     */
    public static FontMetrics getFontMetricsFromCache(Font f) {
        if (fontMetricsCache.containsKey(f)) {
            return fontMetricsCache.get(f);
        }
        fontMetricsCache.put(f, ((Graphics2D)Browser.getRootSurface().getGraphics()).getFontMetrics(f));
        return fontMetricsCache.get(f);
    }


    public URL getUrl() {
        return url;
    }

    public void goBack() throws NoSuchAlgorithmException, IOException, KeyManagementException {
        if (history.size() >= 2) {
            history.pop();
            var back = history.pop();
            load(back, null);
        }
    }

    public void keypress(char keyChar) {
        if (focus != null) {
            js.dispatchEvent("keydown", focus);
            focus.getAttributes().put("value", focus.getAttributes().get("value") + keyChar);
            render();
        }
    }

    public INode getNodes() {
        return nodes;
    }

    public int getScroll() {
        return scroll;
    }
}
