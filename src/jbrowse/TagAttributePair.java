package jbrowse;

import java.util.Map;

public record TagAttributePair(String tag, Map<String, String> attributes) {
}
