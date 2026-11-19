package com.poseidon.ad.mediation.admob

import java.math.BigDecimal
import java.math.RoundingMode

internal fun roundRevenueScale6(revenue: Double): Double {
    return BigDecimal.valueOf(revenue)
        .setScale(6, RoundingMode.HALF_UP)
        .toDouble()
}

internal fun extPriceToRoundedRevenue(extPrice: Double): Double {
    if (extPrice <= 0.0) return extPrice
    return roundRevenueScale6(extPrice / 1000.0)
}
