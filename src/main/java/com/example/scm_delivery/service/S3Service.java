package com.example.scm_delivery.service;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Template s3Template;

    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucket;

    public String uploadPodImage(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String storeFileName = "pod/" + UUID.randomUUID() + "_" + originalFilename;

        try (InputStream inputStream = file.getInputStream()) {
            // io.awspring.cloud 3.x의 S3Template을 이용한 업로드
            var resource = s3Template.upload(bucket, storeFileName, inputStream);
            return resource.getURL().toString();
        }
    }
}