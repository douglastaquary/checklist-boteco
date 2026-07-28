package com.checklistboteco.receipt

object CategoryClassifier {
    private val rules: List<Pair<String, List<String>>> = listOf(
        "Bebidas" to listOf(
            "cerveja", "heineken", "brahma", "skol", "antarctica", "amstel", "corona",
            "refrigerante", "coca", "guarana", "sprite", "fanta", "agua", "suco",
            "vinho", "whisky", "vodka", "gin", "energetico", "red bull", "monster",
            "cha mate", "ice", "long neck"
        ),
        "Alimentos" to listOf(
            "carne", "frango", "peixe", "bovina", "suina", "linguica", "salsicha",
            "queijo", "presunto", "pao", "farinha", "arroz", "feijao", "oleo",
            "sal", "acucar", "leite", "ovos", "batata", "cebola", "tomate",
            "alface", "limao", "laranja", "massa", "macarrao", "molho", "ketchup",
            "maionese", "mostarda", "temper", "alho", "manteiga", "margarina",
            "sorvete", "doce", "chocolate", "biscuit", "bolacha", "snack",
            "amendoim", "castanha", "bacon", "hamburguer", "file", "costela"
        ),
        "Limpeza" to listOf(
            "detergente", "sabao", "sabonete", "desinfetante", "alcool", "agua sanitaria",
            "esponja", "papel higienico", "papel toalha", "guardanapo", "limpador",
            "multiuso", "cloro", "amaciante", "lavanda", "saco lixo", "luva"
        ),
        "Descartáveis" to listOf(
            "copo", "prato", "talher", "canudo", "embalagem", "filme", "aluminio",
            "papel filme", "bandeja", "pote", "caixa", "sacola"
        ),
        "Utilidades" to listOf(
            "pilha", "bateria", "lampada", "isqueiro", "vela", "fita", "cola"
        )
    )

    fun classify(description: String): String {
        val normalized = normalize(description)
        for ((category, keywords) in rules) {
            if (keywords.any { keyword -> normalized.contains(normalize(keyword)) }) {
                return category
            }
        }
        return "Outros"
    }

    private fun normalize(value: String): String {
        return value.lowercase()
            .replace('á', 'a').replace('à', 'a').replace('ã', 'a').replace('â', 'a')
            .replace('é', 'e').replace('ê', 'e')
            .replace('í', 'i')
            .replace('ó', 'o').replace('ô', 'o').replace('õ', 'o')
            .replace('ú', 'u').replace('ü', 'u')
            .replace('ç', 'c')
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
