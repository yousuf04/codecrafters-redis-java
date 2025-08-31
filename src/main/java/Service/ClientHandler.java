package Service;

import Models.DataStore;
import Models.Entry;
import Models.ExpiryKey;
import Models.LockAndCondition;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ClientHandler implements Runnable {

    Socket clientSocket;
    OutputEncoderService outputEncoderService = new OutputEncoderService();
    private final ConcurrentHashMap<String, ExpiryKey> keyMap = DataStore.getInstance().getKeyMap();
    private final ConcurrentHashMap<String, LinkedList<String>> listMap = DataStore.getInstance().getListMap();
    private final ConcurrentHashMap<String, List<Entry>> streamMap = DataStore.getInstance().getStreamMap();
    private final ConcurrentHashMap<String, LockAndCondition> listLocks = DataStore.getInstance().getListLocks();

    public ClientHandler(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    private static final String nullRespString = "$-1\r\n";
    private static final String EmptyRespArray = "*0\r\n";
    private static final String nullRespArray = "*-1\r\n";

    private LockAndCondition getLockAndCondition(String key) {
        return listLocks.computeIfAbsent(key, k -> new LockAndCondition());
    }

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
                byte[] actual = Arrays.copyOf(input, num);
                System.out.println("Received input from client: " + new String(actual));
                String output = respond(actual);
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
                int itemsPushed = arguments.size() - 2;
                if (itemsPushed <= 0) {
                    return outputEncoderService.encodeInteger(sizeOfList(key));
                }

                LockAndCondition lac = getLockAndCondition(key);
                lac.getLock().lock();
                try {
                    for (int i = 2; i < arguments.size(); i++) {
                        String value = arguments.get(i);
                        appendRightToList(key, value);
                    }
                    Condition condition = lac.getWaiters().poll();
                    if (condition != null) {
                        condition.signalAll();
                    }
                    return outputEncoderService.encodeInteger(sizeOfList(key));
                }
                finally {
                    lac.getLock().unlock();
                }
            } else if ("LRANGE".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                ReentrantLock lock = getLockAndCondition(key).getLock();
                lock.lock();
                try {
                    Integer startIndex = Integer.parseInt(arguments.get(2));
                    Integer endIndex = Integer.parseInt(arguments.get(3));
                    return listElementsInRange(key, startIndex, endIndex);
                }
                finally {
                    lock.unlock();
                }
            } else if ("LPUSH".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                int itemsPushed = arguments.size() - 2;
                if (itemsPushed <= 0) return outputEncoderService.encodeInteger(sizeOfList(key));

                LockAndCondition lac = getLockAndCondition(key);
                lac.getLock().lock();
                try {
                    for (int i = 2; i < arguments.size(); i++) {
                        String value = arguments.get(i);
                        appendLeftToList(key, value);
                    }
                    Condition condition = lac.getWaiters().poll();
                    if (condition != null) {
                        condition.signalAll();
                    }
                    return outputEncoderService.encodeInteger(sizeOfList(key));
                }
                finally {
                    lac.getLock().unlock();
                }
            } else if ("LLEN".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                ReentrantLock lock = getLockAndCondition(key).getLock();
                lock.lock();
                try {
                    return outputEncoderService.encodeInteger(sizeOfList(key));
                }
                finally {
                    lock.unlock();
                }
            } else if ("LPOP".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                ReentrantLock lock = getLockAndCondition(key).getLock();
                lock.lock();
                try {
                    if (arguments.size() < 3) {
                        return outputEncoderService.encodeBulkString(removeElementFromLeft(key));
                    }
                    int count = Integer.parseInt(arguments.get(2));
                    List<String> elements = new ArrayList<>();
                    Integer sizeOfList = sizeOfList(key);
                    for (int i = 0; i < Math.min(count, sizeOfList); i++) {
                        String element = removeElementFromLeft(key);
                        elements.add(element);
                    }
                    return outputEncoderService.encodeList(elements);
                }
                finally {
                    lock.unlock();
                }
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
                return addEntry(key, id, keyValueMap);
            } else if ("XRANGE".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                String startId = arguments.get(2);
                String endId = arguments.get(3);
                return entriesInRange(key, startId, endId);
            }
            else if ("XREAD".equalsIgnoreCase(command)) {
                if(arguments.get(1).equalsIgnoreCase("block")) {
                    String time = arguments.get(2);
                    String key = arguments.get(4);
                    String startId = arguments.get(5);
                    return elementsAddedInTime(time, key, startId);
                }
                int numberOfKeys = (arguments.size() - 2)/2;
                StringBuilder stringBuilder = new StringBuilder("*");
                stringBuilder.append(numberOfKeys).append("\r\n");
                for(int i=0; i<numberOfKeys; i++) {
                    String key = arguments.get(i+2);
                    String startId = arguments.get(i + numberOfKeys +2);
                    stringBuilder.append(entriesStartingFrom(key, startId));
                }
                return stringBuilder.toString();
            }
            else if ("INCR".equalsIgnoreCase(command)) {
                String key = arguments.get(1);
                ExpiryKey value = keyMap.computeIfAbsent(key, k -> new ExpiryKey("0", -1));
                Integer newValue = Integer.parseInt(value.getValue()) + 1;
                keyMap.get(key).setValue(newValue.toString());
                return outputEncoderService.encodeInteger(Integer.parseInt(value.getValue()));
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
            StringBuilder argument = new StringBuilder();
            for (int j = 0; j < argumentLength; j++, in++) {
                argument.append((char) input[in]);
            }
            in += 2;
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
            return EmptyRespArray;
        }
        int len = list.size();
        if (startIndex < 0) startIndex += len;
        if (endIndex < 0) endIndex += len;

        if (startIndex < 0) startIndex = 0;
        if (startIndex >= len || startIndex > endIndex) {
            return EmptyRespArray;
        }
        endIndex = Math.min(endIndex, len - 1);

        List<String> subList = new ArrayList<>();
        for (int i = startIndex; i <= endIndex; i++) {
            subList.add(list.get(i));
        }
        return outputEncoderService.encodeList(subList);
    }

    private String removeBlockedElementFromLeft(String key, Double timeout) {
        LockAndCondition lac = getLockAndCondition(key);
        Condition myCondition = null;
        try {
            lac.getLock().lockInterruptibly();
            try {
                if (!isListEmpty(key) && lac.getWaiters().isEmpty()) {
                    return encodeBlpopValue(key);
                }
                else {
                    myCondition = lac.getLock().newCondition();
                    lac.getWaiters().add(myCondition);
                }

                long deadline = (timeout > 0) ? System.nanoTime() + (long)(timeout * 1_000_000_000L) : 0;

                while(isListEmpty(key) || listLocks.get(key).getWaiters().contains(myCondition)) {
                    if (timeout == 0.0) {
                        myCondition.await();
                    } else {
                        long remainingNanos = deadline - System.nanoTime();
                        if (remainingNanos<=0) {
                            lac.getWaiters().remove(myCondition);
                            return nullRespArray;
                        }
                        myCondition.awaitNanos(remainingNanos);
                    }
                }
                return encodeBlpopValue(key);
            } finally {
                lac.getLock().unlock();
            }
        } catch (InterruptedException e) {
            if (myCondition != null) {
                lac.getLock().lock();
                try {
                    lac.getWaiters().remove(myCondition);
                } finally {
                    lac.getLock().unlock();
                }
            }
            Thread.currentThread().interrupt();
            return nullRespArray;
        }
    }

    private boolean isListEmpty(String key) {
        List<String> list = listMap.get(key);
        return list == null || list.isEmpty();
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

    private String addEntry(String key, String id, Map<String, String> keyValueMap) {
        if(id.equals("*")) {
            streamMap.computeIfAbsent(key, k -> new ArrayList<>());
            Long milliseconds = System.currentTimeMillis();
            Long sequenceNumber;
            if (!streamMap.get(key).isEmpty() &&
                    streamMap.get(key).getLast().getMilliseconds().compareTo(milliseconds) == 0) {
                sequenceNumber = streamMap.get(key).getLast().getSequenceNumber() + 1;
            }
            else {
                if(milliseconds == 0) {
                    sequenceNumber = 1L;
                }
                else {
                    sequenceNumber = 0L;
                }
            }
            Entry entry = new Entry(milliseconds, sequenceNumber, keyValueMap);
            streamMap.get(key).add(entry);
        }
        else {
            List<String> parts = List.of(id.split("-"));
            Long milliseconds = Long.parseLong(parts.get(0));
            Long sequenceNumber;
            if (parts.get(1).equals("*")) {
                streamMap.computeIfAbsent(key, k -> new ArrayList<>());
                if (!streamMap.get(key).isEmpty() &&
                        streamMap.get(key).getLast().getMilliseconds().compareTo(milliseconds) == 0) {
                    sequenceNumber = streamMap.get(key).getLast().getSequenceNumber() + 1;
                } else {
                    if (milliseconds == 0) {
                        sequenceNumber = 1L;
                    } else {
                        sequenceNumber = 0L;
                    }
                }
            } else {
                sequenceNumber = Long.parseLong(parts.get(1));
                if (milliseconds == 0 && sequenceNumber == 0) {
                    return outputEncoderService.encodeSimpleError("The ID specified in XADD must be greater than 0-0");
                }
                streamMap.computeIfAbsent(key, k -> new ArrayList<>());
                if (!streamMap.get(key).isEmpty()) {
                    Entry lastEntry = streamMap.get(key).getLast();
                    Long previousMilliseconds = lastEntry.getMilliseconds();
                    Long previousSequenceNumber = lastEntry.getSequenceNumber();

                    if (milliseconds.compareTo(previousMilliseconds) < 0 ||
                            (milliseconds.compareTo(previousMilliseconds) == 0
                                    && sequenceNumber.compareTo(previousSequenceNumber) <= 0)) {
                        return outputEncoderService.encodeSimpleError("The ID specified in XADD is equal or smaller " +
                                "than the target stream top item");
                    }
                }
            }
            Entry entry = new Entry(milliseconds, sequenceNumber, keyValueMap);
            streamMap.get(key).add(entry);
        }
        return outputEncoderService.encodeBulkString(createId(key));
    }

    private String createId(String key) {
        if(!streamMap.get(key).isEmpty()) {
            Entry lastEntry = streamMap.get(key).getLast();
            Long milliseconds = lastEntry.getMilliseconds();
            Long sequenceNumber = lastEntry.getSequenceNumber();
            return milliseconds.toString() + "-" + sequenceNumber.toString();
        }
        else {
            return "0-1";
        }
    }

    private String encodeBlpopValue(String key) {
        List<String> list = listMap.get(key);
        String element = list.removeFirst();
        List<String> response = Arrays.asList(key, element);
        return outputEncoderService.encodeList(response);
    }

    private String entriesInRange(String key, String startId, String endId) {
        Long startMilliseconds;
        Long startSequenceNumber;

        if(startId.equals("-")) {
            startMilliseconds = 0L;
            startSequenceNumber = 0L;
        }
        else if(startId.contains("-")) {
            List<String> parts = Arrays.asList(startId.split("-"));
            startMilliseconds = Long.parseLong(parts.getFirst());
            startSequenceNumber = Long.parseLong(parts.getLast());
        }
        else {
            startMilliseconds = Long.parseLong(startId);
            startSequenceNumber = 0L;
        }

        Long endMilliseconds;
        Long endSequenceNumber;

        if(endId.equals("+")) {
            endMilliseconds = Long.MAX_VALUE;
            endSequenceNumber = Long.MAX_VALUE;
        }
        else if(endId.contains("-")) {
            List<String> parts = Arrays.asList(endId.split("-"));
            endMilliseconds = Long.parseLong(parts.getFirst());
            endSequenceNumber = Long.parseLong(parts.getLast());
        }
        else {
            endMilliseconds = Long.parseLong(endId);
            endSequenceNumber = Long.MAX_VALUE;
        }

        List<Entry> entries = new ArrayList<>();
        for (Entry entry:streamMap.get(key)) {

            Long milliseconds = entry.getMilliseconds();
            Long sequenceNumber = entry.getSequenceNumber();

            if(milliseconds.compareTo(startMilliseconds)>=0
                    && sequenceNumber.compareTo(startSequenceNumber)>=0
                    && milliseconds.compareTo(endMilliseconds) <=0
                    && sequenceNumber.compareTo(endSequenceNumber) <=0) {
                entries.add(entry);
            }
        }
        return outputEncoderService.encodeEntryList(entries);
    }

    private String entriesStartingFrom(String key, String startId) {

        List<String> parts = Arrays.asList(startId.split("-"));
        Long startMilliseconds = Long.parseLong(parts.getFirst());
        Long startSequenceNumber = Long.parseLong(parts.getLast());

        List<Entry> entries = new ArrayList<>();
        for (Entry entry:streamMap.get(key)) {

            Long milliseconds = entry.getMilliseconds();
            Long sequenceNumber = entry.getSequenceNumber();

            if(milliseconds.compareTo(startMilliseconds)>=0
                    && sequenceNumber.compareTo(startSequenceNumber)>=0) {
                entries.add(entry);
            }
        }
        return  "*2\r\n" +
                outputEncoderService.encodeBulkString(key) +
                outputEncoderService.encodeEntryList(entries);
    }

    private String elementsAddedInTime(String time, String key, String startId) {
        int lastIndex = streamMap.get(key).size();
        List<Entry> entries = new ArrayList<>();

        if(Long.parseLong(time) ==0) {
            while (true) {
                for (int i = lastIndex; i < streamMap.get(key).size(); i++) {
                    if ("$".equals(startId) || compare(streamMap.get(key).get(i).getId(), startId) > 0) {
                        entries.add(streamMap.get(key).get(i));
                        break;
                    }
                    lastIndex = i;
                }
                if(!entries.isEmpty()) {
                    break;
                }
                try {
                    Thread.sleep(Long.parseLong(time));
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage());
                }
            }
        }
        else {
            try {
                Thread.sleep(Long.parseLong(time));
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
            for (int i = lastIndex; i < streamMap.get(key).size(); i++) {
                if ("$".equals(startId) || compare(streamMap.get(key).get(i).getId(), startId) > 0) {
                    entries.add(streamMap.get(key).get(i));
                    break;
                }
            }
            if (entries.isEmpty()) {
                return nullRespArray;
            }
        }
        return "*1\r\n" + "*2\r\n" + outputEncoderService.encodeBulkString(key) +
                outputEncoderService.encodeEntryList(entries);
    }

    private int compare(String id1, String id2) {

        List<String> parts1 = Arrays.asList(id1.split("-"));
        Long milliseconds1 = Long.parseLong(parts1.getFirst());
        Long sequenceNumber1 = Long.parseLong(parts1.getLast());

        List<String> parts2 = Arrays.asList(id2.split("-"));
        Long milliseconds2 = Long.parseLong(parts2.getFirst());
        Long sequenceNumber2 = Long.parseLong(parts2.getLast());

        if(milliseconds1.compareTo(milliseconds2) ==0 ) {
            return Integer.compare(sequenceNumber1.compareTo(sequenceNumber2), 0);
        }
        else if (milliseconds1.compareTo(milliseconds2) >0) {
            return 1;
        }
        else {
            return 0;
        }
    }
}