package com.ravert.guitar_trainer.db

enum class MembershipTier(val apiValue: String) {
    PREMIUM("premium"),
    PREMIUM_PLUS("premium_plus");

    companion object {
        fun fromApiValue(value: String?): MembershipTier? =
            entries.firstOrNull { it.apiValue == value?.trim()?.lowercase() }
    }
}
