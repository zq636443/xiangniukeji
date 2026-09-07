package com.xniu.rental;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xniu.rental.asset.dto.AssetPickupRequest;
import com.xniu.rental.asset.dto.AssetReturnRequest;
import com.xniu.rental.asset.repository.AssetFulfillmentRepository;
import com.xniu.rental.asset.repository.AssetRepository;
import com.xniu.rental.asset.service.AssetFulfillmentService;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.bill.service.BillService;
import com.xniu.rental.common.BusinessException;
import com.xniu.rental.merchant.model.MerchantStore;
import com.xniu.rental.merchant.model.StoreStatus;
import com.xniu.rental.merchant.repository.StoreRepository;
import com.xniu.rental.order.model.OrderStatus;
import com.xniu.rental.order.model.RentalOrder;
import com.xniu.rental.order.repository.OrderRepository;
import com.xniu.rental.product.repository.ProductRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssetFulfillmentLockOrderingContractTests {

    @Mock
    private AssetFulfillmentRepository fulfillmentRepository;

    @Mock
    private AssetRepository assetRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private BillService billService;

    @Mock
    private RentalOrder order;

    @Mock
    private MerchantStore store;

    @InjectMocks
    private AssetFulfillmentService service;

    @Test
    void pickupLocksOrderBeforeTryingToLockAnAsset() {
        prepareOrder(OrderStatus.PENDING_PICKUP);
        when(assetRepository.findByIdForUpdate(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pickup(
            1L,
            new AssetPickupRequest(11L, null, "lock-order")
        )).isInstanceOf(BusinessException.class).hasMessageContaining("资产不存在");

        assertOrderThenAssetLock();
    }

    @Test
    void shipWithoutPaymentLocksOrderBeforeTryingToLockAnAsset() {
        prepareOrder(OrderStatus.PENDING_PAYMENT);
        when(assetRepository.findByIdForUpdate(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.shipWithoutPayment(
            1L,
            new AssetPickupRequest(11L, null, "lock-order")
        )).isInstanceOf(BusinessException.class).hasMessageContaining("资产不存在");

        assertOrderThenAssetLock();
    }

    @Test
    void returnLocksOrderBeforeTryingToLockItsBoundAsset() {
        prepareOrder(OrderStatus.RENTING);
        when(order.frameAssetId()).thenReturn(11L);
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        when(store.id()).thenReturn(1L);
        when(store.merchantId()).thenReturn(1L);
        when(store.status()).thenReturn(StoreStatus.ENABLED);
        when(assetRepository.findByIdForUpdate(11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.returnAssets(
            1L,
            new AssetReturnRequest(null, "IDLE", null, "lock-order")
        )).isInstanceOf(BusinessException.class).hasMessageContaining("资产不存在");

        assertOrderThenAssetLock();
    }

    private void prepareOrder(OrderStatus status) {
        when(orderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(order.merchantId()).thenReturn(1L);
        when(order.storeId()).thenReturn(1L);
        when(order.orderStatus()).thenReturn(status);
    }

    private void assertOrderThenAssetLock() {
        var locks = inOrder(orderRepository, assetRepository);
        locks.verify(orderRepository).findByIdForUpdate(1L);
        locks.verify(assetRepository).findByIdForUpdate(11L);
        verify(orderRepository, never()).findById(anyLong());
    }
}
