import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import javax.imageio.ImageIO;
import lk.vexview.engine.BufferImageLib;
import lk.vexview.engine.ImageReadLimits;

public final class VerifyImageDownload {
    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", encoded);
        byte[] png = encoded.toByteArray();
        try (ServerSocket server = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))) {
            Thread worker = new Thread(() -> {
                try {
                    for (int i = 0; i < 2; i++) {
                        try (Socket socket = server.accept()) {
                            InputStream request = socket.getInputStream();
                            int state = 0, value;
                            while (state < 4 && (value = request.read()) != -1) {
                                state = value == (state % 2 == 0 ? '\r' : '\n') ? state + 1 : 0;
                            }
                            OutputStream output = socket.getOutputStream();
                            output.write(("HTTP/1.1 200 OK\r\nContent-Length: " + png.length + "\r\nConnection: close\r\n\r\n").getBytes("US-ASCII"));
                            output.flush();
                            if (i == 0) { output.write(png); output.flush(); }
                            else Thread.sleep(4500);
                        }
                    }
                } catch (Exception exception) { throw new RuntimeException(exception); }
            });
            worker.setDaemon(true);
            worker.start();
            String address = "http://127.0.0.1:" + server.getLocalPort() + "/image";
            BufferedImage result = BufferImageLib.readImage(address, ImageReadLimits::read);
            if (result == null || result.getWidth() != 1 || result.getHeight() != 1) throw new AssertionError("PNG download failed");
            long start = System.nanoTime();
            try {
                if (BufferImageLib.readImage(address, ImageReadLimits::read) != null) {
                    throw new AssertionError("A stalled image response was accepted");
                }
            } catch (SocketTimeoutException expected) {
                // ImageIO may either propagate the timeout or report no matching reader.
            }
            long millis = (System.nanoTime() - start) / 1000000;
            if (millis < 2000 || millis > 7000) throw new AssertionError("Unexpected read timeout: " + millis);
        }
        System.out.println("PASS: real HTTP PNG download and stalled response timeout");
    }
}
