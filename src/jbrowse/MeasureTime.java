package jbrowse;

import java.io.FileWriter;
import java.io.IOException;

public class MeasureTime {
    FileWriter file;

    public MeasureTime() {
        try {
            file = new FileWriter("browser.trace");
            file.write("{\"traceEvents\": [");
            long ts = System.nanoTime() / 1000; // Get current time in microseconds
            file.write(
                    "{ \"name\": \"process_name\"," +
                            "\"ph\": \"M\"," +
                            "\"ts\": " + ts + "," +
                            "\"pid\": 1, \"cat\": \"__metadata\"," +
                            "\"args\": {\"name\": \"Browser\"}}");
            file.flush();
        } catch (IOException e) {
            e.printStackTrace();
            // Handle the exception appropriately, e.g., log an error or inform the user.
        }
    }

    public synchronized void time(String name) {
        try {
            long ts = System.nanoTime() / 1000; // Convert nanoseconds to microseconds
            this.file.write(
                    ", { \"ph\": \"B\", \"cat\": \"_\"," +
                            "\"name\": \"" + name + "\"," +
                            "\"ts\": " + ts + "," +
                            "\"pid\": 1, \"tid\": " + Thread.currentThread().threadId() + "}");
            this.file.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void stop(String name) {
        try {
            long ts = System.nanoTime() / 1000; // Convert nanoseconds to microseconds
            file.write(
                    ", { \"ph\": \"E\", \"cat\": \"_\"," +
                            "\"name\": \"" + name + "\"," +
                            "\"ts\": " + ts + "," +
                            "\"pid\": 1, \"tid\": " + Thread.currentThread().threadId() + "}");
            file.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void finish() {
        try {
            file.write("]}");
            file.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}