package com.xniu.rental.contract.dto;

import jakarta.validation.constraints.NotBlank;

public record ContractArchiveRequest(
    @NotBlank(message = "请输入归档 PDF 地址") String archivePdfUrl
) {
}
