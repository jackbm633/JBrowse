package jbrowse;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static jbrowse.Tab.getFont;
import static jbrowse.Tab.getFontMetricsFromCache;

public class InputLayout implements ILayoutNode {

    private final INode node;
    private final ILayoutNode parent;
    private final List<ILayoutNode> children;
    private final ILayoutNode previous;
    static final int INPUT_WIDTH_PX = 200;
    private int x;
    private int y;
    private Font font;
    private int height;

    public FontMetrics getFontMetrics() {
        return fontMetrics;
    }

    private FontMetrics fontMetrics;

    public InputLayout(INode node, ILayoutNode parent, ILayoutNode previous) {
        this.node = node;
        this.parent = parent;
        this.previous = previous;
        this.children = new ArrayList<>();
    }

    @Override
    public void layout() {
        int weight = getFontWeight(node.getStyle().get("font-weight"));
        int style = getFontStyle(node.getStyle().get("font-style")) | weight;
        double size = Float.parseFloat(node.getStyle().get("font-size").replace("px", ""));

        font = getFont((int) size, style);
        fontMetrics = getFontMetricsFromCache(font);
        
        this.height = fontMetrics.getHeight();

        if (this.previous != null && this.previous instanceof InputLayout tl) {
            int spaceWidth = tl.getFontMetrics().stringWidth(" ");
            x = this.previous.getX() + spaceWidth + this.previous.getWidth();
        } else {
            x = this.parent.getX();
        }
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


    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getWidth() {
        return INPUT_WIDTH_PX;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public ILayoutNode getParent() {
        return parent;
    }

    @Override
    public void setParent(ILayoutNode parent) {

    }

    @Override
    public List<ILayoutNode> getChildren() {
        return children;
    }

    @Override
    public List<IDrawCommand> paint() {
        List<IDrawCommand> cmds = new ArrayList<>();
        org.silentsoft.csscolor4j.Color c = org.silentsoft.csscolor4j.Color.valueOf(node.getStyle().getOrDefault(
                "background-color", "transparent"));
        Color bgcolor = new Color(c.getRed(), c.getGreen(), c.getBlue(), (int) (c.getOpacity() * 255.0));
        if (bgcolor.getAlpha() > 0) {
            var rect = new DrawRRect(new Rectangle(x, y, getWidth(), height), 2, bgcolor);
            cmds.add(rect);
        }
        String text = "";
        if (node instanceof Element e && Objects.equals(e.getTag(), "input")) {
            text = e.getAttributes().getOrDefault("value", "");

        } else if (node instanceof Element e && Objects.equals(e.getTag(), "button")) {
                if (e.getChildren().size() == 1 && e.getChildren().getFirst() instanceof Text t) {
                    text = t.getText();
                }
                else  {
                    System.out.println("Ignoring HTML contents inside button");
                    text = "";
                }

        }
        c = org.silentsoft.csscolor4j.Color.valueOf(node.getStyle().get(
                "color"));

        Color textColor = new Color(c.getRed(), c.getGreen(), c.getBlue());
        cmds.add(new DrawText(x, y , text, font, fontMetrics, textColor));

        if (node.isFocused()) {
            var cx = x + fontMetrics.stringWidth(text);
            cmds.add(new DrawLine(cx, y, cx, y + height, Color.BLACK, 1));
        }

        return cmds;
    }

    @Override
    public INode getNode() {
        return node;
    }

    public void setY(int y) {
        this.y = y;
    }

    @Override
    public boolean shouldPaint() {
        return true;
    }
}
