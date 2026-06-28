package com.strikerkk.aicommerce.product_service.service;

import com.strikerkk.aicommerce.product_service.auth.UserContext;
import com.strikerkk.aicommerce.product_service.common.PageResponse;
import com.strikerkk.aicommerce.product_service.dto.request.ProductRequest;
import com.strikerkk.aicommerce.product_service.dto.response.ProductResponse;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.product_service.helper.ProductOwnershipValidator;
import com.strikerkk.aicommerce.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductOwnershipValidator productOwnershipValidator;
    private final ModelMapper modelMapper;

    @Transactional
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

        log.info("Creating a new product by admin id={}", userId);

        return modelMapper.map(savedProduct, ProductResponse.class);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getAllProducts(Pageable pageable) {

        Page<Product> productPage = productRepository.findAll(pageable);

        log.info("Fetching all products from product db");

        List<ProductResponse> content =  productPage.getContent().stream()
                .map(product -> modelMapper.map(product, ProductResponse.class))
                .toList();

        return PageResponse.<ProductResponse>builder()
                .content(content)
                .pageNumber(productPage.getNumber())
                .pageSize(productPage.getSize())
                .totalElements(productPage.getTotalElements())
                .totalPages(productPage.getTotalPages())
                .last(productPage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductDetails(Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        log.info("Getting product details of productId={}", productId);

        return modelMapper.map(product, ProductResponse.class);
    }

    @Transactional
    public ProductResponse updateProduct(ProductRequest request, Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product is not found"));

        // Authorization check
        productOwnershipValidator.validate(product);

        product.setName(request.getName());
        product.setBrand(request.getBrand());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setStockCount(request.getStockCount());
        product.setIsAvailable(request.getIsAvailable());

        Product updatedProduct = productRepository.save(product);

        log.info("Updating product details of productId={} by admin id={}", productId, UserContext.getUserId());

        return modelMapper.map(updatedProduct, ProductResponse.class);
    }

    @Transactional
    public void deleteProduct(Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product is not found"));

        log.info("Deleting product of productId={} by admin id={}", productId, UserContext.getUserId());

        // Authorization check
        productOwnershipValidator.validate(product);

        productRepository.delete(product);
    }

}
