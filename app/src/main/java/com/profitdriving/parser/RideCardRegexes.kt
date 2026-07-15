package com.profitdriving.parser

// ─── VALOR ───
private val RE_VALUE = Regex("""(?:^|\s)R\$\s*(\d+(?:[.,]\d+)?)(?=\s|$)""", RegexOption.IGNORE_CASE)
private val RE_KM_PER_REAL = Regex("""R\$(\d+[.,]\d+)\s*/\s*km""", RegexOption.IGNORE_CASE)
private val RE_KM_REAL = Regex("""R\$(\d+[.,]\d+)\s*por km""", RegexOption.IGNORE_CASE)
private val RE_HOUR_REAL = Regex("""R\$(\d+[.,]\d+)\s*/\s*h""", RegexOption.IGNORE_CASE)

// ─── PICKUP ───
private val RE_PICKUP = Regex(
    """(\d+)\s*min(?:uto)?s?\s*\(\s*(\d+[.,]\d+)\s*km\s*\)""",
    RegexOption.IGNORE_CASE
)
private val RE_PICKUP_OLD = Regex(
    """(\d+)\s*min(?:uto)?s?\s*\(\s*(\d+[.,]\d+)\s*km\s*\)\s*de\s*dist[âa]ncia""",
    RegexOption.IGNORE_CASE
)

// ─── TRIP ───
private val RE_TRIP = Regex(
    """(?:(\d+)\s*[Hh](?:ora(?:s)?)?(?:\s*e\s*)?)?(?:(\d+)\s*[Mm]in(?:uto)?s?\s*)?\((\d+[.,]\d+)\s*km\)""",
    RegexOption.IGNORE_CASE
)
private val RE_TRIP_EXCLUSIVE = Regex(
    """(?:(\d+)\s*[Hh](?:ora(?:s)?)?(?:\s*e\s*)?)?(?:(\d+)\s*[Mm]in(?:uto)?s?\s*)?\((\d+[.,]\d+)\s*km\)(?!\s*de\s*dist[âa]ncia)""",
    RegexOption.IGNORE_CASE
)
private val RE_TRIP_OLD = Regex(
    """[Vv]iagem\s+de\s+(?:(\d+)\s*[Hh](?:ora(?:s)?)?\s*e\s*)?(\d+)\s*[Mm]in(?:uto)?s?\s*\((\d+[.,]\d+)\s*km\)""",
    RegexOption.IGNORE_CASE
)
private val RE_TRIP_EXCLUSIVE_OLD = Regex(
    """[Vv]iagem\s+de\s+(?:(\d+)\s*[Hh](?:ora(?:s)?)?\s*e\s*)?(\d+)\s*[Mm]in(?:uto)?s?\s*\((\d+[.,]\d+)\s*km\)(?!\s*de\s*dist[âa]ncia)""",
    RegexOption.IGNORE_CASE
)

// ─── FALLBACK ───
private val RE_DISTANCE = listOf(
    Regex("""\((\d+[.,]?\d*)\s*km""", RegexOption.IGNORE_CASE),
    Regex("""(\d+[.,]?\d*)\s*km""", RegexOption.IGNORE_CASE),
    Regex("""km[:\s]*(\d+[.,]?\d*)""", RegexOption.IGNORE_CASE),
    Regex("""(\d+[.,]?\d*)\s*quilômetro""", RegexOption.IGNORE_CASE),
    Regex("""dist[âa]ncia[:\s]*(\d+[.,]?\d*)\s*km""", RegexOption.IGNORE_CASE),
    Regex("""(\d+[.,]?\d*)\s*km\s*de\s*dist[âa]ncia""", RegexOption.IGNORE_CASE)
)
private val RE_TIME = listOf(
    Regex("""(\d+)\s*[Mm]in(?:uto)?s?"""),
    Regex("""(\d+)\s*[Hh](?:ora)?s?"""),
    Regex("""tempo[:\s]*(\d+)\s*min""", RegexOption.IGNORE_CASE),
    Regex("""duração[:\s]*(\d+)\s*min""", RegexOption.IGNORE_CASE)
)

// ─── RATING ───
private val RE_RATING_STAR = Regex("""(\d[.,]\d{1,2})\s*[★⭐*]""")
private val RE_RATING_COUNT = Regex("""(\d[.,]\d{1,2})\s*\(\d+\)""")
private val RE_RATING_BULLET = Regex("""(\d[.,]\d{1,2})\s*[·•]""")
private val RE_RATING_DECIMAL = Regex("""(\d[.,]\d{1,2})""")

// ─── SERVIÇO ───
typealias ServiceTypeEntry = Pair<Regex, String>
val SERVICE_TYPES: List<ServiceTypeEntry> = listOf(
    "uberx" to "UberX", "uber flash" to "Flash", "uber juntos" to "Juntos",
    "uber moto" to "Moto", "uber black" to "Black", "uber comfort" to "Comfort",
    "uber bag" to "Black Bag", "uber priority" to "Prioridade",
    "business comfort" to "Business Comfort", "business black" to "Business Black",
    "envios moto" to "Envios Moto", "envios carro" to "Envios Carro",
    "black bag" to "Black Bag", "flash" to "Flash", "juntos" to "Juntos",
    "moto" to "Moto", "black" to "Black", "bag" to "Black Bag",
    "comfort" to "Comfort", "priority" to "Prioridade",
    "pop" to "Pop", "top" to "Top", "entrega" to "Entrega"
).map { (keyword, label) ->
    Regex("\\b${Regex.escape(keyword)}\\b", RegexOption.IGNORE_CASE) to label
}

// ─── BÔNUS ───
private val RE_BONUS_PRIORITY = Regex(
    """\+R\$\s*(\d+(?:[.,]\d+)?)\s*inclu[íi]do\s+para\s+embarque\s+priorit[áa]rio""",
    RegexOption.IGNORE_CASE
)
private val RE_BONUS_DYNAMIC = Regex(
    """\+R\$\s*(\d+(?:[.,]\d+)?)\s*inclu[íi]do(?!\s+para\s+embarque\s+priorit[áa]rio)""",
    RegexOption.IGNORE_CASE
)

// ─── ENDEREÇO ───
private val RE_ADDR_LINE = Regex(
    """(?:\d+\s*min(?:uto)?s?\s*\([\d.,]+\s*km\)|\d+\s*[Hh](?:ora(?:s)?)?\s*\([\d.,]+\s*km\))\s*\n\s*([^\n]+)""",
    RegexOption.IGNORE_CASE
)
private val RE_ADDR_INLINE = Regex(
    """(?:\d+\s*min(?:uto)?s?\s*\([\d.,]+\s*km\)|\d+\s*[Hh](?:ora(?:s)?)?\s*\([\d.,]+\s*km\))\s+([A-Za-zÀ-Úà-ú0-9\s,./°-]+?)(?=\s*(?:\d+\s*min(?:uto)?s?\s*\([\d.,]+\s*km\)|\d+\s*[Hh](?:ora(?:s)?)?\s*\([\d.,]+\s*km\))|\s*(?:Reservas|Aceitar|Informações|Selecionar|$))""",
    RegexOption.IGNORE_CASE
)
private val RE_ADDR_PICKUP_OLD = Regex(
    """\d+\s*min(?:uto)?s?\s*\([\d.,]+\s*km\)\s*de\s*dist[âa]ncia\s+([A-Za-zÀ-Úà-ú0-9\s,.-]+?)(?=\s*(?:\d+\s*min|Viagem|Aceitar|$))""",
    RegexOption.IGNORE_CASE
)
private val RE_ADDR_DROPOFF_OLD = Regex(
    """Viagem\s+de\s+\d+\s*min(?:uto)?s?\s*\([\d.,]+\s*km\)\s+([A-Za-zÀ-Úà-ú0-9\s,.-]+?\d{5}-\d{3})""",
    RegexOption.IGNORE_CASE
)

// ════════════════════════════════════════════
//  EXPOSIÇÃO PARA PARSERS
// ════════════════════════════════════════════
object RideCardRegexes {
    // ─── VALOR ───
    val value: Regex get() = RE_VALUE
    val kmPerReal: Regex get() = RE_KM_PER_REAL
    val kmReal: Regex get() = RE_KM_REAL
    val hourReal: Regex get() = RE_HOUR_REAL

    // ─── PICKUP ───
    val pickup: Regex get() = RE_PICKUP
    val pickupOld: Regex get() = RE_PICKUP_OLD

    // ─── TRIP ───
    val trip: Regex get() = RE_TRIP
    val tripExclusive: Regex get() = RE_TRIP_EXCLUSIVE
    val tripOld: Regex get() = RE_TRIP_OLD
    val tripExclusiveOld: Regex get() = RE_TRIP_EXCLUSIVE_OLD

    // ─── FALLBACK ───
    val distanceFallback: List<Regex> get() = RE_DISTANCE
    val timeFallback: List<Regex> get() = RE_TIME

    // ─── RATING ───
    val ratingStar: Regex get() = RE_RATING_STAR
    val ratingCount: Regex get() = RE_RATING_COUNT
    val ratingBullet: Regex get() = RE_RATING_BULLET
    val ratingDecimal: Regex get() = RE_RATING_DECIMAL

    // ─── BÔNUS ───
    val bonusPriority: Regex get() = RE_BONUS_PRIORITY
    val bonusDynamic: Regex get() = RE_BONUS_DYNAMIC

    // ─── ENDEREÇO ───
    val addressLine: Regex get() = RE_ADDR_LINE
    val addressInline: Regex get() = RE_ADDR_INLINE
    val addressPickupOld: Regex get() = RE_ADDR_PICKUP_OLD
    val addressDropoffOld: Regex get() = RE_ADDR_DROPOFF_OLD
}
