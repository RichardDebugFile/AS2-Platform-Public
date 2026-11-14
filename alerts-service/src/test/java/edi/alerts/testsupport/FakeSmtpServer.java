package edi.alerts.testsupport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servidor SMTP minimo para las pruebas, en 127.0.0.1 y un puerto libre: acepta el dialogo basico
 * (EHLO, MAIL, RCPT, DATA, QUIT) y guarda cada mensaje recibido. Con {@code rejectAuth} anuncia AUTH y
 * rechaza cualquier intento, como un servidor con la contrasena equivocada.
 */
public final class FakeSmtpServer implements AutoCloseable {

    /** Mensaje recibido: remitente y destinatarios del sobre y el contenido completo (cabeceras y cuerpo). */
    public record Received(String mailFrom, List<String> rcptTo, String data) {
    }

    private final ServerSocket server;
    private final boolean rejectAuth;
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private final Thread acceptor;

    public FakeSmtpServer(boolean rejectAuth) throws IOException {
        this.rejectAuth = rejectAuth;
        this.server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        this.acceptor = Thread.ofVirtual().start(this::acceptLoop);
    }

    public int port() {
        return server.getLocalPort();
    }

    public List<Received> received() {
        return received;
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket s = server.accept();
                Thread.ofVirtual().start(() -> session(s));
            } catch (IOException e) {
                return;
            }
        }
    }

    private void session(Socket socket) {
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                     StandardCharsets.UTF_8));
             OutputStream out = socket.getOutputStream()) {
            reply(out, "220 fake-smtp ESMTP listo");
            String from = null;
            List<String> to = new CopyOnWriteArrayList<>();
            String line;
            while ((line = in.readLine()) != null) {
                String cmd = line.toUpperCase(Locale.ROOT);
                if (cmd.startsWith("EHLO")) {
                    reply(out, rejectAuth ? "250-fake-smtp\r\n250-AUTH LOGIN PLAIN\r\n250 OK" : "250-fake-smtp\r\n250 OK");
                } else if (cmd.startsWith("HELO")) {
                    reply(out, "250 fake-smtp");
                } else if (cmd.startsWith("AUTH")) {
                    reply(out, "535 5.7.8 Authentication credentials invalid");
                } else if (cmd.startsWith("MAIL FROM:")) {
                    from = line.substring("MAIL FROM:".length()).trim();
                    reply(out, "250 OK");
                } else if (cmd.startsWith("RCPT TO:")) {
                    to.add(line.substring("RCPT TO:".length()).trim());
                    reply(out, "250 OK");
                } else if (cmd.equals("DATA")) {
                    reply(out, "354 Termine con <CRLF>.<CRLF>");
                    received.add(new Received(from, List.copyOf(to), readData(in)));
                    to.clear();
                    reply(out, "250 OK en cola");
                } else if (cmd.equals("QUIT")) {
                    reply(out, "221 Adios");
                    return;
                } else {
                    // RSET, NOOP y cualquier otro comando
                    reply(out, "250 OK");
                }
            }
        } catch (SocketException e) {
            // el cliente cerro la conexion
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String readData(BufferedReader in) throws IOException {
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null && !line.equals(".")) {
            data.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
        }
        return data.toString();
    }

    private static void reply(OutputStream out, String text) throws IOException {
        out.write((text + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @Override
    public void close() throws IOException {
        server.close();
        acceptor.interrupt();
    }
}
