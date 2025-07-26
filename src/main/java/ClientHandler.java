import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ClientHandler implements Runnable{

    Socket clientSocket;
    Map<String, ExpiryKey> keyValueMap;

    private class ExpiryKey {

        String value;
        Long expiryTime;

        public ExpiryKey(String value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }

        public String getValue() {
            return value;
        }
        public Long getExpiryTime() {
            return expiryTime;
        }
    }

    public ClientHandler(Socket clientSocket) {
        this.clientSocket=clientSocket;
        this.keyValueMap = new HashMap<>();
    }

    private static final String nullRespString = "$-1\r\n";

    private String encodeBulkString(String input) {
        return "$" + input.length() + "\r\n" + input + "\r\n";
    }

    private String respond(byte[] input) {
        if(input[0]=='*')
        {
            int in =1;
            int numberOfArguments=0;
            while(input[in]!='\r') {
                numberOfArguments = numberOfArguments*10+ (input[in]-'0');
                in++;
            }
            System.out.println("The number of arguments sent are :"+numberOfArguments);
            in+=2;
            ArrayList<String> arguments= new ArrayList<>();
            for(int i=0;i<numberOfArguments;i++) {
                in++;
                int argumentLength=0;
                while(input[in]!='\r') {
                    argumentLength = argumentLength*10+ (input[in]-'0');
                    in++;
                }
                in+=2;
                System.out.println("Argument "+i+" Length: "+argumentLength);
                StringBuilder argument= new StringBuilder();
                for(int j=0;j<argumentLength;j++,in++) {
                    argument.append((char)input[in]);
                }
                in+=2;
                System.out.println("Argument "+i+" : "+argument);
                arguments.add(argument.toString());
            }
            String command = arguments.get(0);
            if("PING".equalsIgnoreCase(command)) {
                return "+PONG\r\n";
            }
            else if("ECHO".equalsIgnoreCase(command)) {
                String echoString = arguments.get(1);
                return encodeBulkString(echoString);
            }
            else if("SET".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                String value = arguments.get(2);
                long currentTime = Instant.now().toEpochMilli();
                Long expiryTime = getExpiryTime(currentTime, arguments);
                keyValueMap.put(key, new ExpiryKey(value, expiryTime));
                System.out.println("Key : "+key+" set with the value: "+value+" and expiry time: "+expiryTime);
                return "+OK\r\n";
            }
            else if("GET".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                ExpiryKey expiryKey = keyValueMap.get(key);
                if(expiryKey == null) {
                    return nullRespString;
                }
                String value = expiryKey.getValue();
                long currentTime = Instant.now().toEpochMilli();

                Long expiryTime = expiryKey.getExpiryTime();
                System.out.println("The value of key : "+key+" is : "+value+", and the expiry time is: "+expiryTime);
                System.out.println("Time when accessing key : "+key+ " is : " + currentTime);
                if(expiryTime!=-1 && currentTime>expiryTime) {
                    keyValueMap.remove(key);
                    return nullRespString;
                }
                return encodeBulkString(value);
            }
            else {
                throw new RuntimeException("Command not found");
            }

        }
        return "";
    }

    private Long getExpiryTime(long currentTime, ArrayList<String> arguments) {
        long expiryTime = currentTime;
        if(arguments.size()>3) {
            String timeType = arguments.get(3);
            long time = Long.parseLong(arguments.get(4));
            if("EX".equalsIgnoreCase(timeType)) {
                expiryTime+=time*1000;
            }
            else if("PX".equalsIgnoreCase(timeType)) {
                expiryTime += time;
            }
            else {
                expiryTime = -1;
            }
        }
        else {
            expiryTime = -1;
        }
        return expiryTime;
    }

    public void run() {
        try (
            InputStream inputStream = clientSocket.getInputStream();
            OutputStream outputStream = clientSocket.getOutputStream();
        ) {
            byte[] input = new byte[2048];
            int num = 1;
            while (true) {
                num = inputStream.read(input);
                if (num < 1)
                    break;
                System.out.println("Received input from client: "+new String(input));
                String output = respond(input);
                System.out.println("Response being sent to client :"+output);
                outputStream.write(output.getBytes());
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
