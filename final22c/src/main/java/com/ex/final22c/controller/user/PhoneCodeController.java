package com.ex.final22c.controller.user;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ex.final22c.service.user.PhoneCodeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(path = "/auth/phone", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class PhoneCodeController {

    private final PhoneCodeService phoneCodeService;

    // JSON
    @PostMapping(path = "/send", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> sendJson(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        var r = phoneCodeService.send(phone);
        return ResponseEntity.ok(Map.of(
                "ok", r.ok, "msg", r.msg,
                "cooldownSeconds", r.cooldownSeconds,
                "remainToday", r.remainToday
        ));
    }

    @PostMapping(path = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> verifyJson(@RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        String code = body.get("code");
        var r = phoneCodeService.verify(phone, code);
        return ResponseEntity.ok(Map.of("ok", r.ok, "msg", r.msg));
    }

    // (선택) FORM 버전
    @PostMapping(path = "/send")
    public ResponseEntity<Map<String, Object>> sendForm(@RequestParam("phone") String phone) {
        var r = phoneCodeService.send(phone);
        return ResponseEntity.ok(Map.of(
                "ok", r.ok, "msg", r.msg,
                "cooldownSeconds", r.cooldownSeconds,
                "remainToday", r.remainToday
        ));
    }

    @PostMapping(path = "/verify")
    public ResponseEntity<Map<String, Object>> verifyForm(
            @RequestParam("phone") String phone,
            @RequestParam("code") String code) {
        var r = phoneCodeService.verify(phone, code);
        return ResponseEntity.ok(Map.of("ok", r.ok, "msg", r.msg));
    }
}
