package org.example.ticketmanagement.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BCryptEncoded {
    // 临时方法转换方法，便于测试通过密码校验
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String encodedPassword = encoder.encode("123456");
        System.out.println(encodedPassword);

    }
}
