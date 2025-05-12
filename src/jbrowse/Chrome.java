package jbrowse;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Chrome {
    private static final int WIDTH = 800;
    private final Browser browser;
    private final Font font;
    private final FontMetrics fontMetrics;
    private final int fontHeight;
    private final int padding;
    private final int tabBarTop;
    private final int tabBarBottom;
    private final Rectangle newtabRect;
    private final int urlBarTop;
    private final int urlBarBottom;
    private final int bottom;
    private final int backWidth;
    private final Rectangle backRect;
    private final Rectangle addressRect;

    private String focus = "";
    private String addressBar = "";
    public Chrome (Browser browser) {
        this.browser = browser;
        this.font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        this.fontMetrics = Browser.getCanvas().getFontMetrics(font);
        this.fontHeight = fontMetrics.getHeight();

        this.padding = 5;
        this.tabBarTop = 0;
        this.tabBarBottom = fontHeight + 2 * padding;
        var plusWidth = fontMetrics.stringWidth("+") + 2*padding;
        this.newtabRect = new Rectangle(padding, padding, plusWidth, fontHeight);
        this.urlBarTop = tabBarBottom;
        this.urlBarBottom = urlBarTop + fontHeight  + 2*padding;
        this.bottom = urlBarBottom;
        this.backWidth = fontMetrics.stringWidth("<") + 2*padding;
        this.backRect = new Rectangle(padding, urlBarTop + padding, backWidth, this.urlBarBottom - 2 * padding - urlBarTop);
        this.addressRect = new Rectangle(backRect.x + backRect.width + padding, urlBarTop + padding,
                WIDTH - 2*padding - backRect.x - backRect.width, urlBarBottom - 2 * padding - urlBarTop);
    }

    public Rectangle tabRect(int i) {
        int tabsStart = newtabRect.x + newtabRect.width + padding;
        int tabWidth = Browser.getCanvas().getFontMetrics(this.font).stringWidth("Tab X") + 2*padding;
        return new Rectangle(tabsStart+tabWidth * i, tabBarTop, tabWidth, tabBarBottom - tabBarTop);
    }

    public List<IDrawCommand> paint() {
        List<IDrawCommand> drawCommands = new ArrayList<>();
        drawCommands.add(new DrawRRect(new Rectangle(0, 0, WIDTH, bottom), 0, Color.WHITE));
        // Paint the new tab button.
        drawCommands.add(new DrawOutline(newtabRect, Color.BLACK, 1));
        drawCommands.add(new DrawText(newtabRect.x + padding, newtabRect.y, "+",
                this.font, Browser.getCanvas().getFontMetrics(this.font), Color.BLACK));
        for (int i = 0; i < browser.getTabs().size(); i++)
        {
            var bounds = tabRect(i);
            drawCommands.add(new DrawLine(bounds.x, 0, bounds.x, bounds.y + bounds.height, Color.BLACK, 1));
            drawCommands.add(new DrawLine(bounds.x + bounds.width, 0, bounds.x + bounds.width, bounds.y + bounds.height, Color.BLACK, 1));
            drawCommands.add(new DrawText(bounds.x + padding, bounds.y + padding, "Tab " + i, font, Browser.getCanvas().getFontMetrics(this.font), Color.BLACK));
            if (browser.getTabs().get(i) == browser.getActiveTab())
            {
                drawCommands.add(new DrawLine(0, bounds.y + bounds.height, bounds.x, bounds.y + bounds.height, Color.BLACK, 1));
                drawCommands.add(new DrawLine(bounds.x + bounds.width, bounds.y + bounds.height, WIDTH, bounds.y + bounds.height, Color.BLACK, 1));

            }
        }
        drawCommands.add(new DrawOutline(backRect, Color.BLACK, 1));
        drawCommands.add(new DrawText(backRect.x + padding, backRect.y, "<", font, fontMetrics, Color.BLACK));
        drawCommands.add(new DrawOutline(addressRect, Color.BLACK, 1));
        if (Objects.equals(focus, "address bar")) {
            drawCommands.add(new DrawText(addressRect.x + padding, addressRect.y, addressBar, font, fontMetrics, Color.BLACK));
        } else {
            var url = browser.getActiveTab().getUrl().toString();
            drawCommands.add(new DrawText(addressRect.x + padding, addressRect.y, url, font, fontMetrics, Color.BLACK));
        }

        return drawCommands;
    }

    public int getBottom() {
        return bottom;
    }

    public void onClick(MouseEvent e) throws IOException, NoSuchAlgorithmException, KeyManagementException {
        if (newtabRect.contains(e.getX(), e.getY()))
        {
            browser.newTab(new URL("https://browser.engineering/"));
        } else if (backRect.contains(e.getX(), e.getY())) {
            browser.getActiveTab().goBack();
        } else if (addressRect.contains(e.getX(), e.getY())) {
            focus = "address bar";
            addressBar = "";
        }else {
            for (int i = 0; i < browser.getTabs().size(); i++) {
                if (tabRect(i).contains(e.getX(), e.getY())){
                    browser.setActiveTab(browser.getTabs().get(i));
                }
            }
        }
    }

    public boolean keypress(char keyChar) {
        if (Objects.equals(focus, "address bar")) {
            addressBar += keyChar;
            return true;
        }
        return false;
    }

    public void handleEnter() throws NoSuchAlgorithmException, IOException, KeyManagementException {
        browser.getActiveTab().load(new URL(addressBar), null);
        focus = null;
    }

    public void blur() {
        focus = null;
    }
}
