package jbrowse;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.VolatileImage;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static java.util.Map.entry;

public final class Tab {

    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int VSTEP = 18;
    private static final int SCROLL_STEP = 100;
    private final int tabHeight;
    private final ExecutorService mainThread;
    private volatile boolean isLoading = false;


    public void setNeedsRender(boolean needsRender) {
        this.needsRender = needsRender;
        if (needsRender) {
            layoutInvalidated = true;
        }
    }

    private boolean needsRender = false;
    // How far you've scrolled.
    private int scroll = 0;
    private final Map<ISelector, Map<String, String>> defaultStyleSheet;

    private static final Map<FontCombo, Font> fontCache = new HashMap<>();
    private static final Map<Font, FontMetrics> fontMetricsCache = new HashMap<>();

    private final Stack<URL> history = new Stack<>();

    private final Map<String, String> inheritedProperties = Map.ofEntries(
        entry("font-size", "16px"),
            entry("font-style", "normal"),
            entry("font-weight", "normal"),
            entry("color", "black")
    );

    private List<IDrawCommand> displayList;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    private List<String> allowedOrigins;

    public DocumentLayout getDocument() {
        return document;
    }

    private DocumentLayout document;
    private URL url;
    private INode nodes;
    private Map<ISelector, Map<String, String>> rules;

    private JsContext js = null;

    private Element focus = null;

    private Map<ISelector, Map<String, String>> sortedRules;
    private INode lastStyledNodes;
    private boolean rulesChanged;
    private boolean layoutInvalidated;

    public Tab(int tabHeight) throws IOException {
        this.tabHeight = tabHeight;
        this.mainThread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Tab-Main-Thread");
            t.setDaemon(true);
            return t;
        });

        defaultStyleSheet = new CssParser(
                new String(Objects.requireNonNull(Tab.class.getResourceAsStream("/browser.css")).readAllBytes()))
                .parse();
    }

    public void load(URL url, String payload) {
        isLoading = true;
        mainThread.execute(() -> {
            try {
                loadInternal(url, payload);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isLoading = false;
            }
        });
    }


    void onClick(MouseEvent e) throws NoSuchAlgorithmException, IOException, KeyManagementException {
        renderInternal();
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
                        loadInternal(url, null);
                        return;
                    }
                    case Element el when el.getTag().equals("input") -> {
                        if (js.dispatchEvent("click", el)) return;
                        focus = el;
                        el.setFocused(true);
                        el.getAttributes().put("value", "");
                        needsRender = true;
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
        needsRender = true;
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
        loadInternal(url, body.toString());
    }

    public void onKeyDown()
    {
        int maxY = Math.max(document.getHeight() + 2 * VSTEP - tabHeight, 0);
        scroll = Math.min(scroll + SCROLL_STEP, maxY);
    }

    private void loadInternal(URL url, String payload) throws NoSuchAlgorithmException, IOException, KeyManagementException {
        // 1. Fetch HTML
        var response = url.request(payload, this.url);
        String body = response.content();
        this.url = url;
        history.add(url);
        scroll = 0;
        // 2. Parse HTML
        nodes = new HtmlParser(body).parse();

        if (response.headers().containsKey("content-security-policy")) {
            String[] csp = response.headers().get("content-security-policy").split(" ");
            if (csp.length > 0 && csp[0].equals("default-src")) {
                allowedOrigins = new ArrayList<>();
                for (int i = 1; i < csp.length; i++) {
                    allowedOrigins.add(new URL(csp[i]).getOrigin());
                }
            } else {
                allowedOrigins = null;
            }
        } else {
            allowedOrigins = null;
        }

        // 3. Extract CSS links and JS sources (Traverse once)
        List<String> cssLinks = new ArrayList<>();
        List<String> scriptSrcs = new ArrayList<>();
        // The 'allNodes' list isn't strictly necessary if only links/scripts are needed,
        // but kept here if the full list was used elsewhere implicitly.
        // Consider removing List<INode> allNodes parameter if not needed.
        List<INode> allNodes = new ArrayList<>();
        extractLinksAndScripts(nodes, allNodes, cssLinks, scriptSrcs);

        // 4. Initialize JsContext
        if (js != null)
        {
            js.setDiscarded(true);
        }
        js = new JsContext(this);

        System.out.println("JS1");
        // 5. Start asynchronous JS fetching/execution tasks (run in background)
        try (var jsExecutor = Executors.newSingleThreadScheduledExecutor()) {
            List<CompletableFuture<Void>> jsFutures = scriptSrcs.stream()
                    .map(script -> CompletableFuture.runAsync(() -> {
                        try {
                            var scriptUrl = url.resolve(script);
                            if (allowedOrigins == null || allowedOrigins.contains(scriptUrl.getOrigin()))
                            {
                                // Consider making url.request() asynchronous if it's a bottleneck
                                String scriptBody = scriptUrl.request(null, url).content(); // Network I/O
                                js.run(scriptUrl.toString(), scriptBody);   // JS Execution
                            }

                        } catch (Exception e) {
                            // Log or handle exception appropriately
                            System.err.println("Failed to load or run script: " + script + " - " + e.getMessage());
                        }
                    }, jsExecutor))
                    .toList();


            // We don't wait for JS completion here.
            System.out.println("JS2");

            // 6. Initialize rules with default stylesheet (create copies)
            rules = new HashMap<>();
            defaultStyleSheet.forEach((selector, rule) -> {
                rules.put(selector, new HashMap<>(rule)); // Create a new HashMap for each rule
            });

            // 7. Start asynchronous CSS fetching and parsing tasks
            List<CompletableFuture<Map<ISelector, Map<String, String>>>> cssFutures = cssLinks.stream()
                    .map(link -> CompletableFuture.supplyAsync(() -> {
                        try {
                            URL styleUrl = url.resolve(link);
                            if (allowedOrigins == null || allowedOrigins.contains(styleUrl.getOrigin())) {
                                // Consider making url.request() asynchronous if it's a bottleneck
                                String styleBody = styleUrl.request(null, url).content(); // Network I/O
                                return new CssParser(styleBody).parse(); // CPU-bound parsing
                            }
                            return Collections.<ISelector, Map<String, String>>emptyMap();
                        } catch (Exception e) {
                            // Log or handle exception appropriately
                            System.err.println("Failed to load or parse stylesheet: " + link + " - " + e.getMessage());
                            // Wrap checked exceptions for CompletableFuture
                            // Return empty map on failure to avoid breaking the chain
                            return Collections.<ISelector, Map<String, String>>emptyMap();
                        }
                    }))
                    .toList();

            // 8. Wait for all CSS tasks to complete
            CompletableFuture<Void> allCssFutures = CompletableFuture.allOf(cssFutures.toArray(new CompletableFuture[0]));

            try {
                // Block until all CSS futures are complete before proceeding to render
                allCssFutures.join();
            } catch (CompletionException e) {
                // Handle exceptions that occurred during CSS processing
                System.err.println("Error occurred during CSS loading/parsing: " + e.getCause());
                // Decide how to proceed: render with partial/default styles or show an error
            } catch (Exception e) {
                System.err.println("Unexpected error waiting for CSS tasks: " + e.getMessage());
            }


            // 9. Merge the fetched CSS rules (ensure this happens after waiting)
            // Use stream().map(CompletableFuture::join) which is safe now after allOf().join()
            cssFutures.stream()
                    .map(CompletableFuture::join) // Get results now that they are ready
                    .forEach(fetchedRules -> rules.putAll(fetchedRules)); // Merge results into the main 'rules' map

            // 10. Call render() only after CSS rules are processed
            needsRender = true;
        }
    }

    /**
     * Helper method to traverse the node tree once and extract CSS links and JS sources.
     * @param node Current node to process.
     * @param visited List to accumulate all nodes (optional, remove if not needed elsewhere).
     * @param cssLinks List to accumulate CSS hrefs.
     * @param scriptSrcs List to accumulate JS srcs.
     */
    private void extractLinksAndScripts(INode node, List<INode> visited, List<String> cssLinks, List<String> scriptSrcs) {
        if (node == null) return;

        visited.add(node); // Add current node to the visited list (if needed)

        if (node instanceof Element e) {
            String tag = e.getTag();
            Map<String, String> attrs = e.getAttributes();
            // Check for CSS links
            if ("link".equals(tag) &&
                "stylesheet".equals(attrs.get("rel")) &&
                attrs.containsKey("href")) {
                cssLinks.add(attrs.get("href"));
            }
            // Check for external scripts
            else if ("script".equals(tag) &&
                     attrs.containsKey("src")) {
                scriptSrcs.add(attrs.get("src"));
            }
        }
        // Recursively process children
        for (INode child : node.getChildren()) {
            extractLinksAndScripts(child, visited, cssLinks, scriptSrcs);
        }
    }


    private CompletableFuture<Void> runJs(String scriptUrl, String scriptBody) {
        return CompletableFuture.runAsync(() -> {
            try {
                js.run(scriptUrl, scriptBody);
            } catch (Exception ignored) {} // Consider better error handling
        });
    }

    // Modify render to ensure it runs on the tab's main thread
    void render() {
        if (!mainThread.isShutdown() && needsRender) {
            mainThread.execute(() -> {
                if (Thread.currentThread().getName().equals("Tab-Main-Thread")) {
                    renderInternal();
                }
            });
        }
    }

    private void renderInternal() {
        if (!needsRender) {
            return;
        }
        needsRender = false;
        js.run("jsCallback", "__runRAFHandlers()");
        Browser.getMeasure().time("render");
        
        // Cache sorted rules if they haven't changed
        if (sortedRules == null) {
            sortedRules = rules.entrySet().stream()
                    .sorted(Comparator.comparingInt(entry -> entry.getKey().getPriority()))
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    ));
        }

        style(nodes, sortedRules);



        // Create new document layout only if needed

        document = new DocumentLayout(nodes, Browser.getCanvas());
        document.layout();
        layoutInvalidated = false;


        // Reuse display list if possible, otherwise create new one
        if (displayList == null) {
            displayList = new CopyOnWriteArrayList<>();
        } else {
            displayList.clear();
        }
        
        paintTree(document, displayList);
        Browser.needsDraw = true;
        Browser.getMeasure().stop("render");
    }

    // Add method to mark rules as changed
    public void markRulesChanged() {
        rulesChanged = true;
        needsRender = true;
    }

    // Helper method to estimate initial display list size
    private int estimateDisplayListSize() {
        return nodes != null ? countNodes(nodes) * 2 : 16; // multiply by 2 as each node might generate multiple commands
    }

    private int countNodes(INode node) {
        int count = 1;
        for (INode child : node.getChildren()) {
            count += countNodes(child);
        }
        return count;
    }

    // Remove the old treeToLayoutList method if it's no longer used

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
        if (this.document == null || this.displayList == null) {
            return;
        }

        // Get the graphics configuration for hardware acceleration
        GraphicsConfiguration gc = g2D.getDeviceConfiguration();

        // Create a volatile image for hardware-accelerated rendering
        VolatileImage volatileImage = gc.createCompatibleVolatileImage(
                WIDTH,
                document.getHeight() + VSTEP,
                Transparency.TRANSLUCENT
        );

        do {
            // Check if we need to recreate the volatile image
            if (volatileImage.validate(gc) == VolatileImage.IMAGE_INCOMPATIBLE) {
                volatileImage = gc.createCompatibleVolatileImage(
                        WIDTH,
                        document.getHeight() + VSTEP,
                        Transparency.TRANSLUCENT
                );
            }

            Graphics2D volGraphics = volatileImage.createGraphics();
            try {
                // Set up the graphics context
                volGraphics.setBackground(Color.WHITE);
                volGraphics.clearRect(0, 0, WIDTH, document.getHeight() + VSTEP);
                volGraphics.setColor(Color.BLACK);

                // Enable high-quality rendering
                volGraphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
                volGraphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                volGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw all display list items onto the volatile image
                for (IDrawCommand cmd : this.displayList) {
                    cmd.draw(volGraphics, 0);
                }

            } finally {
                volGraphics.dispose();
            }

            // Draw the accelerated image to the destination graphics
            g2D.drawImage(volatileImage, 0, 0, null);

        } while (volatileImage.contentsLost());

        // Clean up
        volatileImage.flush();
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
        fontMetricsCache.put(f, Browser.getRootSurface().getGraphics().getFontMetrics(f));
        return fontMetricsCache.get(f);
    }


    public URL getUrl() {
        return url;
    }

    public void goBack() throws NoSuchAlgorithmException, IOException, KeyManagementException {
        if (history.size() >= 2) {
            history.pop();
            var back = history.pop();
            loadInternal(back, null);
        }
    }

    public void keypress(char keyChar) {
        if (focus != null) {
            js.dispatchEvent("keydown", focus);
            focus.getAttributes().put("value", focus.getAttributes().get("value") + keyChar);
            needsRender = true;
        }
    }

    public INode getNodes() {
        return nodes;
    }

    public int getScroll() {
        return scroll;
    }
}