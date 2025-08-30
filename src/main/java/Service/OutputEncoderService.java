package Service;

import java.util.Arrays;
import java.util.List;

public class OutputEncoderService {

    @SuppressWarnings("unchecked")
    public String encodeObject(Object input) {
        if(input instanceof String) {
            return encodeBulkString((String)input);
        }
        if(input instanceof Integer) {
            return encodeInteger((Integer) input);
        }
        if(input instanceof List<?>) {
            return encodeList((List<String>) input);
        }
        else {
            throw new RuntimeException("Unknown data type found");
        }
    }

    public String encodeBulkString(String input) {
        return "$" + input.length() + "\r\n" + input + "\r\n";
    }

    public String encodeSimpleString(String input) {
        return "+" + input + "\r\n";
    }

    public String encodeInteger(Integer number) {
        return ":"+number.toString()+"\r\n";
    }

    public String encodeList(List<String> list) {
        StringBuilder ans = new StringBuilder("*" + list.size()+"\r\n");
        for (String s : list) {
            ans.append(encodeBulkString(s));
        }
        return ans.toString();
    }

    public String encodeSimpleError(String errorMessage) {
        return "-ERR " + errorMessage + "\r\n";
    }

}
