package com.raccoonsquad.core.util

import com.raccoonsquad.core.log.LogManager

object CountryFlags {
    private const val TAG = "CountryFlags"
    
    // Country code to emoji flag
    fun getFlag(countryCode: String?): String {
        if (countryCode.isNullOrBlank()) return "🌐"
        
        val code = countryCode.uppercase().take(2)
        
        // Convert country code to flag emoji
        return try {
            val base = 0x1F1E6 - 'A'.code
            val first = Character.toChars(base + code[0].code)
            val second = Character.toChars(base + code[1].code)
            String(first) + String(second)
        } catch (e: Exception) {
            "🌐"
        }
    }
    
    // Detect country from server address or IP
    fun detectCountry(serverAddress: String): String {
        val address = serverAddress.lowercase().trim()
        
        // Check for known hosting providers and regions
        return when {
            // Russia
            address.contains(".ru") || 
            address.contains("moscow") ||
            address.contains("msk") ||
            address.contains("spb") ||
            address.contains("russia") -> "RU"
            
            // Netherlands
            address.contains(".nl") ||
            address.contains("amsterdam") ||
            address.contains("netherlands") -> "NL"
            
            // Germany
            address.contains(".de") ||
            address.contains("frankfurt") ||
            address.contains("berlin") ||
            address.contains("germany") -> "DE"
            
            // Finland
            address.contains(".fi") ||
            address.contains("helsinki") ||
            address.contains("finland") -> "FI"
            
            // USA
            address.contains(".us") ||
            address.contains("usa") ||
            address.contains("america") ||
            address.contains("new-york") ||
            address.contains("newyork") ||
            address.contains("los-angeles") ||
            address.contains("california") -> "US"
            
            // United Kingdom
            address.contains(".uk") ||
            address.contains("london") ||
            address.contains("britain") -> "GB"
            
            // France
            address.contains(".fr") ||
            address.contains("paris") ||
            address.contains("france") -> "FR"
            
            // Japan
            address.contains(".jp") ||
            address.contains("tokyo") ||
            address.contains("japan") -> "JP"
            
            // Singapore
            address.contains(".sg") ||
            address.contains("singapore") -> "SG"
            
            // Hong Kong
            address.contains(".hk") ||
            address.contains("hongkong") ||
            address.contains("hong-kong") -> "HK"
            
            // Ukraine
            address.contains(".ua") ||
            address.contains("kyiv") ||
            address.contains("kiev") ||
            address.contains("ukraine") -> "UA"
            
            // Kazakhstan
            address.contains(".kz") ||
            address.contains("kazakhstan") ||
            address.contains("almaty") -> "KZ"
            
            // Belarus
            address.contains(".by") ||
            address.contains("belarus") ||
            address.contains("minsk") -> "BY"
            
            // Moldova
            address.contains(".md") ||
            address.contains("moldova") ||
            address.contains("chisinau") -> "MD"
            
            // Romania
            address.contains(".ro") ||
            address.contains("romania") ||
            address.contains("bucharest") -> "RO"
            
            // Poland
            address.contains(".pl") ||
            address.contains("poland") ||
            address.contains("warsaw") -> "PL"
            
            // Turkey
            address.contains(".tr") ||
            address.contains("turkey") ||
            address.contains("istanbul") -> "TR"
            
            // Switzerland
            address.contains(".ch") ||
            address.contains("switzerland") ||
            address.contains("zurich") -> "CH"
            
            // Iceland
            address.contains(".is") ||
            address.contains("iceland") ||
            address.contains("reykjavik") -> "IS"
            
            // Sweden
            address.contains(".se") ||
            address.contains("sweden") ||
            address.contains("stockholm") -> "SE"
            
            // Canada
            address.contains(".ca") ||
            address.contains("canada") ||
            address.contains("toronto") -> "CA"
            
            // Australia
            address.contains(".au") ||
            address.contains("australia") ||
            address.contains("sydney") -> "AU"
            
            // Austria
            address.contains(".at") ||
            address.contains("austria") ||
            address.contains("vienna") -> "AT"
            
            // Belgium
            address.contains(".be") ||
            address.contains("belgium") ||
            address.contains("brussels") -> "BE"
            
            // Czech Republic
            address.contains(".cz") ||
            address.contains("czech") ||
            address.contains("prague") -> "CZ"
            
            // Spain
            address.contains(".es") ||
            address.contains("spain") ||
            address.contains("madrid") -> "ES"
            
            // Italy
            address.contains(".it") ||
            address.contains("italy") ||
            address.contains("milan") -> "IT"
            
            // Portugal
            address.contains(".pt") ||
            address.contains("portugal") ||
            address.contains("lisbon") -> "PT"
            
            // Ireland
            address.contains(".ie") ||
            address.contains("ireland") ||
            address.contains("dublin") -> "IE"
            
            // Denmark
            address.contains(".dk") ||
            address.contains("denmark") ||
            address.contains("copenhagen") -> "DK"
            
            // Norway
            address.contains(".no") ||
            address.contains("norway") ||
            address.contains("oslo") -> "NO"
            
            // Latvia
            address.contains(".lv") ||
            address.contains("latvia") ||
            address.contains("riga") -> "LV"
            
            // Lithuania
            address.contains(".lt") ||
            address.contains("lithuania") ||
            address.contains("vilnius") -> "LT"
            
            // Estonia
            address.contains(".ee") ||
            address.contains("estonia") ||
            address.contains("tallinn") -> "EE"
            
            // Bulgaria
            address.contains(".bg") ||
            address.contains("bulgaria") ||
            address.contains("sofia") -> "BG"
            
            // Hungary
            address.contains(".hu") ||
            address.contains("hungary") ||
            address.contains("budapest") -> "HU"
            
            // Greece
            address.contains(".gr") ||
            address.contains("greece") ||
            address.contains("athens") -> "GR"
            
            // Croatia
            address.contains(".hr") ||
            address.contains("croatia") ||
            address.contains("zagreb") -> "HR"
            
            // Serbia
            address.contains(".rs") ||
            address.contains("serbia") ||
            address.contains("belgrade") -> "RS"
            
            // Slovakia
            address.contains(".sk") ||
            address.contains("slovakia") ||
            address.contains("bratislava") -> "SK"
            
            // Slovenia
            address.contains(".si") ||
            address.contains("slovenia") ||
            address.contains("ljubljana") -> "SI"
            
            // Luxembourg
            address.contains(".lu") ||
            address.contains("luxembourg") -> "LU"
            
            // Mexico
            address.contains(".mx") ||
            address.contains("mexico") -> "MX"
            
            // Brazil
            address.contains(".br") ||
            address.contains("brazil") ||
            address.contains("saopaulo") ||
            address.contains("sao-paulo") -> "BR"
            
            // Argentina
            address.contains(".ar") ||
            address.contains("argentina") ||
            address.contains("buenos-aires") -> "AR"
            
            // Chile
            address.contains(".cl") ||
            address.contains("chile") ||
            address.contains("santiago") -> "CL"
            
            // Colombia
            address.contains(".co") ||
            address.contains("colombia") -> "CO"
            
            // South Africa
            address.contains(".za") ||
            address.contains("southafrica") ||
            address.contains("south-africa") ||
            address.contains("johannesburg") -> "ZA"
            
            // Israel
            address.contains(".il") ||
            address.contains("israel") ||
            address.contains("tel-aviv") -> "IL"
            
            // UAE
            address.contains(".ae") ||
            address.contains("dubai") ||
            address.contains("uae") -> "AE"
            
            // Saudi Arabia
            address.contains(".sa") ||
            address.contains("saudi") ||
            address.contains("riyadh") -> "SA"
            
            // India
            address.contains(".in") ||
            address.contains("india") ||
            address.contains("mumbai") ||
            address.contains("delhi") -> "IN"
            
            // South Korea
            address.contains(".kr") ||
            address.contains("korea") ||
            address.contains("seoul") -> "KR"
            
            // Taiwan
            address.contains(".tw") ||
            address.contains("taiwan") ||
            address.contains("taipei") -> "TW"
            
            // Vietnam
            address.contains(".vn") ||
            address.contains("vietnam") ||
            address.contains("hanoi") -> "VN"
            
            // Thailand
            address.contains(".th") ||
            address.contains("thailand") ||
            address.contains("bangkok") -> "TH"
            
            // Malaysia
            address.contains(".my") ||
            address.contains("malaysia") ||
            address.contains("kuala-lumpur") -> "MY"
            
            // Indonesia
            address.contains(".id") ||
            address.contains("indonesia") ||
            address.contains("jakarta") -> "ID"
            
            // Philippines
            address.contains(".ph") ||
            address.contains("philippines") ||
            address.contains("manila") -> "PH"
            
            // New Zealand
            address.contains(".nz") ||
            address.contains("newzealand") ||
            address.contains("new-zealand") ||
            address.contains("auckland") -> "NZ"
            
            // Unknown
            else -> ""
        }
    }
    
    // Get flag for server address
    fun getFlagForServer(serverAddress: String): String {
        val country = detectCountry(serverAddress)
        return getFlag(country)
    }
    
    // Country name in Russian
    fun getCountryName(countryCode: String?): String {
        return when (countryCode?.uppercase()) {
            "RU" -> "Россия"
            "NL" -> "Нидерланды"
            "DE" -> "Германия"
            "FI" -> "Финляндия"
            "US" -> "США"
            "GB" -> "Великобритания"
            "FR" -> "Франция"
            "JP" -> "Япония"
            "SG" -> "Сингапур"
            "HK" -> "Гонконг"
            "UA" -> "Украина"
            "KZ" -> "Казахстан"
            "BY" -> "Беларусь"
            "MD" -> "Молдова"
            "RO" -> "Румыния"
            "PL" -> "Польша"
            "TR" -> "Турция"
            "CH" -> "Швейцария"
            "IS" -> "Исландия"
            "SE" -> "Швеция"
            "CA" -> "Канада"
            "AU" -> "Австралия"
            "AT" -> "Австрия"
            "BE" -> "Бельгия"
            "CZ" -> "Чехия"
            "ES" -> "Испания"
            "IT" -> "Италия"
            "PT" -> "Португалия"
            "IE" -> "Ирландия"
            "DK" -> "Дания"
            "NO" -> "Норвегия"
            "LV" -> "Латвия"
            "LT" -> "Литва"
            "EE" -> "Эстония"
            "BG" -> "Болгария"
            "HU" -> "Венгрия"
            "GR" -> "Греция"
            "HR" -> "Хорватия"
            "RS" -> "Сербия"
            "SK" -> "Словакия"
            "SI" -> "Словения"
            "LU" -> "Люксембург"
            "MX" -> "Мексика"
            "BR" -> "Бразилия"
            "AR" -> "Аргентина"
            "CL" -> "Чили"
            "CO" -> "Колумбия"
            "ZA" -> "ЮАР"
            "IL" -> "Израиль"
            "AE" -> "ОАЭ"
            "SA" -> "Саудовская Аравия"
            "IN" -> "Индия"
            "KR" -> "Южная Корея"
            "TW" -> "Тайвань"
            "VN" -> "Вьетнам"
            "TH" -> "Таиланд"
            "MY" -> "Малайзия"
            "ID" -> "Индонезия"
            "PH" -> "Филиппины"
            "NZ" -> "Новая Зеландия"
            else -> "Неизвестно"
        }
    }
}
