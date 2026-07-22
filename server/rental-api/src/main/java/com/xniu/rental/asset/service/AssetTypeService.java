package com.xniu.rental.asset.service;

import com.xniu.rental.asset.dto.AssetTypeRequest;
import com.xniu.rental.asset.dto.AssetTypeResponse;
import com.xniu.rental.asset.model.AssetType;
import com.xniu.rental.asset.model.AssetTypeDefinition;
import com.xniu.rental.asset.repository.AssetTypeRepository;
import com.xniu.rental.auth.security.AuthorizationService;
import com.xniu.rental.common.BusinessException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetTypeService {

    private final AssetTypeRepository assetTypeRepository;
    private final AuthorizationService authorizationService;

    public AssetTypeService(AssetTypeRepository assetTypeRepository, AuthorizationService authorizationService) {
        this.assetTypeRepository = assetTypeRepository;
        this.authorizationService = authorizationService;
    }

    public List<AssetTypeResponse> listTypes(boolean enabledOnly) {
        authorizationService.requirePermission("asset.read");
        return assetTypeRepository.list(enabledOnly).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AssetTypeResponse createType(AssetTypeRequest request) {
        authorizationService.requirePermission("asset.manage");
        authorizationService.requirePlatformAccount();
        var name = requiredText(request.typeName(), "类型名称");
        ensureUniqueName(name, null);
        var created = assetTypeRepository.create(
            "CUSTOM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
            name,
            parseAssetClass(request.assetClass()),
            requiredText(request.serialLabel(), "编号字段名称"),
            request.sortOrder(),
            parseStatus(request.status())
        );
        return toResponse(created);
    }

    @Transactional
    public AssetTypeResponse updateType(Long id, AssetTypeRequest request) {
        authorizationService.requirePermission("asset.manage");
        authorizationService.requirePlatformAccount();
        var existing = ensureType(id);
        var name = requiredText(request.typeName(), "类型名称");
        ensureUniqueName(name, id);
        var nextClass = parseAssetClass(request.assetClass());
        var nextStatus = parseStatus(request.status());
        var assetCount = assetTypeRepository.countAssets(id);
        if (existing.systemDefined() && nextClass != existing.assetClass()) {
            throw BusinessException.badRequest("系统资产类型不能修改业务归类");
        }
        if (!existing.systemDefined() && assetCount > 0 && nextClass != existing.assetClass()) {
            throw BusinessException.badRequest("该类型已有资产，不能修改业务归类");
        }
        if (existing.systemDefined() && !"ENABLED".equals(nextStatus)) {
            throw BusinessException.badRequest("系统资产类型不能停用");
        }
        return toResponse(assetTypeRepository.update(
            id,
            name,
            nextClass,
            requiredText(request.serialLabel(), "编号字段名称"),
            request.sortOrder(),
            nextStatus
        ));
    }

    public AssetTypeDefinition resolveForEntry(Long assetTypeId, String legacyAssetType) {
        AssetTypeDefinition definition;
        if (assetTypeId != null) {
            definition = ensureType(assetTypeId);
        } else {
            definition = resolveImportType(requiredText(legacyAssetType, "资产类型"));
        }
        if (!definition.enabled()) {
            throw BusinessException.badRequest("所选资产类型已停用");
        }
        return definition;
    }

    public AssetTypeDefinition requireType(Long assetTypeId) {
        return ensureType(assetTypeId);
    }

    public AssetTypeDefinition resolveImportType(String value) {
        var normalized = requiredText(value, "资产类型");
        var direct = assetTypeRepository.findByCodeOrName(normalized);
        if (direct.isPresent()) {
            return direct.get();
        }
        var assetClass = parseLegacyAssetClass(normalized);
        return assetTypeRepository.findSystemType(assetClass)
            .orElseThrow(() -> BusinessException.badRequest("资产类型不存在"));
    }

    private AssetTypeDefinition ensureType(Long id) {
        return assetTypeRepository.findById(id).orElseThrow(() -> BusinessException.badRequest("资产类型不存在"));
    }

    private void ensureUniqueName(String name, Long currentId) {
        assetTypeRepository.findByCodeOrName(name).ifPresent(existing -> {
            if (currentId == null || !currentId.equals(existing.id())) {
                throw BusinessException.badRequest("资产类型名称已存在");
            }
        });
    }

    private AssetType parseAssetClass(String value) {
        try {
            return AssetType.valueOf(requiredText(value, "业务归类").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("不支持的资产业务归类");
        }
    }

    private AssetType parseLegacyAssetClass(String value) {
        var normalized = value.toUpperCase(Locale.ROOT);
        if ("车架".equals(value) || "FRAME".equals(normalized)) {
            return AssetType.VEHICLE_FRAME;
        }
        if ("电池".equals(value)) {
            return AssetType.BATTERY;
        }
        if ("车电一体".equals(value) || "一体车".equals(value) || "INTEGRATED".equals(normalized)) {
            return AssetType.INTEGRATED_VEHICLE;
        }
        try {
            return AssetType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw BusinessException.badRequest("资产类型不存在，请先在资产类型管理中新增");
        }
    }

    private String parseStatus(String value) {
        var status = value == null || value.isBlank() ? "ENABLED" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("ENABLED", "DISABLED").contains(status)) {
            throw BusinessException.badRequest("不支持的资产类型状态");
        }
        return status;
    }

    private String requiredText(String value, String fieldName) {
        var normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isBlank()) {
            throw BusinessException.badRequest("请填写" + fieldName);
        }
        return normalized;
    }

    private AssetTypeResponse toResponse(AssetTypeDefinition definition) {
        return new AssetTypeResponse(
            definition.id(),
            definition.typeCode(),
            definition.typeName(),
            definition.assetClass().name(),
            definition.serialLabel(),
            definition.systemDefined(),
            definition.sortOrder(),
            definition.status(),
            assetTypeRepository.countAssets(definition.id())
        );
    }
}
