package com.strikerkk.aicommerce.product_service.service;

import com.strikerkk.aicommerce.product_service.auth.UserContext;
import com.strikerkk.aicommerce.product_service.dto.request.ProductImageRequest;
import com.strikerkk.aicommerce.product_service.dto.response.ProductImageResponse;
import com.strikerkk.aicommerce.product_service.entity.Product;
import com.strikerkk.aicommerce.product_service.entity.ProductImage;
import com.strikerkk.aicommerce.product_service.exception.ResourceNotFoundException;
import com.strikerkk.aicommerce.product_service.exception.UnauthorizedException;
import com.strikerkk.aicommerce.product_service.helper.ProductOwnershipValidator;
import com.strikerkk.aicommerce.product_service.repository.ProductImageRepository;
import com.strikerkk.aicommerce.product_service.repository.ProductRepository;
import com.strikerkk.aicommerce.product_service.support.TestDataFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductImageService")
class ProductImageServiceTest {

    private static final Long PRODUCT_ID = TestDataFactory.PRODUCT_ID;
    private static final Long IMAGE_ID = TestDataFactory.IMAGE_ID;
    private static final String NEW_KEY = "products/1/new-uuid-flip6.png";

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductOwnershipValidator productOwnershipValidator;

    @Mock
    private S3ImageService s3ImageService;

    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private ProductImageService productImageService;

    @Captor
    private ArgumentCaptor<ProductImage> imageCaptor;

    private Product product;

    @BeforeEach
    void setUp() {
        UserContext.setUserId(TestDataFactory.ADMIN_ID);
        UserContext.setUserRole("ADMIN");
        product = TestDataFactory.product();
    }

    @AfterEach
    void tearDown() {
        UserContext.setUserId(null);
        UserContext.setUserRole(null);
    }

    // addProductImage ----------------------------------------------------------------

    @Nested
    @DisplayName("addProductImage")
    class AddProductImage {

        @Test
        @DisplayName("uploads the file to S3 and stores the returned key, not a raw URL")
        void shouldStoreTheS3Key() {
            ProductImageRequest request = TestDataFactory.productImageRequest(true);
            ProductImage saved = TestDataFactory.image(IMAGE_ID, product, true, NEW_KEY);

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(s3ImageService.uploadImage(request.getImage(), "1")).thenReturn(NEW_KEY);
            when(productImageRepository.save(any(ProductImage.class))).thenReturn(saved);
            when(modelMapper.map(saved, ProductImageResponse.class))
                    .thenReturn(TestDataFactory.productImageResponse(true));

            productImageService.addProductImage(request, PRODUCT_ID);

            verify(productImageRepository).save(imageCaptor.capture());
            ProductImage persisted = imageCaptor.getValue();
            assertThat(persisted.getUrl()).isEqualTo(NEW_KEY);
            assertThat(persisted.getIsPrimary()).isTrue();
            assertThat(persisted.getProduct()).isSameAs(product);
            assertThat(persisted.getId()).isNull();
        }

        @Test
        @DisplayName("returns the mapped response")
        void shouldReturnMappedResponse() {
            ProductImageRequest request = TestDataFactory.productImageRequest(false);
            ProductImage saved = TestDataFactory.image(IMAGE_ID, product, false, NEW_KEY);
            ProductImageResponse expected = TestDataFactory.productImageResponse(false);

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(s3ImageService.uploadImage(any(MultipartFile.class), eq("1"))).thenReturn(NEW_KEY);
            when(productImageRepository.save(any(ProductImage.class))).thenReturn(saved);
            when(modelMapper.map(saved, ProductImageResponse.class)).thenReturn(expected);

            assertThat(productImageService.addProductImage(request, PRODUCT_ID)).isSameAs(expected);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the product does not exist")
        void shouldThrowWhenProductMissing() {
            when(productRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    productImageService.addProductImage(TestDataFactory.productImageRequest(true), 404L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Product not found");

            verifyNoInteractions(s3ImageService, productImageRepository, productOwnershipValidator);
        }

        @Test
        @DisplayName("refuses to upload an image for a product owned by another admin")
        void shouldRejectForeignProduct() {
            Product foreign = TestDataFactory.product(PRODUCT_ID, TestDataFactory.OTHER_ADMIN_ID);
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(foreign));
            doThrow(new UnauthorizedException("You are not authorised to modify this product"))
                    .when(productOwnershipValidator).validate(foreign);

            assertThatThrownBy(() -> productImageService
                    .addProductImage(TestDataFactory.productImageRequest(true), PRODUCT_ID))
                    .isInstanceOf(UnauthorizedException.class);

            // nothing must ever reach S3 for a product the caller does not own
            verifyNoInteractions(s3ImageService, productImageRepository);
        }

        @Test
        @DisplayName("removes the orphan S3 object when the database insert fails")
        void shouldCleanUpS3WhenTheInsertFails() {
            ProductImageRequest request = TestDataFactory.productImageRequest(true);

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(s3ImageService.uploadImage(any(MultipartFile.class), eq("1"))).thenReturn(NEW_KEY);
            when(productImageRepository.save(any(ProductImage.class)))
                    .thenThrow(new DataIntegrityViolationException("constraint violation"));

            assertThatThrownBy(() -> productImageService.addProductImage(request, PRODUCT_ID))
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessage("constraint violation");

            verify(s3ImageService).deleteImage(NEW_KEY);
            verifyNoInteractions(modelMapper);
        }

        @Test
        @DisplayName("uploads only after the ownership has been validated")
        void shouldValidateBeforeUploading() {
            ProductImageRequest request = TestDataFactory.productImageRequest(true);

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(s3ImageService.uploadImage(any(MultipartFile.class), eq("1"))).thenReturn(NEW_KEY);
            when(productImageRepository.save(any(ProductImage.class)))
                    .thenReturn(TestDataFactory.image(IMAGE_ID, product, true, NEW_KEY));
            when(modelMapper.map(any(), eq(ProductImageResponse.class)))
                    .thenReturn(TestDataFactory.productImageResponse(true));

            productImageService.addProductImage(request, PRODUCT_ID);

            InOrder inOrder = Mockito.inOrder(productOwnershipValidator, s3ImageService, productImageRepository);
            inOrder.verify(productOwnershipValidator).validate(product);
            inOrder.verify(s3ImageService).uploadImage(any(MultipartFile.class), eq("1"));
            inOrder.verify(productImageRepository).save(any(ProductImage.class));
        }
    }

    // updateProductImage -------------------------------------------------------------

    @Nested
    @DisplayName("updateProductImage")
    class UpdateProductImage {

        @Test
        @DisplayName("replaces the S3 object and swaps the stored key when a new file is supplied")
        void shouldReplaceTheFile() {
            ProductImage existing = TestDataFactory.image(IMAGE_ID, product, false, TestDataFactory.S3_KEY);
            ProductImageRequest request = TestDataFactory.productImageRequest(false);

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productImageRepository.findByIdAndProductId(IMAGE_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existing));
            when(s3ImageService.updateImage(request.getImage(), TestDataFactory.S3_KEY, "1"))
                    .thenReturn(NEW_KEY);
            when(productImageRepository.save(existing)).thenReturn(existing);
            when(modelMapper.map(existing, ProductImageResponse.class))
                    .thenReturn(TestDataFactory.productImageResponse(false));

            productImageService.updateProductImage(request, PRODUCT_ID, IMAGE_ID);

            assertThat(existing.getUrl()).isEqualTo(NEW_KEY);
            verify(s3ImageService).updateImage(request.getImage(), TestDataFactory.S3_KEY, "1");
        }

        @Test
        @DisplayName("keeps the current key when no file is supplied")
        void shouldKeepTheKeyWhenNoFileSupplied() {
            ProductImage existing = TestDataFactory.image(IMAGE_ID, product, false);
            ProductImageRequest request = new ProductImageRequest(null, false);

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productImageRepository.findByIdAndProductId(IMAGE_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existing));
            when(productImageRepository.save(existing)).thenReturn(existing);
            when(modelMapper.map(existing, ProductImageResponse.class))
                    .thenReturn(TestDataFactory.productImageResponse(false));

            productImageService.updateProductImage(request, PRODUCT_ID, IMAGE_ID);

            assertThat(existing.getUrl()).isEqualTo(TestDataFactory.S3_KEY);
            verify(s3ImageService, never()).updateImage(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("keeps the current key when the supplied file is empty")
        void shouldKeepTheKeyWhenFileIsEmpty() {
            ProductImage existing = TestDataFactory.image(IMAGE_ID, product, false);
            ProductImageRequest request =
                    new ProductImageRequest(TestDataFactory.emptyMultipartFile(), false);

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productImageRepository.findByIdAndProductId(IMAGE_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existing));
            when(productImageRepository.save(existing)).thenReturn(existing);
            when(modelMapper.map(existing, ProductImageResponse.class))
                    .thenReturn(TestDataFactory.productImageResponse(false));

            productImageService.updateProductImage(request, PRODUCT_ID, IMAGE_ID);

            assertThat(existing.getUrl()).isEqualTo(TestDataFactory.S3_KEY);
            verify(s3ImageService, never()).updateImage(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("demotes every other image of the product when the new one becomes primary")
        void shouldResetTheOtherPrimaryImages() {
            ProductImage existing = TestDataFactory.image(IMAGE_ID, product, false);
            ProductImageRequest request = new ProductImageRequest(null, true);

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productImageRepository.findByIdAndProductId(IMAGE_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existing));
            when(productImageRepository.save(existing)).thenReturn(existing);
            when(modelMapper.map(existing, ProductImageResponse.class))
                    .thenReturn(TestDataFactory.productImageResponse(true));

            productImageService.updateProductImage(request, PRODUCT_ID, IMAGE_ID);

            verify(productImageRepository).restPrimaryImages(PRODUCT_ID);
            assertThat(existing.getIsPrimary()).isTrue();
        }

        @Test
        @DisplayName("does not demote the other images when the flag is false")
        void shouldNotResetWhenNotPrimary() {
            ProductImage existing = TestDataFactory.image(IMAGE_ID, product, true);
            ProductImageRequest request = new ProductImageRequest(null, false);

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productImageRepository.findByIdAndProductId(IMAGE_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existing));
            when(productImageRepository.save(existing)).thenReturn(existing);
            when(modelMapper.map(existing, ProductImageResponse.class))
                    .thenReturn(TestDataFactory.productImageResponse(false));

            productImageService.updateProductImage(request, PRODUCT_ID, IMAGE_ID);

            verify(productImageRepository, never()).restPrimaryImages(anyLong());
            assertThat(existing.getIsPrimary()).isFalse();
        }

        @Test
        @DisplayName("does not demote the other images when the flag is null")
        void shouldNotResetWhenFlagIsNull() {
            ProductImage existing = TestDataFactory.image(IMAGE_ID, product, true);
            ProductImageRequest request = new ProductImageRequest(null, null);

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productImageRepository.findByIdAndProductId(IMAGE_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existing));
            when(productImageRepository.save(existing)).thenReturn(existing);
            when(modelMapper.map(existing, ProductImageResponse.class))
                    .thenReturn(TestDataFactory.productImageResponse(false));

            productImageService.updateProductImage(request, PRODUCT_ID, IMAGE_ID);

            verify(productImageRepository, never()).restPrimaryImages(anyLong());
            assertThat(existing.getIsPrimary()).isNull();
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the product does not exist")
        void shouldThrowWhenProductMissing() {
            when(productRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productImageService
                    .updateProductImage(TestDataFactory.productImageRequest(true), 404L, IMAGE_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Product not found");

            verifyNoInteractions(productImageRepository, s3ImageService, productOwnershipValidator);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the image belongs to another product")
        void shouldThrowWhenImageDoesNotBelongToProduct() {
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productImageRepository.findByIdAndProductId(999L, PRODUCT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productImageService
                    .updateProductImage(TestDataFactory.productImageRequest(true), PRODUCT_ID, 999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Image not found for this product");

            verifyNoInteractions(s3ImageService);
            verify(productImageRepository, never()).save(any());
        }

        @Test
        @DisplayName("refuses to update an image of a product owned by another admin")
        void shouldRejectForeignProduct() {
            Product foreign = TestDataFactory.product(PRODUCT_ID, TestDataFactory.OTHER_ADMIN_ID);
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(foreign));
            doThrow(new UnauthorizedException("You are not authorised to modify this product"))
                    .when(productOwnershipValidator).validate(foreign);

            assertThatThrownBy(() -> productImageService
                    .updateProductImage(TestDataFactory.productImageRequest(true), PRODUCT_ID, IMAGE_ID))
                    .isInstanceOf(UnauthorizedException.class);

            verifyNoInteractions(s3ImageService, productImageRepository);
        }
    }

    // deleteProductImage -------------------------------------------------------------

    @Nested
    @DisplayName("deleteProductImage")
    class DeleteProductImage {

        @Test
        @DisplayName("removes the S3 object and then the row")
        void shouldDeleteImage() {
            ProductImage existing = TestDataFactory.image(IMAGE_ID, product, true);

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productImageRepository.findByIdAndProductId(IMAGE_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existing));

            productImageService.deleteProductImage(PRODUCT_ID, IMAGE_ID);

            InOrder inOrder = Mockito.inOrder(productOwnershipValidator, s3ImageService, productImageRepository);
            inOrder.verify(productOwnershipValidator).validate(product);
            inOrder.verify(s3ImageService).deleteImage(TestDataFactory.S3_KEY);
            inOrder.verify(productImageRepository).delete(existing);
        }

        @Test
        @DisplayName("keeps the row when the S3 deletion fails, so it can be retried")
        void shouldKeepTheRowWhenS3Fails() {
            ProductImage existing = TestDataFactory.image(IMAGE_ID, product, true);

            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productImageRepository.findByIdAndProductId(IMAGE_ID, PRODUCT_ID))
                    .thenReturn(Optional.of(existing));
            doThrow(new IllegalStateException("S3 unavailable"))
                    .when(s3ImageService).deleteImage(TestDataFactory.S3_KEY);

            assertThatThrownBy(() -> productImageService.deleteProductImage(PRODUCT_ID, IMAGE_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("S3 unavailable");

            verify(productImageRepository, never()).delete(any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the product does not exist")
        void shouldThrowWhenProductMissing() {
            when(productRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productImageService.deleteProductImage(404L, IMAGE_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Product not found");

            verifyNoInteractions(s3ImageService, productImageRepository, productOwnershipValidator);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when the image belongs to another product")
        void shouldThrowWhenImageDoesNotBelongToProduct() {
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
            when(productImageRepository.findByIdAndProductId(999L, PRODUCT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productImageService.deleteProductImage(PRODUCT_ID, 999L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Image not found for this product");

            verifyNoInteractions(s3ImageService);
            verify(productImageRepository, never()).delete(any());
        }

        @Test
        @DisplayName("refuses to delete an image of a product owned by another admin")
        void shouldRejectForeignProduct() {
            Product foreign = TestDataFactory.product(PRODUCT_ID, TestDataFactory.OTHER_ADMIN_ID);
            when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(foreign));
            doThrow(new UnauthorizedException("You are not authorised to modify this product"))
                    .when(productOwnershipValidator).validate(foreign);

            assertThatThrownBy(() -> productImageService.deleteProductImage(PRODUCT_ID, IMAGE_ID))
                    .isInstanceOf(UnauthorizedException.class);

            verifyNoInteractions(s3ImageService, productImageRepository);
        }
    }
}

