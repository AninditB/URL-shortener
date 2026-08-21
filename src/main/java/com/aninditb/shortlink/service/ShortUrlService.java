package com.aninditb.shortlink.service;

import com.aninditb.shortlink.dto.CreateShortUrlRequest;
import com.aninditb.shortlink.dto.ShortUrlResponse;
import com.aninditb.shortlink.dto.UrlDetailsResponse;
import com.aninditb.shortlink.entity.ShortUrl;

public interface ShortUrlService {

    ShortUrlResponse create(CreateShortUrlRequest request);

    ShortUrl resolve(String shortCode);

    UrlDetailsResponse getDetails(Long id);

    void delete(Long id);
}
