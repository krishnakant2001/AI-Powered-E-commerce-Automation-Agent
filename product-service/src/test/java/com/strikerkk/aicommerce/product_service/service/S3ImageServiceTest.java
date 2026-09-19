package com.strikerkk.aicommerce.product_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3ImageService")
class S3ImageServiceTest {

    private static final String BUCKET = "test-bucket";
    private static final String PRODUCT_ID = "42";

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private S3ImageService s3ImageService;

    @Captor
    private ArgumentCaptor<PutObjectRequest> putRequestCaptor;

    @Captor
    private ArgumentCaptor<DeleteObjectRequest> deleteRequestCaptor;

    @BeforeEach
    void setUp() {
        // @Value("${aws.s3.bucket-name}") is not processed outside a Spring context
        ReflectionTestUtils.setField(s3ImageService, "bucketName", BUCKET);
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("image", "flip6.png", "image/png", "content".getBytes());
    }

    // uploadImage -------------------------------------------------------------------

    @Nested
    @DisplayName("uploadImage")
    class UploadImage {

        @Test
        @DisplayName("stores the object under products/{productId}/{uuid}-{originalFilename}")
        void shouldBuildTheKey() {
            String key = s3ImageService.uploadImage(file(), PRODUCT_ID);

            assertThat(key)
                    .startsWith("products/42/")
                    .endsWith("-flip6.png")
                    .matches("products/42/[0-9a-f\\-]{36}-flip6\\.png");
        }

        @Test
        @DisplayName("sends the configured bucket, the generated key and the content type to S3")
        void shouldSendTheExpectedPutObjectRequest() {
            String key = s3ImageService.uploadImage(file(), PRODUCT_ID);

            verify(s3Client).putObject(putRequestCaptor.capture(), any(RequestBody.class));
            PutObjectRequest request = putRequestCaptor.getValue();
            assertThat(request.bucket()).isEqualTo(BUCKET);
            assertThat(request.key()).isEqualTo(key);
            assertThat(request.contentType()).isEqualTo("image/png");
        }

        @Test
        @DisplayName("generates a different key for every upload of the same file")
        void shouldGenerateUniqueKeys() {
            String first = s3ImageService.uploadImage(file(), PRODUCT_ID);
            String second = s3ImageService.uploadImage(file(), PRODUCT_ID);

            assertThat(first).isNotEqualTo(second);
            verify(s3Client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }

        @Test
        @DisplayName("wraps an unreadable upload into a RuntimeException")
        void shouldWrapIOException() throws IOException {
            MultipartFile broken = org.mockito.Mockito.mock(MultipartFile.class);
            when(broken.getOriginalFilename()).thenReturn("broken.png");
            when(broken.getContentType()).thenReturn("image/png");
            when(broken.getInputStream()).thenThrow(new IOException("stream closed"));

            assertThatThrownBy(() -> s3ImageService.uploadImage(broken, PRODUCT_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Failed to upload a file on S3 bucket")
                    .hasCauseInstanceOf(IOException.class);
        }

        @Test
        @DisplayName("propagates an S3 failure as is")
        void shouldPropagateS3Failure() {
            doThrow(S3Exception.builder().message("access denied").build())
                    .when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

            assertThatThrownBy(() -> s3ImageService.uploadImage(file(), PRODUCT_ID))
                    .isInstanceOf(S3Exception.class);
        }
    }

    // updateImage -------------------------------------------------------------------

    @Nested
    @DisplayName("updateImage")
    class UpdateImage {

        @Test
        @DisplayName("uploads the new object and removes the previous one")
        void shouldReplaceTheObject() {
            String newKey = s3ImageService.updateImage(file(), "products/42/old-key.png", PRODUCT_ID);

            assertThat(newKey).startsWith("products/42/").isNotEqualTo("products/42/old-key.png");
            verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
            verify(s3Client).deleteObject(deleteRequestCaptor.capture());
            assertThat(deleteRequestCaptor.getValue().key()).isEqualTo("products/42/old-key.png");
            assertThat(deleteRequestCaptor.getValue().bucket()).isEqualTo(BUCKET);
        }

        @Test
        @DisplayName("does not try to delete anything when there is no previous key")
        void shouldSkipDeleteWhenNoPreviousKey() {
            s3ImageService.updateImage(file(), null, PRODUCT_ID);

            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("does not try to delete anything when the previous key is blank")
        void shouldSkipDeleteWhenPreviousKeyIsBlank() {
            s3ImageService.updateImage(file(), "   ", PRODUCT_ID);

            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }

        @Test
        @DisplayName("still returns the new key when the cleanup of the previous object fails")
        void shouldSwallowCleanupFailures() {
            doThrow(S3Exception.builder().message("not found").build())
                    .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

            String newKey = s3ImageService.updateImage(file(), "products/42/old-key.png", PRODUCT_ID);

            assertThat(newKey).startsWith("products/42/");
        }

        @Test
        @DisplayName("does not delete the previous object when the upload itself fails")
        void shouldNotDeleteWhenUploadFails() {
            doThrow(S3Exception.builder().message("boom").build())
                    .when(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

            assertThatThrownBy(() ->
                    s3ImageService.updateImage(file(), "products/42/old-key.png", PRODUCT_ID))
                    .isInstanceOf(S3Exception.class);

            verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
        }
    }

    // deleteImage -------------------------------------------------------------------

    @Nested
    @DisplayName("deleteImage")
    class DeleteImage {

        @Test
        @DisplayName("deletes the object from the configured bucket")
        void shouldDeleteTheObject() {
            s3ImageService.deleteImage("products/42/key.png");

            verify(s3Client).deleteObject(deleteRequestCaptor.capture());
            assertThat(deleteRequestCaptor.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(deleteRequestCaptor.getValue().key()).isEqualTo("products/42/key.png");
        }

        @Test
        @DisplayName("is a no-op for a null key")
        void shouldIgnoreNullKey() {
            assertThatCode(() -> s3ImageService.deleteImage(null)).doesNotThrowAnyException();
            verifyNoInteractions(s3Client);
        }

        @Test
        @DisplayName("is a no-op for a blank key")
        void shouldIgnoreBlankKey() {
            assertThatCode(() -> s3ImageService.deleteImage("  ")).doesNotThrowAnyException();
            verifyNoInteractions(s3Client);
        }

        @Test
        @DisplayName("propagates an S3 failure so the caller can react")
        void shouldPropagateS3Failure() {
            doThrow(S3Exception.builder().message("access denied").build())
                    .when(s3Client).deleteObject(any(DeleteObjectRequest.class));

            assertThatThrownBy(() -> s3ImageService.deleteImage("products/42/key.png"))
                    .isInstanceOf(S3Exception.class);
        }
    }
}

