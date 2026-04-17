package com.strikerkk.aicommerce.product_service.service;

import com.strikerkk.aicommerce.product_service.auth.UserContext;
import com.strikerkk.aicommerce.product_service.dto.request.ProductRequest;
import com.strikerkk.aicommerce.product_service.dto.response.ProductResponse;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.product_service.exception.UnauthorizedException;
import com.strikerkk.aicommerce.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ModelMapper modelMapper;


    public ProductResponse createProduct(ProductRequest request) {

        String userId = UserContext.getUserId();

        Product newProduct = Product.builder()
                .name(request.getName())
                .brand(request.getBrand())
                .description(request.getDescription())
                .price(request.getPrice())
                .category(request.getCategory())
                .stockCount(request.getStockCount())
                .isAvailable(request.getIsAvailable())
                .createdBy(userId)
                .variants(new ArrayList<>())
                .images(new ArrayList<>())
                .build();

        Product savedProduct = productRepository.save(newProduct);

        return modelMapper.map(savedProduct, ProductResponse.class);

    }

    public List<ProductResponse> getAllProducts() {

        List<Product> productList = productRepository.findAll();

        return productList.stream()
                .map(product -> modelMapper.map(product, ProductResponse.class))
                .toList();
    }

    public ProductResponse getProductDetails(Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        return modelMapper.map(product, ProductResponse.class);

    }

    public ProductResponse updateProduct(ProductRequest request, Long productId) {

        String userId = UserContext.getUserId();

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product is not found"));

        // Authorization check
        if (!product.getCreatedBy().equals(userId)) {
            throw new UnauthorizedException("You are not allowed to update this product");
        }

        product.setName(request.getName());
        product.setBrand(request.getBrand());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setStockCount(request.getStockCount());
        product.setIsAvailable(request.getIsAvailable());

        Product updatedProduct = productRepository.save(product);

        return modelMapper.map(updatedProduct, ProductResponse.class);

    }

    public void deleteProduct(Long productId) {

        String userId = UserContext.getUserId();

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product is not found"));

        // Authorization check
        if (!product.getCreatedBy().equals(userId)) {
            throw new UnauthorizedException("You are not allowed to delete this product");
        }

        productRepository.delete(product);
    }

}
