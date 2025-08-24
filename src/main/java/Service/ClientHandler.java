package Service;

import Models.DataStore;
import Models.Entry;
import Models.ExpiryKey;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class ClientHandler implements Runnable {

    Socket clientSocket;
    OutputEncoderService outputEncoderService = new OutputEncoderService();
    private final ConcurrentHashMap<String, ExpiryKey> keyMap = DataStore.getInstance().getKeyMap();
    private final ConcurrentHashMap<String, List<String>> listMap = DataStore.getInstance().getListMap();
    private final ConcurrentHashMap<String, BlockingQueue<Thread>> clientWaiters = DataStore.getInstance().getClientWaiters();
    private final ConcurrentHashMap<String, Entry> streamMap = DataStore.getInstance().getStreamMap();

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
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
                System.out.println("Received input from client: " + new String(input));
                String output = respond(input);
                System.out.println("Response being sent to client :" + output);
                outputStream.write(output.getBytes());
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        } finally {
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
        if (input[0] == '*') {
            List<String> arguments = parseArguments(input);
            String command = arguments.get(0);
            if ("PING".equalsIgnoreCase(command)) {
                return outputEncoderService.encodeSimpleString("PONG");
            } else if ("ECHO".equalsIgnoreCase(command)) {
                String echoString = arguments.get(1);
                return outputEncoderService.encodeBulkString(echoString);
            } else if ("SET".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                String value = arguments.get(2);
                if (arguments.size() < 5) {
                    return setValue(key, value, null, null);
                } else {
                    String timeUnit = arguments.get(3);
                    Long time = Long.parseLong(arguments.get(4));
                    return setValue(key, value, timeUnit, time);
                }
            } else if ("GET".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                return getValue(key);
            } else if ("RPUSH".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                for (int i = 2; i < arguments.size(); i++) {
                    String value = arguments.get(i);
                    appendRightToList(key, value);
                }
                return outputEncoderService.encodeInteger(sizeOfList(key));
            } else if ("LRANGE".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                Integer startIndex = Integer.parseInt(arguments.get(2));
                Integer endIndex = Integer.parseInt(arguments.get(3));
                return listElementsInRange(key, startIndex, endIndex);
            } else if ("LPUSH".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                for (int i = 2; i < arguments.size(); i++) {
                    String value = arguments.get(i);
                    appendLeftToList(key, value);
                }
                return outputEncoderService.encodeInteger(sizeOfList(key));
            } else if ("LLEN".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                return outputEncoderService.encodeInteger(sizeOfList(key));
            } else if ("LPOP".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                int len = 1;
                if (arguments.size() < 3) {
                    return outputEncoderService.encodeBulkString(removeElementFromLeft(key));
                }
                len = Integer.parseInt(arguments.get(2));
                List<String> elements = new LinkedList<>();
                for (int i = 0; i < Math.min(len, sizeOfList(key)); i++) {
                    String element = removeElementFromLeft(key);
                    elements.addLast(element);
                }
                return outputEncoderService.encodeList(elements);
            } else if ("BLPOP".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                Double timeout = Double.parseDouble(arguments.get(2));
                return removeBlockedElementFromLeft(key, timeout);
            } else if ("TYPE".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                return getType(key);
            } else if ("XADD".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                String id = arguments.get(2);
                Map<String, String> keyValueMap = new HashMap<>();
                for(int i = 3; i < arguments.size(); i+=2) {
                    keyValueMap.put(arguments.get(i), arguments.get(i+1));
                }
                streamMap.put(key, new Entry(id, keyValueMap));
                return outputEncoderService.encodeBulkString(id);
            }
            else {
                throw new RuntimeException("Command not found");
            }
        }
        return "";
    }

    public List<String> parseArguments(byte[] input) {
        int in = 1;
        int numberOfArguments = 0;
        while (input[in] != '\r') {
            numberOfArguments = numberOfArguments * 10 + (input[in] - '0');
            in++;
        }
        System.out.println("The number of arguments sent are :" + numberOfArguments);
        in += 2;
        ArrayList<String> arguments = new ArrayList<>();
        for (int i = 0; i < numberOfArguments; i++) {
            in++;
            int argumentLength = 0;
            while (input[in] != '\r') {
                argumentLength = argumentLength * 10 + (input[in] - '0');
                in++;
            }
            in += 2;
            System.out.println("Argument " + i + " Length: " + argumentLength);
            StringBuilder argument = new StringBuilder();
            for (int j = 0; j < argumentLength; j++, in++) {
                argument.append((char) input[in]);
            }
            in += 2;
            System.out.println("Argument " + i + " : " + argument);
            arguments.add(argument.toString());
        }
        return arguments;
    }

    private String setValue(String key, String value, String timeUnit, Long time) {
        long expiryTime = Instant.now().toEpochMilli();
        if (timeUnit == null) {
            expiryTime = -1L;
        } else {
            if ("EX".equalsIgnoreCase(timeUnit)) {
                expiryTime += time * 1000;
            } else if ("PX".equalsIgnoreCase(timeUnit)) {
                expiryTime += time;
            } else {
                throw new RuntimeException("Incorrect unit for time sent, it can only be PX or EX.");
            }
        }
        keyMap.put(key, new ExpiryKey(value, expiryTime));
        System.out.println("Key : " + key + " set with the value: " + value + " and expiry time: " + expiryTime);
        return "+OK\r\n";
    }

    private String getValue(String key) {
        ExpiryKey expiryKey = keyMap.get(key);
        if (expiryKey == null) {
            return nullRespString;
        }
        String value = expiryKey.getValue();
        long currentTime = Instant.now().toEpochMilli();

        Long expiryTime = expiryKey.getExpiryTime();
        System.out.println("The value of key : " + key + " is : " + value + ", and the expiry time is: " + expiryTime);
        System.out.println("Time when accessing key : " + key + " is : " + currentTime);
        if (expiryTime != -1 && currentTime > expiryTime) {
            keyMap.remove(key);
            return nullRespString;
        }
        return outputEncoderService.encodeBulkString(value);
    }

    public void appendRightToList(String key, String value) {
        listMap.computeIfAbsent(key, k -> new LinkedList<>()).addLast(value);
    }

    public void appendLeftToList(String key, String value) {
        listMap.computeIfAbsent(key, k -> new LinkedList<>()).addFirst(value);
    }

    public String removeElementFromLeft(String key) {
        List<String> list = listMap.get(key);
        if (list == null || list.isEmpty()) {
            return nullRespString;
        }
        return list.removeFirst();
    }

    public Integer sizeOfList(String key) {
        List<String> list = listMap.get(key);
        return (list == null) ? 0 : list.size();
    }

    public String listElementsInRange(String key, Integer startIndex, Integer endIndex) {
        List<String> list = listMap.get(key);
        if (list == null || list.isEmpty()) {
            return nullRespArray;
        }

        int len = list.size();
        if (startIndex < 0)
            startIndex += len;
        if (endIndex < 0)
            endIndex += len;
        if (startIndex >= len) {
            return nullRespArray;
        } else if (startIndex > endIndex) {
            return nullRespArray;
        }
        List<String> subList = list.subList(Math.max(0, startIndex), Math.max(0, Math.min(len, endIndex + 1)));
        return outputEncoderService.encodeList(subList);
    }

    private String removeBlockedElementFromLeft(String key, Double timeout) {
        long start = System.currentTimeMillis();
        long timeoutMilliSeconds = Math.round(timeout * 1000);
        BlockingQueue<Thread> waiters = clientWaiters.computeIfAbsent(key, k -> new LinkedBlockingQueue<>());
        waiters.add(Thread.currentThread());

        while (true) {
            List<String> elements = listMap.get(key);
            if (elements != null && !elements.isEmpty()) {
                waiters.remove(Thread.currentThread());
                ArrayList<String> response = new ArrayList<>();
                response.add(key);
                response.add(removeElementFromLeft(key));
                return outputEncoderService.encodeList(response);
            }

            long current = System.currentTimeMillis();
            if (timeoutMilliSeconds != 0 && (current - start) > timeoutMilliSeconds) {
                waiters.remove(Thread.currentThread());
                return nullRespString;
            }

            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                waiters.remove(Thread.currentThread());
                return nullRespString;
            }
        }
    }

    private String getType(String key) {
        if (keyMap.containsKey(key)) {
            return outputEncoderService.encodeSimpleString("string");
        } else if (listMap.containsKey(key)) {
            return outputEncoderService.encodeSimpleString("list");
        } else if (streamMap.containsKey(key)) {
            return outputEncoderService.encodeSimpleString("stream");
        }
        else {
            return outputEncoderService.encodeSimpleString("none");
        }
    }
}