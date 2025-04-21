package jbrowse;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class URL {
    private String scheme;
    private String host;
    private String path;
    private int port;
    public URL (String url)
    {
        if (url == null){
            throw new NullPointerException("Supplied URL is null");
        }
        List<String> parts = Arrays.stream(url.split("://", 2)).toList();
        this.scheme = parts.getFirst();
        if (this.scheme.equals("http")) {
            this.port = 80;
        } else {
            if (!this.scheme.equals("https")) {
                throw new IllegalArgumentException("URL scheme must be http or https");
            }

            this.port = 443;
        }

        // Ensure that "/" is present in the remaining URL part
        String pathUrl = parts.get(1);
        if (!pathUrl.contains("/")){
            pathUrl += "/";
        }
        // Split host and path
        List<String> hostPathParts = Arrays.stream(pathUrl.split("/", 2)).toList();
        if (hostPathParts.size() < 2) {
            throw new IllegalArgumentException("Invalid URL format");
        } else {
            this.host = hostPathParts.get(0);
            this.path = "/" + hostPathParts.get(1);
            if (this.host.contains(":")){
                List<String> hostPortParts = Arrays.stream(this.host.split(":")).toList();
                this.host = hostPortParts.getFirst();
                this.port = Integer.parseInt(hostPortParts.get(1));
            }
        }

    }

    public final String request(String payload) throws NoSuchAlgorithmException, KeyManagementException, IOException {
        try (Socket s = getSocket()) {
            var method = payload != null ? "POST" : "GET";
            String request = method+" " + this.path + " HTTP/1.0\r\n";
            if (payload != null) {
                request += "Content-Length: " + payload.getBytes(StandardCharsets.UTF_8).length + "\r\n";
            }
            request = request + "Host: " + this.host + "\r\n" + "\r\n";

            if (payload != null) {
                request += payload;
            }
            try {
                // Get the output stream and write the request.
                s.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
                // Get the response from the server.
                InputStream is = s.getInputStream();
                Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader response = new BufferedReader(reader, 8192);
                // Get the status line from the response.
                String statusLine = response.readLine();
                if (statusLine == null) {
                    throw new NullPointerException("Could not read status line from HTTP response");
                }
                // Then, get the response headers.
                Map<String, String> responseHeaders = new LinkedHashMap<>();

                while (true) {
                    String line = response.readLine();
                    if ("".equals(line)) {
                        if (responseHeaders.containsKey("transfer-encoding") || responseHeaders.containsKey("content-encoding")) {
                            throw new AssertionError("Cannot support transfer-encodinfg or content-encoding headers.");
                        }
                        StringWriter buffer = new StringWriter();
                        char[] charBuffer = new char[8192];
                        int readChars = response.read(charBuffer);
                        while (readChars >= 0) {
                            buffer.write(charBuffer, 0, readChars);
                            readChars = response.read(charBuffer);
                        }
                        return buffer.toString();
                    }
                    if (line == null) {
                        throw new NullPointerException("Could not read line from HTTP response");
                    }
                    List<String> headerComponents = Arrays.stream(line.split(":", 2)).toList();
                    String header = headerComponents.getFirst().toLowerCase(Locale.ROOT);
                    String value = headerComponents.get(1);
                    responseHeaders.put(header, value);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    private Socket getSocket() throws NoSuchAlgorithmException, KeyManagementException, IOException {
        Socket s;
        if (this.scheme.equals("https")) {
            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, null, null);
            SSLSocketFactory sf = sslContext.getSocketFactory();
            s = sf.createSocket(this.host, this.port);
        } else {
            s = new Socket(this.host, this.port);
        }
        return s;
    }

    public URL resolve(String url) {
        if (url.contains("://")) {
            return new URL(url);
        }
        if (!url.startsWith("/")) {
            String dir = this.path.substring(0, this.path.lastIndexOf("/"));
            while (url.startsWith("../")) {
                url = url.substring(3);
                if (dir.contains("/")) {
                    dir = dir.substring(0, dir.lastIndexOf('/'));
                }
            }
            url = dir + "/" + url;
        }
        if (url.startsWith("//")) {
            return new URL(scheme + ":" + url);
        } else {
            return new URL(scheme + "://" + host + ":" + port + url);

        }
    }

    public String toString() {
        var portPart = ":" + port;
        if (scheme.equals("https") && port == 443 || scheme.equals("http") && port == 80) {
            portPart = "";
        }
        return scheme + "://" + host + portPart + path;
    }

}
