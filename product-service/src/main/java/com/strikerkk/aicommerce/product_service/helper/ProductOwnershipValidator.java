package com.strikerkk.aicommerce.product_service.helper;

import com.strikerkk.aicommerce.product_service.auth.UserContext;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

@Component
public class ProductOwnershipValidator {

    public void validate(Product product) {

        String userId = UserContext.getUserId();

        if(!product.getCreatedBy().equals(userId)) {
            throw new UnauthorizedException("You are not authorised to modify this product");
        }

    }
}
