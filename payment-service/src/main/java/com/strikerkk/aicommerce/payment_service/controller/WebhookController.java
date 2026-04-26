package com.strikerkk.aicommerce.payment_service.controller;

import com.strikerkk.aicommerce.payment_service.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@Slf4j
@RestController
@RequestMapping("/payments/webhook")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping("/razorpay")
    public ResponseEntity<Void> handleRazorpayWebhook(@RequestBody String payload,
                                                      @RequestHeader("X-Razorpay-Signature") String razorpaySignature) {

        log.info("Razorpay webhook received");

        webhookService.handleRazorpayWebhook(payload, razorpaySignature);
        return ResponseEntity.ok().build();
    }
}