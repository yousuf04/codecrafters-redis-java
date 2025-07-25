import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable{

    Socket clientSocket;

    public ClientHandler(Socket clientSocket) {
        this.clientSocket=clientSocket;
    }

    public void run() {
        try {
            InputStream inputStream = clientSocket.getInputStream();
            OutputStream outputStream = clientSocket.getOutputStream();
            byte[] input = new byte[1024];
            int num = 1;
            while (true) {
                num = inputStream.read(input);
                if (num < 1)
                    break;
                outputStream.write("+PONG\r\n".getBytes());
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
        finally {
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.out.println("IOException: " + e.getMessage());
            }
        }
    }
}
