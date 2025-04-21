package jbrowse;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JsContext {

    private final Tab tab;
    private Context context;
    private Value bindings;

    public JsContext(Tab tab) {
        this.tab = tab;
        try {
            var runtime = getResourceFileAsString("runtime.js");
            context = Context.newBuilder("js").allowAllAccess(true).build();
            bindings = context.getBindings("js");
            bindings.putMember("jsContext", this);
            context.eval("js", runtime);
        } catch (Exception e) {
            System.out.println("Could not initialise JS runtime: " + e.getMessage());
        }
    }

    public Object run(String scriptName, String code) {
        try {
            var eval = context.eval("js", code);
            if (eval.isHostObject()) return eval;
            return null;
        } catch (Exception sex) {
            System.out.println("Script " + scriptName + " crashed " + sex.getMessage());
            return null;
        }
    }

    public static void log(Object content) {
        System.out.println(content);
    }

    public List<Integer> querySelectorAll(String selectorText) {
        var selector = new CssParser(selectorText).selector();
        return Tab.treeToLayoutList(tab.getNodes(), new ArrayList<>()).stream().filter(selector::matches).map(this::getHandle).toList();
    }

    public String getAttribute(int handle, String attr) {
        var node = getNode(handle);
        return ((Element) node).getAttributes().getOrDefault(attr, "");
    }

    public int getHandle(INode node) {
        return node.hashCode();
    }

    public INode getNode(int handle) {
        return Tab.treeToLayoutList(tab.getNodes(), new ArrayList<>()).stream().filter(n -> n.hashCode() == handle).findFirst().get();
    }

    /**
     * Reads given resource file as a string.
     *
     * @param fileName path to the resource file
     * @return the file's contents
     * @throws IOException if read fails for any reason
     */
    static String getResourceFileAsString(String fileName) throws IOException {
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        try (InputStream is = classLoader.getResourceAsStream(fileName)) {
            if (is == null) return null;
            try (InputStreamReader isr = new InputStreamReader(is);
                 BufferedReader reader = new BufferedReader(isr)) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        }
    }

    public boolean dispatchEvent(String type, Element el) {
        var handle = getHandle(el);
        try {
            Value jsHandle = context.eval("js", "new Node(" + handle + ")");
            Value newEvent = context.eval("js", "newEvent('" + type + "')");
            return !jsHandle.invokeMember("dispatchEvent", newEvent).asBoolean();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void innerHtmlSet(int handle, String s) {
        var doc = new HtmlParser("<html><body>" + s + "</body></html>").parse();
        var newNodes = doc.getChildren().getFirst().getChildren();

        var element = getNode(handle);
        element.setChildren(newNodes);
        for (INode child : element.getChildren()) {
            child.setParent(element);
        }
        tab.render();
    }
}