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
            // Build request using StringBuilder
            StringBuilder requestBuilder = new StringBuilder();
            String method = payload != null ? "POST" : "GET";
            requestBuilder.append(method)
                    .append(" ")
                    .append(this.path)
                    .append(" HTTP/1.0\r\n");

            if (payload != null) {
                requestBuilder.append("Content-Length: ")
                        .append(payload.getBytes(StandardCharsets.UTF_8).length)
                        .append("\r\n");
            }

            requestBuilder.append("Host: ")
                    .append(this.host)
                    .append("\r\n\r\n");

            if (payload != null) {
                requestBuilder.append(payload);
            }

            // Write request
            s.getOutputStream().write(requestBuilder.toString().getBytes(StandardCharsets.UTF_8));

            // Read response using NIO
            InputStream is = s.getInputStream();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384]; // Increased buffer size

            // Read headers first
            StringBuilder headerBuilder = new StringBuilder();
            int b;
            boolean headersDone = false;
            int consecutiveNewlines = 0;

            while ((b = is.read()) != -1) {
                headerBuilder.append((char) b);
                if (b == '\n') {
                    consecutiveNewlines++;
                    if (consecutiveNewlines == 2) {
                        headersDone = true;
                        break;
                    }
                } else if (b != '\r') {
                    consecutiveNewlines = 0;
                }
            }

            // Parse headers
            String[] headerLines = headerBuilder.toString().split("\r\n");
            if (headerLines.length == 0) {
                throw new IOException("No response headers");
            }

            // Check for unsupported encodings
            for (String headerLine : headerLines) {
                String lowerHeader = headerLine.toLowerCase(Locale.ROOT);
                if (lowerHeader.startsWith("transfer-encoding:") ||
                        lowerHeader.startsWith("content-encoding:")) {
                    throw new AssertionError("Cannot support transfer-encoding or content-encoding headers.");
                }
            }

            // Read body
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                response.write(buffer, 0, bytesRead);
            }

            return response.toString(StandardCharsets.UTF_8);
        }
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
        s.setTcpNoDelay(true);
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
