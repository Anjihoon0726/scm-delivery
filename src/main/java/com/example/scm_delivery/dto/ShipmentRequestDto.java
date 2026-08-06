package com.example.scm_delivery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ShipmentRequestDto {

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("courier_code")
    private String courierCode;

    @JsonProperty("destination")
    private Destination destination;

    @JsonProperty("items")
    private List<Item> items;

    @JsonProperty("box_packing")
    private BoxPacking boxPacking;

    @Data
    public static class Destination {
        @JsonProperty("recipient_name")
        private String recipientName;

        @JsonProperty("recipient_phone")
        private String recipientPhone;

        @JsonProperty("zipcode")
        private String zipCode;

        @JsonProperty("road_address")
        private String roadAddress;

        @JsonProperty("detail_address")
        private String detailAddress;

        @JsonProperty("delivery_request")
        private String deliveryRequest;
    }

    @Data
    public static class Item {
        @JsonProperty("product_id")
        private String productId;

        @JsonProperty("quantity")
        private int quantity;
    }

    @Data
    public static class BoxPacking {
        @JsonProperty("large")
        private int large;

        @JsonProperty("medium")
        private int medium;

        @JsonProperty("small")
        private int small;
    }
}