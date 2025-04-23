package jbrowse;

import org.jdesktop.swingx.graphics.BlendComposite;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

import static jbrowse.Tab.getFont;
import static jbrowse.Tab.getFontMetricsFromCache;

/**
 * The BlockLayout class implements a layout manager for laying out graphical nodes in a block or inline mode.
 */
public class BlockLayout implements ILayoutNode {


    private static final int INPUT_WIDTH_PX = 200;
    /**
     * The panel associated with this layout.
     */
    private final JPanel p;

    /**
     * The parent layout node.
     */
    private ILayoutNode parent;

    /**
     * The previous layout node.
     */
    private ILayoutNode previous;

    /**
     * The list of child layout nodes.
     */
    private final List<ILayoutNode> children = new ArrayList<>();

    /**
     * The node associated with this layout.
     */
    private final INode node;

    /**
     * The current layout mode (BLOCK or INLINE).
     */
    private LayoutMode layoutMode;

    /**
     * The list of display list items.
     */

    /**
     * The list of display list items for the current line.
     */
    private final List<DisplayListItem> line = new ArrayList<>();

    private static final int HSTEP = 13;
    private static final int VSTEP = 18;
    private int cursorX = HSTEP;
    private int cursorY = VSTEP;
    private int fontStyle = Font.PLAIN;
    private int fontSize = 16;
    private int x;
    private int y;
    private int width;
    private int height;

    private final String[] blockElements = { "html", "body", "article", "section", "nav", "aside",
            "h1", "h2", "h3", "h4", "h5", "h6", "hgroup", "header",
            "footer", "address", "p", "hr", "pre", "blockquote",
            "ol", "ul", "menu", "li", "dl", "dt", "dd", "figure",
            "figcaption", "main", "div", "table", "form", "fieldset",
            "legend", "details", "summary"};


    /**
     * Constructs a BlockLayout instance.
     *
     * @param node The node associated with this layout.
     * @param p The panel associated with this layout.
     * @param parent The parent layout node.
     * @param previous The previous layout node.
     */
    public BlockLayout(INode node, JPanel p, ILayoutNode parent, ILayoutNode previous) {
        this.node = node;
        this.p = p;
        this.parent = parent;
        this.previous = previous;
    }

    /**
     * Lays out the nodes based on the layout mode.
     */
    public void layout() {
        if (previous == null) {
            y = parent.getY();
        } else {
            y = previous.getY() + previous.getHeight();
        }
        x = parent.getX();
        width = parent.getWidth();

        layoutMode = getLayoutMode();

        if (layoutMode == LayoutMode.BLOCK) {
            previous = null;
            for (INode child : node.getChildren()) {
                var next = new BlockLayout(child, p, this, previous);
                children.add(next);
                previous = next;
            }
        } else {
            newLine();
            recurse(node);
        }
        for (ILayoutNode child : children) {
            child.layout();
        }

        height = children.stream().mapToInt(ILayoutNode::getHeight).sum();
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getHeight() {
        return height;
    }



    /**
     * Recursively processes the nodes and adds words to the line.
     *
     * @param tree The node to process.
     */
    private void recurse(INode tree) {
        if (tree instanceof Text txt) {
            for (String word : txt.getText().split("\\s+")) {
                word(txt, word);
            }
        } else if (tree instanceof Element ele) {
            if (Objects.equals(ele.getTag(), "br"))
            {
                newLine();
            }
            else if (Objects.equals(ele.getTag(), "input") || Objects.equals(ele.getTag(), "button")) {
                input(ele);
            } else
            {
                for (INode child : ele.getChildren()) {
                    recurse(child);
                }
            }

        }
    }

    private void input(INode node) {
        var w = INPUT_WIDTH_PX;
        if (cursorX + w > width) {
            newLine();
        }
        var line = children.getLast();
        var previousWord = !line.getChildren().isEmpty() ? line.getChildren().getLast() : null;
        var input = new InputLayout(node, line, previousWord);
        line.getChildren().add(input);

        int weight = getFontWeight(node.getStyle().get("font-weight"));
        int style = getFontStyle(node.getStyle().get("font-style")) | weight;
        double size = Float.parseFloat(node.getStyle().get("font-size").replace("px", ""));

        var font = getFont((int) size, style);
        var fontMetrics = getFontMetricsFromCache(font);

        cursorX += w + fontMetrics.stringWidth(" ");
    }

    /**
     * Adds a word to the current line.
     *
     * @param text The word to add.
     */
    private void word(Text node, String text) {
        int weight = getFontWeight(node.getStyle().get("font-weight"));
        int style = getFontStyle(node.getStyle().get("font-style")) | weight;
        double size = Float.parseFloat(node.getStyle().get("font-size").replace("px", ""));

        var font = getFont((int) size, style);
        var fontMetrics = getFontMetricsFromCache(font);

        var stringWidth = fontMetrics.stringWidth(text);
        if (cursorX + stringWidth > width) {
            newLine();
        }
        cursorX += stringWidth + fontMetrics.stringWidth(" ");
        var line = children.getLast();
        var previousWord = line.getChildren().isEmpty() ? null : line.getChildren().getLast();
        var textLayout = new TextLayout(node, line, text, previousWord);
        line.getChildren().add(textLayout);

    }

    private int getFontStyle(String s) {
        if (Objects.equals(s, "italic")) {
            return Font.ITALIC;
        }
        return Font.PLAIN;
    }

    private int getFontWeight(String s) {
        if (Objects.equals(s, "normal") || "400".equals(s)) {
            return Font.PLAIN;
        } else if ("bold".equals(s) || "700".equals(s)) {
            return Font.BOLD;
        }
        return Font.PLAIN;
    }

    private void newLine() {
        cursorX = 0;
        ILayoutNode lastLine = null;
        if (!children.isEmpty()) {
            lastLine = children.getLast();
        }
        ILayoutNode newLine = new LineLayout(this.node, this, lastLine);
        children.add(newLine);
    }





    /**
     * Retrieves the font metrics for the specified font.
     *
     * @param f The font to get metrics for.
     * @return The font metrics for the specified font.
     */

    /**
     * Determines the layout mode of the element; whether it is an inline or a block element.
     *
     * @return The layout mode of the element.
     */
    private LayoutMode getLayoutMode() {
        if (node instanceof Text) {
            return LayoutMode.INLINE;
        }
        for (INode child : node.getChildren()) {
            if (child instanceof Element e && Arrays.stream(blockElements).toList().contains(e.getTag())) {
                return LayoutMode.BLOCK;
            }
        }
        if (node.getChildren() != null || (node instanceof Element e && Objects.equals(e.getTag(), "input"))) {
            return LayoutMode.INLINE;
        }
        else {
            return LayoutMode.BLOCK;
        }
    }

    @Override
    public ILayoutNode getParent() {
        return this.parent;
    }

    @Override
    public void setParent(ILayoutNode parent) {
        this.parent = parent;
    }

    @Override
    public List<ILayoutNode> getChildren() {
        return this.children;
    }

    @Override
    public List<IDrawCommand> paint() {
        List<IDrawCommand> cmds = new ArrayList<>();

        var colorString = node.getStyle().getOrDefault("background", "transparent");
        var cssColor = org.silentsoft.csscolor4j.Color.valueOf(node.getStyle().getOrDefault("background-color", colorString));

        Color bgColor = new Color(cssColor.getRed(), cssColor.getGreen(), cssColor.getBlue(), (int) (cssColor.getOpacity() * 255));

        if (bgColor.getAlpha() > 0) {
            var radius = Integer.parseInt(node.getStyle().getOrDefault("border-radius", "0px")
                    .replace("px", ""));
            cmds.add(new DrawRRect(new Rectangle(x, y, width, height), radius, bgColor));
        }
        return cmds;
    }

    public List<IDrawCommand> paintEffects(List<IDrawCommand> cmds)
    {
        cmds = paintVisualEffects(node, cmds, selfRect());
        return cmds;
    }

    private Rectangle selfRect() {
        return new Rectangle(x, y, width, height);
    }

    public static List<IDrawCommand> paintVisualEffects(INode node, List<IDrawCommand> cmds, Rectangle rectangle) {
        double opacity = Double.parseDouble(node.getStyle().getOrDefault("opacity","1.0"));
        BlendComposite bc = Browser.getCompositeFromBlendMode(node.getStyle().getOrDefault("mix-blend-mode", "normal"));
        var returnList = new ArrayList<IDrawCommand>();
        List<IDrawCommand> iDrawCommands = new ArrayList<>();
        iDrawCommands.add(new Opacity(opacity, cmds));
        returnList.add(new Blend(bc, iDrawCommands));
        return returnList;
    }

    @Override
    public INode getNode() {
        return node;
    }

    @Override
    public boolean shouldPaint() {
        return node instanceof Text || node instanceof Element e &&
                (!Objects.equals(e.getTag(), "input") && !Objects.equals(e.getTag(), "button"));
    }

}
