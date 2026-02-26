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
            else -> "Неизвестно"
        }
    }
}
