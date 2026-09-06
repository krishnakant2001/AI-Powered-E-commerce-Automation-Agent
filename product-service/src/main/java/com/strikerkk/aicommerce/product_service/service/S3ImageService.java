package com.strikerkk.aicommerce.product_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class S3ImageService {

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    private final S3Client s3Client;

    public String uploadImage(MultipartFile file, String productId) {
        String key = "products/" + productId + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize())
            );
        } catch (IOException ioException) {
            throw new RuntimeException("Failed to upload a file on S3 bucket", ioException);
        }

        return key;
    }

    public String updateImage(MultipartFile file, String existingKey, String productId) {

        String newKey = uploadImage(file, productId);
        try {
            if (existingKey != null && !existingKey.isBlank()) {
                deleteImage(existingKey);
            }
        } catch (Exception ex) {
            log.warn("Existing image deletion failed. key={}", existingKey);
        }
        return newKey;
    }

    public void deleteImage(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build());
    }
}
