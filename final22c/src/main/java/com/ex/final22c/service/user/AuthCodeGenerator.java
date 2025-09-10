package com.ex.final22c.service.user;

import java.util.Random;

public class AuthCodeGenerator {
    public static String generateCode() {
        Random random = new Random();
        int code = 100000 + random.nextInt(900000); // 100000~999999
        return String.valueOf(code);
    }
}
