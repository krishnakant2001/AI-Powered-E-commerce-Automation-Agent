package com.strikerkk.aicommerce.order_service.service;

import com.strikerkk.aicommerce.order_service.auth.UserContext;
import com.strikerkk.aicommerce.order_service.clients.CartClient;
import com.strikerkk.aicommerce.order_service.clients.UserClient;
import com.strikerkk.aicommerce.order_service.dto.ClientResponse.AddressResponse;
import com.strikerkk.aicommerce.order_service.dto.ClientResponse.CartItemResponse;
import com.strikerkk.aicommerce.order_service.dto.ClientResponse.ProductItemResponse;
import com.strikerkk.aicommerce.order_service.dto.request.PlaceOrderRequest;
import com.strikerkk.aicommerce.order_service.dto.response.OrderItemResponse;
import com.strikerkk.aicommerce.order_service.dto.response.OrderResponse;
import com.strikerkk.aicommerce.order_service.dto.response.OrderSummaryResponse;
import com.strikerkk.aicommerce.order_service.entity.Order;
import com.strikerkk.aicommerce.order_service.entity.OrderItem;
import com.strikerkk.aicommerce.order_service.entity.enums.OrderStatus;
import com.strikerkk.aicommerce.order_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartClient cartClient;
    private final UserClient userClient;
    private final OrderResilience4j orderResilience4j;
    private final ModelMapper modelMapper;

    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request) {

        Long userId = Long.valueOf(UserContext.getUserId());

        List<CartItemResponse> cartItems = cartClient.getCartItems();

        AddressResponse address = userClient.getAddressByAddressId(request.getAddressId());

        log.info("Placing the order by userId={}", userId);

        if(cartItems == null || cartItems.isEmpty()) {
            throw new RuntimeException("Cart is empty, cannot place order");
        }

        List<OrderItem> orderItems = cartItems.stream()
                .map(cartItem -> OrderItem.builder()
                        .productId(cartItem.getProductId())
                        .variantId(cartItem.getVariantId())
                        .productName(cartItem.getProductDetails().getProductName())
                        .productBrand(cartItem.getProductDetails().getProductBrand())
                        .productImageUrl(cartItem.getProductDetails().getProductImageUrl())
                        .size(cartItem.getProductDetails().getSize())
                        .color(cartItem.getProductDetails().getColor())
                        .quantity(cartItem.getQuantity())
                        .priceAtOrder(cartItem.getPriceAtAdd())
                        .lineTotal(cartItem.getPriceAtAdd()
                                .multiply(BigDecimal.valueOf(cartItem.getQuantity())))
                        .build()
                )
                .toList();

        BigDecimal totalAmount = orderItems.stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);


        BigDecimal thresholdAmount = BigDecimal.valueOf(1999);
        BigDecimal deliveryCharges =
                (totalAmount.compareTo(thresholdAmount) > 0)
                        ? BigDecimal.valueOf(0)
                        : BigDecimal.valueOf(40);

        BigDecimal finalAmount = totalAmount.add(deliveryCharges);

        Order order = Order.builder()
                .userId(userId)
                .addressId(request.getAddressId())
                .totalAmount(totalAmount)
                .deliveryCharges(deliveryCharges)
                .needToPay(finalAmount)
                .status(OrderStatus.PENDING)
                .build();

        orderItems.forEach(orderItem -> orderItem.setOrder(order));
        order.setOrderItems(orderItems);

        Order savedOrder = orderRepository.save(order);

        // Payment logic

        cartClient.clearCart();

        OrderResponse orderResponse =  modelMapper.map(savedOrder, OrderResponse.class);

        orderResponse.setAddress(address.getHouseNo() + " " + address.getStreet() + " " +
                address.getCity() + " " + address.getState() + " " + address.getCountry() + " "
                + address.getPinCode());

        return orderResponse;
    }


    public OrderResponse buyNow(PlaceOrderRequest request) {

        Long userId = Long.valueOf(UserContext.getUserId());

        if(!userId.equals(request.getUserId())) {
            throw new RuntimeException("User is not matching with auth userId");
        }

        ProductItemResponse itemResponse = orderResilience4j.getItemDetails(request.getProductId(), request.getVariantId());
        AddressResponse address = userClient.getAddressByAddressId(request.getAddressId());

        log.info("Buying now item order by userId={}", userId);

        OrderItem orderItem = OrderItem.builder()
                .productId(request.getProductId())
                .variantId(itemResponse.getVariantId())
                .productName(itemResponse.getProductName())
                .productBrand(itemResponse.getBrandName())
                .productImageUrl(itemResponse.getImageUrl())
                .size(itemResponse.getSize())
                .color(itemResponse.getColor())
                .quantity(1)
                .priceAtOrder(itemResponse.getPrice())
                .lineTotal(itemResponse.getPrice())
                .build();

        BigDecimal thresholdAmount = BigDecimal.valueOf(1999);
        BigDecimal totalAmount = orderItem.getLineTotal();
        BigDecimal deliveryCharges =
                (totalAmount.compareTo(thresholdAmount) > 0)
                        ? BigDecimal.valueOf(0)
                        : BigDecimal.valueOf(40);

        BigDecimal finalAmount = totalAmount.add(deliveryCharges);

        Order order = Order.builder()
                .userId(userId)
                .addressId(request.getAddressId())
                .totalAmount(orderItem.getLineTotal())
                .deliveryCharges(deliveryCharges)
                .needToPay(finalAmount)
                .status(OrderStatus.PENDING)
                .build();

        orderItem.setOrder(order);

        List<OrderItem> orderItems = List.of(orderItem);
        order.setOrderItems(orderItems);

        Order savedOrder = orderRepository.save(order);

        OrderResponse orderResponse =  modelMapper.map(savedOrder, OrderResponse.class);

        orderResponse.setAddress(address.getHouseNo() + " " + address.getStreet() + " " +
                address.getCity() + " " + address.getState() + " " + address.getCountry() + " "
                + address.getPinCode());

        return orderResponse;
    }


    public OrderResponse getOrderById(Long orderId) {
        Long userId = Long.valueOf(UserContext.getUserId());

        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order is not found"));

        log.info("Fetching order details with orderId={} and userId={}", orderId, userId);

        return modelMapper.map(order, OrderResponse.class);
    }


    public List<OrderSummaryResponse> getOrdersByUserId() {
        Long userId = Long.valueOf(UserContext.getUserId());

        List<Order> orderList = orderRepository.findAllByUserId(userId);

        log.info("Getting list of all orders placed by userId={}", userId);

        return orderList.stream()
                .map(order -> {
                    OrderSummaryResponse summaryResponse = modelMapper.map(order, OrderSummaryResponse.class);

                    // Set total items count
                    summaryResponse.setTotalItems(order.getOrderItems().size());

                    // Set first item details for the card thumbnail
                    if(!order.getOrderItems().isEmpty()) {
                        OrderItem firstItem = order.getOrderItems().getFirst();
                        summaryResponse.setFirstItemName(firstItem.getProductName());
                        summaryResponse.setFirstItemImageUrl(firstItem.getProductImageUrl());
                    }

                    return summaryResponse;
                })
                .toList();
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) {
        Long userId = Long.valueOf(UserContext.getUserId());

        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if(order.getStatus() == OrderStatus.DELIVERED) {
            throw new RuntimeException("Delivered order cannot be cancelled");
        }

        log.info("Cancelling the order for orderId={} by userId={}", orderId, userId);

        order.setStatus(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);

        return modelMapper.map(savedOrder, OrderResponse.class);
    }

    public List<OrderItemResponse> getOrderItems(Long orderId) {
        Long userId = Long.valueOf(UserContext.getUserId());

        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        log.info("Getting order items for orderId={} by userId={}", orderId, userId);

        return order.getOrderItems()
                .stream()
                .map(orderItem -> modelMapper.map(orderItem, OrderItemResponse.class))
                .toList();
    }
}
