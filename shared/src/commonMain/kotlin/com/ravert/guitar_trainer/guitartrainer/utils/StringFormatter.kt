package com.ravert.guitar_trainer.guitartrainer.utils

import kotlin.math.roundToInt

fun Float.format(digits: Int): String =
    "${(this / 1000f).let { (it * 100).roundToInt() / 100f }}"
