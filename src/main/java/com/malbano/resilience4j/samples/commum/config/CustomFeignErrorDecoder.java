package com.malbano.resilience4j.samples.commum.config;

import com.malbano.resilience4j.samples.commum.exception.HttpStatusException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

@Slf4j
public class CustomFeignErrorDecoder implements ErrorDecoder {

    private final ErrorDecoder defaultDecoder = new Default();

    @Override
    public Exception decode(String methodKey, Response response) {
        HttpStatus httpStatus = HttpStatus.resolve(response.status());

        if (httpStatus != null) {
            log.debug("Decoding Feign error - Method: {}, Status: {} ({})",
                    methodKey, httpStatus.value(), httpStatus.getReasonPhrase());
            return new HttpStatusException(httpStatus);
        }

        return defaultDecoder.decode(methodKey, response);
    }
}