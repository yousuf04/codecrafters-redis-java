package Service;

import Models.ExpiryKey;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.*;

public class ClientHandler implements Runnable{

    Socket clientSocket;
    Map<String, ExpiryKey> keyValueMap;
    OutputEncoderService outputEncoderService = new OutputEncoderService();

    public ClientHandler(Socket clientSocket) {
        this.clientSocket=clientSocket;
        this.keyValueMap = new HashMap<>();
    }

    private static final String nullRespString = "$-1\r\n";
    private static final String nullRespArray = "*0\r\n";

    public void run() {
        try (
                InputStream inputStream = clientSocket.getInputStream();
                OutputStream outputStream = clientSocket.getOutputStream()
        ) {
            byte[] input = new byte[2048];
            while (true) {
                int num = inputStream.read(input);
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

    private String respond(byte[] input) {
        if(input[0]=='*')
        {
            List<String> arguments = parseArguments(input);
            String command = arguments.get(0);
            if("PING".equalsIgnoreCase(command)) {
                return outputEncoderService.encodeSimpleString("PONG");
            }
            else if("ECHO".equalsIgnoreCase(command)) {
                String echoString = arguments.get(1);
                return outputEncoderService.encodeBulkString(echoString);
            }
            else if("SET".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                String value = arguments.get(2);
                if(arguments.size()<5) {
                    return setValue(key, value, null, null);
                }
                else {
                    String timeUnit = arguments.get(3);
                    Long time = Long.parseLong(arguments.get(4));
                    return setValue(key, value, timeUnit, time);
                }
            }
            else if("GET".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                return getValue(key);
            }
            else if("RPUSH".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                for(int i=2;i<arguments.size();i++) {
                    String value = arguments.get(i);
                    appendRightToList(key,value);
                }
                return outputEncoderService.encodeInteger(sizeOfList(key));
            }
            else if("LRANGE".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                Integer startIndex = Integer.parseInt(arguments.get(2));
                Integer endIndex = Integer.parseInt(arguments.get(3));
                return listElementsInRange(key, startIndex, endIndex);
            }
            else if("LPUSH".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                for(int i=2;i<arguments.size();i++) {
                    String value = arguments.get(i);
                    appendLeftToList(key,value);
                }
                return outputEncoderService.encodeInteger(sizeOfList(key));
            }
            else {
                throw new RuntimeException("Command not found");
            }
        }
        return "";
    }

    public List<String> parseArguments(byte[] input) {
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
        return arguments;
    }

    private String setValue(String key, String value, String timeUnit, Long time) {
        long expiryTime = Instant.now().toEpochMilli();
        if(timeUnit == null) {
            expiryTime = -1;
        }
        else {
            if("EX".equalsIgnoreCase(timeUnit)) {
                expiryTime+=time*1000;
            }
            else if("PX".equalsIgnoreCase(timeUnit)) {
                expiryTime += time;
            }
            else {
                throw new RuntimeException("Incorrect unit for time sent, it can only be PX or EX.");
            }
        }
        keyValueMap.put(key, new ExpiryKey(value, expiryTime));
        System.out.println("Key : "+key+" set with the value: "+value+" and expiry time: "+expiryTime);
        return "+OK\r\n";
    }

    private String getValue(String key) {
        ExpiryKey expiryKey = keyValueMap.get(key);
        if(expiryKey == null) {
            return nullRespString;
        }
        Object value = expiryKey.getValue();
        long currentTime = Instant.now().toEpochMilli();

        Long expiryTime = expiryKey.getExpiryTime();
        System.out.println("The value of key : "+key+" is : "+value+", and the expiry time is: "+expiryTime);
        System.out.println("Time when accessing key : "+key+ " is : " + currentTime);
        if(expiryTime!=-1 && currentTime>expiryTime) {
            keyValueMap.remove(key);
            return nullRespString;
        }
        return outputEncoderService.encodeObject(value);
    }

    public void appendRightToList(String key, String value) {

        if (keyValueMap.get(key) == null) {
            List<String> list = new LinkedList<>();
            list.addLast(value);
            keyValueMap.put(key, new ExpiryKey(list, -1));
        } else if (keyValueMap.get(key).getValue() instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) keyValueMap.get(key).getValue();
            list.addLast(value);
        } else {
            throw new IllegalArgumentException("Value at key is not a List<String>");
        }
    }

    public void appendLeftToList(String key, String value) {

        if (keyValueMap.get(key) == null) {
            List<String> list = new LinkedList<>();
            list.addFirst(value);
            keyValueMap.put(key, new ExpiryKey(list, -1));
        } else if (keyValueMap.get(key).getValue() instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) keyValueMap.get(key).getValue();
            list.addFirst(value);
        } else {
            throw new IllegalArgumentException("Value at key is not a List<String>");
        }
    }

    public Integer sizeOfList(String key) {
        if(keyValueMap.get(key) == null) {
            throw new RuntimeException("Key does not exist in the map");
        }
        if (keyValueMap.get(key).getValue() instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) keyValueMap.get(key).getValue();
            return list.size();
        } else {
            throw new IllegalArgumentException("Value at key is not a List<String>");
        }
    }

    public String listElementsInRange(String key, Integer startIndex, Integer endIndex) {
        if (keyValueMap.get(key) == null) {
            return nullRespArray;
        } else if (keyValueMap.get(key).getValue() instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) keyValueMap.get(key).getValue();
            int len =list.size();
            if(startIndex<0)
                startIndex+=len;
            if(endIndex<0)
                endIndex+=len;
            if(startIndex >= len) {
                return nullRespArray;
            }
            else if(startIndex>endIndex) {
                return nullRespArray;
            }
            List<String> subList = list.subList(Math.max(0,startIndex), Math.max(0,Math.min(len, endIndex+1)));
            return outputEncoderService.encodeList(subList);
        } else {
            throw new IllegalArgumentException("Value at key is not a List<String>");
        }
    }
}
