package com.hmdp.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private Double x;
    private Double y;
    private Integer radius;
}
