package jbrowse;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

public class Program {
    public static void main(String[] args) throws IOException, NoSuchAlgorithmException, KeyManagementException {
        Browser b = new Browser();
        // Open a new tab with the specified URL
        b.newTab(new URL("http://localhost:5500"));
    }


    /**
     * Prints the visible text content from an HTML body.
     * @param body The HTML body string.
     */
    public static void show(String body) {
        boolean inTag = false;

        // Iterate through each character in the HTML string
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);

            // Detect opening and closing of HTML tags
            if (c == '<') {
                inTag = true;
            } else if (c == '>') {
                inTag = false;
            } else if (!inTag) {
                // Print characters outside of HTML tags
                System.out.print(c);
            }
        }
    }
}