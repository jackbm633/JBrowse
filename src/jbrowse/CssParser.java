package jbrowse;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class CssParser {

    /**
     * String to be parsed.
     */
    private final String s;
    /**
     * Current position in the string in the parser.
     */
    private int i = 0;

    /**
     * @param s The string to be parsed.
     */
    public CssParser(String s) {
        this.s = s;
    }

    /**
     * Parses a CSS file
     * @return A Map representing the CSS file.
     */
    public Map<ISelector, Map<String, String>> parse() {
        Map<ISelector, Map<String, String>> rules = new HashMap<>();
        while (i < s.length()) {
            try {
                whitespace();
                ISelector selector = selector();
                literal('{');
                whitespace();
                Map<String, String> body = body();
                literal('}');
                rules.put(selector, body);
            } catch (Exception e) {
                Character why = ignoreUntil(new char[]{'}'});
                if (why != null && why == '}') {
                    literal('}');
                    whitespace();
                }
            }

        }
        return rules;
    }

    /**
     * Parsing function for whitespace - simply moves the cursor along.
     */
    private void whitespace() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i += 1;
        }
    }

    /**
     * Parses property names.
     * @return The property name
     */
    private String word() {
        int start = i;
        while (i <  s.length()) {
            if (Character.isAlphabetic(s.charAt(i)) || Character.isDigit(s.charAt(i)) || this.s.charAt(this.i) == '#'
                    || this.s.charAt(this.i) == '-' || this.s.charAt(this.i) == '.' || this.s.charAt(this.i) == '%') {
                i += 1;
            } else {
                break;
            }
        }
        if (i <= start) {
            throw new StringIndexOutOfBoundsException("Parsing error");
        }
        return s.substring(start, i);
    }

    /**
     * Checks
     * @param literal The literal to check for.
     */
    private void literal(char literal) {
        if (i >= s.length() || s.charAt(i) != literal) {
            throw new StringIndexOutOfBoundsException("Parsing error");
        }
        i += 1;
    }

    /**
     * Parses a property-value pair.
     * @return The parsed pair.
     */
    private Map.Entry<String, String> pair() {
        String prop = word();
        whitespace();
        literal(':');
        whitespace();
        String val = word();
        return new AbstractMap.SimpleEntry<>(prop.toLowerCase(Locale.ROOT), val);
    }

    /**
     * Parses CSS properties.
     * @return A Map containing CSS properties.
     */
    public Map<String, String> body() {
        Map<String, String> pairs = new HashMap<>();
        while (i < s.length() && s.charAt(i) != '}') {
            try {
                Map.Entry<String, String> pair = pair();
                pairs.put(pair.getKey().toLowerCase(Locale.ROOT), pair.getValue());
                whitespace();
                literal(':');
                whitespace();
            } catch (Exception e) {
                // If we fail to parse a property-value pair, skip to the next semicolon or to the end of the string
                Character why = ignoreUntil(new char[]{';', '}'});
                if (why != null && why == ';') {
                    literal(';');
                    whitespace();
                } else {
                    break;
                }
            }

        }
        return pairs;
    }

    private Character ignoreUntil(char[] chars) {
        while (i < s.length()) {
            for (char c : chars) {
                if (s.charAt(i) == c) {
                    return c;
                }
            }
            i += 1;
        }
        return null;
    }

    public ISelector selector() {
        ISelector out = new TagSelector(word().toLowerCase(Locale.ROOT));
        whitespace();
        while (i < s.length() && s.charAt(i) != '{') {
            String tag = word();
            TagSelector descendant = new TagSelector(tag.toLowerCase(Locale.ROOT));
            out = new DescendantSelector(out, descendant);
            whitespace();
        }
        return out;
    }

}
