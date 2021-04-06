package model;

public class Util {
    public static String bytesToString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (byte b : bytes) {
            sb.append(String.format("%02x ", b & 0xff));
            // 16바이트 출력 후 줄 바꿈
            if(++i % 16 == 0) sb.append("\n");
        }
        return sb.toString();
    }

}
