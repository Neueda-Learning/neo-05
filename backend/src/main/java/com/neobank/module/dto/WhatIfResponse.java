package com.neobank.module.dto;

import java.util.List;

/** UC05 response contract: summary plus flips-only list. */
public record WhatIfResponse(
        int evaluated,
        int flips,
        List<WhatIfChangeView> changes) {
}
