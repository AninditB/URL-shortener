package com.aninditb.shortlink.service;

import com.aninditb.shortlink.dto.CreateShortUrlRequest;
import com.aninditb.shortlink.dto.PagedUrlResponse;
import com.aninditb.shortlink.dto.ShortUrlResponse;
import com.aninditb.shortlink.dto.UrlDetailsResponse;

public interface ShortUrlService {

    ShortUrlResponse create(CreateShortUrlRequest request);

    String resolve(String shortCode);

    UrlDetailsResponse getDetails(Long id);

    void delete(Long id);

    void disable(Long id);

    PagedUrlResponse listOwnUrls(int limit, Long cursor);
}
