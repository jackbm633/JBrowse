package jbrowse;

import java.util.Map;

public record Response(String content, Map<String, String> headers) {
}
