package com.calpex_technologies.chama.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class RecordPaymentRequest {
    private BigDecimal amount;
}
