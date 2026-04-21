package com.strikerkk.aicommerce.order_service.dto.request;

import com.strikerkk.aicommerce.order_service.entity.enums.OrderStatus;
import lombok.Data;

@Data
public class UpdateOrderStatusRequest {
    private OrderStatus status;
}
