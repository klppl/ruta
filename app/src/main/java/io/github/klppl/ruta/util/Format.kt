package io.github.klppl.ruta.util

/** 1_234 -> "1,234"; 2_500_000 -> "2.5M". Keeps counts compact without a locale dep. */
fun formatCount(n: Long): String = when {
    n < 1_000 -> n.toString()
    n < 1_000_000 -> {
        val s = n.toString()
        val head = s.dropLast(3)
        val tail = s.takeLast(3)
        "$head,$tail"
    }
    else -> {
        val millions = n / 100_000L / 10.0
        if (millions % 1.0 == 0.0) "${millions.toLong()}M" else "${millions}M"
    }
}
