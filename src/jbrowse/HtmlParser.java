package jbrowse;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlParser {

    private final String body;
    private List<INode> unfinished;

    private final String[] selfClosingTags = new String[] {
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr"
    };

    private final String[] headTags = new String[] {
            "base", "basefont", "bgsound", "noscript",
            "link", "meta", "title", "style", "script",
    };
    
    public HtmlParser(String body) {
        this.body = body;
    }
    
    public INode parse() {
        String text = "";
        boolean inTag = false;
        unfinished = new ArrayList<>();
        for (char c : body.toCharArray()) {
            if (c == '<') {
                inTag = true;
                if (!text.isEmpty()) {
                    addText(text);
                }
                text = "";
            } else if (c == '>') {
                inTag = false;
                addTag(text);
                text = "";
            } else {
                text += c;
            }
        }
        if (!inTag && !text.isEmpty()) {
            addText(text);
        }
        return finish();
    }
                            
    private INode finish() {
        // We turn our incomplete tree into a complete tree by finishing any unfinished nodes.
        if (unfinished.isEmpty()) {
            implicitTags(null);
        }
        while (unfinished.size() > 1) {
            INode node = unfinished.removeLast();
            INode parent = unfinished.getLast();
            parent.getChildren().add(node);
        }
        return unfinished.removeLast();
    }
        
    private void addTag(String tag) {
        TagAttributePair tagAttributes = GetAttributes(tag);
        // Ignore all tags that start with exclamation marks: comments and doctype declarations.
        if (tagAttributes.tag().startsWith("!")) {
            return;
        }
        implicitTags(tag);
        if (tagAttributes.tag().startsWith("/")) {
            // Very last tag is an edge case - no unfinished node to add it to:
            if (unfinished.size() == 1) {
                return;
            }
            // Close tag instead finishes the last unfinished node by adding it to the previous
            // unfinished node in the list.
            INode node = unfinished.removeLast();
            INode parent = unfinished.getLast();
            parent.getChildren().add(node);
        } else if (Arrays.asList(selfClosingTags).contains(tagAttributes.tag())) {
            // If a tag is self-closing, we automatically close it.
            INode parent = unfinished.getLast();
            INode node = new Element(tagAttributes.tag(), tagAttributes.attributes(), parent);
            parent.getChildren().add(node);
        } else {
            // Open tag adds a new unfinished tag to the end of the list.
            INode parent;
            if (unfinished.isEmpty()) {
                parent = null;
            } else {
                parent = unfinished.getLast();
            }
            INode node = new Element(tagAttributes.tag(), tagAttributes.attributes(), parent);
            unfinished.add(node);
        }
    }

    private void implicitTags(String tag) {
        while (true) {
            List<String> openTags = new ArrayList<>();
            for (INode node : unfinished) {
                if (node instanceof Element e) {
                    openTags.add(e.getTag());
                }
            }
            if (openTags.isEmpty() && !Objects.equals(tag, "html")) {
                addTag("html");
            } else if (openTags.size() == 1 && openTags.getFirst().equals("html") && !Objects.equals(tag, "head") &&
                    !Objects.equals(tag, "body") && !Objects.equals(tag, "/html")) {
                if (Arrays.stream(headTags).toList().contains(tag)) {
                    addTag("head");
                } else {
                    addTag("body");
                }
            } else if (Arrays.equals(openTags.toArray(), new String[]{"html", "head"}) &&
                    !Objects.equals("/head", tag) &&
                    !Arrays.asList(headTags).contains(tag)) {
                addTag("/head");
            } else {
                break;
            }
        }
    }

    private void addText(String text) {
        // Browser will skip whitespace-only text nodes to sidestep problem with empty tags.
        if (text.matches("^\\s*$")) {
            return;
        }
        implicitTags(null);
        INode parent = unfinished.getLast();
        INode node = new Text(text, parent);
        parent.getChildren().add(node);
    }

    private TagAttributePair GetAttributes(String text) {
        // Split on whitespace to get the tag name and the attribute/value pairs.
        String attributeRegexOptionalQuotes = "(\\w+)\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]*)";
        Pattern patternOptionalQuotes = Pattern.compile(attributeRegexOptionalQuotes);
        Matcher matcherOptionalQuotes = patternOptionalQuotes.matcher(text);

        List<String> parts = new ArrayList<>();
        while (matcherOptionalQuotes.find()) {
            parts.add(matcherOptionalQuotes.group(0));
        }

        String tag = text.split("\\s+")[0].toLowerCase(Locale.ROOT);
        Map<String, String> attributes = new HashMap<>();
        for (int i = 0; i < parts.size(); i++) {
            // Split each attribute-value pair into a name and a value.
            String attrPair = parts.get(i);
            if (attrPair.contains("=")) {
                String[] split = attrPair.split("=", 2);
                if (split[1].length() > 2 && (split[1].charAt(0) == '\'' ||split[1].charAt(0) == '\"')) {
                    split[1] = split[1].substring(1, split[1].length() - 1);
                }
                attributes.put(split[0].toLowerCase(Locale.ROOT), split[1]);
            } else {
                // The value can also be omitted, in which case the attribute value becomes the empty string.
                attributes.put(attrPair.toLowerCase(Locale.ROOT), "");
            }

        }
        return new TagAttributePair(tag, attributes);
    }
}
