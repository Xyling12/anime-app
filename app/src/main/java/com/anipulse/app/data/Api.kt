package com.anipulse.app.data

/**
 * Единая точка адресов API. Всё ходит через наш шлюз на сервере (дата-центр в РФ),
 * который обходит блокировки провайдеров (Shikimori режется по SNI) и ddos-guard.
 * Сменить домен позже — только здесь.
 */
object Api {
    // Бесплатный тестовый домен sslip.io. Позже заменим на anipulse.duckdns.org / свой.
    const val GATEWAY = "https://anipulsetv.ru/alapi/"

    const val SHIKIMORI = GATEWAY + "shikimori/"
    const val ANILIBRIA = GATEWAY + "anilibria/"
    const val ANIMEGO = GATEWAY + "animego/"

    /** База для сборки URL картинок Shikimori (постеры тоже через шлюз). */
    const val SHIKIMORI_IMAGES = GATEWAY + "shikimori"
}
