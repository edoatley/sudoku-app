package com.sudoku.coach.bedrock;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@ApplicationScoped
class BedrockClientProducer {

    @Produces
    @ApplicationScoped
    BedrockRuntimeClient produce() {
        String region = System.getenv().getOrDefault("AWS_REGION", "eu-west-2");
        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .httpClient(UrlConnectionHttpClient.builder().build())
                .build();
    }
}
