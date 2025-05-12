package jbrowse;

import java.io.FileWriter;
import java.io.IOException;

public class MeasureTime {
    private final FileWriter file;
    private final Object lock = new Object(); // Lock for synchronizing access to the file

    public MeasureTime() throws IOException {
        file = new FileWriter("browser.trace");
        try {
            long ts = System.nanoTime() / 1000; // Get current time in microseconds
            synchronized (lock) {
                file.write("{\"traceEvents\": [");
                file.write(
                        "{ \"name\": \"process_name\"," +
                                "\"ph\": \"M\"," +
                                "\"ts\": " + ts + "," +
                                "\"pid\": 1, \"cat\": \"__metadata\"," +
                                "\"args\": {\"name\": \"Browser\"}}");
                file.flush();
            }
        } catch (IOException e) {
            // Close the file if an exception occurs during initialization
            try {
                file.close();
            } catch (IOException ex) {
                ex.printStackTrace(); // Log the error during close
            }
            throw e; // Re-throw the original exception
        }
    }

    public void time(String name) {
        try {
            long ts = System.nanoTime() / 1000; // Convert nanoseconds to microseconds
            synchronized (lock) {
                file.write(
                        ", { \"ph\": \"B\", \"cat\": \"_\"," +
                                "\"name\": \"" + name + "\"," +
                                "\"ts\": " + ts + "," +
                                "\"pid\": 1, \"tid\": " + Thread.currentThread().threadId() + "}");
                file.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop(String name) {
        try {
            long ts = System.nanoTime() / 1000; // Convert nanoseconds to microseconds
            synchronized (lock) {
                file.write(
                        ", { \"ph\": \"E\", \"cat\": \"_\"," +
                                "\"name\": \"" + name + "\"," +
                                "\"ts\": " + ts + "," +
                                "\"pid\": 1, \"tid\": " + Thread.currentThread().threadId() + "}");
                file.flush();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void finish() {
        try {
            synchronized (lock) {
                file.write("]}");
                file.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}