package org.example.ticketmanagement.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SetDefaultAddressDTO {
    @NotNull(message = "地址ID不能为空")
    private Long addressId;
}
