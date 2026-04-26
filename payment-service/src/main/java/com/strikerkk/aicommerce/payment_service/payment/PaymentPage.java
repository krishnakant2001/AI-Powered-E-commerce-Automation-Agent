package com.strikerkk.aicommerce.payment_service.payment;

import com.strikerkk.aicommerce.payment_service.entity.Payment;
import com.strikerkk.aicommerce.payment_service.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentPage {

    @Value("${razorpay.key.id}")
    private String razorpayKeyId;

    private final PaymentRepository paymentRepository;

    @GetMapping("/page/{gatewayOrderId}")
    public ResponseEntity<String> getPaymentPage(@PathVariable String gatewayOrderId) {
        String PaymentScreen = createPaymentPage(gatewayOrderId);

        return ResponseEntity.ok()
                .header("Content-Type", "text/html; charset=UTF-8")
                .body(PaymentScreen);
    }

    private String createPaymentPage(String gatewayOrderId) {
        Payment payment = paymentRepository.findByGatewayOrderId(gatewayOrderId)
                .orElseThrow(() -> new RuntimeException("Payment is not initiated yet"));

        int amountInPaise = payment.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .intValue();

        return """
                <!DOCTYPE html>
                <html>
                <body>
                    <h2>Test Payment Page</h2>
                    <p style="font-size:16px;">Order ID: %d</p>
                    <p style="font-size:16px;">
                        Amount: <span style="font-weight:bold;">₹%s</span>
                    </p>
                    <button
                        style="background:#7cabd6; font-size:20px; padding:8px; cursor:pointer;"
                        onclick="pay()">
                        Pay Now
                    </button>
                    <h3>After payment copy these values for Postman:</h3>
                    <div id="result" style="background:#f0f0f0; padding:10px; margin-top:10px;">
                        Waiting for payment...
                    </div>
                
                    <script src="https://checkout.razorpay.com/v1/checkout.js"></script>
                    <script>
                    function pay() {
                        var options = {
                            key: "%s",
                            amount: %d,
                            currency: "INR",
                            order_id: "%s",
                            handler: function(response) {
                                console.log("Full Razorpay Response:", response);
                                document.getElementById("result").innerHTML =
                                    "<b>razorpayOrderId:</b> " + response.razorpay_order_id + "<br>" +
                                    "<b>razorpayPaymentId:</b> " + response.razorpay_payment_id + "<br>" +
                                    "<b>razorpaySignature:</b> " + response.razorpay_signature;
                                document.getElementById("result").innerHTML =
                                                "<pre>" + JSON.stringify(response, null, 2) + "</pre>";
                            }
                        };
                        var rzp = new Razorpay(options);
                        rzp.open();
                    }
                  </script>
                </body>
                </html>
                """.formatted(
                payment.getOrderId(),
                payment.getAmount(),
                razorpayKeyId,
                amountInPaise,
                gatewayOrderId
        );
    }
}
