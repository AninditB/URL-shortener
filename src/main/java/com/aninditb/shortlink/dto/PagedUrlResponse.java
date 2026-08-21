package com.aninditb.shortlink.dto;

import java.util.List;

public record PagedUrlResponse(List<UrlDetailsResponse> items, Long nextCursor) {
}
