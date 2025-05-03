package jbrowse;

import java.util.Map;

public record CookiePair(String cookie, Map<String, String> params) {
}
